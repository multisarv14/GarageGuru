package com.example.data.permissions

import org.json.JSONObject

/**
 * A part of the app a role can be granted access to.
 *
 * [tabKey] ties a module to the tab router in `MainAppScreen`, which is what lets a permission
 * check gate navigation itself rather than only hiding a menu entry — on a phone there is no URL
 * to type, but a tab can still be reached programmatically, so the router is the real door.
 */
enum class AppModule(
    val key: String,
    val label: String,
    val tabKey: String?,
    val supportsApprove: Boolean = false
) {
    DASHBOARD("dashboard", "Dashboard", "dashboard"),
    JOB_CARDS("jobCards", "Job Cards", "jobs", supportsApprove = true),
    CUSTOMERS("customers", "Customers", "customers"),
    INVENTORY("inventory", "Inventory / Stock", "system"),
    CASHBOOK("cashbook", "Cashbook", "cashbook", supportsApprove = true),
    REPORTS("reports", "Reports", "reports"),
    TEAM("team", "Team & Roles", null),
    SETTINGS("settings", "Garage Settings", null);

    companion object {
        fun from(key: String?): AppModule? = entries.firstOrNull { it.key.equals(key, true) }

        /** The module that owns a tab, so the router can ask "may this user open this?". */
        fun forTab(tab: String): AppModule? = entries.firstOrNull { it.tabKey == tab }
    }
}

/** What a role may do inside a module. */
enum class PermissionAction(val key: String, val label: String) {
    VIEW("view", "View"),
    CREATE("create", "Add / Create"),
    EDIT("edit", "Edit / Update"),
    DELETE("delete", "Delete"),
    EXPORT("export", "Export / Download"),
    APPROVE("approve", "Approve");

    companion object {
        fun from(key: String?): PermissionAction? = entries.firstOrNull { it.key.equals(key, true) }

        /** Approve only appears where it means something. */
        fun availableFor(module: AppModule): List<PermissionAction> =
            entries.filter { it != APPROVE || module.supportsApprove }
    }
}

/**
 * What one role is allowed to do, as module → actions.
 *
 * Deliberately a value type with no reference to the UI: the same object answers the menu, the
 * tab router and the permission list written onto the user's `admin` document for the security
 * rules, so all three can never disagree about what a role means.
 */
data class PermissionSet(
    private val grants: Map<AppModule, Set<PermissionAction>> = emptyMap()
) {
    fun actionsFor(module: AppModule): Set<PermissionAction> = grants[module].orEmpty()

    fun can(module: AppModule, action: PermissionAction): Boolean =
        grants[module]?.contains(action) == true

    /** Whether the module should appear at all. Anything granted implies at least visibility. */
    fun canView(module: AppModule): Boolean = grants[module]?.isNotEmpty() == true

    val grantedModules: List<AppModule> get() = AppModule.entries.filter { canView(it) }

    val isEmpty: Boolean get() = grants.values.all { it.isEmpty() }

    fun withAction(module: AppModule, action: PermissionAction, granted: Boolean): PermissionSet {
        val current = actionsFor(module).toMutableSet()
        if (granted) {
            current.add(action)
            // Nothing can be done to a module that cannot even be seen, so granting any action
            // implies View — this stops a role being saved in a state it can never use.
            current.add(PermissionAction.VIEW)
        } else {
            current.remove(action)
            // Taking View away takes the whole module away, for the same reason.
            if (action == PermissionAction.VIEW) current.clear()
        }
        return PermissionSet(grants + (module to current))
    }

    /** `jobCards.create` style tokens, which is the shape the security rules match on. */
    fun toTokens(): List<String> = grants.entries
        .flatMap { (module, actions) -> actions.map { "${module.key}.${it.key}" } }
        .sorted()

    fun toJson(): String {
        val root = JSONObject()
        grants.forEach { (module, actions) ->
            if (actions.isNotEmpty()) root.put(module.key, actions.joinToString(",") { it.key })
        }
        return root.toString()
    }

    companion object {
        /** Everything, for the owner. */
        fun all(): PermissionSet = PermissionSet(
            AppModule.entries.associateWith { PermissionAction.availableFor(it).toSet() }
        )

        /** A sensible starting point when a new role is created: read-only across the shop floor. */
        fun viewOnlyDefaults(): PermissionSet = PermissionSet(
            listOf(AppModule.DASHBOARD, AppModule.JOB_CARDS, AppModule.CUSTOMERS)
                .associateWith { setOf(PermissionAction.VIEW) }
        )

        fun fromJson(raw: String?): PermissionSet {
            if (raw.isNullOrBlank()) return PermissionSet()
            return try {
                val root = JSONObject(raw)
                val grants = mutableMapOf<AppModule, Set<PermissionAction>>()
                root.keys().forEach { key ->
                    val module = AppModule.from(key) ?: return@forEach
                    val actions = root.optString(key, "")
                        .split(",")
                        .mapNotNull { PermissionAction.from(it.trim()) }
                        .toSet()
                    if (actions.isNotEmpty()) grants[module] = actions
                }
                PermissionSet(grants)
            } catch (e: Exception) {
                PermissionSet()
            }
        }
    }
}
