package com.example

import com.example.data.api.GarageSession
import com.example.data.permissions.AppModule
import com.example.data.permissions.PermissionAction
import com.example.data.permissions.PermissionSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Permissions decide both what a user sees and what the database will accept, so a mistake here
 * either locks somebody out of their own work or opens a module they were never granted. The
 * encoding is covered too, because the same string is what the security rules match on.
 */
// Runs under Robolectric because the permission encoding uses org.json, which is a
// throw-on-call stub in a plain JVM unit test.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PermissionsTest {

    // --- The example role from the spec ------------------------------------

    private fun salesRole(): PermissionSet = PermissionSet()
        .withAction(AppModule.DASHBOARD, PermissionAction.VIEW, true)
        .withAction(AppModule.CUSTOMERS, PermissionAction.VIEW, true)
        .withAction(AppModule.CUSTOMERS, PermissionAction.CREATE, true)
        .withAction(AppModule.CUSTOMERS, PermissionAction.EDIT, true)
        .withAction(AppModule.JOB_CARDS, PermissionAction.VIEW, true)
        .withAction(AppModule.JOB_CARDS, PermissionAction.CREATE, true)
        .withAction(AppModule.JOB_CARDS, PermissionAction.EDIT, true)
        .withAction(AppModule.INVENTORY, PermissionAction.VIEW, true)
        .withAction(AppModule.REPORTS, PermissionAction.VIEW, true)

    @Test
    fun `the sales role grants exactly what it was given`() {
        val sales = salesRole()

        assertTrue(sales.can(AppModule.CUSTOMERS, PermissionAction.CREATE))
        assertTrue(sales.can(AppModule.JOB_CARDS, PermissionAction.EDIT))
        assertTrue(sales.canView(AppModule.INVENTORY))
        assertTrue(sales.canView(AppModule.REPORTS))

        // Not granted: inventory is view-only, and nothing may be deleted.
        assertFalse(sales.can(AppModule.INVENTORY, PermissionAction.EDIT))
        assertFalse(sales.can(AppModule.CUSTOMERS, PermissionAction.DELETE))
        assertFalse(sales.can(AppModule.JOB_CARDS, PermissionAction.DELETE))
        // Modules never mentioned stay closed.
        assertFalse(sales.canView(AppModule.CASHBOOK))
        assertFalse(sales.canView(AppModule.TEAM))
        assertFalse(sales.canView(AppModule.SETTINGS))
    }

    // --- Coherence rules ---------------------------------------------------

    @Test
    fun `granting any action implies view`() {
        // A role that may edit job cards but not see them would be unusable.
        val set = PermissionSet().withAction(AppModule.JOB_CARDS, PermissionAction.EDIT, true)

        assertTrue(set.canView(AppModule.JOB_CARDS))
        assertTrue(set.can(AppModule.JOB_CARDS, PermissionAction.VIEW))
    }

    @Test
    fun `removing view closes the whole module`() {
        val set = salesRole().withAction(AppModule.CUSTOMERS, PermissionAction.VIEW, false)

        assertFalse(set.canView(AppModule.CUSTOMERS))
        assertFalse(set.can(AppModule.CUSTOMERS, PermissionAction.CREATE))
        assertFalse(set.can(AppModule.CUSTOMERS, PermissionAction.EDIT))
    }

    @Test
    fun `approve is only offered where it means something`() {
        assertTrue(PermissionAction.availableFor(AppModule.JOB_CARDS).contains(PermissionAction.APPROVE))
        assertFalse(PermissionAction.availableFor(AppModule.CUSTOMERS).contains(PermissionAction.APPROVE))
    }

    // --- Encoding ----------------------------------------------------------

    @Test
    fun `a role survives a round trip through storage`() {
        val original = salesRole()
        val restored = PermissionSet.fromJson(original.toJson())

        AppModule.entries.forEach { module ->
            assertEquals(
                "module $module",
                original.actionsFor(module),
                restored.actionsFor(module)
            )
        }
    }

    @Test
    fun `tokens are the module dot action form the rules match on`() {
        val tokens = PermissionSet()
            .withAction(AppModule.JOB_CARDS, PermissionAction.CREATE, true)
            .toTokens()

        assertTrue(tokens.contains("jobCards.create"))
        assertTrue(tokens.contains("jobCards.view"))
        assertFalse(tokens.contains("jobCards.delete"))
    }

    @Test
    fun `unreadable stored permissions grant nothing rather than everything`() {
        // Failing closed matters: a corrupt role must never become a skeleton key.
        assertTrue(PermissionSet.fromJson("not json at all").isEmpty)
        assertTrue(PermissionSet.fromJson("").isEmpty)
        assertTrue(PermissionSet.fromJson(null).isEmpty)
    }

    @Test
    fun `unknown modules and actions are ignored, not guessed at`() {
        val set = PermissionSet.fromJson("""{"jobCards":"view,teleport","warpDrive":"view"}""")

        assertTrue(set.can(AppModule.JOB_CARDS, PermissionAction.VIEW))
        assertEquals(setOf(PermissionAction.VIEW), set.actionsFor(AppModule.JOB_CARDS))
        assertEquals(listOf(AppModule.JOB_CARDS), set.grantedModules)
    }

    // --- The signed-in user ------------------------------------------------

    @Test
    fun `the owner is never locked out whatever the role says`() {
        val owner = GarageSession.SignedInUser(isOwner = true, permissions = PermissionSet())

        assertTrue(owner.canView(AppModule.CASHBOOK))
        assertTrue(owner.can(AppModule.SETTINGS, PermissionAction.DELETE))
    }

    @Test
    fun `a team member gets only their assigned role`() {
        val user = GarageSession.SignedInUser(
            roleName = "Sales",
            isOwner = false,
            permissions = salesRole()
        )

        assertTrue(user.canView(AppModule.JOB_CARDS))
        assertFalse(user.canView(AppModule.CASHBOOK))
        assertFalse(user.can(AppModule.JOB_CARDS, PermissionAction.DELETE))
    }

    @Test
    fun `every tab maps back to the module that guards it`() {
        // The router looks a tab up by key; a typo here would silently ungate a screen.
        assertEquals(AppModule.JOB_CARDS, AppModule.forTab("jobs"))
        assertEquals(AppModule.INVENTORY, AppModule.forTab("system"))
        assertEquals(AppModule.CASHBOOK, AppModule.forTab("cashbook"))
        assertEquals(AppModule.CUSTOMERS, AppModule.forTab("customers"))
        assertEquals(AppModule.REPORTS, AppModule.forTab("reports"))
        assertEquals(AppModule.DASHBOARD, AppModule.forTab("dashboard"))
        // The account screen is deliberately always reachable.
        assertEquals(null, AppModule.forTab("account"))
    }
}
