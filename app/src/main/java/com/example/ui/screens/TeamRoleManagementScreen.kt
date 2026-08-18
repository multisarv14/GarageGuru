package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import com.example.data.api.FirebaseAuthHelper
import com.example.data.api.GarageSession
import com.example.data.model.RoleEntity
import com.example.data.model.TeamEntity
import com.example.data.model.TeamMemberEntity
import com.example.data.permissions.AppModule
import com.example.data.permissions.PermissionAction
import com.example.data.permissions.PermissionSet
import com.example.ui.GarageViewModel

private val Indigo = Color(0xFF4F46E5)
private val Slate = Color(0xFF0F172A)
private val Muted = Color(0xFF64748B)

/**
 * Team & Role Management.
 *
 * Two tabs on purpose: who the people are is a different job from what a job title is allowed to
 * do, and mixing them was what made the old single sheet hard to reason about. Teams and users
 * live on the left, roles and their module permissions on the right, and nothing here deletes
 * anything — a user who leaves is deactivated so the job cards they raised still name a real
 * person.
 */
@Composable
fun TeamRoleManagementScreen(
    viewModel: GarageViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val members by viewModel.teamMembers.collectAsState()
    val teams by viewModel.teams.collectAsState()
    val roles by viewModel.roles.collectAsState()
    val signedIn by GarageSession.signedInUser.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    var editingTeam by remember { mutableStateOf<TeamEntity?>(null) }
    var showTeamDialog by remember { mutableStateOf(false) }
    var editingRole by remember { mutableStateOf<RoleEntity?>(null) }
    var showRoleDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<TeamMemberEntity?>(null) }
    var showUserDialog by remember { mutableStateOf(false) }
    var profileUser by remember { mutableStateOf<TeamMemberEntity?>(null) }
    var confirmDeactivate by remember { mutableStateOf<TeamMemberEntity?>(null) }

    val canManage = signedIn.can(AppModule.TEAM, PermissionAction.CREATE) ||
        signedIn.can(AppModule.TEAM, PermissionAction.EDIT)

    // A garage always needs somebody who can let people back in. These counts are what stops the
    // last one being deactivated or demoted, which would lock the workshop out of its own team.
    fun roleNameOf(m: TeamMemberEntity): String =
        roles.firstOrNull { it.id == m.roleId }?.name ?: m.role

    fun isAdminRole(m: TeamMemberEntity): Boolean = roleNameOf(m).equals("Admin", ignoreCase = true)

    val activeOwners = members.count { it.isActive && it.isOwner }
    val activeAdmins = members.count { it.isActive && isAdminRole(it) }

    val roleRequirementWarning: String? = when {
        activeOwners == 0 && activeAdmins == 0 ->
            "This garage has no active Owner and no active Admin. Assign at least one of each before changing the team setup."
        activeOwners == 0 -> "This garage has no active Owner. Assign at least one Owner."
        activeAdmins == 0 -> "This garage has no active Admin. Assign at least one Admin."
        else -> null
    }

    /** Why the change is refused, or null when it is safe. */
    fun blockedReason(member: TeamMemberEntity, staysOwner: Boolean, staysAdmin: Boolean): String? {
        if (member.isOwner && !staysOwner && activeOwners <= 1) {
            return "${member.name} is the only active Owner. Assign another Owner first."
        }
        if (isAdminRole(member) && !staysAdmin && activeAdmins <= 1) {
            return "${member.name} is the only active Admin. Assign another Admin first."
        }
        return null
    }

    // Both directions run the same way; only deactivation is worth stopping to confirm, because
    // it is the one that locks somebody out of their own shift.
    fun applyUserActive(member: TeamMemberEntity, next: Boolean) {
        FirebaseAuthHelper.setTeamUserActive(member, next) { ok, msg ->
            if (ok) {
                viewModel.setTeamMemberActive(member, next)
                Toast.makeText(
                    context,
                    if (next) "${member.name} activated" else "${member.name} deactivated",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Indigo)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Groups, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Team & Role Management", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
                        Text(
                            "${members.count { it.isActive }} active users • ${teams.size} teams • ${roles.size} roles",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                TabRow(selectedTabIndex = selectedTab, containerColor = Color.White, contentColor = Indigo) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Team & Users", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Roles & Permissions", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Box(modifier = Modifier.weight(1f).background(Color(0xFFF8FAFC))) {
                    if (selectedTab == 0) {
                        TeamAndUsersTab(
                            members = members,
                            teams = teams,
                            roles = roles,
                            canManage = canManage,
                            onCreateTeam = { editingTeam = null; showTeamDialog = true },
                            onEditTeam = { editingTeam = it; showTeamDialog = true },
                            onToggleTeam = { team ->
                                viewModel.setTeamActive(team, !team.isActive)
                                Toast.makeText(
                                    context,
                                    if (team.isActive) "${team.name} deactivated" else "${team.name} activated",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onCreateUser = { editingUser = null; showUserDialog = true },
                            onEditUser = { editingUser = it; showUserDialog = true },
                            onViewProfile = { profileUser = it },
                            requirementWarning = roleRequirementWarning,
                            onToggleUser = { member ->
                                if (!member.isActive) {
                                    applyUserActive(member, true)
                                } else {
                                    // Deactivating removes them from both counts.
                                    val blocked = blockedReason(member, staysOwner = false, staysAdmin = false)
                                    if (blocked != null) Toast.makeText(context, blocked, Toast.LENGTH_LONG).show()
                                    else confirmDeactivate = member
                                }
                            },
                            onAssignTeam = { member, teamId -> viewModel.assignTeamMemberTeam(member, teamId) },
                            onAssignRole = { member, role ->
                                // Moving the last Admin onto another role would leave nobody able
                                // to manage the team, so it is refused the same way deactivation is.
                                val blocked = blockedReason(
                                    member,
                                    staysOwner = true,
                                    staysAdmin = role.name.equals("Admin", ignoreCase = true)
                                )
                                if (blocked != null) {
                                    Toast.makeText(context, blocked, Toast.LENGTH_LONG).show()
                                } else {
                                    viewModel.assignTeamMemberRole(member, role)
                                    FirebaseAuthHelper.syncUserPermissions(member, role)
                                    Toast.makeText(context, "${member.name} is now ${role.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } else {
                        RolesAndPermissionsTab(
                            roles = roles,
                            members = members,
                            canManage = canManage,
                            onCreateRole = { editingRole = null; showRoleDialog = true },
                            onEditRole = { editingRole = it; showRoleDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showTeamDialog) {
        TeamEditorDialog(
            team = editingTeam,
            requirementWarning = roleRequirementWarning,
            onDismiss = { showTeamDialog = false },
            onSave = { team ->
                viewModel.saveTeam(team)
                showTeamDialog = false
                Toast.makeText(context, "Team saved", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showRoleDialog) {
        RoleEditorDialog(
            role = editingRole,
            onDismiss = { showRoleDialog = false },
            onSave = { role ->
                viewModel.saveRole(role)
                showRoleDialog = false
                Toast.makeText(context, "Role saved", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showUserDialog) {
        UserEditorDialog(
            user = editingUser,
            teams = teams,
            roles = roles,
            existingMembers = members,
            onDismiss = { showUserDialog = false },
            onSaved = { showUserDialog = false },
            viewModel = viewModel
        )
    }

    profileUser?.let { user ->
        UserProfileDialog(
            user = user,
            team = teams.firstOrNull { it.id == user.teamId },
            role = roles.firstOrNull { it.id == user.roleId },
            onDismiss = { profileUser = null }
        )
    }

    confirmDeactivate?.let { user ->
        DeactivateUserDialog(
            user = user,
            onCancel = { confirmDeactivate = null },
            onConfirm = {
                applyUserActive(user, false)
                confirmDeactivate = null
            }
        )
    }
}

/**
 * The stop before somebody loses their login.
 *
 * Names the person and their number rather than saying "this user", because the list is a wall of
 * similar rows and picking the wrong one is silent until that mechanic cannot clock in. Says what
 * survives too — deactivation is not deletion, and the job cards keep their author.
 */
@Composable
private fun DeactivateUserDialog(
    user: TeamMemberEntity,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFFEE2E2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Deactivate user?", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Slate)
                }

                Surface(color = Color(0xFFF8FAFC), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(user.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Slate)
                        Text(
                            user.phone.ifBlank { "No mobile number on record" },
                            fontSize = 13.sp,
                            color = Muted
                        )
                    }
                }

                Text(
                    "${user.name} will no longer be able to log in to Garage Guru.",
                    fontSize = 13.sp,
                    color = Slate
                )
                Text(
                    "Nothing is deleted — their job cards, invoices and reports stay linked to them, " +
                        "and you can activate the account again at any time.",
                    fontSize = 12.sp,
                    color = Muted
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Deactivate User", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Tab 1 — Team & Users
// ----------------------------------------------------------------------

@Composable
private fun TeamAndUsersTab(
    members: List<TeamMemberEntity>,
    teams: List<TeamEntity>,
    roles: List<RoleEntity>,
    canManage: Boolean,
    requirementWarning: String?,
    onCreateTeam: () -> Unit,
    onEditTeam: (TeamEntity) -> Unit,
    onToggleTeam: (TeamEntity) -> Unit,
    onCreateUser: () -> Unit,
    onEditUser: (TeamMemberEntity) -> Unit,
    onViewProfile: (TeamMemberEntity) -> Unit,
    onToggleUser: (TeamMemberEntity) -> Unit,
    onAssignTeam: (TeamMemberEntity, Int) -> Unit,
    onAssignRole: (TeamMemberEntity, RoleEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        requirementWarning?.let { warning ->
            item { RequirementBanner(warning) }
        }

        item {
            SectionHeader(
                title = "Teams",
                subtitle = "Group people by shift or function",
                actionLabel = if (canManage) "Create Team" else null,
                onAction = onCreateTeam
            )
        }

        if (teams.isEmpty()) {
            item { EmptyHint("No teams yet. Create one to group your users.") }
        } else {
            items(teams) { team ->
                val memberCount = members.count { it.teamId == team.id }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, if (team.isActive) Color(0xFFE2E8F0) else Color(0xFFFECACA)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(team.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate)
                                Spacer(modifier = Modifier.width(6.dp))
                                StatusPill(team.isActive)
                            }
                            if (team.description.isNotBlank()) {
                                Text(team.description, fontSize = 11.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text("$memberCount user${if (memberCount == 1) "" else "s"}", fontSize = 11.sp, color = Muted)
                        }
                        if (canManage) {
                            IconButton(onClick = { onEditTeam(team) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit team", tint = Indigo, modifier = Modifier.size(16.dp))
                            }
                            Switch(
                                checked = team.isActive,
                                onCheckedChange = { onToggleTeam(team) },
                                colors = SwitchDefaults.colors(checkedTrackColor = Indigo)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(6.dp)) }

        item {
            SectionHeader(
                title = "Users",
                subtitle = "Mobile number is the login id",
                actionLabel = if (canManage) "Create User" else null,
                onAction = onCreateUser
            )
        }

        items(members) { member ->
            UserRow(
                member = member,
                team = teams.firstOrNull { it.id == member.teamId },
                role = roles.firstOrNull { it.id == member.roleId },
                teams = teams,
                roles = roles,
                canManage = canManage,
                onEdit = { onEditUser(member) },
                onViewProfile = { onViewProfile(member) },
                onToggle = { onToggleUser(member) },
                onAssignTeam = { onAssignTeam(member, it) },
                onAssignRole = { onAssignRole(member, it) }
            )
        }
    }
}

@Composable
private fun UserRow(
    member: TeamMemberEntity,
    team: TeamEntity?,
    role: RoleEntity?,
    teams: List<TeamEntity>,
    roles: List<RoleEntity>,
    canManage: Boolean,
    onEdit: () -> Unit,
    onViewProfile: () -> Unit,
    onToggle: () -> Unit,
    onAssignTeam: (Int) -> Unit,
    onAssignRole: (RoleEntity) -> Unit
) {
    var showTeamMenu by remember { mutableStateOf(false) }
    var showRoleMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (member.isActive) Color(0xFFE2E8F0) else Color(0xFFFECACA)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape)
                        .background(if (member.isActive) Indigo else Color(0xFF94A3B8)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(member.name.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(member.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate)
                        if (member.isOwner) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(color = Color(0xFFFEF3C7), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "OWNER",
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color(0xFF92400E)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusPill(member.isActive)
                    }
                    Text(member.phone.ifBlank { "No mobile number" }, fontSize = 11.sp, color = Muted)
                    Text(
                        "${role?.name ?: member.role} • ${team?.name ?: "No team"}",
                        fontSize = 11.sp,
                        color = Indigo,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (canManage && !member.isOwner) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box {
                        SmallAction("Team", Icons.Default.Groups) { showTeamMenu = true }
                        DropdownMenu(expanded = showTeamMenu, onDismissRequest = { showTeamMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("No team", fontSize = 13.sp) },
                                onClick = { onAssignTeam(0); showTeamMenu = false }
                            )
                            teams.filter { it.isActive }.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t.name, fontSize = 13.sp) },
                                    onClick = { onAssignTeam(t.id); showTeamMenu = false }
                                )
                            }
                        }
                    }
                    Box {
                        SmallAction("Role", Icons.Default.Badge) { showRoleMenu = true }
                        DropdownMenu(expanded = showRoleMenu, onDismissRequest = { showRoleMenu = false }) {
                            roles.forEach { r ->
                                DropdownMenuItem(
                                    text = { Text(r.name, fontSize = 13.sp) },
                                    onClick = { onAssignRole(r); showRoleMenu = false }
                                )
                            }
                        }
                    }
                    SmallAction("Edit", Icons.Default.Edit, onClick = onEdit)
                    SmallAction("Profile", Icons.Default.Person, onClick = onViewProfile)
                    // No delete, by design: history stays linked to a real user record.
                    SmallAction(
                        if (member.isActive) "Deactivate" else "Activate",
                        if (member.isActive) Icons.Default.Block else Icons.Default.CheckCircle,
                        tint = if (member.isActive) Color(0xFFDC2626) else Color(0xFF059669),
                        onClick = onToggle
                    )
                }
            } else if (canManage) {
                Spacer(modifier = Modifier.height(8.dp))
                SmallAction("Profile", Icons.Default.Person, onClick = onViewProfile)
            }
        }
    }
}

// ----------------------------------------------------------------------
// Tab 2 — Roles & Permissions
// ----------------------------------------------------------------------

@Composable
private fun RolesAndPermissionsTab(
    roles: List<RoleEntity>,
    members: List<TeamMemberEntity>,
    canManage: Boolean,
    onCreateRole: () -> Unit,
    onEditRole: (RoleEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader(
                title = "Roles",
                subtitle = "What each job title may do in the app",
                actionLabel = if (canManage) "Create Role" else null,
                onAction = onCreateRole
            )
        }

        if (roles.isEmpty()) {
            item { EmptyHint("No roles yet. Create one to control what users can access.") }
        }

        items(roles) { role ->
            val permissions = PermissionSet.fromJson(role.permissionsJson)
            val holders = members.count { it.roleId == role.id }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(role.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate)
                                if (role.isSystem) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            "SYSTEM",
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                            fontSize = 8.sp, fontWeight = FontWeight.Black, color = Muted
                                        )
                                    }
                                }
                            }
                            if (role.description.isNotBlank()) {
                                Text(role.description, fontSize = 11.sp, color = Muted)
                            }
                            Text("$holders user${if (holders == 1) "" else "s"}", fontSize = 11.sp, color = Muted)
                        }
                        if (canManage && !role.isSystem) {
                            IconButton(onClick = { onEditRole(role) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit role", tint = Indigo, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(6.dp))

                    if (permissions.isEmpty) {
                        Text("No modules granted", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    } else {
                        permissions.grantedModules.forEach { module ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(
                                    module.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    permissions.actionsFor(module).sortedBy { it.ordinal }
                                        .joinToString(", ") { it.label },
                                    fontSize = 10.sp,
                                    color = Indigo,
                                    modifier = Modifier.weight(1.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Editors
// ----------------------------------------------------------------------

@Composable
private fun TeamEditorDialog(
    team: TeamEntity?,
    /** Set while the garage is missing a mandatory Owner or Admin; saving stays closed until fixed. */
    requirementWarning: String?,
    onDismiss: () -> Unit,
    onSave: (TeamEntity) -> Unit
) {
    var name by remember { mutableStateOf(team?.name.orEmpty()) }
    var description by remember { mutableStateOf(team?.description.orEmpty()) }
    var active by remember { mutableStateOf(team?.isActive ?: true) }
    var error by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(if (team == null) "Create Team" else "Edit Team", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Slate)
                HorizontalDivider(color = Color(0xFFE2E8F0))

                FormField(name, { name = it; error = "" }, "Team Name *")
                FormField(description, { description = it }, "Team Description", singleLine = false)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Team Status", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Slate, modifier = Modifier.weight(1f))
                    Text(if (active) "Active" else "Inactive", fontSize = 12.sp, color = if (active) Color(0xFF059669) else Color(0xFFDC2626))
                    Switch(checked = active, onCheckedChange = { active = it }, colors = SwitchDefaults.colors(checkedTrackColor = Indigo))
                }

                requirementWarning?.let { RequirementBanner(it) }

                if (error.isNotEmpty()) Text(error, color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                Button(
                    onClick = {
                        if (requirementWarning != null) {
                            error = requirementWarning
                        } else if (name.isBlank()) {
                            error = "Team name is required"
                        } else {
                            onSave(
                                (team ?: TeamEntity(name = "")).copy(
                                    name = name.trim(),
                                    description = description.trim(),
                                    isActive = active
                                )
                            )
                        }
                    },
                    enabled = requirementWarning == null,
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Team", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

/**
 * Role editor: pick the modules, then tick what may be done in each.
 *
 * View is implied by anything else — a role that can edit job cards but not see them would be
 * unusable — so the matrix keeps the two in step rather than letting an admin save that state.
 */
@Composable
private fun RoleEditorDialog(
    role: RoleEntity?,
    onDismiss: () -> Unit,
    onSave: (RoleEntity) -> Unit
) {
    var name by remember { mutableStateOf(role?.name.orEmpty()) }
    var description by remember { mutableStateOf(role?.description.orEmpty()) }
    var permissions by remember {
        mutableStateOf(
            if (role == null) PermissionSet.viewOnlyDefaults()
            else PermissionSet.fromJson(role.permissionsJson)
        )
    }
    var error by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f).clip(RoundedCornerShape(18.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxWidth().background(Indigo).padding(16.dp)) {
                    Text(if (role == null) "Create Role" else "Edit Role", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
                    Text("Choose modules, then what may be done in each", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                }

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FormField(name, { name = it; error = "" }, "Role Name *")
                    FormField(description, { description = it }, "Role Description", singleLine = false)

                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    Text("Module Permissions", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate)

                    AppModule.entries.forEach { module ->
                        val actions = permissions.actionsFor(module)
                        val enabled = actions.isNotEmpty()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (enabled) Color(0xFFEEF2FF) else Color(0xFFF8FAFC)
                            ),
                            border = BorderStroke(1.dp, if (enabled) Color(0xFFC7D2FE) else Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = enabled,
                                        onCheckedChange = { on ->
                                            permissions = permissions.withAction(module, PermissionAction.VIEW, on)
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = Indigo)
                                    )
                                    Text(module.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate)
                                }
                                if (enabled) {
                                    FlowActionChips(
                                        module = module,
                                        selected = actions,
                                        onToggle = { action, on ->
                                            permissions = permissions.withAction(module, action, on)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (error.isNotEmpty()) Text(error, color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(10.dp)) {
                        Text("Cancel", fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            when {
                                name.isBlank() -> error = "Role name is required"
                                permissions.isEmpty -> error = "Grant at least one module"
                                else -> onSave(
                                    (role ?: RoleEntity(name = "")).copy(
                                        name = name.trim(),
                                        description = description.trim(),
                                        permissionsJson = permissions.toJson()
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Role", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowActionChips(
    module: AppModule,
    selected: Set<PermissionAction>,
    onToggle: (PermissionAction, Boolean) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PermissionAction.availableFor(module).forEach { action ->
            val on = selected.contains(action)
            FilterChip(
                selected = on,
                onClick = { onToggle(action, !on) },
                label = { Text(action.label, fontSize = 10.sp) },
                // View is what the module checkbox itself controls; unticking it here would be
                // a second, confusing way to switch the whole module off.
                enabled = action != PermissionAction.VIEW
            )
        }
    }
}

/**
 * Create or edit a user.
 *
 * There is no username field: the mobile number *is* the login id, which is also why a duplicate
 * number is refused outright rather than quietly creating a second account nobody can log into.
 */
@Composable
private fun UserEditorDialog(
    user: TeamMemberEntity?,
    teams: List<TeamEntity>,
    roles: List<RoleEntity>,
    existingMembers: List<TeamMemberEntity>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    viewModel: GarageViewModel
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(user?.name.orEmpty()) }
    var phone by remember { mutableStateOf(user?.phone.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var teamId by remember { mutableIntStateOf(user?.teamId ?: 0) }
    var roleId by remember { mutableIntStateOf(user?.roleId ?: 0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val isNew = user == null
    val selectedRole = roles.firstOrNull { it.id == roleId }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(if (isNew) "Create User" else "Edit User", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Slate)
                HorizontalDivider(color = Color(0xFFE2E8F0))

                FormField(name, { name = it; error = "" }, "Full Name *")
                FormField(
                    phone,
                    { phone = it.filter(Char::isDigit).take(10); error = "" },
                    "Mobile Number * (login id)",
                    keyboardType = KeyboardType.Phone,
                    enabled = isNew
                )
                if (!isNew) {
                    Text("The mobile number is the login id and cannot be changed.", fontSize = 10.sp, color = Color(0xFF94A3B8))
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = "" },
                    label = { Text(if (isNew) "Password *" else "New Password (leave blank to keep)", fontSize = 12.sp) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                PickerField("Team", teams.firstOrNull { it.id == teamId }?.name ?: "No team") { close ->
                    DropdownMenuItem(text = { Text("No team") }, onClick = { teamId = 0; close() })
                    teams.filter { it.isActive }.forEach { t ->
                        DropdownMenuItem(text = { Text(t.name) }, onClick = { teamId = t.id; close() })
                    }
                }
                PickerField("Role *", selectedRole?.name ?: "Select a role") { close ->
                    roles.forEach { r ->
                        DropdownMenuItem(text = { Text(r.name) }, onClick = { roleId = r.id; close() })
                    }
                }

                if (error.isNotEmpty()) Text(error, color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                Button(
                    onClick = {
                        val role = roles.firstOrNull { it.id == roleId }
                        when {
                            name.isBlank() -> error = "Name is required"
                            phone.length < 10 -> error = "Enter a valid 10-digit mobile number"
                            role == null -> error = "Select a role"
                            isNew && password.length < 6 -> error = "Password must be at least 6 characters"
                            isNew && existingMembers.any {
                                it.phone.filter(Char::isDigit) == phone && it.isActive
                            } -> error = "This number is already registered and user is still active with " +
                                "\"${GarageSession.garageName}\"."
                            else -> {
                                busy = true
                                error = ""
                                if (isNew) {
                                    FirebaseAuthHelper.findExistingUser(phone) { conflict ->
                                        if (conflict != null && conflict.active) {
                                            busy = false
                                            error = "This number is already registered and user is " +
                                                "still active with \"${conflict.garageName}\"."
                                        } else {
                                            FirebaseAuthHelper.createTeamUser(
                                                name = name.trim(),
                                                phone = phone,
                                                password = password,
                                                roleId = role.id,
                                                roleName = role.name,
                                                teamId = teamId,
                                                permissionTokens = PermissionSet
                                                    .fromJson(role.permissionsJson).toTokens()
                                            ) { ok, uid, message ->
                                                busy = false
                                                if (ok) {
                                                    viewModel.addTeamMember(
                                                        TeamMemberEntity(
                                                            name = name.trim(),
                                                            role = role.name,
                                                            phone = phone,
                                                            isOwner = false,
                                                            teamId = teamId,
                                                            roleId = role.id,
                                                            isActive = true,
                                                            authUid = uid
                                                        )
                                                    )
                                                    Toast.makeText(context, "User created", Toast.LENGTH_SHORT).show()
                                                    onSaved()
                                                } else {
                                                    error = message
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    busy = false
                                    val updated = user.copy(
                                        name = name.trim(),
                                        role = role.name,
                                        roleId = role.id,
                                        teamId = teamId
                                    )
                                    viewModel.addTeamMember(updated)
                                    FirebaseAuthHelper.syncUserPermissions(updated, role)
                                    Toast.makeText(context, "User updated", Toast.LENGTH_SHORT).show()
                                    onSaved()
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text(if (isNew) "Create User" else "Save Changes", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/** Read-only view of a user: mobile number as the login id, password masked. */
@Composable
private fun UserProfileDialog(
    user: TeamMemberEntity,
    team: TeamEntity?,
    role: RoleEntity?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Indigo),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(user.name.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Slate)
                        StatusPill(user.isActive)
                    }
                }
                HorizontalDivider(color = Color(0xFFE2E8F0))

                ProfileRow("Mobile Number (Login ID)", user.phone.ifBlank { "—" })
                ProfileRow("Password", "••••••••")
                ProfileRow("Role", role?.name ?: user.role)
                ProfileRow("Team", team?.name ?: "No team")
                ProfileRow("Status", if (user.isActive) "Active" else "Inactive")

                role?.let {
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    Text("Access", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate)
                    val perms = PermissionSet.fromJson(it.permissionsJson)
                    if (perms.isEmpty) {
                        Text("No modules granted", fontSize = 11.sp, color = Muted)
                    } else {
                        perms.grantedModules.forEach { m ->
                            Text(
                                "• ${m.label}: ${perms.actionsFor(m).sortedBy { a -> a.ordinal }.joinToString(", ") { a -> a.label }}",
                                fontSize = 11.sp, color = Muted
                            )
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Small shared pieces
// ----------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String, subtitle: String, actionLabel: String?, onAction: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Black, fontSize = 15.sp, color = Slate)
            Text(subtitle, fontSize = 11.sp, color = Muted)
        }
        if (actionLabel != null) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(34.dp),
                shape = RoundedCornerShape(9.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Standing reminder that the team is missing a mandatory role, shown above everything else. */
@Composable
private fun RequirementBanner(message: String) {
    Surface(
        color = Color(0xFFFEF3C7),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFB45309),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(message, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF92400E))
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(text, modifier = Modifier.padding(16.dp), fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun StatusPill(active: Boolean) {
    Surface(
        color = if (active) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            if (active) "ACTIVE" else "INACTIVE",
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            color = if (active) Color(0xFF166534) else Color(0xFF991B1B)
        )
    }
}

@Composable
private fun SmallAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = Indigo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 11.sp, color = Muted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = singleLine,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = TextStyle(fontSize = 13.sp),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PickerField(
    label: String,
    current: String,
    items: @Composable (close: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 11.sp, color = Muted)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(current, fontSize = 13.sp, color = Slate, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Muted)
            }
            // Every entry is handed the closer rather than the menu trying to guess when a
            // selection happened — an item that only mutated the caller's state used to leave the
            // menu hanging open over the form.
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                items { expanded = false }
            }
        }
    }
}
