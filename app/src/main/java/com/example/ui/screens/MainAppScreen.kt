package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.model.CashItemEntity
import com.example.data.model.CustomerProfileEntity
import com.example.data.model.InventoryItemEntity
import com.example.data.model.StockUnits
import com.example.data.model.JobCardEntity
import com.example.data.model.ServiceAlertEntity
import com.example.data.model.ServiceMembershipEntity
import com.example.data.model.TeamMemberEntity
import com.example.ui.GarageViewModel
import com.example.data.api.FirebaseAuthHelper
import com.example.data.api.GarageLogoStore
import com.example.data.api.GarageSession
import com.example.data.api.GarageSettings
import com.example.data.permissions.AppModule
import com.example.data.permissions.PermissionAction
import com.example.data.catalogue.VehicleCatalogue
import com.example.data.catalogue.VehicleCategory
import com.example.data.model.StockIssueEntity
import kotlinx.coroutines.launch
import com.example.ui.components.MechanicalGLogo
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.t
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * A workshop starts with exactly one team member: the owner who signed up. Extra mechanics are
 * added by hand from Garage Team & Mechanics — nothing is invented for them.
 */
fun ownerTeamMember(
    name: String = GarageSession.ownerName,
    phone: String = GarageSession.ownerPhone
) = TeamMemberEntity(
    id = 0,
    name = name.ifBlank { "Owner" },
    role = "Owner",
    phone = phone,
    isOwner = true
)

/**
 * Access questions the screens ask, answered from the signed-in user's assigned role.
 *
 * These used to be a fixed table of role *names* — "Manager can see financials" — which meant a
 * role an admin created could never be given anything. Everything now resolves through the
 * permissions actually granted to that user, which are the same ones the security rules enforce.
 */
object RolePermissions {
    private val user get() = GarageSession.currentUser

    fun canView(module: AppModule): Boolean = user.canView(module)
    fun can(module: AppModule, action: PermissionAction): Boolean = user.can(module, action)

    fun canAccessFinancials(): Boolean = canView(AppModule.REPORTS)
    fun canAccessCashbook(): Boolean = canView(AppModule.CASHBOOK)
    fun canAccessSystem(): Boolean = canView(AppModule.INVENTORY)
    fun canAccessCustomers(): Boolean = canView(AppModule.CUSTOMERS)
    fun canCreateJob(): Boolean = can(AppModule.JOB_CARDS, PermissionAction.CREATE)
    fun canCloseJob(): Boolean = can(AppModule.JOB_CARDS, PermissionAction.APPROVE)

    /** Only the owner may change or remove the garage profile itself. */
    fun canEditGarageProfile(): Boolean = user.isOwner || can(AppModule.SETTINGS, PermissionAction.EDIT)
    fun canDeleteGarageProfile(): Boolean = user.isOwner
}

/**
 * The first tab a user may actually open.
 *
 * A role without Dashboard would otherwise land on a refusal screen every time they sign in.
 */
fun defaultTabFor(user: GarageSession.SignedInUser): String =
    AppModule.entries.firstOrNull { it.tabKey != null && user.canView(it) }?.tabKey ?: "account"

/** Shown when a module is reached that the signed-in role has not been granted. */
@Composable
fun ModuleAccessDenied(moduleLabel: String, roleName: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("$moduleLabel is not available", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF334155))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Your $roleName role does not have access to this module. Ask your garage admin if you need it.",
            fontSize = 12.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Go back", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainAppScreen(
    viewModel: GarageViewModel,
    modifier: Modifier = Modifier,
    onBackTrigger: () -> Unit = {}
) {
    val context = LocalContext.current
    val isOffline by viewModel.isOffline.collectAsState()
    val fcmToken by viewModel.fcmToken.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    // Query states
    val jobList by viewModel.jobCards.collectAsState()
    val cashList by viewModel.cashItems.collectAsState()
    val inventoryList by viewModel.inventoryItems.collectAsState()
    val membershipList by viewModel.memberships.collectAsState()
    val customerProfilesList by viewModel.customerProfiles.collectAsState()
    val serviceAlertsList by viewModel.serviceAlerts.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    // A tab key may carry a deep-link filter after a colon, e.g. "jobs:Completed" from the
    // dashboard counters. `activeTab` is the plain key everything else should switch on.
    var currentTab by remember { mutableStateOf("dashboard") } // dashboard, jobs, customers, cashbook, system, reports, account
    val activeTab = currentTab.substringBefore(':')
    val tabFilter = currentTab.substringAfter(':', "")

    // Auth, Garage & Team Role State
    // Firebase keeps the session across restarts, so a returning owner skips the login form.
    var isLoggedIn by remember { mutableStateOf(FirebaseAuthHelper.isSignedIn()) }
    val teamMembersList by viewModel.teamMembers.collectAsState()
    // Who is signed in and what their assigned role permits. Not switchable from the UI.
    val signedInUser by GarageSession.signedInUser.collectAsState()
    val currentActiveUser = remember(signedInUser) {
        ownerTeamMember(signedInUser.name, GarageSession.ownerPhone).copy(
            role = signedInUser.roleName,
            isOwner = signedInUser.isOwner
        )
    }
    var currentOwnerName by remember { mutableStateOf(GarageSession.ownerName.ifBlank { currentActiveUser.name }) }
    var currentGarageName by remember { mutableStateOf(GarageSession.garageName) }
    // Logo bytes come from the Firestore Blob, cached on disk by GarageLogoStore.
    val currentGarageLogo by GarageLogoStore.logo.collectAsState()
    val logoScope = rememberCoroutineScope()
    var currentOwnerPhone by remember { mutableStateOf(GarageSession.ownerPhone.ifBlank { currentActiveUser.phone }) }
    var showTeamManagementSheet by remember { mutableStateOf(false) }

    // App Language State (en = English, hi = Hindi, mr = Marathi)
    var selectedLanguage by remember { mutableStateOf(GarageSession.appLanguage) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // Workshop configuration — drives the first-run wizard and the billing rules.
    val garageSettings by GarageSettings.state.collectAsState()
    // True once the cloud profile has been read. Until then the settings held here are only the
    // on-device defaults, which would make a fully configured workshop look brand new.
    var profileLoaded by remember { mutableStateOf(false) }

    val userInitials = remember(currentActiveUser.name) {
        val parts = currentActiveUser.name.trim().split(" ")
        if (parts.size >= 2) {
            "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
        } else if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
            parts[0].take(2).uppercase()
        } else "GG"
    }

    // Filtering active jobs and customer lookup
    val filteredJobs = remember(searchQuery, jobList) {
        if (searchQuery.isBlank()) jobList else {
            jobList.filter {
                it.customerName.contains(searchQuery, ignoreCase = true) ||
                it.vehicleName.contains(searchQuery, ignoreCase = true) ||
                it.vehiclePlate.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Modal Overlays state
    var showCreateJobSheet by remember { mutableStateOf(false) }
    var selectedOrderForInvoice by remember { mutableStateOf<JobCardEntity?>(null) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showMoreBottomSheet by remember { mutableStateOf(false) }
    var showDiagnosticsChatSheet by remember { mutableStateOf(false) }
    var prefilledJobForCreation by remember { mutableStateOf<JobCardEntity?>(null) }

    // System Back-Button Registry
    androidx.activity.compose.BackHandler(
        enabled = showCreateJobSheet || selectedOrderForInvoice != null || showNotificationDialog || showMoreBottomSheet || showDiagnosticsChatSheet || activeTab != "dashboard"
    ) {
        if (showCreateJobSheet) {
            showCreateJobSheet = false
            prefilledJobForCreation = null
        } else if (selectedOrderForInvoice != null) {
            selectedOrderForInvoice = null
        } else if (showNotificationDialog) {
            showNotificationDialog = false
        } else if (showMoreBottomSheet) {
            showMoreBottomSheet = false
        } else if (showDiagnosticsChatSheet) {
            showDiagnosticsChatSheet = false
        } else if (activeTab != "dashboard") {
            currentTab = "dashboard"
        }
    }

    // Start connectivity monitor
    LaunchedEffect(Unit) {
        viewModel.startNetworkMonitoring(context)
    }

    // A user whose role excludes the dashboard must not open onto a refusal screen.
    LaunchedEffect(signedInUser) {
        val module = AppModule.forTab(activeTab)
        if (module != null && !signedInUser.canView(module)) {
            currentTab = defaultTabFor(signedInUser)
        }
    }

    // Rehydrate an existing Firebase session and pull this workshop's cloud data down.
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            FirebaseAuthHelper.restoreSession { ok, owner, garage, phone ->
                if (ok) {
                    owner?.let { currentOwnerName = it }
                    garage?.let { currentGarageName = it }
                    phone?.takeIf { it.isNotBlank() }?.let { currentOwnerPhone = it }
                    logoScope.launch { GarageLogoStore.refreshFromCloud(GarageSession.logoUpdatedAt) }
                }
                profileLoaded = true
                viewModel.syncWithCloud()
            }
        }
    }

    if (!isLoggedIn) {
        androidx.compose.runtime.CompositionLocalProvider(LocalAppLanguage provides selectedLanguage) {
        GarageAuthScreen(
            selectedLanguage = selectedLanguage,
            onOpenLanguageDialog = { showLanguageDialog = true },
            onLoginSuccess = { owner, garage, phone ->
                currentOwnerName = owner
                currentGarageName = garage
                currentOwnerPhone = phone
                isLoggedIn = true
            }
        )
        if (showLanguageDialog) {
            LanguageSelectionDialog(
                currentLanguage = selectedLanguage,
                onLanguageSelected = { lang ->
                    selectedLanguage = lang
                    GarageSession.appLanguage = lang
                    showLanguageDialog = false
                    val msg = when(lang) {
                        "hi" -> "भाषा बदलकर हिंदी कर दी गई है"
                        "mr" -> "भाषा बदलून मराठी केली आहे"
                        else -> "Language changed to English"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showLanguageDialog = false }
            )
        }
        }
        return
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalAppLanguage provides selectedLanguage) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (selectedOrderForInvoice == null && !showCreateJobSheet) {
                GarageBottomBar(
                    currentTab = activeTab,
                    selectedLanguage = selectedLanguage,
                    signedInUser = signedInUser,
                    onTabSelected = { currentTab = it },
                    onCreateJobClick = { showCreateJobSheet = true },
                    onMoreClick = { currentTab = "account" }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (selectedOrderForInvoice != null || showCreateJobSheet) PaddingValues(0.dp) else innerPadding)
        ) {
            if (showCreateJobSheet) {
                // A page, not a popup: two steps need the whole screen and a footer that stays put.
                NewJobCardScreen(
                    viewModel = viewModel,
                    inventory = inventoryList,
                    teamMembers = teamMembersList,
                    initialJob = prefilledJobForCreation,
                    onDismiss = {
                        showCreateJobSheet = false
                        prefilledJobForCreation = null
                    }
                )
            } else if (selectedOrderForInvoice != null) {
                InvoiceBreakdownPage(
                    viewModel = viewModel,
                    jobCard = selectedOrderForInvoice!!,
                    inventory = inventoryList,
                    customerProfiles = customerProfilesList,
                    onDismiss = { selectedOrderForInvoice = null },
                    onSavePdf = { job ->
                        viewModel.saveInvoicePdf(job, context)
                    },
                    onPaidAndClosed = { currentTab = "jobs:Closed" }
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // --- Safe Area & Offline Banner ---
                    AnimatedVisibility(
                        visible = isOffline,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.error)
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = "Offline Mode",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = t("offline.banner"),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }

                    // --- Top Diagnostic Bar & Fast Search Bar ---
                    WorkshopTopBar(
                        garageName = currentGarageName,
                        garageLogoBytes = currentGarageLogo,
                        onLogoClick = { currentTab = "dashboard" },
                        onNotificationClick = { showNotificationDialog = true },
                        onProfileClick = { currentTab = "account" },
                        onOpenLanguageDialog = { showLanguageDialog = true },
                        selectedLanguage = selectedLanguage,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        currentTab = activeTab,
                        userInitials = userInitials,
                        pendingAlertCount = serviceAlertsList.count { it.status != "SERVICED" && it.status != "COMPLETED" }
                    )

                    // --- Tab Content Router ---
                    //
                    // The gate lives here, not only on the menu. Hiding a bottom-bar icon stops a
                    // user tapping it, but the tab can still be set from a dashboard shortcut, a
                    // deep link or restored state — so the router itself refuses to render a
                    // module the signed-in role was not granted.
                    Box(modifier = Modifier.weight(1f)) {
                        val requestedModule = AppModule.forTab(activeTab)
                        if (requestedModule != null && !signedInUser.canView(requestedModule)) {
                            ModuleAccessDenied(
                                moduleLabel = requestedModule.label,
                                roleName = signedInUser.roleName,
                                onBack = { currentTab = defaultTabFor(signedInUser) }
                            )
                        } else when (activeTab) {
                            "dashboard" -> DashboardView(
                                viewModel = viewModel,
                                jobList = jobList,
                                cashList = cashList,
                                inventoryList = inventoryList,
                                membershipList = membershipList,
                                customerProfiles = customerProfilesList,
                                onTabNavigate = { currentTab = it },
                                onCreateJobClick = { showCreateJobSheet = true },
                                currentActiveUser = currentActiveUser,
                                onOpenTeamManagement = { showTeamManagementSheet = true }
                            )
                            "jobs" -> JobsView(
                                viewModel = viewModel,
                                jobs = filteredJobs,
                                inventory = inventoryList,
                                searchQuery = searchQuery,
                                onSearchChange = { searchQuery = it },
                                onViewInvoice = { selectedOrderForInvoice = it },
                                onCreateClick = { showCreateJobSheet = true },
                                initialStatusFilter = tabFilter.ifBlank { "Open" }
                            )
                            "customers" -> CustomersManagementView(
                                viewModel = viewModel,
                                memberships = membershipList,
                                customerProfiles = customerProfilesList,
                                jobList = jobList,
                                searchQuery = searchQuery,
                            )
                            "reports" -> ReportsView(
                                viewModel = viewModel,
                                jobs = jobList,
                                cash = cashList,
                                inventory = inventoryList,
                                memberships = membershipList,
                                canSeeFinancials = RolePermissions.canAccessFinancials()
                            )
                            "cashbook" -> CashbookView(
                                viewModel = viewModel,
                                cashItems = cashList
                            )
                            "system" -> SystemCoordinatorView(
                                viewModel = viewModel,
                                inventory = inventoryList,
                                fcmToken = fcmToken,
                                searchQuery = searchQuery,
                                onCreateJobClick = { showCreateJobSheet = true },
                            )
                            "account" -> AccountSettingsView(
                                onBackClick = { currentTab = "dashboard" },
                                onNavigate = { currentTab = it },
                                onOpenNotifications = { showNotificationDialog = true },
                                ownerName = currentOwnerName,
                                garageName = currentGarageName,
                                garageLogoBytes = currentGarageLogo,
                                onGarageNameChange = { newName -> currentGarageName = newName },
                                onGarageLogoSelected = { pickedUri ->
                                    logoScope.launch {
                                        val result = GarageLogoStore.upload(context, pickedUri)
                                        val msg = result.fold(
                                            onSuccess = { size -> "Garage Logo uploaded to cloud (${size / 1024} KB)" },
                                            onFailure = { e -> e.message ?: "Logo upload failed" }
                                        )
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                },
                                onRemoveGarageLogo = {
                                    logoScope.launch {
                                        val result = GarageLogoStore.remove()
                                        val msg = result.fold(
                                            onSuccess = { "Logo removed — the default Garage Guru logo is now used" },
                                            onFailure = { e -> e.message ?: "Could not remove logo" }
                                        )
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                },
                                ownerPhone = currentOwnerPhone,
                                ownerEmail = GarageSession.ownerContactEmail,
                                canEditGarageProfile = RolePermissions.canEditGarageProfile(),
                                selectedLanguage = selectedLanguage,
                                onOpenLanguageDialog = { showLanguageDialog = true },
                                onLogoutClick = {
                                    FirebaseAuthHelper.signOut()
                                    viewModel.clearLocalData()
                                    isLoggedIn = false
                                    currentTab = "dashboard"
                                    Toast.makeText(context, "Logged out of garage successfully", Toast.LENGTH_SHORT).show()
                                },
                                onOpenTeamManagement = { showTeamManagementSheet = true }
                            )
                        }
                    }
                }
            }

            // --- Service Intervals & Room Notifications System Dialog ---
            if (showNotificationDialog) {
                ServiceIntervalsDialog(
                    viewModel = viewModel,
                    fcmToken = fcmToken,
                    onDismiss = { showNotificationDialog = false },
                    onCreateJobForCustomer = { cName, cPhone, vPlate, sType ->
                        prefilledJobForCreation = JobCardEntity(
                            customerName = cName,
                            customerPhone = cPhone,
                            vehicleName = "Routine Care: $sType",
                            vehiclePlate = vPlate,
                            symptoms = "Scheduled interval alert: $sType",
                            status = "OPEN"
                        )
                        showNotificationDialog = false
                        showCreateJobSheet = true
                    }
                )
            }

            // --- Interactive Local Vehicle Diagnostics Assistant Chat ---
            if (showDiagnosticsChatSheet) {
                CarDiagnosticsChatSheet(
                    onDismiss = { showDiagnosticsChatSheet = false },
                    onCreateJobFromDiagnosis = { diagJob ->
                        prefilledJobForCreation = diagJob
                        showDiagnosticsChatSheet = false
                        showCreateJobSheet = true
                    }
                )
            }


            // --- Custom More Options Dialog ---
            if (showMoreBottomSheet) {
                MoreOptionsDialog(
                    onDismiss = { showMoreBottomSheet = false },
                    onNavigate = { tabName ->
                        currentTab = tabName
                        showMoreBottomSheet = false
                    },
                    onTriggerNotifications = {
                        showMoreBottomSheet = false
                        showNotificationDialog = true
                    },
                    ownerName = currentOwnerName,
                    garageName = currentGarageName,
                    ownerPhone = currentOwnerPhone,
                    userInitials = userInitials,
                    selectedLanguage = selectedLanguage,
                    onOpenLanguageDialog = { showLanguageDialog = true },
                    onLogoutClick = {
                        showMoreBottomSheet = false
                        FirebaseAuthHelper.signOut()
                        viewModel.clearLocalData()
                        isLoggedIn = false
                        currentTab = "dashboard"
                        Toast.makeText(context, "Logged out of garage successfully", Toast.LENGTH_SHORT).show()
                    },
                    onOpenTeamManagement = {
                        showMoreBottomSheet = false
                        showTeamManagementSheet = true
                    }
                )
            }

            // --- Team & Role Management ---
            // Role switching is gone: a user works as the role their admin assigned, so there is
            // nothing here to pick between.
            if (showTeamManagementSheet) {
                TeamRoleManagementScreen(
                    viewModel = viewModel,
                    onDismiss = { showTeamManagementSheet = false }
                )
            }

            // --- First-run setup, shown to the owner before anything else ---
            // Held back until the cloud pull has finished, so a returning owner restoring on a
            // new device is not asked to configure a workshop that is already configured.
            if (profileLoaded && !isSyncing && !garageSettings.setupCompleted && currentActiveUser.isOwner) {
                FirstTimeSetupWizard(
                    garageName = currentGarageName,
                    ownerName = currentOwnerName,
                    onGarageLogoSelected = { pickedUri ->
                        logoScope.launch {
                            val result = GarageLogoStore.upload(context, pickedUri)
                            val msg = result.fold(
                                onSuccess = { size -> "Logo uploaded (${size / 1024} KB)" },
                                onFailure = { e -> e.message ?: "Logo upload failed" }
                            )
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    onRemoveGarageLogo = {
                        logoScope.launch {
                            val result = GarageLogoStore.remove()
                            val msg = result.fold(
                                onSuccess = { "Logo removed — the default Garage Guru logo is now used" },
                                onFailure = { e -> e.message ?: "Could not remove logo" }
                            )
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    // Finishing hands the owner to Account Settings rather than the dashboard,
                    // so anything still outstanding is in front of them instead of buried.
                    onFinish = { currentTab = "account" }
                )
            }

            // --- Language Selection Dialog ---
            if (showLanguageDialog) {
                LanguageSelectionDialog(
                    currentLanguage = selectedLanguage,
                    onLanguageSelected = { lang ->
                        selectedLanguage = lang
                        GarageSession.appLanguage = lang
                        showLanguageDialog = false
                        val msg = when(lang) {
                            "hi" -> "भाषा बदलकर हिंदी कर दी गई है"
                            "mr" -> "भाषा बदलून मराठी केली आहे"
                            else -> "Language changed to English"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showLanguageDialog = false }
                )
            }
        }
    }
    }
}

// --- Top Bar Layout ---
@Composable
fun WorkshopTopBar(
    garageName: String = "Garage Guru",
    garageLogoBytes: ByteArray? = null,
    onLogoClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit,
    onOpenLanguageDialog: () -> Unit = {},
    selectedLanguage: String = "en",
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    currentTab: String = "dashboard",
    userInitials: String = "GG",
    userRole: String = "Owner",
    pendingAlertCount: Int = 0
) {
    val searchPlaceholder = LanguageHelper.getSearchPlaceholder(selectedLanguage, currentTab)
    Surface(
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = strokeWidth
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, top = 10.dp, start = 12.dp, end = 12.dp)
        ) {
            // Row 1: Brand & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable { onLogoClick() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEEF2FF))
                            .border(1.dp, Color(0xFFC7D2FE), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (garageLogoBytes != null && garageLogoBytes.isNotEmpty()) {
                            AsyncImage(
                                model = garageLogoBytes,
                                contentDescription = "Garage Logo",
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            MechanicalGLogo(modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = if (garageName.isNotBlank()) garageName else "Garage Guru",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Workshop • $userRole",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Language Switcher Button
                    Surface(
                        onClick = onOpenLanguageDialog,
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEEF2FF),
                        border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("language_topbar_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = "Language", tint = Color(0xFF4F46E5), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = when (selectedLanguage) {
                                    "hi" -> "हिंदी"
                                    "mr" -> "मराठी"
                                    else -> "EN"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5)
                            )
                        }
                    }


                    // Notification Button with Badge
                    IconButton(
                        onClick = onNotificationClick,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("notification_button")
                    ) {
                        BadgedBox(
                            badge = {
                                // Only show a count when there are real alerts outstanding.
                                if (pendingAlertCount > 0) {
                                    Badge(
                                        containerColor = Color(0xFFEF4444),
                                        contentColor = Color.White,
                                        modifier = Modifier.offset(x = 2.dp, y = (-2).dp)
                                    ) {
                                        Text("$pendingAlertCount", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Active Notifications",
                                tint = Color(0xFF334155),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Gmail-style Profile Avatar Pill with Role Badge
                    Box(
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4F46E5))
                            .border(1.5.dp, Color(0xFFC7D2FE), CircleShape)
                            .clickable { onProfileClick() }
                            .testTag("profile_avatar_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(userInitials, fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Gmail-style Search Bar Capsule
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(21.dp),
                color = Color(0xFFF1F5F9),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("global_search_input"),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 12.sp,
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Medium
                        ),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = searchPlaceholder,
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Bottom Navigation ---
@Composable
fun GarageBottomBar(
    currentTab: String,
    selectedLanguage: String = "en",
    signedInUser: GarageSession.SignedInUser = GarageSession.SignedInUser(),
    onTabSelected: (String) -> Unit,
    onCreateJobClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        color = Color.White,
        shadowElevation = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .background(Color.White)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            val selectionColor = DeepIndigo
            val inactiveColor = Color.Gray

            // Tab 1: Home
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onTabSelected("dashboard") }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Home,
                    contentDescription = "Home",
                    tint = if (currentTab == "dashboard") selectionColor else inactiveColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = LanguageHelper.getTabHome(selectedLanguage),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentTab == "dashboard") selectionColor else inactiveColor
                )
            }

            // Tab 2: Jobs
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onTabSelected("jobs") }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Assignment,
                    contentDescription = "Jobs",
                    tint = if (currentTab == "jobs") selectionColor else inactiveColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = LanguageHelper.getTabJobs(selectedLanguage),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentTab == "jobs") selectionColor else inactiveColor
                )
            }

            // Tab 3: Centered ElevatedButton (+)
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DeepIndigo)
                        .clickable { onCreateJobClick() }
                        .testTag("center_plus_action"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(2.dp, Color.White, RoundedCornerShape(50))
                            .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add New Job",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Tab 4: Stock
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        if (!signedInUser.canView(AppModule.INVENTORY)) {
                            Toast.makeText(context, "Your ${signedInUser.roleName} role does not have access to Stock.", Toast.LENGTH_SHORT).show()
                        } else {
                            onTabSelected("system")
                        }
                    }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = "Stock",
                    tint = if (currentTab == "system") selectionColor else inactiveColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = t("nav.stock"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentTab == "system") selectionColor else inactiveColor
                )
            }

            // Tab 5: More
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onMoreClick() }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "More",
                    tint = inactiveColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = LanguageHelper.getTabMore(selectedLanguage),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = inactiveColor
                )
            }
        }
    }
}

// Clickable gesture modifier for Bento Grid cards
@Composable
fun Modifier.bentoSpringClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    return this.clickable(enabled = enabled, onClick = onClick)
}

// ==========================================
// VIEW 1: DASHBOARD
// ==========================================
@Composable
fun DashboardView(
    viewModel: GarageViewModel,
    jobList: List<JobCardEntity>,
    cashList: List<CashItemEntity>,
    inventoryList: List<InventoryItemEntity>,
    membershipList: List<ServiceMembershipEntity>,
    customerProfiles: List<CustomerProfileEntity> = emptyList(),
    onTabNavigate: (String) -> Unit,
    onCreateJobClick: () -> Unit,
    currentActiveUser: TeamMemberEntity = ownerTeamMember(),
    onOpenTeamManagement: () -> Unit = {}
) {
    // Computations
    val openCount = jobList.count { it.status == "OPEN" }
    val completedCount = jobList.count { it.status == "COMPLETED" }
    val closedCount = jobList.count { it.status == "CLOSED" }

    val totalIncome = cashList.filter { it.type == "INCOME" }.sumOf { it.amount }
    val totalExpense = cashList.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val combinedIncome = totalIncome - totalExpense

    // Local controller states for Dashboard action modals
    var showQuickBillDialogLocal by remember { mutableStateOf(false) }
    var quickBillAmountText by remember { mutableStateOf("") }
    var quickBillCategoryText by remember { mutableStateOf("Quick Service Charge") }
    var quickBillDescText by remember { mutableStateOf("Walk-in client payment logged") }

    var showAddMembershipDialogLocal by remember { mutableStateOf(false) }
    var subNameText by remember { mutableStateOf("") }
    var subPhoneText by remember { mutableStateOf("") }
    var subTypeText by remember { mutableStateOf("Basic Wash") }
    var subLimitText by remember { mutableStateOf("4") }

    var showAddProfileDialogInDashboard by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SwissWhite)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section 1: Bento Action Buttons (+ NEW JOB CARD, QUICK BILL)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button 1: + NEW JOB CARD
                Button(
                    onClick = onCreateJobClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = t("dash.newJob"), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }

                // Button 2: QUICK BILL
                OutlinedButton(
                    onClick = { showQuickBillDialogLocal = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    border = BorderStroke(1.2.dp, DeepIndigo),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DeepIndigo),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Bolt, contentDescription = null, tint = DeepIndigo, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = t("dash.billing"), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

            }
        }

        // Section 2: ACTIVE / DONE / CLOSED Status Counters
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Card 1: ACTIVE (Blue)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .bentoSpringClickable { onTabNavigate("jobs:Open") },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "$openCount", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(text = t("dash.active"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
                    }
                }
                // Card 2: DONE (Green)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .bentoSpringClickable { onTabNavigate("jobs:Completed") },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "$completedCount", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(text = t("dash.done"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
                    }
                }
                // Card 3: CLOSED (Grey Slate)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .bentoSpringClickable { onTabNavigate("jobs:Closed") },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF334155)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "$closedCount", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(text = t("dash.closed"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
        }

        // Section 3: Horizontal quick actions bento cards
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuickActionIconCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Inventory2,
                    iconColor = EmeraldGreen,
                    title = t("dash.stock"),
                    onClick = { onTabNavigate("system") }
                )
                QuickActionIconCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.AccountBalanceWallet,
                    iconColor = Color.Red,
                    title = t("dash.cash"),
                    onClick = { onTabNavigate("cashbook") }
                )
                QuickActionIconCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.BarChart,
                    iconColor = DeepIndigo,
                    title = t("dash.reports"),
                    onClick = { onTabNavigate("reports") }
                )
                QuickActionIconCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.People,
                    iconColor = Color(0xFF2563EB),
                    title = t("dash.clients"),
                    onClick = { onTabNavigate("customers") }
                )
            }
        }

        // Section 4: SUBSCRIPTIONS Layout (2x2 Cards Grid)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t("dash.servicePlans"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E293B),
                        letterSpacing = 0.5.sp
                    )
                    Button(
                        onClick = { showAddMembershipDialogLocal = true },
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Plan",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = t("dash.plan"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubscriptionStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Group,
                            iconColor = Color(0xFF8B5CF6),
                            title = t("dash.activeSubs"),
                            value = "${membershipList.size}",
                            subtitle = t("dash.totalCustomers")
                        )
                        SubscriptionStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.CalendarToday,
                            iconColor = Color(0xFFF59E0B),
                            title = t("dash.expiringSoon"),
                            value = "0",
                            subtitle = t("dash.next3Days")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubscriptionStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.AccessTime,
                            iconColor = Color(0xFF10B981),
                            title = t("dash.todaysLoad"),
                            value = "$openCount",
                            subtitle = t("dash.scheduled")
                        )
                        SubscriptionStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Payment,
                            iconColor = Color(0xFF3B82F6),
                            title = t("dash.subRevenue"),
                            value = "₹${String.format("%.0f", totalIncome)}",
                            subtitle = t("dash.thisMonth")
                        )
                    }
                }
            }
        }

        // Section 5: TOTAL REVENUE combined dashboard statistics
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t("dash.totalRevenue"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = DarkSlate
                        )
                        Text(
                            text = "JUNE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "₹",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF2563EB)
                            )
                        }
                        Column {
                            if (!RolePermissions.canAccessFinancials()) {
                                Text(
                                    text = "🔒 " + t("dash.restricted"),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B)
                                )
                            } else {
                                Text(
                                    text = "₹${String.format("%.2f", combinedIncome)}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = DarkSlate
                                )
                            }
                            Text(
                                text = t("dash.combinedIncome"),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }

        // Section 6: Warning Indicators / LOW STOCK ALERT
        item {
            val lowStockItems = inventoryList.filter { it.quantity <= it.minStockAlert }
            if (lowStockItems.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFEE2E2))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Warning",
                                tint = Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "कम स्टॉक चेतावनी (LOW STOCK)",
                                color = Color.Red,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            lowStockItems.forEach { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFEE2E2).copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = DarkSlate
                                        )
                                        Text(
                                            text = "${item.quantity} left",
                                            color = Color.Red,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 7: RECENT JOB CARDS — latest six, whatever their status
        item {
            // jobList already arrives newest-first from the DAO, so this is simply the last six
            // jobs the workshop touched, each carrying its own status chip.
            val activeJobs = jobList.take(6)
            if (activeJobs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t("dash.recentJobs"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CharcoalGray,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = t("action.viewAll"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepIndigo,
                            modifier = Modifier.clickable { onTabNavigate("jobs") }
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeJobs.forEach { job ->
                            DashboardWorkLogCard(
                                job = job,
                                onClick = { onTabNavigate("jobs") }
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Modal dialogue constructors
    if (showQuickBillDialogLocal) {
        Dialog(onDismissRequest = { showQuickBillDialogLocal = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Log Quick Bill", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF0F172A))
                        IconButton(onClick = { showQuickBillDialogLocal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                        }
                    }

                    Divider(color = Color(0xFFE2E8F0))

                    OutlinedTextField(
                        value = quickBillAmountText,
                        onValueChange = { quickBillAmountText = it },
                        label = { Text("Bill Amount (INR / ₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = quickBillCategoryText,
                        onValueChange = { quickBillCategoryText = it },
                        label = { Text("Category (e.g. Service, Parts)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = quickBillDescText,
                        onValueChange = { quickBillDescText = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Button(
                        onClick = {
                            val amt = quickBillAmountText.toDoubleOrNull() ?: 0.0
                            if (amt > 0) {
                                viewModel.addCashItem(
                                    CashItemEntity(
                                        type = "INCOME",
                                        amount = amt,
                                        category = quickBillCategoryText,
                                        paymentMode = "Cash",
                                        description = quickBillDescText
                                    )
                                )
                                showQuickBillDialogLocal = false
                                quickBillAmountText = ""
                                quickBillDescText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Entry", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    if (showAddMembershipDialogLocal) {
        Dialog(onDismissRequest = { showAddMembershipDialogLocal = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Register Wash Membership", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF0F172A))
                        IconButton(onClick = { showAddMembershipDialogLocal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                        }
                    }

                    Divider(color = Color(0xFFE2E8F0))

                    OutlinedTextField(
                        value = subNameText,
                        onValueChange = { subNameText = it },
                        label = { Text("Client Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = subPhoneText,
                        onValueChange = { subPhoneText = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("+91 ...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Text("Select Service Package Plan:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Basic Wash", "Gold Tier", "Premium Sub").forEach { plan ->
                            FilterChip(
                                selected = subTypeText == plan,
                                onClick = {
                                    subTypeText = plan
                                    subLimitText = when (plan) {
                                        "Basic Wash" -> "4"
                                        "Gold Tier" -> "8"
                                        else -> "12"
                                    }
                                },
                                label = { Text(plan, fontSize = 10.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = subLimitText,
                        onValueChange = { subLimitText = it },
                        label = { Text("Wash Visits Limit Count") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Button(
                        onClick = {
                            if (subNameText.isNotBlank() && subPhoneText.isNotBlank()) {
                                val limit = subLimitText.toIntOrNull() ?: 4
                                viewModel.addMembership(
                                    ServiceMembershipEntity(
                                        customerName = subNameText,
                                        customerPhone = subPhoneText,
                                        membershipType = subTypeText,
                                        monthlyWashLimit = limit,
                                        renewalDate = "2026-07-01",
                                        pendingBalance = 0.0
                                    )
                                )
                                showAddMembershipDialogLocal = false
                                subNameText = ""
                                subPhoneText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add Plan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    if (showAddProfileDialogInDashboard) {
        val context = LocalContext.current
        AddCustomerProfileDialog(
            onDismiss = { showAddProfileDialogInDashboard = false },
            onAddProfile = { profile ->
                viewModel.addCustomerProfile(profile)
                showAddProfileDialogInDashboard = false
                Toast.makeText(context, "Saved customer profile!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun SubscriptionStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color,
    title: String,
    value: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = if (onClick != null) modifier.bentoSpringClickable { onClick() } else modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title.uppercase(java.util.Locale.getDefault()),
                    fontSize = 8.sp,
                    color = CharcoalGray,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkSlate)
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle.uppercase(java.util.Locale.getDefault()),
                fontSize = 8.sp,
                color = Color.Gray,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun QuickActionIconCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color,
    title: String,
    onClick: () -> Unit,
    isHighlighted: Boolean = false
) {
    Card(
        modifier = modifier
            .height(54.dp)
            .bentoSpringClickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) Color(0xFFEEF2FF) else Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (isHighlighted) Color(0xFFC7D2FE) else Color(0xFFF1F5F9))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isHighlighted) Color(0xFF4338CA) else DarkSlate,
                maxLines = 1
            )
        }
    }
}

/**
 * The Garage Guru mark, or the workshop's own logo once one has been uploaded.
 *
 * Sized by the caller rather than fixed, so the same block can head the welcome popup and sit in
 * a settings row without a second copy of the fallback logic.
 */
@Composable
fun GarageLogoMark(
    logoBytes: ByteArray?,
    size: Dp,
    modifier: Modifier = Modifier,
    background: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        if (logoBytes != null && logoBytes.isNotEmpty()) {
            AsyncImage(
                model = logoBytes,
                contentDescription = "Garage Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(size / 4)),
                contentScale = ContentScale.Fit
            )
        } else {
            // No upload yet: the default Garage Guru mark stands in, scaled to the box.
            MechanicalGLogo(modifier = Modifier.size(size * 0.62f))
        }
    }
}

/**
 * The wizard a new workshop owner is walked through the first time they reach the dashboard.
 *
 * It asks only for the things the app cannot work out for itself and would otherwise print wrong
 * or block on later: the address that heads every invoice, the workshop's GST position, how its
 * invoices are numbered, and a logo. It runs once — [GarageSettings.setupCompleted] is set at the
 * end — and every answer stays editable afterwards from Account Settings, which is where the
 * owner is handed on to so any remaining gaps are visible rather than buried.
 *
 * Only the owner sees it; staff roles have no business setting a workshop's tax position.
 */
@Composable
fun FirstTimeSetupWizard(
    garageName: String,
    ownerName: String,
    onGarageLogoSelected: (Uri) -> Unit,
    onRemoveGarageLogo: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val settings by GarageSettings.state.collectAsState()
    val currentLogo by GarageLogoStore.logo.collectAsState()

    var step by remember { mutableIntStateOf(1) }
    val totalSteps = 5

    var servicingType by remember { mutableStateOf(settings.servicingType) }
    var address by remember { mutableStateOf(settings.address) }
    var gstApplicable by remember { mutableStateOf(settings.gstApplicable) }
    var gstin by remember { mutableStateOf(settings.gstin) }
    var cgst by remember { mutableStateOf(trimmedPercent(settings.cgstPercent)) }
    var sgst by remember { mutableStateOf(trimmedPercent(settings.sgstPercent)) }
    var invoicePrefix by remember {
        mutableStateOf(settings.invoicePrefix.ifBlank { GarageSettings.garageCodeFor(garageName) })
    }
    var invoiceStart by remember { mutableStateOf(settings.invoiceStartNumber.toString()) }
    var stockValidation by remember { mutableStateOf(settings.stockValidationEnabled) }
    var showVehicle by remember { mutableStateOf(settings.showVehicleOnInvoice) }
    var stepError by remember { mutableStateOf("") }

    // A workshop that registered while offline still has no Client ID; ask again on the way in.
    LaunchedEffect(garageName) {
        if (settings.clientId.isBlank()) GarageSettings.ensureClientId(garageName)
    }

    val logoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> if (uri != null) onGarageLogoSelected(uri) }

    /**
     * [servicingAnswered] is false only when the wizard is skipped from the very first step: the
     * owner never actually answered the servicing question, so recording the default as their
     * choice would leave the Account Settings checklist claiming a decision that was never made.
     */
    fun commit(markComplete: Boolean, servicingAnswered: Boolean = true) {
        GarageSettings.update(
            servicingType = servicingType,
            servicingTypeChosen = settings.servicingTypeChosen || servicingAnswered,
            address = address.trim(),
            gstin = if (gstApplicable) gstin.trim().uppercase() else gstin.trim().uppercase(),
            gstApplicable = gstApplicable,
            cgstPercent = (cgst.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0),
            sgstPercent = (sgst.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0),
            invoicePrefix = invoicePrefix.trim().uppercase(),
            invoiceStartNumber = (invoiceStart.toIntOrNull() ?: 1001).coerceAtLeast(1),
            stockValidationEnabled = stockValidation,
            showVehicleOnInvoice = showVehicle,
            setupCompleted = markComplete
        )
    }

    /** Blocks Continue only where the requirement makes a field genuinely mandatory. */
    fun validateStep(): String = when (step) {
        3 -> when {
            !gstApplicable -> ""
            gstin.isBlank() -> "GSTIN is required when GST is applicable"
            !GarageSettings.isValidGstin(gstin) -> "That GSTIN does not look valid (e.g. 27ABCDE1234F1Z5)"
            (cgst.toDoubleOrNull() ?: 0.0) + (sgst.toDoubleOrNull() ?: 0.0) <= 0.0 ->
                "Enter the CGST and SGST percentages"
            else -> ""
        }
        4 -> when {
            invoicePrefix.isBlank() -> "Invoice prefix code is required"
            (invoiceStart.toIntOrNull() ?: 0) <= 0 -> "Invoice start number must be a positive number"
            else -> ""
        }
        else -> ""
    }

    Dialog(
        onDismissRequest = { /* deliberate: finished or skipped, never dismissed by accident */ },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header — carries the workshop's logo, falling back to the Garage Guru mark.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeepIndigo)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GarageLogoMark(logoBytes = currentLogo, size = 44.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Welcome, ${ownerName.ifBlank { "Owner" }}",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Set up ${garageName.ifBlank { "your workshop" }} • Step $step of $totalSteps",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Progress
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color(0xFFE5E7EB))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(step.toFloat() / totalSteps)
                            .fillMaxHeight()
                            .background(DeepIndigo)
                    )
                }

                // The whole body scrolls and lifts above the keyboard, so no field or button on
                // any step can end up off the bottom of a small screen.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFF9FAFB))
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (step) {
                        1 -> {
                            // Asked first because it decides which vehicle types, services,
                            // spares and lubricants the rest of the app offers at all.
                            Text(
                                "Garage Servicing For *",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                "This sets up your vehicle master, services, spare parts, oils, job cards and stock for the right kind of vehicle.",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                            GarageSettings.ServicingType.entries.forEach { option ->
                                val selected = servicingType == option
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) Color(0xFFEEF2FF) else Color.White)
                                        .border(
                                            if (selected) 1.5.dp else 1.dp,
                                            if (selected) Color(0xFF4F46E5) else Color(0xFFE2E8F0),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { servicingType = option }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { servicingType = option },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4F46E5))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = when (option) {
                                            GarageSettings.ServicingType.TWO_WHEELER -> Icons.Default.TwoWheeler
                                            GarageSettings.ServicingType.FOUR_WHEELER -> Icons.Default.DirectionsCar
                                            GarageSettings.ServicingType.BOTH -> Icons.Default.Garage
                                        },
                                        contentDescription = null,
                                        tint = if (selected) Color(0xFF4F46E5) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        option.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (selected) Color(0xFF312E81) else Color(0xFF475569)
                                    )
                                }
                            }
                            Text(
                                "You can change this later from Account Settings.",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        2 -> {
                            Text(
                                "Where is your garage?",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                "This address is printed at the top of invoice you hand a customer",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text("Garage Address", fontSize = 12.sp) },
                                placeholder = { Text("Shop no, street, area, city, PIN", fontSize = 12.sp) },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                shape = RoundedCornerShape(10.dp),
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // The Client ID is issued by the system and shown read-only — it is
                            // what support and the printed invoice both refer to.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFEEF2FF))
                                    .padding(12.dp)
                            ) {
                                Text("YOUR CLIENT ID", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
                                Text(
                                    text = settings.clientId.ifBlank { "Generating…" },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF312E81),
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (settings.clientId.isBlank())
                                        "Your ID is being issued and will appear here shortly."
                                    else
                                        "System generated. Quote this when you contact support — it also prints on your invoices.",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        3 -> {
                            Text(
                                "GST details",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                "Answer No if your workshop is not GST registered — invoices will then carry no tax line at all.",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )

                            // Yes / No rather than a switch, because that is the question being
                            // asked and the answer decides whether GSTIN is mandatory.
                            Text("GST Applicable", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                listOf(true to "Yes", false to "No").forEach { (value, label) ->
                                    val selected = gstApplicable == value
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) Color(0xFFEEF2FF) else Color.White)
                                            .border(
                                                1.dp,
                                                if (selected) Color(0xFF4F46E5) else Color(0xFFE2E8F0),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                gstApplicable = value
                                                stepError = ""
                                            }
                                            .padding(vertical = 10.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = {
                                                gstApplicable = value
                                                stepError = ""
                                            },
                                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4F46E5))
                                        )
                                        Text(
                                            label,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (selected) Color(0xFF312E81) else Color(0xFF64748B)
                                        )
                                    }
                                }
                            }

                            if (gstApplicable) {
                                val gstinBad = gstin.isNotBlank() && !GarageSettings.isValidGstin(gstin)
                                OutlinedTextField(
                                    value = gstin,
                                    onValueChange = {
                                        gstin = it.uppercase()
                                        stepError = ""
                                    },
                                    label = { Text("GSTIN *", fontSize = 12.sp) },
                                    placeholder = { Text("27ABCDE1234F1Z5", fontSize = 12.sp) },
                                    isError = gstinBad,
                                    supportingText = {
                                        Text(
                                            if (gstinBad) "15 characters, e.g. 27ABCDE1234F1Z5" else "Required — GST is applicable",
                                            fontSize = 10.sp,
                                            color = if (gstinBad) Color(0xFFEF4444) else Color(0xFF94A3B8)
                                        )
                                    },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Held apart because a tax invoice has to show them apart.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = cgst,
                                        onValueChange = {
                                            cgst = it
                                            stepError = ""
                                        },
                                        label = { Text("CGST %", fontSize = 12.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = sgst,
                                        onValueChange = {
                                            sgst = it
                                            stepError = ""
                                        },
                                        label = { Text("SGST %", fontSize = 12.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                val totalGst = (cgst.toDoubleOrNull() ?: 0.0) + (sgst.toDoubleOrNull() ?: 0.0)
                                Text(
                                    "Total GST charged on a bill: ${trimmedPercent(totalGst)}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4F46E5)
                                )
                            }
                        }

                        4 -> {
                            Text(
                                "How should invoices be numbered?",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                "Each job card gets the next number in this series, and a number is never reused.",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = invoicePrefix,
                                    onValueChange = {
                                        invoicePrefix = it.uppercase()
                                        stepError = ""
                                    },
                                    label = { Text("Invoice Prefix Code *", fontSize = 12.sp) },
                                    placeholder = { Text("SG", fontSize = 12.sp) },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = invoiceStart,
                                    onValueChange = {
                                        invoiceStart = it.filter { ch -> ch.isDigit() }
                                        stepError = ""
                                    },
                                    label = { Text("Start From *", fontSize = 12.sp) },
                                    placeholder = { Text("1001", fontSize = 12.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            val preview = "${invoicePrefix.trim()}${invoiceStart.ifBlank { "1001" }}"
                            val nextPreview = "${invoicePrefix.trim()}${(invoiceStart.toIntOrNull() ?: 1001) + 1}"
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFEEF2FF))
                                    .padding(12.dp)
                            ) {
                                Text("YOUR FIRST INVOICE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
                                Text(
                                    preview,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF312E81),
                                    fontFamily = FontFamily.Monospace
                                )
                                Text("Next will be $nextPreview", fontSize = 10.sp, color = Color(0xFF64748B))
                            }

                            HorizontalDivider(color = Color(0xFFE2E8F0))

                            SettingToggleRow(
                                icon = Icons.Default.Inventory2,
                                title = "Stock availability validation",
                                subtitle = if (stockValidation)
                                    "Block billing an item the stock cannot cover"
                                else
                                    "Allow billing even when stock has run out",
                                checked = stockValidation,
                                onCheckedChange = { stockValidation = it }
                            )
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            SettingToggleRow(
                                icon = Icons.Default.DirectionsCar,
                                title = "Vehicle details on invoice",
                                subtitle = if (showVehicle)
                                    "Print make, model, odometer and fuel"
                                else
                                    "Print the registration plate only",
                                checked = showVehicle,
                                onCheckedChange = { showVehicle = it }
                            )
                        }

                        else -> {
                            Text(
                                "Add your garage logo",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                "Printed on every invoice. Leave it out and the Garage Guru logo is used instead.",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )

                            val hasLogo = currentLogo != null && currentLogo!!.isNotEmpty()

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                GarageLogoMark(
                                    logoBytes = currentLogo,
                                    size = 110.dp,
                                    modifier = Modifier.border(
                                        1.5.dp,
                                        Color(0xFFC7D2FE),
                                        RoundedCornerShape(110.dp / 4)
                                    ),
                                    background = Color(0xFFEEF2FF)
                                )
                                Text(
                                    text = if (hasLogo) "Your logo — this is what customers will see"
                                    else "Default Garage Guru logo in use",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )

                                Button(
                                    onClick = { logoLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(42.dp)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (hasLogo) "Replace Logo" else "Upload Logo",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                if (hasLogo) {
                                    OutlinedButton(
                                        onClick = onRemoveGarageLogo,
                                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(40.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Remove Logo", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                Text(
                                    "PNG, JPG or WEBP • up to ${GarageLogoStore.MAX_UPLOAD_BYTES / 1024} KB after compression",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8),
                                    textAlign = TextAlign.Center
                                )
                            }

                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            Text(
                                "Next you'll land on Account Settings, where anything still outstanding is listed so you can finish it off.",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    if (stepError.isNotEmpty()) {
                        Text(
                            stepError,
                            color = Color(0xFFDC2626),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Divider(color = Color(0xFFE5E7EB), thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step > 1) {
                        OutlinedButton(
                            onClick = {
                                stepError = ""
                                step -= 1
                            },
                            border = BorderStroke(1.dp, DeepIndigo),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DeepIndigo),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text("Back", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Skipping still marks setup done, so the wizard does not reappear on
                        // every launch. Whatever is missing shows up on the Account Settings
                        // checklist instead of being silently forgotten.
                        TextButton(
                            onClick = {
                                commit(markComplete = true, servicingAnswered = false)
                                onFinish()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text("Skip for now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = {
                            val problem = validateStep()
                            if (problem.isNotEmpty()) {
                                stepError = problem
                                return@Button
                            }
                            stepError = ""
                            if (step < totalSteps) {
                                commit(markComplete = false)
                                step += 1
                            } else {
                                commit(markComplete = true)
                                Toast.makeText(context, "Workshop setup saved", Toast.LENGTH_SHORT).show()
                                onFinish()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(
                            if (step < totalSteps) "Continue" else "Finish Setup",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/** `9.0` reads as `9`, `2.5` stays `2.5` — percentages should not gain a pointless decimal. */
fun trimmedPercent(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** Icon, title, explanation and a switch — the shape every workshop-level preference takes. */
@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (checked) Color(0xFF4F46E5) else Color(0xFF94A3B8),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF0F172A))
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF64748B))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF4F46E5)
            )
        )
    }
}

@Composable
fun AccountSettingsView(
    onBackClick: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    ownerName: String = "Owner",
    garageName: String = "Garage Guru",
    garageLogoBytes: ByteArray? = null,
    onGarageNameChange: (String) -> Unit = {},
    onGarageLogoSelected: (Uri) -> Unit = {},
    onRemoveGarageLogo: () -> Unit = {},
    ownerPhone: String = "+91 9876543210",
    ownerEmail: String = "",
    /** Only an owner (or a role granted Settings→Edit) may alter the garage profile itself. */
    canEditGarageProfile: Boolean = true,
    selectedLanguage: String = "en",
    onOpenLanguageDialog: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onOpenTeamManagement: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val settings by GarageSettings.state.collectAsState()
    var isEditingGarageName by remember { mutableStateOf(false) }
    var editedGarageName by remember(garageName) { mutableStateOf(garageName) }
    val hasLogo = garageLogoBytes != null && garageLogoBytes.isNotEmpty()

    // Address and GSTIN are edited in place; the drafts live here so a half-typed value is not
    // written to the cloud on every keystroke.
    var addressDraft by remember(settings.address) { mutableStateOf(settings.address) }
    var gstinDraft by remember(settings.gstin) { mutableStateOf(settings.gstin) }
    var cgstDraft by remember(settings.cgstPercent) { mutableStateOf(trimmedPercent(settings.cgstPercent)) }
    var sgstDraft by remember(settings.sgstPercent) { mutableStateOf(trimmedPercent(settings.sgstPercent)) }
    var invoicePrefixDraft by remember(settings.invoicePrefix) { mutableStateOf(settings.invoicePrefix) }
    var invoiceStartDraft by remember(settings.invoiceStartNumber) {
        mutableStateOf(settings.invoiceStartNumber.toString())
    }

    val logoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // Encoding + upload happens in GarageLogoStore; it reports the outcome itself.
            onGarageLogoSelected(uri)
        }
    }

    val userInitials = remember(ownerName) {
        val parts = ownerName.trim().split(" ")
        if (parts.size >= 2) {
            "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
        } else if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
            parts[0].take(2).uppercase()
        } else "GG"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.White, CircleShape)
                    .border(1.dp, Color(0xFFE2E8F0), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F172A))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = t("set.title"),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A)
            )
        }

        // --- Garage Profile & Logo Upload Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t("set.garageDetails"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4F46E5)
                    )
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = t("set.verified"),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo Preview & Upload Trigger
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFEEF2FF))
                            .border(1.5.dp, Color(0xFFC7D2FE), RoundedCornerShape(16.dp))
                            .clickable { logoLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (garageLogoBytes != null && garageLogoBytes.isNotEmpty()) {
                            AsyncImage(
                                model = garageLogoBytes,
                                contentDescription = "Garage Logo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            MechanicalGLogo(modifier = Modifier.size(36.dp))
                        }

                        // Camera Overlay Icon Badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 4.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4F46E5))
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = "Upload Logo", tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        if (isEditingGarageName) {
                            OutlinedTextField(
                                value = editedGarageName,
                                onValueChange = { editedGarageName = it },
                                label = { Text("Garage Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        if (editedGarageName.isNotBlank()) {
                                            onGarageNameChange(editedGarageName)
                                            isEditingGarageName = false
                                            Toast.makeText(context, "Garage name updated to $editedGarageName!", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Icon(Icons.Default.Check, contentDescription = "Save", tint = Color(0xFF059669))
                                    }
                                }
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = garageName,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (canEditGarageProfile) IconButton(
                                    onClick = { isEditingGarageName = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Garage Name", tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(
                                text = if (hasLogo) "Custom logo active" else "Tap icon or button to upload custom logo",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                if (!canEditGarageProfile) {
                    Text(
                        "Only the garage owner can change these details.",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                if (canEditGarageProfile) Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { logoLauncher.launch("image/*") },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF4F46E5)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4F46E5))
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Upload Logo", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (hasLogo) "Change Logo" else "Upload Logo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // Removing falls back to the default Garage Guru mark rather than to nothing.
                    if (hasLogo) {
                        OutlinedButton(
                            onClick = onRemoveGarageLogo,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove Logo", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Remove", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Gmail-style Large Profile Banner Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4F46E5)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(userInitials, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(ownerName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF0F172A))
                Text(garageName, fontSize = 13.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                Text(ownerPhone, fontSize = 12.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)

                // The workshop's own Client ID, tappable to copy because it is what support asks
                // for and what goes on the printed invoice.
                if (settings.clientId.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEEF2FF))
                            .border(1.dp, Color(0xFFC7D2FE), RoundedCornerShape(8.dp))
                            .clickable {
                                clipboard.setText(AnnotatedString(settings.clientId))
                                Toast.makeText(context, "Client ID ${settings.clientId} copied", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("CLIENT ID", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            settings.clientId,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF312E81),
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Client ID",
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = t("set.ownerAdmin"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Prominent Log Out Button in Profile Card
                OutlinedButton(
                    onClick = onLogoutClick,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Log Out", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(t("action.logout"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // --- Setup checklist: what is done, what is still outstanding ---
        // The wizard hands the owner straight here, so the state of the configuration has to be
        // legible at a glance rather than something they have to go hunting for.
        val pending = settings.pendingSetup
        Text("WORKSHOP SETUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), modifier = Modifier.padding(start = 4.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (pending.isEmpty()) Color(0xFFECFDF5) else Color(0xFFFFFBEB)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (pending.isEmpty()) Color(0xFFA7F3D0) else Color(0xFFFDE68A))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (pending.isEmpty()) Icons.Default.CheckCircle else Icons.Default.PendingActions,
                        contentDescription = null,
                        tint = if (pending.isEmpty()) Color(0xFF059669) else Color(0xFFD97706),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (pending.isEmpty()) "Setup complete" else "${pending.size} setting${if (pending.size == 1) "" else "s"} pending",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = if (pending.isEmpty()) Color(0xFF065F46) else Color(0xFF92400E)
                        )
                        Text(
                            text = if (pending.isEmpty())
                                "Everything needed for billing is configured. You're ready to work."
                            else
                                "Fill these in below before you start billing.",
                            fontSize = 11.sp,
                            color = if (pending.isEmpty()) Color(0xFF047857) else Color(0xFFB45309)
                        )
                    }
                }

                // Each required item, ticked or flagged.
                val checklist = listOf(
                    "Garage servicing type" to settings.servicingTypeChosen,
                    "Garage address" to settings.address.isNotBlank(),
                    "GST position" to (!settings.gstApplicable ||
                        (settings.gstin.isNotBlank() && GarageSettings.isValidGstin(settings.gstin) && settings.gstRatePercent > 0)),
                    "Invoice numbering" to (settings.invoicePrefix.isNotBlank() && settings.invoiceStartNumber > 0),
                    "Client ID issued" to settings.clientId.isNotBlank(),
                    "Garage logo (optional)" to hasLogo
                )
                checklist.forEach { (label, done) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (done) Color(0xFF059669) else Color(0xFF94A3B8),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = if (done) FontWeight.Normal else FontWeight.Bold,
                            color = if (done) Color(0xFF475569) else Color(0xFF92400E)
                        )
                    }
                }

                if (pending.isEmpty()) {
                    Button(
                        onClick = { onNavigate("dashboard") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.Dashboard, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Go to Dashboard", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // --- Business, Billing & Stock rules ---
        Text("BUSINESS & BILLING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), modifier = Modifier.padding(start = 4.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // What the workshop services. Changing it re-filters the vehicle master, the
                // service and parts catalogues, the job-card dropdowns and the stock list.
                Text("Garage Servicing For", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF0F172A))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GarageSettings.ServicingType.entries.forEach { option ->
                        val selected = settings.servicingType == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Color(0xFF4F46E5) else Color(0xFFF1F5F9))
                                .clickable {
                                    GarageSettings.update(servicingType = option, servicingTypeChosen = true)
                                    Toast.makeText(context, "Servicing type set to ${option.label}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (option) {
                                    GarageSettings.ServicingType.TWO_WHEELER -> "2 Wheeler"
                                    GarageSettings.ServicingType.FOUR_WHEELER -> "4 Wheeler"
                                    GarageSettings.ServicingType.BOTH -> "Both"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Garage address — printed at the top of every invoice.
                OutlinedTextField(
                    value = addressDraft,
                    onValueChange = { addressDraft = it },
                    label = { Text("Garage Address *", fontSize = 12.sp) },
                    placeholder = { Text("Shop no, street, area, city, PIN", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (addressDraft != settings.address) {
                            IconButton(onClick = {
                                GarageSettings.update(address = addressDraft.trim())
                                Toast.makeText(context, "Garage address saved", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Check, contentDescription = "Save address", tint = Color(0xFF059669))
                            }
                        }
                    },
                    supportingText = {
                        Text(
                            "This address is printed at the top of invoice you hand a customer",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // GST applicable — Yes / No. Answering No drops the tax line from bills entirely.
                SettingToggleRow(
                    icon = Icons.Default.ReceiptLong,
                    title = "GST Applicable",
                    subtitle = if (settings.gstApplicable)
                        "Invoices add ${trimmedPercent(settings.gstRatePercent)}% GST (CGST ${trimmedPercent(settings.cgstPercent)}% + SGST ${trimmedPercent(settings.sgstPercent)}%)"
                    else
                        "No tax line is added to invoices",
                    checked = settings.gstApplicable,
                    onCheckedChange = { GarageSettings.update(gstApplicable = it) }
                )

                // Every GST field is gone — not merely disabled — when GST is not applicable, so
                // there is no way to enter a GSTIN or a rate that would never be charged.
                if (settings.gstApplicable) {
                    // Mandatory the moment GST applies, so it is labelled and validated as such.
                    val gstinBad = gstinDraft.isNotBlank() && !GarageSettings.isValidGstin(gstinDraft)
                    OutlinedTextField(
                        value = gstinDraft,
                        onValueChange = { gstinDraft = it.uppercase() },
                        label = { Text("GSTIN *", fontSize = 12.sp) },
                        placeholder = { Text("27ABCDE1234F1Z5", fontSize = 12.sp) },
                        isError = gstinBad || gstinDraft.isBlank(),
                        leadingIcon = {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (gstinDraft != settings.gstin && !gstinBad) {
                                IconButton(onClick = {
                                    GarageSettings.update(gstin = gstinDraft.trim())
                                    Toast.makeText(context, "GSTIN saved", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "Save GSTIN", tint = Color(0xFF059669))
                                }
                            }
                        },
                        supportingText = {
                            Text(
                                when {
                                    gstinDraft.isBlank() -> "Required — GST is applicable"
                                    gstinBad -> "15 characters, e.g. 27ABCDE1234F1Z5"
                                    else -> "Printed on every tax invoice"
                                },
                                fontSize = 10.sp,
                                color = if (gstinBad || gstinDraft.isBlank()) Color(0xFFEF4444) else Color(0xFF94A3B8)
                            )
                        },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Stored and printed separately, as a tax invoice requires.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = cgstDraft,
                            onValueChange = { cgstDraft = it },
                            label = { Text("CGST %", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sgstDraft,
                            onValueChange = { sgstDraft = it },
                            label = { Text("SGST %", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    val cgstValue = cgstDraft.toDoubleOrNull()
                    val sgstValue = sgstDraft.toDoubleOrNull()
                    val ratesChanged = cgstValue != null && sgstValue != null &&
                        (cgstValue != settings.cgstPercent || sgstValue != settings.sgstPercent)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total GST: ${trimmedPercent((cgstValue ?: 0.0) + (sgstValue ?: 0.0))}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4F46E5)
                        )
                        if (ratesChanged) {
                            TextButton(onClick = {
                                GarageSettings.update(
                                    cgstPercent = cgstValue!!.coerceIn(0.0, 100.0),
                                    sgstPercent = sgstValue!!.coerceIn(0.0, 100.0)
                                )
                                Toast.makeText(context, "GST rates updated", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Save rates", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Invoice numbering. The prefix and start number are what produce SG1001, SG1002…
                Text("Invoice Numbering", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF0F172A))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = invoicePrefixDraft,
                        onValueChange = { invoicePrefixDraft = it.uppercase() },
                        label = { Text("Prefix Code *", fontSize = 12.sp) },
                        placeholder = { Text("SG", fontSize = 12.sp) },
                        isError = invoicePrefixDraft.isBlank(),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = invoiceStartDraft,
                        onValueChange = { invoiceStartDraft = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Start From *", fontSize = 12.sp) },
                        placeholder = { Text("1001", fontSize = 12.sp) },
                        isError = (invoiceStartDraft.toIntOrNull() ?: 0) <= 0,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                val invoiceChanged = invoicePrefixDraft.trim() != settings.invoicePrefix ||
                    (invoiceStartDraft.toIntOrNull() ?: 0) != settings.invoiceStartNumber
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Next invoice: ${settings.invoicePrefix}${maxOf(settings.invoiceNextNumber, settings.invoiceStartNumber)}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace
                    )
                    if (invoiceChanged && invoicePrefixDraft.isNotBlank() && (invoiceStartDraft.toIntOrNull() ?: 0) > 0) {
                        TextButton(onClick = {
                            GarageSettings.update(
                                invoicePrefix = invoicePrefixDraft.trim(),
                                invoiceStartNumber = invoiceStartDraft.toInt()
                            )
                            Toast.makeText(context, "Invoice numbering updated", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                SettingToggleRow(
                    icon = Icons.Default.DirectionsCar,
                    title = "Vehicle details on invoice",
                    subtitle = if (settings.showVehicleOnInvoice)
                        "Make, model, odometer and fuel are printed"
                    else
                        "Only the registration plate is printed",
                    checked = settings.showVehicleOnInvoice,
                    onCheckedChange = { GarageSettings.update(showVehicleOnInvoice = it) }
                )

                HorizontalDivider(color = Color(0xFFF1F5F9))

                SettingToggleRow(
                    icon = Icons.Default.Inventory2,
                    title = "Stock availability validation",
                    subtitle = if (settings.stockValidationEnabled)
                        "An item cannot be billed unless the stock covers it"
                    else
                        "Items can be billed even when stock has run out",
                    checked = settings.stockValidationEnabled,
                    onCheckedChange = { GarageSettings.update(stockValidationEnabled = it) }
                )

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Publishing the shared rate card is an operator action, not a garage one — only
                // the owner sees it, and the Cloud Function refuses anybody else.
                if (canEditGarageProfile) {
                    var publishing by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Master Data", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF0F172A))
                            Text(
                                "Publish the service rate card and vehicle list to the cloud",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        TextButton(
                            onClick = {
                                publishing = true
                                // Both masters go up together; reporting them separately would
                                // leave the owner guessing which half succeeded.
                                com.example.data.api.ServiceMasterStore.publish { okS, msgS ->
                                    com.example.data.api.VehicleMasterStore.publish { okV, msgV ->
                                        publishing = false
                                        Toast.makeText(
                                            context,
                                            if (okS && okV) "$msgS\n$msgV" else listOf(msgS, msgV).joinToString("\n"),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            enabled = !publishing
                        ) {
                            if (publishing) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = Color(0xFF4F46E5))
                            } else {
                                Text("Publish", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))
                }

                // The garage owner's own registered address. The application operator's address
                // is deliberately not shown here — it is not the garage user's contact detail and
                // showing it invited support mail from the wrong direction.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Garage Owner Email", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF0F172A))
                        Text(
                            ownerEmail.ifBlank { "Not set during registration" },
                            fontSize = 12.sp,
                            color = if (ownerEmail.isBlank()) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }
                    if (ownerEmail.isNotBlank()) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(ownerEmail))
                            Toast.makeText(context, "Owner email copied", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy owner email", tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Section 1: Language Switcher Card
        Text("APP PREFERENCES & LANGUAGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), modifier = Modifier.padding(start = 4.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenLanguageDialog() },
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4F46E5).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Translate, contentDescription = "Language", tint = Color(0xFF4F46E5), modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (selectedLanguage) {
                            "hi" -> "ऐप की भाषा (Language)"
                            "mr" -> "ॲपची भाषा (Language)"
                            else -> "App Language"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = when (selectedLanguage) {
                            "hi" -> "वर्तमान भाषा: हिंदी (Hindi)"
                            "mr" -> "सध्याची भाषा: मराठी (Marathi)"
                            else -> "Current: English"
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
                Surface(
                    color = Color(0xFF4F46E5).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when (selectedLanguage) {
                            "hi" -> "हिंदी"
                            "mr" -> "मराठी"
                            else -> "English"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4F46E5),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Section 2: Workshop Modules
        Text(t("set.modules"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), modifier = Modifier.padding(start = 4.dp))

        // Admin Team & Mechanics Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenTeamManagement() },
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
            border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4F46E5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Engineering, contentDescription = "Garage Team", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(t("set.team"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                    Text(t("set.teamSub"), fontSize = 12.sp, color = Color(0xFF64748B))
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF4F46E5))
            }
        }

        // Module Grid Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Stock Inventory
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigate("system") }
                    .height(96.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(24.dp))
                    Text(t("set.stockModule"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                }
            }

            // Clients & Wash
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigate("customers") }
                    .height(96.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(Icons.Default.PeopleAlt, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(24.dp))
                    Text(t("set.clientsModule"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Ledger Book
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigate("cashbook") }
                    .height(96.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                    Text(t("set.ledgerModule"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                }
            }

            // Notifications
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenNotifications() }
                    .height(96.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
                    Text(t("set.alertsModule"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                }
            }
        }

        // Section 2: Preferences
        Text(t("set.system"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), modifier = Modifier.padding(start = 4.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(t("set.dbSync"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF0F172A), modifier = Modifier.weight(1f))
                    Text(t("set.online"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                }
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(t("set.version"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF0F172A), modifier = Modifier.weight(1f))
                    // Read from the build rather than typed in: the hardcoded string said
                    // "v3.2.0-clean" while the app shipped as 2.3, so a support call could never
                    // establish which build a workshop was actually running.
                    Text(
                        "v${com.example.BuildConfig.VERSION_NAME} (${com.example.BuildConfig.VERSION_CODE})",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun MoreOptionsDialog(
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onTriggerNotifications: () -> Unit,
    ownerName: String = "Owner",
    garageName: String = "Garage Guru Service Center",
    ownerPhone: String = "+91 9876543210",
    userInitials: String = "GG",
    selectedLanguage: String = "en",
    onOpenLanguageDialog: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onOpenTeamManagement: () -> Unit = {}
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MechanicalGLogo(modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Garage Account", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color(0xFF0F172A))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                // Gmail-style Profile Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4F46E5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(userInitials, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ownerName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                            Text(garageName, fontSize = 11.sp, color = Color(0xFF64748B))
                            Text(ownerPhone, fontSize = 11.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(3.dp))
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = t("set.ownerAdmin"),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Divider(color = Color(0xFFE2E8F0))

                Text("Workshop Modules & Tools", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)

                // Featured Option: Garage Team & Mechanics
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onOpenTeamManagement()
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                    border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4F46E5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Engineering, contentDescription = "Team", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t("set.team"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                            Text("Manage Mechanics & Staff", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: Customers / Memberships
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate("customers") }
                            .height(88.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(Icons.Default.PeopleAlt, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(22.dp))
                            Text(t("set.clientsModule"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                        }
                    }

                    // Option 2: Cashbook / Ledger
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate("cashbook") }
                            .height(88.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                            Text(t("set.ledgerModule"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 3: System Coordinator
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate("system") }
                            .height(88.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(22.dp))
                            Text(t("set.stockModule"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                        }
                    }

                    // Option 4: Notifications Simulator
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onTriggerNotifications() }
                            .height(88.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(22.dp))
                            Text(t("set.alertsModule"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                        }
                    }
                }

                // Option 5: Language Switcher
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onOpenLanguageDialog()
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4F46E5).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = "Language", tint = Color(0xFF4F46E5), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (selectedLanguage) {
                                    "hi" -> "भाषा बदलें (App Language)"
                                    "mr" -> "भाषा बदला (App Language)"
                                    else -> "App Language"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "English • हिंदी • मराठी",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Surface(
                            color = Color(0xFF4F46E5).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = when (selectedLanguage) {
                                    "hi" -> "हिंदी"
                                    "mr" -> "मराठी"
                                    else -> "English"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Divider(color = Color(0xFFE2E8F0))

                val context = LocalContext.current
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onLogoutClick()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Log Out", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Log Out", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * One field of the auth form.
 *
 * Every field on that screen wanted the same colours, shape, text size and 11sp label, and the
 * sign-up form has eight of them — spelling each one out in full was what made the form tall
 * enough to run off a small phone. Pulling the shared parts here keeps the form itself short
 * enough to read as a single block.
 */
@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        leadingIcon = leading?.let {
            {
                Icon(it, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
            }
        },
        trailingIcon = onTogglePassword?.let {
            {
                IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        visualTransformation = if (!isPassword || passwordVisible)
            androidx.compose.ui.text.input.VisualTransformation.None
        else
            androidx.compose.ui.text.input.PasswordVisualTransformation(),
        isError = isError,
        supportingText = supportingText?.let {
            { Text(it, fontSize = 10.sp, color = Color(0xFFEF4444)) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF4F46E5),
            focusedLabelColor = Color(0xFF4F46E5),
            unfocusedBorderColor = Color(0xFFCBD5E1),
            cursorColor = Color(0xFF4F46E5)
        ),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    )
}

/**
 * Backdrop for the Log in / Register Garage screens.
 *
 * The approved artwork is looked up by name at runtime rather than referenced as `R.drawable.*`,
 * so dropping `auth_background.(png|jpg|webp)` into `res/drawable/` is the only step needed to
 * adopt it — no code change, and the screen still builds and looks finished while the file is
 * absent. Whatever is behind it, the same dark scrim goes on top: that is what keeps the fields,
 * buttons and logo legible over artwork nobody has seen yet.
 *
 * `ContentScale.Crop` is what makes it work from a small phone to a tablet — the image fills the
 * screen at any aspect ratio instead of letterboxing or stretching.
 */
@Composable
fun AuthBackground(content: @Composable BoxScope.() -> Unit) {
    val context = LocalContext.current
    val backgroundResId = remember {
        runCatching {
            context.resources.getIdentifier("auth_background", "drawable", context.packageName)
        }.getOrDefault(0)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (backgroundResId != 0) {
            Image(
                painter = painterResource(id = backgroundResId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Without this the artwork decides whether white labels are readable. With it, they
            // always are.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F172A).copy(alpha = 0.82f),
                                Color(0xFF1E1B4B).copy(alpha = 0.88f),
                                Color(0xFF312E81).copy(alpha = 0.92f)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF312E81))
                        )
                    )
            )
        }
        content()
    }
}

/**
 * Forgot-password flow: mobile number → OTP → new password.
 *
 * Each stage refuses to move on until the one before it has genuinely succeeded — an unregistered
 * number never triggers an SMS, and a wrong or expired code never reaches the password field.
 */
@Composable
fun ForgotPasswordDialog(
    initialPhone: String,
    onDismiss: () -> Unit,
    onResetComplete: (phone: String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    var stage by remember { mutableIntStateOf(1) } // 1 phone, 2 otp, 3 new password
    var phone by remember { mutableStateOf(initialPhone) }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }

    // Android can read the SMS itself; when it does, the typed-code stage is simply skipped.
    var autoCredential by remember {
        mutableStateOf<com.google.firebase.auth.PhoneAuthCredential?>(null)
    }

    DisposableEffect(Unit) {
        onDispose { FirebaseAuthHelper.PasswordReset.reset() }
    }

    fun sendOtp() {
        val act = activity
        if (act == null) {
            error = "Could not start verification on this screen."
            return
        }
        busy = true
        error = ""
        FirebaseAuthHelper.PasswordReset.isRegistered(phone) { registration ->
            // Only a definite "no" stops the reset. When the lookup itself could not be reached the
            // attempt carries on: the Cloud Function that actually sets the password re-checks the
            // number against Firebase Auth and refuses there, so a flaky lookup costs one unusable
            // SMS instead of permanently locking a real workshop out of its own account.
            if (registration == FirebaseAuthHelper.PasswordReset.Registration.NOT_REGISTERED) {
                busy = false
                error = "No garage is registered against this mobile number."
                return@isRegistered
            }
            FirebaseAuthHelper.PasswordReset.sendOtp(
                activity = act,
                phone = phone,
                onCodeSent = {
                    busy = false
                    info = "OTP sent to ${phone.trim()}"
                    stage = 2
                },
                onVerified = { credential ->
                    // Auto-retrieved: go straight to choosing a password.
                    autoCredential = credential
                    busy = false
                    info = "Mobile number verified automatically."
                    stage = 3
                },
                onError = { message ->
                    busy = false
                    error = message
                }
            )
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reset Password", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color(0xFF0F172A))
                    IconButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    text = when (stage) {
                        1 -> "Step 1 of 3 • Confirm your registered mobile number"
                        2 -> "Step 2 of 3 • Enter the OTP sent to your phone"
                        else -> "Step 3 of 3 • Choose a new password"
                    },
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
                Divider(color = Color(0xFFE2E8F0))

                when (stage) {
                    1 -> {
                        AuthField(
                            value = phone,
                            onValueChange = {
                                phone = it
                                error = ""
                            },
                            label = "Registered Mobile Number",
                            leading = Icons.Default.Phone,
                            keyboardType = KeyboardType.Phone,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "We'll send a one-time password to this number by SMS.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    2 -> {
                        AuthField(
                            value = otp,
                            onValueChange = {
                                otp = it.filter(Char::isDigit).take(6)
                                error = ""
                            },
                            label = "6-digit OTP",
                            leading = Icons.Default.Sms,
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            onClick = { sendOtp() },
                            enabled = !busy,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("Resend OTP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
                        }
                    }

                    else -> {
                        AuthField(
                            value = newPassword,
                            onValueChange = {
                                newPassword = it
                                error = ""
                            },
                            label = "New Password",
                            leading = Icons.Default.Lock,
                            isPassword = true,
                            passwordVisible = showPassword,
                            onTogglePassword = { showPassword = !showPassword },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val mismatch = confirmPassword.isNotEmpty() && confirmPassword != newPassword
                        AuthField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                error = ""
                            },
                            label = "Confirm New Password",
                            leading = Icons.Default.Lock,
                            isPassword = true,
                            passwordVisible = showPassword,
                            isError = mismatch,
                            supportingText = if (mismatch) "Passwords do not match" else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (info.isNotEmpty() && error.isEmpty()) {
                    Text(info, fontSize = 11.sp, color = Color(0xFF059669), fontWeight = FontWeight.Medium)
                }
                if (error.isNotEmpty()) {
                    Text(error, fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        when (stage) {
                            1 -> {
                                if (phone.filter(Char::isDigit).length < 10) {
                                    error = "Enter a valid 10-digit mobile number."
                                } else sendOtp()
                            }
                            2 -> {
                                val credential = FirebaseAuthHelper.PasswordReset.credentialFor(otp)
                                credential.fold(
                                    onSuccess = {
                                        autoCredential = it
                                        error = ""
                                        info = ""
                                        stage = 3
                                    },
                                    onFailure = { error = it.message ?: "Invalid OTP." }
                                )
                            }
                            else -> {
                                when {
                                    newPassword.length < 6 ->
                                        error = "Password must be at least 6 characters."
                                    newPassword != confirmPassword ->
                                        error = "Passwords do not match."
                                    else -> {
                                        busy = true
                                        error = ""
                                        val credential = autoCredential
                                        val finish = { ok: Boolean, message: String ->
                                            busy = false
                                            if (ok) {
                                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                                onResetComplete(phone)
                                            } else {
                                                error = message
                                                // A rejected code sends the owner back to retype
                                                // it rather than stranding them on this step.
                                                if (message.contains("OTP", ignoreCase = true)) stage = 2
                                            }
                                        }
                                        if (credential != null) {
                                            FirebaseAuthHelper.PasswordReset.finishWithCredential(
                                                phone, credential, newPassword, finish
                                            )
                                        } else {
                                            FirebaseAuthHelper.PasswordReset.completeReset(
                                                phone, otp, newPassword, finish
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = when (stage) {
                                1 -> "Send OTP"
                                2 -> "Verify OTP"
                                else -> "Set New Password"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// --- First Time User Mobile Login & Garage Sign Up Screen ---
@Composable
fun GarageAuthScreen(
    selectedLanguage: String = "en",
    onOpenLanguageDialog: () -> Unit = {},
    onLoginSuccess: (ownerName: String, garageName: String, ownerPhone: String) -> Unit
) {
    var isSignUpTab by remember { mutableStateOf(false) }

    // Login fields
    var loginPhone by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var showLoginPassword by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }

    // Sign Up fields
    var signUpGarageName by remember { mutableStateOf("") }
    var signUpOwnerName by remember { mutableStateOf("") }
    var signUpPhone by remember { mutableStateOf("") }
    var signUpEmail by remember { mutableStateOf("") }
    var signUpCity by remember { mutableStateOf("") }
    var signUpPassword by remember { mutableStateOf("") }
    var signUpConfirmPassword by remember { mutableStateOf("") }
    var showSignUpPassword by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var signUpError by remember { mutableStateOf("") }

    val context = LocalContext.current

    // The theme asks for dark status-bar icons because the app is light, but this screen sits on
    // a dark backdrop — dark-on-dark makes the clock, battery and signal icons invisible.
    // Flip them to light while the auth screen is up and hand them back on the way out.
    val authView = androidx.compose.ui.platform.LocalView.current
    if (!authView.isInEditMode) {
        DisposableEffect(Unit) {
            val window = (authView.context as? android.app.Activity)?.window
            val controller = window?.let {
                androidx.core.view.WindowCompat.getInsetsController(it, authView)
            }
            controller?.isAppearanceLightStatusBars = false
            onDispose { controller?.isAppearanceLightStatusBars = true }
        }
        SideEffect {
            val window = (authView.context as? android.app.Activity)?.window
            window?.let {
                androidx.core.view.WindowCompat.getInsetsController(it, authView)
                    .isAppearanceLightStatusBars = false
            }
        }
    }

    fun label(en: String, hi: String, mr: String) = when (selectedLanguage) {
        "hi" -> hi; "mr" -> mr; else -> en
    }

    AuthBackground {
        // The card scrolls and lifts above the keyboard, and is capped in width so it stays a
        // readable column on a tablet instead of stretching edge to edge.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Default Garage Guru logo above the card, on the backdrop.
            GarageLogoMark(
                logoBytes = null,
                size = 62.dp,
                background = Color.White.copy(alpha = 0.12f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Garage Guru",
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Heading row + language pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSignUpTab)
                                label("Register Your Garage", "अपना गैराज पंजीकृत करें", "तुमचे गॅरेज नोंदवा")
                            else
                                label("Welcome Back", "वापस स्वागत है", "पुन्हा स्वागत आहे"),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            onClick = onOpenLanguageDialog,
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEEF2FF),
                            border = BorderStroke(1.dp, Color(0xFFC7D2FE))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Translate,
                                    contentDescription = "Language",
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label("English", "हिंदी", "मराठी"),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4F46E5)
                                )
                            }
                        }
                    }

                    // Tab switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(3.dp)
                    ) {
                        listOf(false, true).forEach { signUp ->
                            val active = isSignUpTab == signUp
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (active) Color(0xFF4F46E5) else Color.Transparent)
                                    .clickable {
                                        isSignUpTab = signUp
                                        signUpError = ""
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (signUp)
                                        label("Register Garage", "गैराज पंजीकरण", "गॅरेज नोंदणी")
                                    else
                                        label("Login", "लॉगिन", "लॉगिन"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (active) Color.White else Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    if (!isSignUpTab) {
                        AuthField(
                            value = loginPhone,
                            onValueChange = { loginPhone = it },
                            label = label("Mobile Number", "मोबाइल नंबर", "मोबाईल नंबर"),
                            leading = Icons.Default.Phone,
                            keyboardType = KeyboardType.Phone,
                            modifier = Modifier.fillMaxWidth()
                        )

                        AuthField(
                            value = loginPassword,
                            onValueChange = { loginPassword = it },
                            label = label("Password", "पासवर्ड", "पासवर्ड"),
                            leading = Icons.Default.Lock,
                            isPassword = true,
                            passwordVisible = showLoginPassword,
                            onTogglePassword = { showLoginPassword = !showLoginPassword },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                onClick = { showForgotPassword = true },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    label("Forgot Password?", "पासवर्ड भूल गए?", "पासवर्ड विसरलात?"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF4F46E5)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (loginPhone.isBlank() || loginPassword.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        label("Please enter mobile number and password", "कृपया मोबाइल नंबर और पासवर्ड दर्ज करें", "कृपया मोबाईल नंबर आणि पासवर्ड टाका"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    isBusy = true
                                    FirebaseAuthHelper.signIn(loginPhone, loginPassword) { success, owner, garage, message ->
                                        isBusy = false
                                        if (success) {
                                            Toast.makeText(context, label("Logged in successfully!", "लॉग इन सफल रहा!", "लॉग इन यशस्वी झाले!"), Toast.LENGTH_SHORT).show()
                                            onLoginSuccess(owner ?: "Owner", garage ?: "Garage Guru", loginPhone)
                                        } else {
                                            Toast.makeText(context, message ?: "Log in failed", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label("LOG IN", "लॉग इन करें", "लॉग इन करा"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    } else {
                        // Paired side by side wherever a field is short, so the form stays one
                        // screen tall with no dead space between rows.
                        AuthField(
                            value = signUpGarageName,
                            onValueChange = {
                                signUpGarageName = it
                                signUpError = ""
                            },
                            label = label("Garage / Workshop Name", "गैराज / वर्कशॉप का नाम", "गॅरेज / वर्कशॉपचे नाव"),
                            leading = Icons.Default.Store,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AuthField(
                                value = signUpOwnerName,
                                onValueChange = {
                                    signUpOwnerName = it
                                    signUpError = ""
                                },
                                label = label("Owner Name", "मालिक का नाम", "मालकाचे नाव"),
                                leading = Icons.Default.Person,
                                modifier = Modifier.weight(1.3f)
                            )
                            AuthField(
                                value = signUpCity,
                                onValueChange = { signUpCity = it },
                                label = label("City", "शहर", "शहर"),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // A real address is what makes password recovery possible at all — the
                        // phone identity maps to a synthetic domain that can never receive mail.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AuthField(
                                value = signUpPhone,
                                onValueChange = {
                                    signUpPhone = it
                                    signUpError = ""
                                },
                                label = label("Mobile Number", "मोबाइल नंबर", "मोबाईल नंबर"),
                                leading = Icons.Default.Phone,
                                keyboardType = KeyboardType.Phone,
                                modifier = Modifier.weight(1f)
                            )
                            AuthField(
                                value = signUpEmail,
                                onValueChange = {
                                    signUpEmail = it
                                    signUpError = ""
                                },
                                label = label("Email ID", "ईमेल आईडी", "ईमेल आयडी"),
                                keyboardType = KeyboardType.Email,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        val mismatch = signUpConfirmPassword.isNotEmpty() && signUpConfirmPassword != signUpPassword
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AuthField(
                                value = signUpPassword,
                                onValueChange = { signUpPassword = it },
                                label = label("Password", "पासवर्ड", "पासवर्ड"),
                                isPassword = true,
                                passwordVisible = showSignUpPassword,
                                onTogglePassword = { showSignUpPassword = !showSignUpPassword },
                                modifier = Modifier.weight(1f)
                            )
                            AuthField(
                                value = signUpConfirmPassword,
                                onValueChange = { signUpConfirmPassword = it },
                                label = label("Confirm", "पुष्टि करें", "खात्री करा"),
                                isPassword = true,
                                passwordVisible = showSignUpPassword,
                                isError = mismatch,
                                supportingText = if (mismatch)
                                    label("Passwords do not match", "पासवर्ड मेल नहीं खाते", "पासवर्ड जुळत नाहीत")
                                else null,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Names the actual clash — mobile, email, or both — rather than leaving
                        // an owner to guess at Firebase's "email already in use".
                        if (signUpError.isNotEmpty()) {
                            Text(
                                signUpError,
                                color = Color(0xFFDC2626),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = {
                                val emailOk = signUpEmail.isBlank() ||
                                    android.util.Patterns.EMAIL_ADDRESS.matcher(signUpEmail.trim()).matches()
                                when {
                                    signUpGarageName.isBlank() || signUpOwnerName.isBlank() ||
                                        signUpPhone.isBlank() || signUpPassword.isBlank() ->
                                        signUpError = label("Please fill all details to create garage", "कृपया गैराज बनाने के लिए सभी जानकारी भरें", "कृपया गॅरेज तयार करण्यासाठी सर्व माहिती भरा")

                                    !emailOk ->
                                        signUpError = label("Please enter a valid email id", "कृपया वैध ईमेल दर्ज करें", "कृपया वैध ईमेल टाका")

                                    signUpPassword.length < 6 ->
                                        signUpError = label("Password must be at least 6 characters", "पासवर्ड कम से कम 6 अक्षर का हो", "पासवर्ड किमान 6 अक्षरांचा हवा")

                                    signUpPassword != signUpConfirmPassword ->
                                        signUpError = label("Passwords do not match", "पासवर्ड मेल नहीं खाते", "पासवर्ड जुळत नाहीत")

                                    else -> {
                                        isBusy = true
                                        signUpError = ""
                                        FirebaseAuthHelper.signUp(
                                            garageName = signUpGarageName,
                                            ownerName = signUpOwnerName,
                                            phone = signUpPhone,
                                            password = signUpPassword,
                                            city = signUpCity,
                                            contactEmail = signUpEmail.trim()
                                        ) { success, message ->
                                            isBusy = false
                                            if (success) {
                                                Toast.makeText(context, label("Garage Account Registered!", "गैराज खाता पंजीकृत हो गया!", "गॅरेज खाते नोंदणीकृत झाले!"), Toast.LENGTH_SHORT).show()
                                                onLoginSuccess(signUpOwnerName, signUpGarageName, signUpPhone)
                                            } else {
                                                signUpError = message
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Icon(Icons.Default.AppRegistration, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label("REGISTER GARAGE", "गैराज पंजीकृत करें", "गॅरेज नोंदवा"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    if (showForgotPassword) {
        ForgotPasswordDialog(
            initialPhone = loginPhone,
            onDismiss = { showForgotPassword = false },
            onResetComplete = { resetPhone ->
                showForgotPassword = false
                isSignUpTab = false
                loginPhone = resetPhone
                loginPassword = ""
            }
        )
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPath(
    name: String,
    path: Path,
    color: Color
) {
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 4f)
    )
}

@Composable
fun DashboardWorkLogCard(job: JobCardEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bentoSpringClickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = job.vehiclePlate,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkSlate)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = job.vehicleName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Client: ${job.customerName}",
                    fontSize = 10.sp,
                    color = CharcoalGray
                )
            }

            // Status chip
            StatusChip(status = job.status)
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val (bgColor, textColor) = when (status.uppercase()) {
        "OPEN" -> Pair(LightBlueChip, DeepIndigo)
        "COMPLETED" -> Pair(LightEmeraldChip, EmeraldDark)
        else -> Pair(LightSlateChip, CharcoalGray)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = status,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp,
            color = textColor
        )
    }
}

// ==========================================
// VIEW 2: ACTIVE JOBS (Cards & Estimator & AI Assistant)
// ==========================================
@Composable
fun JobsView(
    viewModel: GarageViewModel,
    jobs: List<JobCardEntity>,
    inventory: List<InventoryItemEntity>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onViewInvoice: (JobCardEntity) -> Unit,
    onCreateClick: () -> Unit,
    initialStatusFilter: String = "Open"
) {
    var selectedTab by remember { mutableStateOf(initialStatusFilter) }
    // The dashboard counters deep-link straight to their own status, so react when it changes.
    LaunchedEffect(initialStatusFilter) { selectedTab = initialStatusFilter }
    val tabsList = listOf("Open", "Completed", "Closed", "All Jobs")

    // Filter jobs based on selected tab
    val filteredJobsByStatus = remember(jobs, selectedTab) {
        when (selectedTab) {
            "Open" -> jobs.filter { it.status.uppercase() == "OPEN" }
            "Completed" -> jobs.filter { it.status.uppercase() == "COMPLETED" }
            "Closed" -> jobs.filter { it.status.uppercase() == "CLOSED" }
            else -> jobs // All Jobs
        }
    }

    Scaffold(
        containerColor = Color(0xFFF8FAFC), // subtle light slate background matching image
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateClick,
                containerColor = Color(0xFF5B4DFF),
                contentColor = Color.White,
                modifier = Modifier.testTag("create_new_job_fab"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Job Card", modifier = Modifier.size(24.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp) // extremely compact top and side padding to manage blank space
        ) {
            // Screen Title Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Jobs",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1E293B)
                )
            }

            // Scrollable Capsule Style Tabs Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tabsList.forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) Color(0xFF5B4DFF) else Color.White)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFF5B4DFF) else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { selectedTab = tab }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Color(0xFF64748B)
                        )
                    }
                }
            }

            // Separator line
            Divider(color = Color(0xFFE2E8F0), thickness = 1.dp, modifier = Modifier.padding(bottom = 8.dp))

            // Job Cards List
            if (filteredJobsByStatus.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Garage,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No $selectedTab Job Cards found.",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Click the + button to create a new job card.",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredJobsByStatus) { job ->
                        JobCardItem(
                            job = job,
                            onStatusChange = { newStatus ->
                                viewModel.updateJobCardStatus(job, newStatus)
                            },
                            onDelete = {
                                viewModel.deleteJobCard(job.id)
                            },
                            onViewInvoice = {
                                onViewInvoice(job)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JobCardItem(
    job: JobCardEntity,
    onStatusChange: (String) -> Unit,
    onDelete: () -> Unit,
    onViewInvoice: () -> Unit
) {
    val accentColor = remember(job.status) {
        when (job.status.uppercase()) {
            "ESTIMATE" -> Color(0xFFFFA500) // Orange
            "OPEN" -> Color(0xFF3B82F6) // Blue
            "COMPLETED" -> Color(0xFF10B981) // Green
            "CLOSED" -> Color(0xFF64748B) // Slate Gray
            else -> Color(0xFF64748B)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewInvoice() }
            .testTag("job_item_${job.id}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left margin colored border indicator strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            // Content column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                // Row 1: Plate & Total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = job.vehiclePlate.uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "₹${String.format("%.0f", job.totalAmount)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Row 2: Specs, Customer, Items count, Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val cleanVehicle = job.vehicleName.replace(Regex("(?i)Bike:|Scooter:|Car:"), "").trim()
                    val parts = cleanVehicle.split(" ")
                    val primaryName = parts.take(2).joinToString(" ")
                    
                    val dateStr = SimpleDateFormat("M/d", Locale.getDefault()).format(Date(job.creationTime))

                    val tasksCount = if (job.tasks.isBlank()) 0 else job.tasks.split(",").size
                    val materialsCount = if (job.materials.isBlank()) 0 else job.materials.split(",").size
                    val totalItems = tasksCount + materialsCount

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = job.customerName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "•",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = primaryName.ifEmpty { cleanVehicle },
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "•",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "$totalItems Items",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Text(
                        text = dateStr,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

// ==========================================
// VIEW 3: CUSTOMERS & SERVICE PLAN MEMBERSHIPS
// ==========================================
@Composable
fun CustomersView(
    viewModel: GarageViewModel,
    memberships: List<ServiceMembershipEntity>
) {
    val context = LocalContext.current
    var showAddMembershipDialog by remember { mutableStateOf(false) }

    // Forms fields
    var inputName by remember { mutableStateOf("") }
    var inputPhone by remember { mutableStateOf("") }
    var inputType by remember { mutableStateOf("Basic Wash Special") }
    var inputWashLimit by remember { mutableStateOf("4") }

    Scaffold(
        containerColor = SwissWhite,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddMembershipDialog = true },
                containerColor = DeepIndigo,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Subscription")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Client Portal & WASH Plans",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = DarkSlate
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (memberships.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No clients or wash plans registered.", color = CharcoalGray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(memberships) { member ->
                        CustomerMembershipCard(
                            member = member,
                            onIncrementWash = {
                                viewModel.recordMembershipWash(member)
                            },
                            onWhatsAppClick = {
                                val text = "Hello ${member.customerName}, this is Garage Guru. Your wash membership is registered! Limit: ${member.washesUsed}/${member.monthlyWashLimit}. Pending balance level: CHF ${member.pendingBalance}."
                                val url = "whatsapp://send?phone=${member.customerPhone}&text=${UriCodec.encode(text)}"
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Quick WhatsApp sub-link shared (Text copied): $text", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showAddMembershipDialog) {
            Dialog(onDismissRequest = { showAddMembershipDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Register Wash Membership", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF0F172A))
                            IconButton(onClick = { showAddMembershipDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                            }
                        }

                        Divider(color = Color(0xFFE2E8F0))

                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it },
                            label = { Text("Client Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = inputPhone,
                            onValueChange = { inputPhone = it },
                            label = { Text("Phone Number") },
                            placeholder = { Text("+41 79 ...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Text("Select Service Package Plan:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Basic Wash", "Gold Tier", "Premium Sub").forEach { plan ->
                                FilterChip(
                                    selected = inputType == plan,
                                    onClick = {
                                        inputType = plan
                                        inputWashLimit = when (plan) {
                                            "Basic Wash" -> "4"
                                            "Gold Tier" -> "8"
                                            else -> "12"
                                        }
                                    },
                                    label = { Text(plan, fontSize = 10.sp) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = inputWashLimit,
                            onValueChange = { inputWashLimit = it },
                            label = { Text("Wash Visits Limit Count") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Button(
                            onClick = {
                                if (inputName.isNotBlank() && inputPhone.isNotBlank()) {
                                    val limit = inputWashLimit.toIntOrNull() ?: 4
                                    viewModel.addMembership(
                                        ServiceMembershipEntity(
                                            customerName = inputName,
                                            customerPhone = inputPhone,
                                            membershipType = inputType,
                                            monthlyWashLimit = limit,
                                            renewalDate = "2026-07-01",
                                            pendingBalance = 0.0
                                        )
                                    )
                                    showAddMembershipDialog = false
                                    inputName = ""
                                    inputPhone = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Add Plan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

// Quick custom codec for WhatsApp URL share
object UriCodec {
    fun encode(text: String): String {
        return java.net.URLEncoder.encode(text, "UTF-8")
    }
}

@Composable
fun CustomerMembershipCard(
    member: ServiceMembershipEntity,
    onIncrementWash: () -> Unit,
    onWhatsAppClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CoolGrayBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(member.customerName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkSlate)
                    Text(member.customerPhone, fontSize = 11.sp, color = CharcoalGray)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(LightBlueChip)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(member.membershipType, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = DeepIndigo)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Wash tracker slider bar
            Text(
                "Monthly Scheduled Wash Limit Counter:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CharcoalGray
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // visual indicators
                Text(
                    text = "${member.washesUsed} / ${member.monthlyWashLimit} Used",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate,
                    modifier = Modifier.width(90.dp)
                )

                LinearProgressIndicator(
                    progress = { if (member.monthlyWashLimit > 0) member.washesUsed.toFloat() / member.monthlyWashLimit else 0f },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = EmeraldGreen,
                    trackColor = CoolGrayBorder
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Auto Renew: ${member.renewalDate}", fontSize = 10.sp, color = CharcoalGray)
                    if (member.pendingBalance > 0) {
                        Text("Balance: CHF ${member.pendingBalance}", fontSize = 11.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Fully Paid", fontSize = 11.sp, color = EmeraldDark, fontWeight = FontWeight.Bold)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onWhatsAppClick,
                        modifier = Modifier
                            .size(34.dp)
                            .background(LightEmeraldChip, RoundedCornerShape(17.dp))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "WhatsApp Share link", tint = EmeraldDark, modifier = Modifier.size(16.dp))
                    }

                    Button(
                        onClick = onIncrementWash,
                        colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Log Wash", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// VIEW 4: CASHBOOK LEDGER (Ledger book logging)
// ==========================================
@Composable
fun CashbookView(
    viewModel: GarageViewModel,
    cashItems: List<CashItemEntity>
) {
    var showLogDialog by remember { mutableStateOf(false) }
    var selectedFilterTab by remember { mutableStateOf("ALL") } // ALL, INCOME, EXPENSE
    var selectedPaymentFilter by remember { mutableStateOf("ALL") } // ALL, Cash, UPI, Card, Bank

    // Form fields
    var typeSelection by remember { mutableStateOf("INCOME") }
    var amountText by remember { mutableStateOf("") }
    var categoryText by remember { mutableStateOf("Service") }
    var paymentSelection by remember { mutableStateOf("Cash") }
    var descText by remember { mutableStateOf("") }

    val totalIncome = cashItems.filter { it.type == "INCOME" }.sumOf { it.amount }
    val totalExpense = cashItems.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val balance = totalIncome - totalExpense

    val filteredCashItems = remember(cashItems, selectedFilterTab, selectedPaymentFilter) {
        cashItems.filter { item ->
            val matchesTab = when (selectedFilterTab) {
                "INCOME" -> item.type == "INCOME"
                "EXPENSE" -> item.type == "EXPENSE"
                else -> true
            }
            val matchesPayment = if (selectedPaymentFilter == "ALL") true else item.paymentMode.equals(selectedPaymentFilter, ignoreCase = true)
            matchesTab && matchesPayment
        }
    }

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showLogDialog = true },
                containerColor = Color(0xFF4F46E5),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Log Cash")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Log Entry", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // Item 1: Header Title
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = t("cash.title"),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = t("cash.subtitle"),
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEEF2FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color(0xFF4F46E5),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Item 2: Premium Hero Balance Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF4338CA))
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    t("cash.netBalance"),
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFC7D2FE)
                                )
                                Surface(
                                    color = if (balance >= 0) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (balance >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                            contentDescription = null,
                                            tint = if (balance >= 0) Color(0xFF34D399) else Color(0xFFFCA5A5),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (balance >= 0) "Surplus" else "Deficit",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (balance >= 0) Color(0xFF34D399) else Color(0xFFFCA5A5)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "₹ ${String.format("%,.2f", balance)}",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Income & Expense Sub-Cards
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Total Income Pill
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981).copy(alpha = 0.25f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = Color(0xFF34D399),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Total Cash In", fontSize = 10.sp, color = Color(0xFFA5B4FC))
                                            Text(
                                                "₹ ${String.format("%.0f", totalIncome)}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                // Total Expense Pill
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFEF4444).copy(alpha = 0.25f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                tint = Color(0xFFFCA5A5),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Total Cash Out", fontSize = 10.sp, color = Color(0xFFA5B4FC))
                                            Text(
                                                "₹ ${String.format("%.0f", totalExpense)}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Item 3: Filter Tabs (ALL / INCOME / EXPENSES)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE2E8F0))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("ALL" to "All Postings", "INCOME" to "Cash In (+)", "EXPENSE" to "Cash Out (-)").forEach { (tabKey, tabLabel) ->
                        val isSelected = selectedFilterTab == tabKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) Color.White else Color.Transparent)
                                .clickable { selectedFilterTab = tabKey }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    when (tabKey) {
                                        "INCOME" -> Color(0xFF059669)
                                        "EXPENSE" -> Color(0xFFDC2626)
                                        else -> Color(0xFF4F46E5)
                                    }
                                } else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            // Item 4: Payment Mode Filters
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mode:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    listOf("ALL", "Cash", "UPI", "Card", "Bank").forEach { mode ->
                        val isSelected = selectedPaymentFilter == mode
                        Surface(
                            color = if (isSelected) Color(0xFF4F46E5) else Color.White,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF4F46E5) else Color(0xFFCBD5E1)),
                            modifier = Modifier.clickable { selectedPaymentFilter = mode }
                        ) {
                            Text(
                                text = mode,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Item 5: List Header with count
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transaction History",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "${filteredCashItems.size} entries",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Item 6+: Empty state or items list
            if (filteredCashItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No ledger entries found.",
                                color = Color(0xFF64748B),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Tap '+ Log Entry' to add income or expense.",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
                items(filteredCashItems, key = { it.id }) { cash ->
                    LedgerItemRow(cash = cash, onDelete = { viewModel.deleteCashItem(cash.id) })
                }
            }
        }

        if (showLogDialog) {
            Dialog(onDismissRequest = { showLogDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEEF2FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PostAdd, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("New Cashbook Entry", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF0F172A))
                            }
                            IconButton(onClick = { showLogDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                            }
                        }

                        Divider(color = Color(0xFFE2E8F0))

                        // INCOME / EXPENSE Segment Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (typeSelection == "INCOME") Color(0xFF10B981) else Color.Transparent)
                                    .clickable { typeSelection = "INCOME" }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("➕ CASH IN (INCOME)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (typeSelection == "INCOME") Color.White else Color(0xFF475569))
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (typeSelection == "EXPENSE") Color(0xFFEF4444) else Color.Transparent)
                                    .clickable { typeSelection = "EXPENSE" }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("➖ CASH OUT (EXPENSE)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (typeSelection == "EXPENSE") Color.White else Color(0xFF475569))
                            }
                        }

                        // Amount Field
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Amount (₹)") },
                            prefix = { Text("₹ ", fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        // Quick Category Chips
                        Column {
                            Text("Category Suggestion:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val suggestions = if (typeSelection == "INCOME") listOf("Service", "Spare Part", "Washing", "Advance") else listOf("Parts Buy", "Rent", "Tea/Snacks", "Salary", "Fuel")
                                suggestions.forEach { cat ->
                                    Surface(
                                        color = if (categoryText == cat) Color(0xFFEEF2FF) else Color(0xFFF8FAFC),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, if (categoryText == cat) Color(0xFF4F46E5) else Color(0xFFE2E8F0)),
                                        modifier = Modifier.clickable { categoryText = cat }
                                    ) {
                                        Text(
                                            text = cat,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (categoryText == cat) Color(0xFF4F46E5) else Color(0xFF475569),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = categoryText,
                            onValueChange = { categoryText = it },
                            label = { Text("Category Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        // Payment Channel
                        Column {
                            Text("Payment Mode:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("Cash", "UPI", "Card", "Bank").forEach { mode ->
                                    val isSelected = paymentSelection == mode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0xFF4F46E5) else Color(0xFFF1F5F9))
                                            .border(1.dp, if (isSelected) Color(0xFF4F46E5) else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                            .clickable { paymentSelection = mode }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            mode,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else Color(0xFF475569)
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = descText,
                            onValueChange = { descText = it },
                            label = { Text("Description Note / Customer Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Button(
                            onClick = {
                                val amount = amountText.toDoubleOrNull() ?: 0.0
                                if (amount > 0) {
                                    viewModel.addCashItem(
                                        CashItemEntity(
                                            type = typeSelection,
                                            amount = amount,
                                            category = categoryText,
                                            paymentMode = paymentSelection,
                                            description = descText
                                        )
                                    )
                                    showLogDialog = false
                                    amountText = ""
                                    descText = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save Ledger Entry", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LedgerItemRow(cash: CashItemEntity, onDelete: () -> Unit) {
    val isIncome = cash.type == "INCOME"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Avatar Badge
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isIncome) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (isIncome) Color(0xFF166534) else Color(0xFF991B1B),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = cash.category.ifBlank { if (isIncome) "Income" else "Expense" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        // Payment Mode Tag
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = cash.paymentMode,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    if (cash.description.isNotBlank()) {
                        Text(
                            text = cash.description,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (isIncome) "+" else "-"} ₹${String.format("%,.0f", cash.amount)}",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = if (isIncome) Color(0xFF15803D) else Color(0xFFDC2626)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete entry",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// VIEW 5: SYSTEM COORDINATOR (Stock check & FCM notifications, Migration backup import exports)
// ==========================================
/** One line of the stock issue log, with the way to undo it. */
@Composable
fun StockIssueRow(issue: StockIssueEntity, onRevert: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(issue.itemName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(color = Color(0xFFEEF2FF), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            "− ${issue.quantity}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF4F46E5)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(issue.timestamp)))
                        if (issue.issuedTo.isNotBlank()) append("  •  to ${issue.issuedTo}")
                        if (issue.jobCardId > 0) append("  •  Job #${issue.jobCardId}")
                    },
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
                if (issue.reason.isNotBlank()) {
                    Text(issue.reason, fontSize = 11.sp, color = Color(0xFF94A3B8), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "₹${String.format("%.0f", issue.totalValue)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = Color(0xFF0F172A)
                )
                TextButton(
                    onClick = onRevert,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(Icons.Default.Undo, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Return", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                }
            }
        }
    }
}

/**
 * Takes stock out of the store room against a person and a reason.
 *
 * The quantity is capped at what is actually on the shelf, so an issue can never drive a count
 * negative — a shortage has to be corrected by editing the item, where it is visible.
 */
@Composable
fun IssueStockDialog(
    item: InventoryItemEntity,
    onDismiss: () -> Unit,
    onIssue: (quantity: Int, issuedTo: String, reason: String) -> Unit
) {
    var qtyText by remember { mutableStateOf("1") }
    var issuedTo by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    val qty = qtyText.toIntOrNull() ?: 0
    val tooMany = qty > item.quantity
    val valid = qty > 0 && !tooMany

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Issue Stock", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color(0xFF0F172A))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                    }
                }

                Divider(color = Color(0xFFE2E8F0))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF8FAFC))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                        Text("${item.stockType} • ₹${String.format("%.0f", item.unitPrice)} each", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                    Text(
                        "In stock: ${item.quantity}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF10B981)
                    )
                }

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Quantity to issue", fontSize = 12.sp) },
                    isError = tooMany,
                    supportingText = if (tooMany) {
                        { Text("Only ${item.quantity} in stock", fontSize = 11.sp, color = Color(0xFFEF4444)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = issuedTo,
                    onValueChange = { issuedTo = it },
                    label = { Text("Issued to", fontSize = 12.sp) },
                    placeholder = { Text("Mechanic or customer name", fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason / Reference", fontSize = 12.sp) },
                    placeholder = { Text("Job card, counter sale, workshop use", fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Value issued", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    Text(
                        "₹${String.format("%.2f", qty * item.unitPrice)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF5B4DFF)
                    )
                }

                Button(
                    onClick = { onIssue(qty, issuedTo.trim(), reason.trim()) },
                    enabled = valid,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B4DFF)),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Issue Stock", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun SystemCoordinatorView(
    viewModel: GarageViewModel,
    inventory: List<InventoryItemEntity>,
    fcmToken: String,
    searchQuery: String = "",
    onCreateJobClick: () -> Unit,
) {
    val context = LocalContext.current
    var selectedStockTab by remember { mutableStateOf("ALL") }
    val stockIssues by viewModel.stockIssues.collectAsState()
    val stockSettings by GarageSettings.state.collectAsState()

    var showAddStockDialog by remember { mutableStateOf(false) }
    var issuingItem by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var editingItem by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var inputVehicleCategory by remember { mutableStateOf(VehicleCategory.BOTH) }

    val clipboardManager = LocalClipboardManager.current

    // The dialog owns its own fields now, keyed off the item being edited, so nothing has to be
    // copied in and out by hand — which is what used to leave a stale value behind when the
    // stock type changed mid-edit.
    val openAddStockDialog = { itemToEdit: InventoryItemEntity? ->
        editingItem = itemToEdit
        // A new item defaults to the workshop's own kind of work, right far more often than not.
        inputVehicleCategory = itemToEdit?.let { VehicleCategory.from(it.vehicleCategory) }
            ?: when (stockSettings.servicingType) {
                GarageSettings.ServicingType.TWO_WHEELER -> VehicleCategory.TWO_WHEELER
                GarageSettings.ServicingType.FOUR_WHEELER -> VehicleCategory.FOUR_WHEELER
                GarageSettings.ServicingType.BOTH -> VehicleCategory.BOTH
            }
        showAddStockDialog = true
    }

    // Filter stock dynamically based on search query and selected filter tab
    val filteredInventory = remember(searchQuery, inventory, selectedStockTab, stockSettings.servicingType) {
        inventory.filter { part ->
            // A four-wheeler-only workshop has no business seeing chain sprockets on its shelf.
            val matchesServicing = VehicleCatalogue.matches(
                stockSettings.servicingType,
                VehicleCategory.from(part.vehicleCategory)
            )
            val matchesSearch = matchesServicing && part.name.contains(searchQuery, ignoreCase = true)
            val matchesTab = when (selectedStockTab) {
                "LOW STOCK" -> part.tracksStock && part.quantity <= part.minStockAlert
                "SERVICE", "SPARE", "LUBE" -> part.stockType.equals(selectedStockTab, ignoreCase = true)
                else -> true
            }
            matchesSearch && matchesTab
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp)
        ) {
            // Row 1: Title and Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stock",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1E293B)
                )

                // Import the default rate card. Shown only while the workshop has no services
                // of its own — once it does, this is clutter and Add Stock is the way in.
                val hasServices = inventory.any { it.isService }
                if (!hasServices) {
                    OutlinedButton(
                        onClick = {
                            viewModel.importDefaultServices { added, _ ->
                                Toast.makeText(
                                    context,
                                    if (added > 0) "Imported $added default services"
                                    else "Default services are already in your list",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF5B4DFF))
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF5B4DFF), modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import Services", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B4DFF))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // Dedicated Add Stock Button
                Button(
                    onClick = { openAddStockDialog(null) },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B4DFF))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Stock", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            // Filter Tabs Row — scrolls sideways so every filter stays reachable on a narrow phone
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Capsule stock filter tabs
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("ALL", "SERVICE", "SPARE", "LUBE", "LOW STOCK", "ISSUES").forEach { tab ->
                        val isActive = selectedStockTab == tab
                        val bg = if (isActive) Color(0xFF5B4DFF) else Color(0xFFE2E8F0)
                        val tc = if (isActive) Color.White else Color(0xFF475569)

                        val count = when (tab) {
                            "ALL" -> inventory.size
                            "LOW STOCK" -> inventory.count { it.tracksStock && it.quantity <= it.minStockAlert }
                            "ISSUES" -> stockIssues.size
                            else -> inventory.count { it.stockType.equals(tab, ignoreCase = true) }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(bg)
                                .clickable { selectedStockTab = tab }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = tab,
                                    color = tc,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isActive) Color.White.copy(alpha = 0.25f) else Color.White)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = count.toString(),
                                        color = if (isActive) Color.White else Color(0xFF5B4DFF),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                if (selectedStockTab == "ISSUES") {
                    // The withdrawal log: what left the store room, when, and for whom.
                    if (stockIssues.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Outbound, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "No stock issued yet. Use Issue on any item to take it out of the store room.",
                                        fontSize = 13.sp,
                                        color = Color(0xFF64748B),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(stockIssues) { issue ->
                            StockIssueRow(
                                issue = issue,
                                onRevert = {
                                    viewModel.revertStockIssue(issue)
                                    Toast.makeText(
                                        context,
                                        "Returned ${issue.quantity} × ${issue.itemName} to stock",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                } else if (filteredInventory.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No items match '${searchQuery}'" else "No stock items in catalog.",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { openAddStockDialog(null) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B4DFF)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("+ Add First Item", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                } else {
                    items(filteredInventory) { part ->
                        // A service is never counted, so it can never be "low".
                        val isLow = part.tracksStock && part.quantity <= part.minStockAlert
                        val accentColor = if (isLow) Color(0xFFEF4444) else Color(0xFF10B981)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            border = BorderStroke(1.dp, if (isLow) Color(0xFFFECACA) else Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                // Colored stripe on left margin
                                Box(
                                    modifier = Modifier
                                        .width(5.dp)
                                        .fillMaxHeight()
                                        .background(accentColor)
                                )

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = part.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "₹${String.format("%.0f", part.unitPrice)}",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp,
                                                color = Color(0xFF0F172A)
                                            )
                                            if (part.tracksStock && part.purchasePrice > 0) {
                                                Text(
                                                    text = "+₹${String.format("%.0f", part.profitPerUnit)} margin",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (part.profitPerUnit < 0) Color(0xFFDC2626) else Color(0xFF059669)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Stock quantity badge and alert threshold
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (part.tracksStock) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isLow) Color(0xFFFEE2E2) else Color(0xFFECFDF5))
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = "Qty: ${part.quantity}${if (part.unit.isNotBlank()) " ${part.unit}" else ""}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isLow) Color(0xFFEF4444) else Color(0xFF10B981)
                                                    )
                                                }
                                            }

                                            Surface(
                                                color = Color(0xFFEEF2FF),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = part.stockType.uppercase(),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF4F46E5)
                                                )
                                            }

                                            if (part.tracksStock) {
                                                Text(
                                                    text = "Alert under ${part.minStockAlert}",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            } else {
                                                Text(
                                                    text = "Service charge",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                        }

                                        // Quick Action Controls (Issue / Edit / Delete). The bare
                                        // +/- steppers are gone: a count that changes with no
                                        // record of why is what "Issue" exists to replace.
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            // Issue stock out of the store room
                                            TextButton(
                                                onClick = { issuingItem = part },
                                                enabled = part.tracksStock && part.quantity > 0,
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Outbound,
                                                    contentDescription = "Issue Stock",
                                                    tint = if (part.tracksStock && part.quantity > 0) Color(0xFF5B4DFF) else Color(0xFFCBD5E1),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    "Issue",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (part.tracksStock && part.quantity > 0) Color(0xFF5B4DFF) else Color(0xFFCBD5E1)
                                                )
                                            }

                                            // Edit Item
                                            IconButton(
                                                onClick = { openAddStockDialog(part) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Stock",
                                                    tint = Color(0xFF64748B),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            // Delete Item
                                            IconButton(
                                                onClick = { viewModel.deleteInventory(part.id) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete Part",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    issuingItem?.let { item ->
        IssueStockDialog(
            item = item,
            onDismiss = { issuingItem = null },
            onIssue = { qty, issuedTo, reason ->
                viewModel.issueStock(item, qty, issuedTo, reason)
                issuingItem = null
                Toast.makeText(
                    context,
                    "Issued $qty × ${item.name} • stock now ${(item.quantity - qty).coerceAtLeast(0)}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showAddStockDialog) {
        AddStockDialog(
            formKey = editingItem?.id ?: 0,
            editingItem = editingItem,
            inventory = inventory,
            defaultVehicleCategory = inputVehicleCategory,
            onDismiss = { showAddStockDialog = false; editingItem = null },
            onSave = { item ->
                viewModel.addInventoryItem(item)
                showAddStockDialog = false
                editingItem = null
                Toast.makeText(
                    context,
                    if (item.isService) "Service saved" else "Stock item saved",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

/** One read-only computed line — the purchase rate, or the profit it implies. */
@Composable
private fun StockSummaryRow(label: String, value: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF475569))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Black, color = accent)
    }
}

/**
 * Add / edit a catalogue line.
 *
 * The stock type comes first and decides the rest of the form: a Service is labour and needs only
 * a name and a charge, while a Spare or Lube is real stock and needs a supplier, a code, a counted
 * quantity with its own unit, and both prices. Showing the whole superset and leaving most of it
 * blank is what made the old single form confusing — and left services carrying a meaningless
 * quantity that the invoice's stock check could then refuse to bill.
 */
@Composable
fun AddStockDialog(
    /** Changes when a different row is opened, so the form rebuilds instead of keeping old text. */
    formKey: Int,
    editingItem: InventoryItemEntity?,
    inventory: List<InventoryItemEntity>,
    defaultVehicleCategory: VehicleCategory,
    onDismiss: () -> Unit,
    onSave: (InventoryItemEntity) -> Unit
) {
    var stockType by remember(formKey) { mutableStateOf(editingItem?.stockType?.ifBlank { "Spare" } ?: "Spare") }
    var name by remember(formKey) { mutableStateOf(editingItem?.name.orEmpty()) }
    var company by remember(formKey) { mutableStateOf(editingItem?.companyName.orEmpty()) }
    var partCode by remember(formKey) { mutableStateOf(editingItem?.partCode.orEmpty()) }
    var quantity by remember(formKey) { mutableStateOf(editingItem?.quantity?.takeIf { it > 0 }?.toString().orEmpty()) }
    var unit by remember(formKey) {
        mutableStateOf(editingItem?.unit?.ifBlank { StockUnits.defaultFor(stockType) } ?: StockUnits.defaultFor("Spare"))
    }
    var purchasePrice by remember(formKey) {
        mutableStateOf(editingItem?.purchasePrice?.takeIf { it > 0 }?.let { trimmedPercent(it) }.orEmpty())
    }
    var sellingPrice by remember(formKey) {
        mutableStateOf(editingItem?.unitPrice?.takeIf { it > 0 }?.let { trimmedPercent(it) }.orEmpty())
    }
    var minAlert by remember(formKey) { mutableStateOf((editingItem?.minStockAlert ?: 3).toString()) }
    var vehicleCategory by remember(formKey) {
        mutableStateOf(editingItem?.let { VehicleCategory.from(it.vehicleCategory) } ?: defaultVehicleCategory)
    }
    var error by remember { mutableStateOf("") }

    val isService = stockType.equals("Service", ignoreCase = true)
    val units = StockUnits.forStockType(stockType)

    // Switching type carries nothing meaningless across: a unit chosen for a spare is not a unit
    // for a lubricant, and neither applies to a service.
    LaunchedEffect(stockType) {
        if (units.isNotEmpty() && unit !in units) unit = StockUnits.defaultFor(stockType)
        error = ""
    }

    val purchaseValue = purchasePrice.toDoubleOrNull() ?: 0.0
    val sellingValue = sellingPrice.toDoubleOrNull() ?: 0.0
    val profit = sellingValue - purchaseValue

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color(0xFF1E293B),
        unfocusedTextColor = Color(0xFF1E293B),
        focusedBorderColor = Color(0xFF5B4DFF),
        unfocusedBorderColor = Color(0xFFE2E8F0)
    )

    @Composable
    fun field(
        value: String,
        onChange: (String) -> Unit,
        label: String,
        modifier: Modifier = Modifier.fillMaxWidth(),
        numeric: Boolean = false,
        placeholder: String? = null
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); error = "" },
            label = { Text(label, fontSize = 12.sp) },
            placeholder = placeholder?.let { { Text(it, fontSize = 12.sp) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text
            ),
            singleLine = true,
            colors = fieldColors,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
            shape = RoundedCornerShape(8.dp),
            modifier = modifier
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            editingItem != null && isService -> "Edit Service"
                            editingItem != null -> "Edit Stock Item"
                            isService -> "Add Service"
                            else -> "Add Stock Item"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = Color(0xFF0F172A)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Always first, and what everything below depends on.
                    SimpleFormDropdown(
                        label = "Service / Stock Type *",
                        options = listOf("Service", "Spare", "Lube"),
                        selectedOption = stockType,
                        onOptionSelected = { stockType = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Divider(color = Color(0xFFE2E8F0))

                    if (isService) {
                        // 2. A service is labour: a name and a charge, nothing else.
                        field(name, { name = it }, "Add Service Name *", placeholder = "e.g. Engine Oil Change")
                        field(
                            sellingPrice, { sellingPrice = it }, "Service Charge (₹) *",
                            numeric = true, placeholder = "e.g. 300"
                        )
                        Text(
                            "A service has no stock, so it is never counted and never blocks a bill.",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    } else {
                        // 3 & 4. Spare and Lube share a shape; only the wording and units differ.
                        field(
                            name, { name = it },
                            if (stockType.equals("Lube", true)) "Oil/Lube Name *" else "Part/Item Name *",
                            placeholder = if (stockType.equals("Lube", true)) "e.g. Engine Oil 1L" else "e.g. Brake Pad Set"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            field(company, { company = it }, "Company Name", Modifier.weight(1f), placeholder = "Bosch")
                            field(partCode, { partCode = it }, "Part No/Code", Modifier.weight(1f), placeholder = "BP-1204")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            field(quantity, { quantity = it.filter(Char::isDigit) }, "Quantity *", Modifier.weight(1f), numeric = true)
                            SimpleFormDropdown(
                                label = "Unit",
                                options = units,
                                selectedOption = unit,
                                onOptionSelected = { unit = it },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        field(purchasePrice, { purchasePrice = it }, "Purchase Price (₹) *", numeric = true, placeholder = "500")
                        StockSummaryRow(
                            label = "Purchase Rate",
                            value = "₹${String.format("%.2f", purchaseValue)}" +
                                if (unit.isNotBlank()) " / ${unit.lowercase()}" else "",
                            accent = Color(0xFF0284C7)
                        )

                        field(sellingPrice, { sellingPrice = it }, "Selling Price (₹) *", numeric = true, placeholder = "700")
                        StockSummaryRow(
                            label = "Profit Amount",
                            value = "₹${String.format("%.2f", profit)}",
                            accent = if (profit < 0) Color(0xFFDC2626) else Color(0xFF059669)
                        )
                        if (profit < 0 && sellingPrice.isNotBlank() && purchasePrice.isNotBlank()) {
                            Text(
                                "Selling below cost — this item will lose money on every sale.",
                                fontSize = 10.sp,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        field(minAlert, { minAlert = it.filter(Char::isDigit) }, "Low Stock Alert Qty", numeric = true, placeholder = "3")

                        SimpleFormDropdown(
                            label = "Vehicle Category",
                            options = VehicleCategory.entries.map { it.label },
                            selectedOption = vehicleCategory.label,
                            onOptionSelected = { picked ->
                                vehicleCategory = VehicleCategory.entries.firstOrNull { it.label == picked }
                                    ?: VehicleCategory.BOTH
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (error.isNotEmpty()) {
                        Text(error, color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Divider(color = Color(0xFFE2E8F0))
                Button(
                    onClick = {
                        val qty = quantity.toIntOrNull() ?: 0
                        when {
                            name.isBlank() ->
                                error = if (isService) "Service name is required" else "Item name is required"
                            isService && sellingValue <= 0.0 ->
                                error = "Service charge is required"
                            !isService && quantity.isBlank() ->
                                error = "Quantity is required"
                            !isService && purchasePrice.isBlank() ->
                                error = "Purchase price is required"
                            !isService && sellingValue <= 0.0 ->
                                error = "Selling price is required"
                            else -> {
                                val existingMatch = inventory.firstOrNull {
                                    it.id != (editingItem?.id ?: 0) &&
                                        it.name.trim().equals(name.trim(), ignoreCase = true) &&
                                        it.stockType.equals(stockType, ignoreCase = true)
                                }
                                val base = editingItem ?: existingMatch
                                onSave(
                                    (base ?: InventoryItemEntity(name = "", quantity = 0, minStockAlert = 0, unitPrice = 0.0)).copy(
                                        name = name.trim(),
                                        stockType = stockType,
                                        vehicleCategory = if (isService) VehicleCategory.BOTH.stored else vehicleCategory.stored,
                                        companyName = if (isService) "" else company.trim(),
                                        partCode = if (isService) "" else partCode.trim(),
                                        // Adding more of something already on the shelf tops it up
                                        // rather than creating a second row for the same part.
                                        quantity = when {
                                            isService -> 0
                                            editingItem != null -> qty
                                            existingMatch != null -> existingMatch.quantity + qty
                                            else -> qty
                                        },
                                        unit = if (isService) "" else unit,
                                        purchasePrice = if (isService) 0.0 else purchaseValue,
                                        minStockAlert = if (isService) 0 else (minAlert.toIntOrNull() ?: 3),
                                        unitPrice = sellingValue
                                    )
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B4DFF)),
                    modifier = Modifier.fillMaxWidth().padding(20.dp).height(46.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when {
                            editingItem != null -> "Save Changes"
                            isService -> "Add Service"
                            else -> "Add Stock"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

// ==========================================
// OVERLAY 1: MULTI-STEP CREATION DIALOG
// ==========================================
@Composable
fun SimpleFormDropdown(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = "Toggle Dropdown",
                        tint = DeepIndigo
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DeepIndigo,
                focusedLabelColor = DeepIndigo,
                unfocusedLabelColor = Color.Gray,
                cursorColor = DeepIndigo
            ),
            modifier = Modifier.fillMaxWidth()
        )
        // Overlay to capture clicks on the TextField area securely
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent)
                .clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 14.sp) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ==========================================
// OVERLAY 2: DOUBLE BREAKDOWN INVOICE PREVIEW & INTERACTIVE EDITING
// ==========================================
data class InvoiceRowItem(
    val id: Int,
    val description: String,
    val qty: Int,
    val price: Double,
    val isService: Boolean,
    /**
     * Position of this row inside its own source list (`tasks` or `materials`).
     *
     * Editing and deleting used to match on the description text, which silently hit the wrong
     * line whenever a bill listed the same part twice. The index is what actually identifies the
     * entry, so both operations use it.
     */
    val sourceIndex: Int = 0
) {
    val total: Double get() = qty * price
}

/**
 * Renders one line back into the comma-separated form the job card stores.
 *
 * Written as `Name (qty) @ ₹unitPrice`. The `@` matters: job cards created by earlier versions
 * wrote `Name (qty) - ₹total`, where the figure after the dash was the *line* total, not the unit
 * price. Reusing that shape would have made every multi-quantity part reload at a fraction of its
 * price. The two markers are read differently by [parseInvoiceItems], so old bills keep their
 * totals and new ones round-trip exactly — which is what lets an edited price stick instead of
 * snapping back to the catalogue rate.
 */
fun formatInvoiceItem(description: String, qty: Int, price: Double): String {
    // Commas separate entries and parentheses carry the quantity, so neither can appear in a name.
    val safeName = description.replace(",", " ").replace("(", "[").replace(")", "]").trim()
    return "$safeName ($qty) @ ₹${String.format("%.2f", price)}"
}

/**
 * Catalogue rate for a service line that carries no price of its own.
 *
 * Both lookups now go through [VehicleCatalogue], so the rate an invoice falls back to is the
 * same one the picker offered — the two used to be separate hardcoded lists that could drift.
 */
fun getPriceForService(title: String): Double =
    VehicleCatalogue.servicePrice(title) ?: 100.0

fun getPriceForMaterial(title: String): Double =
    VehicleCatalogue.materialPrice(title) ?: 150.0

/**
 * Splits one stored entry into name, quantity and unit price.
 *
 * Three shapes are accepted, so no bill written by an earlier version changes value when it is
 * reopened:
 *  - `Engine Oil Change` — bare name, price looked up through [fallbackPrice];
 *  - `Clutch Cable (2) - ₹300` — the legacy shape, where the figure is the whole line;
 *  - `Clutch Cable (2) @ ₹150` — what [formatInvoiceItem] writes now, a unit price.
 *
 * A written-down price always beats the catalogue, which is what makes an edited rate stick.
 */
private fun parseInvoiceEntry(
    raw: String,
    isService: Boolean,
    fallbackPrice: (String) -> Double
): Triple<String, Int, Double>? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null

    var name = trimmed
    var qty = 1
    var price = 0.0
    var pricedExplicitly = false
    var priceIsPerUnit = false

    fun readPriceAfter(marker: String, perUnit: Boolean): Boolean {
        // The *last* marker wins, so a hyphenated name ("Multi-point check - ₹400") still parses.
        val at = trimmed.lastIndexOf(marker)
        if (at <= 0) return false
        val figure = trimmed.substring(at + marker.length)
            .replace("$", "").replace("₹", "").replace(",", "").trim()
        val parsed = figure.toDoubleOrNull() ?: return false
        price = parsed
        pricedExplicitly = true
        priceIsPerUnit = perUnit
        name = trimmed.substring(0, at).trim()
        return true
    }

    if (!readPriceAfter(" @ ", perUnit = true)) readPriceAfter(" - ", perUnit = false)

    val qtyMatcher = java.util.regex.Pattern.compile("\\((\\d+)\\)\\s*$").matcher(name)
    if (qtyMatcher.find()) {
        qty = qtyMatcher.group(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        name = name.substring(0, qtyMatcher.start()).trim()
    }

    if (!pricedExplicitly) price = fallbackPrice(name)
    // Legacy material entries carried the line total; everything else is already per unit.
    val unitPrice =
        if (!isService && pricedExplicitly && !priceIsPerUnit && qty > 1) price / qty else price

    return Triple(name.ifBlank { trimmed }, qty, unitPrice)
}

fun parseInvoiceItems(tasks: String, materials: String): List<InvoiceRowItem> {
    val rowItems = mutableListOf<InvoiceRowItem>()
    var index = 1

    // Parse tasks (services)
    if (tasks.isNotBlank()) {
        tasks.split(",").forEachIndexed { sourceIndex, s ->
            parseInvoiceEntry(s, isService = true, fallbackPrice = ::getPriceForService)
                ?.let { (name, qty, price) ->
                    rowItems.add(InvoiceRowItem(index++, name, qty, price, true, sourceIndex))
                }
        }
    }

    // Parse materials (spares/lubes)
    if (materials.isNotBlank()) {
        materials.split(",").forEachIndexed { sourceIndex, m ->
            parseInvoiceEntry(m, isService = false, fallbackPrice = ::getPriceForMaterial)
                ?.let { (name, qty, price) ->
                    rowItems.add(InvoiceRowItem(index++, name, qty, price, false, sourceIndex))
                }
        }
    }
    return rowItems
}

/**
 * Re-prices a job card from its item lists.
 *
 * Every add, edit and delete on the invoice ended up repeating the same four lines of arithmetic
 * with an 18% rate written into each copy, so turning GST off — or applying a discount — had to be
 * remembered in eight places. This is the one place the bill is totalled: subtotals from the rows,
 * GST from [GarageSettings] (zero when the owner has switched it off), then the discount taken off
 * the end.
 */
fun recalculatedJob(
    job: JobCardEntity,
    tasks: String = job.tasks,
    materials: String = job.materials,
    discount: Double = job.discount
): JobCardEntity {
    val rows = parseInvoiceItems(tasks, materials)
    val serviceCharges = rows.filter { it.isService }.sumOf { it.total }
    val partsCharges = rows.filterNot { it.isService }.sumOf { it.total }
    val tax = GarageSettings.current.taxOn(serviceCharges + partsCharges)
    val gross = serviceCharges + partsCharges + tax
    // A discount can zero a bill but never turn it into a refund.
    val appliedDiscount = discount.coerceIn(0.0, gross)
    return job.copy(
        tasks = tasks,
        materials = materials,
        serviceCharges = serviceCharges,
        partsCharges = partsCharges,
        taxes = tax,
        discount = appliedDiscount,
        totalAmount = gross - appliedDiscount
    )
}

fun convertAmountToWords(amount: Double): String {
    val cleanVal = amount.toInt()
    if (cleanVal == 0) return "Zero"

    val units = listOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen")
    val tens = listOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

    fun helper(valNum: Int): String {
        return when {
            valNum < 20 -> units[valNum]
            valNum < 100 -> tens[valNum / 10] + (if (valNum % 10 != 0) " " + units[valNum % 10] else "")
            valNum < 1000 -> units[valNum / 100] + " Hundred" + (if (valNum % 100 != 0) " " + helper(valNum % 100) else "")
            valNum < 100000 -> helper(valNum / 1000) + " Thousand" + (if (valNum % 1000 != 0) " " + helper(valNum % 1000) else "")
            else -> helper(valNum / 100000) + " Lakh" + (if (valNum % 100000 != 0) " " + helper(valNum % 100000) else "")
        }
    }

    return try {
        val result = helper(cleanVal).trim().replace(Regex("\\s+"), " ")
        if (result.isBlank()) "Zero Rupees Only" else "$result Rupees Only"
    } catch (e: Exception) {
        "Rupees Only"
    }
}

// Dialog: Receive Payment Block (Double verification & saving entry)
@Composable
fun ReceivePaymentDialog(
    totalBill: Double,
    existingDiscount: Double = 0.0,
    onDismiss: () -> Unit,
    onPaymentConfirmed: (netReceived: Double, discount: Double, mode: String) -> Unit
) {
    // Seeded with whatever discount the invoice already carries, so the figure the customer was
    // shown is the figure being collected.
    var discountInput by remember {
        mutableStateOf(if (existingDiscount > 0) String.format("%.2f", existingDiscount) else "0")
    }
    var selectedPaymentMode by remember { mutableStateOf("Online") } // Online or Cash

    val totalDouble = totalBill
    val discountDouble = (discountInput.trim().toDoubleOrNull() ?: 0.0).coerceIn(0.0, totalDouble)
    val finalPayable = maxOf(0.0, totalDouble - discountDouble)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Receive Payment", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF0F172A))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Divider(color = Color(0xFFE2E8F0))

                // Display Invoice amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Invoice Value", fontSize = 14.sp, color = Color(0xFF475569))
                    Text("₹${String.format("%.2f", totalDouble)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Discount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                    Text("− ₹${String.format("%.2f", discountDouble)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFDC2626))
                }

                // Discount Input
                OutlinedTextField(
                    value = discountInput,
                    onValueChange = { discountInput = it },
                    label = { Text("Flat Discount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("0") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                // Cash or Online radio option
                Text("Payment Mode", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF475569))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { selectedPaymentMode = "Online" }
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedPaymentMode == "Online") Color(0xFFF3E8FF) else Color(0xFFF8FAFC))
                            .border(1.dp, if (selectedPaymentMode == "Online") Color(0xFFC084FC) else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        RadioButton(
                            selected = selectedPaymentMode == "Online",
                            onClick = { selectedPaymentMode = "Online" },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF8B5CF6))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Online", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF5B21B6))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { selectedPaymentMode = "Cash" }
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedPaymentMode == "Cash") Color(0xFFEFF6FF) else Color(0xFFF8FAFC))
                            .border(1.dp, if (selectedPaymentMode == "Cash") Color(0xFF3B82F6) else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        RadioButton(
                            selected = selectedPaymentMode == "Cash",
                            onClick = { selectedPaymentMode = "Cash" },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF3B82F6))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cash", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E3A8A))
                    }
                }

                // Final Net Payable display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFECFDF5))
                        .border(1.dp, Color(0xFFA7F3D0), RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Net Payable Amount", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF047857))
                        Text("₹${String.format("%.2f", finalPayable)}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF065F46))
                    }
                }

                // Submit Action Button
                Button(
                    onClick = { onPaymentConfirmed(finalPayable, discountDouble, selectedPaymentMode) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Paid & Close Job Card", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

/**
 * Popup that edits one line of the bill.
 *
 * Reached from the pencil on each row. Every field the line carries is here — description,
 * quantity, unit price and whether it bills as labour or as a part — because a workshop that
 * quotes a non-standard rate for a job needs to say so on the invoice itself, not work around it
 * by deleting the line and re-adding a custom one.
 */
@Composable
fun EditInvoiceItemDialog(
    item: InvoiceRowItem,
    stockOnHand: Int?,
    onDismiss: () -> Unit,
    onSave: (description: String, qty: Int, price: Double, isService: Boolean) -> Unit
) {
    var description by remember { mutableStateOf(item.description) }
    var qtyText by remember { mutableStateOf(item.qty.toString()) }
    var priceText by remember { mutableStateOf(String.format("%.2f", item.price)) }
    var isService by remember { mutableStateOf(item.isService) }

    val qty = qtyText.toIntOrNull() ?: 0
    val price = priceText.toDoubleOrNull() ?: 0.0
    val valid = description.isNotBlank() && qty > 0 && price >= 0.0

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Edit Bill Item", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color(0xFF0F172A))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                    }
                }

                Divider(color = Color(0xFFE2E8F0))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description", fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Quantity", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Unit Price (₹)", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bills as", fontSize = 12.sp, color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.width(10.dp))
                    FilterChip(
                        selected = isService,
                        onClick = { isService = true },
                        label = { Text("Labour", fontSize = 11.sp) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(
                        selected = !isService,
                        onClick = { isService = false },
                        label = { Text("Part / Lube", fontSize = 11.sp) }
                    )
                }

                if (stockOnHand != null) {
                    Text(
                        text = "In stock: $stockOnHand • changing the quantity adjusts the stock list",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Line total", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    Text(
                        "₹${String.format("%.2f", qty * price)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF5B4DFF)
                    )
                }

                Button(
                    onClick = { onSave(description.trim(), qty, price, isService) },
                    enabled = valid,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B4DFF)),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Item", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

/** Popup for the flat discount, which the invoice then shows as its own deduction line. */
@Composable
fun EditDiscountDialog(
    currentDiscount: Double,
    grossAmount: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var discountText by remember { mutableStateOf(if (currentDiscount > 0) String.format("%.2f", currentDiscount) else "") }
    val discount = (discountText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, grossAmount)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Discount", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color(0xFF0F172A))
                Divider(color = Color(0xFFE2E8F0))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Bill before discount", fontSize = 13.sp, color = Color(0xFF64748B))
                    Text("₹${String.format("%.2f", grossAmount)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                }

                OutlinedTextField(
                    value = discountText,
                    onValueChange = { discountText = it },
                    label = { Text("Flat Discount (₹)", fontSize = 12.sp) },
                    placeholder = { Text("0", fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Payable after discount", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                    Text(
                        "₹${String.format("%.2f", grossAmount - discount)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF047857)
                    )
                }

                Button(
                    onClick = { onSave(discount) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B4DFF)),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Apply Discount", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

/**
 * Popup for the vehicle particulars that are printed on the invoice.
 *
 * The job card composes a display name out of these when it is created; keeping them as their own
 * fields is what lets a wrong registration or an unrecorded odometer reading be corrected before
 * the invoice goes out.
 */
@Composable
fun EditVehicleDetailsDialog(
    job: JobCardEntity,
    onDismiss: () -> Unit,
    onSave: (JobCardEntity) -> Unit
) {
    var plate by remember { mutableStateOf(job.vehiclePlate) }
    var make by remember { mutableStateOf(job.vehicleMake) }
    var model by remember { mutableStateOf(job.vehicleModel) }
    var variant by remember { mutableStateOf(job.vehicleVariant) }
    var km by remember { mutableStateOf(job.vehicleKm) }
    var fuel by remember { mutableStateOf(job.vehicleFuel) }

    @Composable
    fun field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier, numeric: Boolean = false) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label, fontSize = 11.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
            shape = RoundedCornerShape(8.dp),
            modifier = modifier
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Vehicle Details", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color(0xFF0F172A))
                Divider(color = Color(0xFFE2E8F0))

                field(plate, { plate = it }, "Registration Plate", Modifier.fillMaxWidth())

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    field(make, { make = it }, "Make", Modifier.weight(1f))
                    field(model, { model = it }, "Model", Modifier.weight(1f))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    field(variant, { variant = it }, "Variant", Modifier.weight(1f))
                    field(km, { km = it }, "KM Reading", Modifier.weight(1f), numeric = true)
                }

                field(fuel, { fuel = it }, "Fuel Level %", Modifier.fillMaxWidth(), numeric = true)

                Button(
                    onClick = {
                        // The display name is regenerated from the edited parts, so the job list
                        // and the invoice header cannot drift apart from these fields.
                        val prefix = job.vehicleName.substringBefore(":", "").trim()
                        val descriptive = listOf(make, model, variant)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                        val composed = buildString {
                            if (prefix.isNotBlank()) append("$prefix: ")
                            append(descriptive.ifBlank { job.vehicleName.substringAfter(":", job.vehicleName).trim() })
                            if (km.isNotBlank() || fuel.isNotBlank()) {
                                append(" (KM: ${km.ifBlank { "-" }}, Fuel: ${fuel.ifBlank { "-" }}%)")
                            }
                        }.trim()

                        onSave(
                            job.copy(
                                vehiclePlate = plate.trim(),
                                vehicleMake = make.trim(),
                                vehicleModel = model.trim(),
                                vehicleVariant = variant.trim(),
                                vehicleKm = km.trim(),
                                vehicleFuel = fuel.trim(),
                                vehicleName = composed.ifBlank { job.vehicleName }
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B4DFF)),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Vehicle Details", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

/** One selectable line in the Add Item drawer — deliberately a single compact row. */
@Composable
private fun DrawerCatalogueRow(
    title: String,
    price: Double,
    stockOnHand: Int?,
    blocked: Boolean,
    accent: Color,
    qty: Int,
    onQtyChange: (Int) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (blocked) Color(0xFFFEF2F2) else Color(0xFFF8FAFC))
            .border(1.dp, if (blocked) Color(0xFFFECACA) else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFF1E293B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append("₹${String.format("%.0f", price)}")
                    if (stockOnHand != null) append("  •  ${if (blocked) "out of stock" else "stock $stockOnHand"}")
                },
                fontSize = 10.sp,
                color = if (blocked) Color(0xFFDC2626) else Color(0xFF64748B),
                fontWeight = if (blocked) FontWeight.Bold else FontWeight.Normal
            )
        }

        // Quantity stepper, then add — kept on one line so a long catalogue stays scannable.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (qty > 1) onQtyChange(qty - 1) }, modifier = Modifier.size(22.dp)) {
                Text("−", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFF475569))
            }
            Text(
                "$qty",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFF0F172A),
                modifier = Modifier.widthIn(min = 18.dp),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { onQtyChange(qty + 1) }, modifier = Modifier.size(22.dp)) {
                Text("+", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFF475569))
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onAdd,
                enabled = !blocked,
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (blocked) Color(0xFFCBD5E1) else accent)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
fun InvoiceBreakdownPage(
    viewModel: GarageViewModel,
    jobCard: JobCardEntity,
    inventory: List<InventoryItemEntity>,
    customerProfiles: List<CustomerProfileEntity>,
    onDismiss: () -> Unit,
    onSavePdf: (JobCardEntity) -> Unit,
    onPaidAndClosed: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings by GarageSettings.state.collectAsState()

    // Live state connection for instant reactivity on item add/delete
    val allJobs by viewModel.jobCards.collectAsState()
    val activeJobCard = allJobs.find { it.id == jobCard.id } ?: jobCard

    val isClosed = activeJobCard.status.equals("CLOSED", ignoreCase = true)
    // A closed bill is read-only until the owner deliberately reopens it for correction — that
    // switch is what requirement "edit closed bills" asks for, without making a paid invoice
    // silently editable every time it is opened.
    var closedBillUnlocked by remember(activeJobCard.id) { mutableStateOf(false) }
    val canEdit = !isClosed || closedBillUnlocked

    // Custom item state
    var customItemName by remember { mutableStateOf("") }
    var customItemPrice by remember { mutableStateOf("") }
    var customIsService by remember { mutableStateOf(true) }

    // Drawer & popup state
    var showAddItemDrawer by remember { mutableStateOf(false) }
    var drawerTab by remember { mutableStateOf("Services") }
    var showPaymentSelector by remember { mutableStateOf(false) }
    var editingRow by remember { mutableStateOf<InvoiceRowItem?>(null) }
    var showDiscountDialog by remember { mutableStateOf(false) }
    var showVehicleDialog by remember { mutableStateOf(false) }
    var showReopenConfirm by remember { mutableStateOf(false) }

    // Parse list of tasks & components
    val rowItems = remember(activeJobCard.tasks, activeJobCard.materials) {
        parseInvoiceItems(activeJobCard.tasks, activeJobCard.materials)
    }

    // The client's own code, printed alongside the name so a walk-in can be looked up later.
    val clientCode = remember(customerProfiles, activeJobCard.customerPhone, activeJobCard.customerName) {
        customerProfiles.firstOrNull {
            (it.phone.isNotBlank() && it.phone.filter { c -> c.isDigit() } ==
                activeJobCard.customerPhone.filter { c -> c.isDigit() }) ||
                it.name.equals(activeJobCard.customerName, ignoreCase = true)
        }?.clientId.orEmpty()
    }

    /**
     * The stock row a bill line was taken from, if the workshop actually carries that item.
     *
     * Services are excluded: they live in the same catalogue but have no shelf, so restoring or
     * deducting a quantity against one would invent stock that never existed.
     */
    fun stockRowFor(name: String): InventoryItemEntity? =
        inventory.firstOrNull { it.tracksStock && it.name.trim().equals(name.trim(), ignoreCase = true) }

    /** Splits a stored list, drops blanks and keeps the positions [InvoiceRowItem.sourceIndex] uses. */
    fun entriesOf(raw: String): MutableList<String> =
        if (raw.isBlank()) mutableListOf() else raw.split(",").toMutableList()

    fun persist(updated: JobCardEntity) = viewModel.addJobCard(updated)

    /**
     * Writes a rebuilt item list back onto the job card and re-totals it.
     * The status is left alone, so correcting a closed bill keeps it closed.
     */
    fun applyItems(tasks: String, materials: String) {
        persist(recalculatedJob(activeJobCard, tasks = tasks, materials = materials))
    }

    /**
     * Adds one line, taking the units off the stock list when the item is one the workshop carries.
     *
     * With stock validation switched on, an item the shelf cannot cover is refused outright rather
     * than billed and left to be reconciled by hand later.
     */
    fun addLine(name: String, qty: Int, unitPrice: Double, isService: Boolean, fromStock: Boolean): Boolean {
        val stockRow = if (fromStock) stockRowFor(name) else null
        if (stockRow != null && settings.stockValidationEnabled && stockRow.quantity < qty) {
            Toast.makeText(
                context,
                "Only ${stockRow.quantity} of ${stockRow.name} in stock — cannot add $qty",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val entry = formatInvoiceItem(name, qty, unitPrice)
        if (isService) {
            val entries = entriesOf(activeJobCard.tasks).apply { add(entry) }
            applyItems(entries.joinToString(","), activeJobCard.materials)
        } else {
            val entries = entriesOf(activeJobCard.materials).apply { add(entry) }
            applyItems(activeJobCard.tasks, entries.joinToString(","))
        }

        stockRow?.let { viewModel.adjustInventoryQuantity(it, -qty) }
        return true
    }

    /** Removes a line by its position and puts any consumed stock back on the shelf. */
    fun removeLine(item: InvoiceRowItem) {
        if (item.isService) {
            val entries = entriesOf(activeJobCard.tasks)
            if (item.sourceIndex in entries.indices) entries.removeAt(item.sourceIndex)
            applyItems(entries.joinToString(","), activeJobCard.materials)
        } else {
            val entries = entriesOf(activeJobCard.materials)
            if (item.sourceIndex in entries.indices) entries.removeAt(item.sourceIndex)
            applyItems(activeJobCard.tasks, entries.joinToString(","))
        }
        stockRowFor(item.description)?.let { viewModel.adjustInventoryQuantity(it, item.qty) }
    }

    /**
     * Rewrites a line in place, moving it between the two lists if its billing type changed.
     * Returns false when validation refuses the change, so the popup can stay open.
     */
    fun updateLine(original: InvoiceRowItem, description: String, qty: Int, price: Double, isService: Boolean): Boolean {
        val sameItem = description.trim().equals(original.description, ignoreCase = true)
        val stockRowForEdit = if (sameItem) stockRowFor(original.description) else null
        val extraNeeded = qty - original.qty

        // Raising the quantity draws more off the shelf, so the same rule that guards adding an
        // item has to guard editing one.
        if (stockRowForEdit != null && settings.stockValidationEnabled && extraNeeded > stockRowForEdit.quantity) {
            Toast.makeText(
                context,
                "Only ${stockRowForEdit.quantity} more of ${stockRowForEdit.name} in stock",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val tasks = entriesOf(activeJobCard.tasks)
        val materials = entriesOf(activeJobCard.materials)
        val entry = formatInvoiceItem(description, qty, price)

        val sourceList = if (original.isService) tasks else materials
        if (original.sourceIndex !in sourceList.indices) return false

        if (original.isService == isService) {
            sourceList[original.sourceIndex] = entry
        } else {
            sourceList.removeAt(original.sourceIndex)
            (if (isService) tasks else materials).add(entry)
        }

        applyItems(tasks.joinToString(","), materials.joinToString(","))

        // Only the *change* in quantity moves stock: re-saving a line without touching its
        // quantity must not deduct the same units a second time.
        stockRowForEdit?.let { viewModel.adjustInventoryQuantity(it, -extraNeeded) }
        return true
    }

    // The masters are filtered by what this workshop services, so a two-wheeler garage is never
    // offered car labour, car parts or 4L engine oil.
    val servicesCatalogue = remember(settings.servicingType) {
        VehicleCatalogue.services(settings.servicingType)
    }
    val sparesCatalogue = remember(settings.servicingType) {
        VehicleCatalogue.spares(settings.servicingType)
    }
    val lubesCatalogue = remember(settings.servicingType) {
        VehicleCatalogue.lubes(settings.servicingType)
    }

    /**
     * Stock of a given type that this workshop should be billing.
     *
     * An item recorded before categories existed carries `BOTH`, so nothing already on the shelf
     * disappears from the picker when a servicing type is chosen.
     */
    val stockFor: (String) -> List<InventoryItemEntity> = { type ->
        inventory
            .filter { it.stockType.equals(type, ignoreCase = true) }
            .filter {
                VehicleCatalogue.matches(
                    settings.servicingType,
                    VehicleCategory.from(it.vehicleCategory)
                )
            }
            .sortedBy { it.name }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Surface(
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1E293B))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${activeJobCard.status.uppercase()} #JOB-${activeJobCard.id}",
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = Color(0xFF1E293B)
                            )
                        }

                        // Actions on the top right
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Print PDF
                            IconButton(
                                onClick = {
                                    val file = com.example.util.InvoicePdfGenerator.generatePdfFile(context, activeJobCard, clientCode)
                                    if (file != null) {
                                        com.example.util.InvoicePdfGenerator.printPdf(context, file)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Print, contentDescription = "Print PDF", tint = Color(0xFF10B981))
                            }

                            // Share PDF
                            IconButton(
                                onClick = {
                                    val file = com.example.util.InvoicePdfGenerator.generatePdfFile(context, activeJobCard, clientCode)
                                    if (file != null) {
                                        com.example.util.InvoicePdfGenerator.sharePdf(context, file, activeJobCard)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share PDF", tint = Color(0xFF0EA5E9))
                            }

                            // Open / Download PDF
                            IconButton(
                                onClick = {
                                    val file = com.example.util.InvoicePdfGenerator.generatePdfFile(context, activeJobCard, clientCode)
                                    if (file != null) {
                                        com.example.util.InvoicePdfGenerator.openPdf(context, file)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = Color(0xFF5B4DFF))
                            }

                            IconButton(
                                onClick = {
                                    viewModel.deleteJobCard(activeJobCard.id)
                                    onDismiss()
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Job", tint = Color.Red.copy(0.8f))
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFFF1F5F9)
        ) { paddingValues ->
            // The drawer lives inside this box rather than over the whole window, so it opens
            // exactly against the app's own content area instead of across the status bar.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Invoice paper center worksheet (Scrollable content)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp) // more compact container padding
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // A closed bill says so, and offers the way back into editing.
                    if (isClosed) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (closedBillUnlocked) Color(0xFFFEF3C7) else Color(0xFFF1F5F9)
                            ),
                            border = BorderStroke(1.dp, if (closedBillUnlocked) Color(0xFFFCD34D) else Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (closedBillUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (closedBillUnlocked) Color(0xFFB45309) else Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (closedBillUnlocked)
                                        "Editing a closed bill — changes are saved to the closed invoice"
                                    else
                                        "This bill is closed and locked",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (closedBillUnlocked) Color(0xFFB45309) else Color(0xFF64748B),
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        if (closedBillUnlocked) closedBillUnlocked = false else showReopenConfirm = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = if (closedBillUnlocked) "Done" else "Edit Bill",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF5B4DFF)
                                    )
                                }
                            }
                        }
                    }

                    // White Invoice Paper Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) { // streamlined inner padding from 16 to 12
                            // Row 1: Garage details & Status flag
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                // Real workshop identity — logo and name from the signed-in garage.
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    val invoiceLogo by GarageLogoStore.logo.collectAsState()
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFEEF2FF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (invoiceLogo != null && invoiceLogo!!.isNotEmpty()) {
                                            AsyncImage(
                                                model = invoiceLogo,
                                                contentDescription = "Garage Logo",
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            MechanicalGLogo(modifier = Modifier.size(26.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            GarageSession.garageName.ifBlank { "Garage Guru" }.uppercase(),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        if (settings.address.isNotBlank()) {
                                            Text(settings.address, fontSize = 10.sp, color = Color(0xFF64748B))
                                        }
                                        if (GarageSession.ownerPhone.isNotBlank()) {
                                            Text("Contact: ${GarageSession.ownerPhone}", fontSize = 10.sp, color = Color(0xFF64748B))
                                        }
                                        if (settings.gstApplicable && settings.gstin.isNotBlank()) {
                                            Text(
                                                "GSTIN: ${settings.gstin}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF475569)
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = activeJobCard.status.uppercase(),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp, // slightly reduced to fit nicely
                                        color = when (activeJobCard.status.uppercase()) {
                                            "ESTIMATE" -> Color(0xFFFFA500)
                                            "OPEN" -> Color(0xFF3B82F6)
                                            "COMPLETED" -> Color(0xFF10B981)
                                            else -> Color(0xFF64748B)
                                        }
                                    )
                                    Text(
                                        text = "Invoice #: ${activeJobCard.invoiceNumber.ifBlank { "GG-${activeJobCard.id}" }}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569)
                                    )
                                    if (settings.clientId.isNotBlank()) {
                                        Text("Client ID: ${settings.clientId}", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                    val formattedDateStr = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(activeJobCard.creationTime))
                                    Text("Date: $formattedDateStr", fontSize = 10.sp, color = Color(0xFF64748B))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp)) // compact spacer
                            Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(8.dp)) // compact spacer

                            // Row 2: Customer (BILL TO) and Vehicle Information columns
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(t("inv.billTo"), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(activeJobCard.customerName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                    Text("Phone: ${activeJobCard.customerPhone}", fontSize = 11.sp, color = Color(0xFF64748B))
                                    if (clientCode.isNotBlank()) {
                                        Text(
                                            "Client ID: $clientCode",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4F46E5)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(t("inv.vehicleInfo"), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                        if (canEdit) {
                                            IconButton(
                                                onClick = { showVehicleDialog = true },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Edit vehicle details",
                                                    tint = Color(0xFF5B4DFF),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(activeJobCard.vehiclePlate.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))

                                    // Printed particulars, when the owner has left them switched on.
                                    if (settings.showVehicleOnInvoice) {
                                        val descriptive = listOf(
                                            activeJobCard.vehicleMake,
                                            activeJobCard.vehicleModel,
                                            activeJobCard.vehicleVariant
                                        ).filter { it.isNotBlank() }.joinToString(" ")

                                        val fallback = activeJobCard.vehicleName
                                            .replace(Regex("(?i)Bike:|Scooter:|Car:"), "").trim()

                                        Text(
                                            text = descriptive.ifBlank { fallback },
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                        if (activeJobCard.vehicleKm.isNotBlank()) {
                                            Text("Odometer: ${activeJobCard.vehicleKm} km", fontSize = 10.sp, color = Color(0xFF64748B))
                                        }
                                        if (activeJobCard.vehicleFuel.isNotBlank()) {
                                            Text("Fuel: ${activeJobCard.vehicleFuel}%", fontSize = 10.sp, color = Color(0xFF64748B))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp)) // compact spacer
                            Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(8.dp)) // compact spacer

                            // Single + button design requested
                            if (canEdit) {
                                OutlinedButton(
                                    onClick = { showAddItemDrawer = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp), // more compact button
                                    border = BorderStroke(1.dp, Color(0xFF5B4DFF)),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF5F3FF)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color(0xFF5B4DFF),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(t("inv.addItem"), color = Color(0xFF5B4DFF), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }

                                Spacer(modifier = Modifier.height(8.dp)) // compact spacer
                                Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(8.dp)) // compact spacer
                            }

                            // Row 4: Itemized Billing Table Layout
                            Text(t("inv.itemized"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), modifier = Modifier.padding(bottom = 6.dp))

                            // Table Headers Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC))
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("#", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.width(20.dp))
                                Text("ITEM DESCRIPTION", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.weight(1f))
                                Text("QTY", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
                                Text("PRICE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.width(50.dp), textAlign = TextAlign.End)
                                Text("TOTAL", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.width(58.dp), textAlign = TextAlign.End)
                                if (canEdit) {
                                    Spacer(modifier = Modifier.width(52.dp))
                                }
                            }

                            Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                            // Empty Table placeholder
                            if (rowItems.isEmpty()) {
                                Text(
                                    text = "No services or materials added yet. Click + Add button above to insert items.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                rowItems.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${item.id}", fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.width(20.dp))
                                        Text(item.description, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B), modifier = Modifier.weight(1f))
                                        Text("${item.qty}", fontSize = 11.sp, color = Color(0xFF1E293B), modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
                                        Text("₹${String.format("%.0f", item.price)}", fontSize = 11.sp, color = Color(0xFF1E293B), modifier = Modifier.width(50.dp), textAlign = TextAlign.End)
                                        Text("₹${String.format("%.0f", item.total)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), modifier = Modifier.width(58.dp), textAlign = TextAlign.End)

                                        if (canEdit) {
                                            IconButton(
                                                onClick = { editingRow = item },
                                                modifier = Modifier
                                                    .padding(start = 4.dp)
                                                    .size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit item",
                                                    tint = Color(0xFF5B4DFF),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    removeLine(item)
                                                    Toast.makeText(
                                                        context,
                                                        if (item.isService) "Removed service item" else "Removed part item",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                modifier = Modifier
                                                    .padding(start = 4.dp)
                                                    .size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Remove",
                                                    tint = Color.Red.copy(0.7f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                    Divider(color = Color(0xFFF1F5F9))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Total Breakdown Block
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Labor / Services Subtotal", fontSize = 12.sp, color = Color(0xFF64748B))
                                    Text("₹${String.format("%.2f", activeJobCard.serviceCharges)}", fontSize = 12.sp, color = Color(0xFF1E293B))
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Spares & Lubes Subtotal", fontSize = 12.sp, color = Color(0xFF64748B))
                                    Text("₹${String.format("%.2f", activeJobCard.partsCharges)}", fontSize = 12.sp, color = Color(0xFF1E293B))
                                }

                                // GST only appears when the workshop actually charges it, and is
                                // split into CGST and SGST because a tax invoice must show both.
                                if (settings.gstApplicable && settings.gstRatePercent > 0) {
                                    val taxableValue = activeJobCard.serviceCharges + activeJobCard.partsCharges
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            "CGST (${trimmedPercent(settings.cgstPercent)}%)",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                        Text(
                                            "₹${String.format("%.2f", settings.cgstOn(taxableValue))}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            "SGST (${trimmedPercent(settings.sgstPercent)}%)",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                        Text(
                                            "₹${String.format("%.2f", settings.sgstOn(taxableValue))}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            "Total GST (${trimmedPercent(settings.gstRatePercent)}%)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64748B)
                                        )
                                        Text(
                                            "₹${String.format("%.2f", activeJobCard.taxes)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )
                                    }
                                }

                                // DISCOUNT — always shown under its own heading, as a deduction.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "DISCOUNT",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFDC2626)
                                        )
                                        if (canEdit) {
                                            IconButton(
                                                onClick = { showDiscountDialog = true },
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Edit discount",
                                                    tint = Color(0xFF5B4DFF),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (activeJobCard.discount > 0)
                                            "− ₹${String.format("%.2f", activeJobCard.discount)}"
                                        else
                                            "− ₹0.00",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
                                    )
                                }

                                Divider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("GRAND TOTAL VALUE", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                    Text(
                                        text = "₹${String.format("%.2f", activeJobCard.totalAmount)}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = Color(0xFF5B4DFF)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(4.dp))

                            // Amount in Words box
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("AMOUNT IN WORDS: ", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF64748B))
                                Text(
                                    text = convertAmountToWords(activeJobCard.totalAmount),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                            // Terms and Signature lines
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(t("inv.terms"), fontWeight = FontWeight.Bold, fontSize = 8.sp, color = Color(0xFF94A3B8))
                            Text("1. Goods once sold or installed will not be taken back.\n2. Warranty on spare parts as per manufacturer specifications.", fontSize = 8.sp, color = Color(0xFF94A3B8))
                            Text(
                                "3. Next service due on ${com.example.util.InvoicePdfGenerator.nextServiceDueDate(activeJobCard.creationTime)} (3 months from this service).",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF5B4DFF)
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(modifier = Modifier.width(100.dp).height(1.dp).background(Color(0xFFCBD5E1)))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Customer Signature", fontSize = 9.sp, color = Color(0xFF64748B))
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(modifier = Modifier.width(100.dp).height(1.dp).background(Color(0xFFCBD5E1)))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Authorized Signatory", fontSize = 9.sp, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }
                }

                // Sticky bottom control panel
                Surface(
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val pdfFile = com.example.util.InvoicePdfGenerator.generatePdfFile(context, activeJobCard, clientCode)
                                    if (pdfFile != null) {
                                        com.example.util.InvoicePdfGenerator.printPdf(context, pdfFile)
                                    }
                                },
                                border = BorderStroke(1.dp, Color(0xFF64748B)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(42.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Print", color = Color(0xFF64748B), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    val pdfFile = com.example.util.InvoicePdfGenerator.generatePdfFile(context, activeJobCard, clientCode)
                                    if (pdfFile != null) {
                                        com.example.util.InvoicePdfGenerator.sharePdf(context, pdfFile, activeJobCard)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B4DFF)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(42.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Export PDF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Actions determined by status
                        Button(
                            onClick = {
                                when (activeJobCard.status.uppercase()) {
                                    "ESTIMATE" -> {
                                        viewModel.updateJobCardStatus(activeJobCard, "OPEN")
                                        Toast.makeText(context, "Converted Estimate to live Job Card!", Toast.LENGTH_LONG).show()
                                    }
                                    "OPEN" -> {
                                        // "Complete" button is required and moves Open job to Completed. No Paid dialogue triggered here.
                                        viewModel.updateJobCardStatus(activeJobCard, "COMPLETED")
                                        Toast.makeText(context, "Completed Job! Status updated.", Toast.LENGTH_SHORT).show()
                                    }
                                    "COMPLETED" -> {
                                        // Paid & Close button is only required in COMPLETED job card
                                        showPaymentSelector = true
                                    }
                                    else -> {
                                        val text = "Dear ${activeJobCard.customerName}, your invoice GG-${activeJobCard.id} for vehicle ${activeJobCard.vehiclePlate} has been paid and closed. Total amount ₹${String.format("%.0f", activeJobCard.totalAmount)}. Thank you for choosing ${GarageSession.garageName.ifBlank { "Garage Guru" }}!"
                                        val url = "whatsapp://send?phone=${activeJobCard.customerPhone}&text=${UriCodec.encode(text)}"
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Copied SMS text: $text", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (activeJobCard.status.uppercase()) {
                                    "ESTIMATE" -> Color(0xFFFFA500)
                                    "OPEN" -> Color(0xFF3B82F6) // Complete button Blue
                                    "COMPLETED" -> Color(0xFF10B981) // Paid & Close button Green
                                    else -> Color(0xFF5B4DFF) // Closed Share button
                                }
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            val btnText = when (activeJobCard.status.uppercase()) {
                                "ESTIMATE" -> "Convert to Job Card"
                                "OPEN" -> "Complete"
                                "COMPLETED" -> "Paid & Close"
                                else -> "Share SMS Invoice"
                            }
                            Text(btnText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // --- sliding right-to-left drawer overlay, sized to the app's content area ---
            if (showAddItemDrawer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(0.4f))
                        .clickable { showAddItemDrawer = false }
                )
            }

            AnimatedVisibility(
                visible = showAddItemDrawer,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 340.dp)
                        .fillMaxWidth(0.85f),
                    color = Color.White,
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(top = 10.dp)
                    ) {
                        // Drawer Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Add Item",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color(0xFF1E293B)
                            )
                            IconButton(onClick = { showAddItemDrawer = false }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                            }
                        }

                        Divider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 8.dp))

                        // Drawer Tabs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Services", "Spares", "Lubes").forEach { tab ->
                                val isSel = drawerTab == tab
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) Color(0xFF5B4DFF) else Color(0xFFF1F5F9))
                                        .clickable { drawerTab = tab }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tab,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.White else Color(0xFF64748B)
                                    )
                                }
                            }
                        }

                        // Tab contents
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Custom Item Input section — one compact block rather than a card
                            // twice the height of the rows it sits above.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Custom Item", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF0F172A))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = customItemName,
                                        onValueChange = { customItemName = it },
                                        placeholder = { Text("Name", fontSize = 11.sp) },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1.3f)
                                    )
                                    OutlinedTextField(
                                        value = customItemPrice,
                                        onValueChange = { customItemPrice = it },
                                        placeholder = { Text("₹", fontSize = 11.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(0.7f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        FilterChip(
                                            selected = customIsService,
                                            onClick = { customIsService = true },
                                            label = { Text("Service", fontSize = 10.sp) }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        FilterChip(
                                            selected = !customIsService,
                                            onClick = { customIsService = false },
                                            label = { Text("Part", fontSize = 10.sp) }
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            val priceVal = customItemPrice.toDoubleOrNull() ?: 0.0
                                            if (customItemName.isNotBlank() && priceVal > 0) {
                                                // A hand-typed item is not stock, so nothing is deducted for it.
                                                addLine(customItemName.trim(), 1, priceVal, customIsService, fromStock = false)
                                                Toast.makeText(context, "Added: $customItemName", Toast.LENGTH_SHORT).show()
                                                customItemName = ""
                                                customItemPrice = ""
                                            } else {
                                                Toast.makeText(context, "Enter valid item name & price", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                        contentPadding = PaddingValues(horizontal = 14.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            val accent = when (drawerTab) {
                                "Spares" -> Color(0xFFF97316)
                                "Lubes" -> Color(0xFF10B981)
                                else -> Color(0xFF5B4DFF)
                            }
                            val stockRows = stockFor(if (drawerTab == "Services") "Service" else drawerTab.dropLast(1))

                            // Master catalogue for the open tab, already narrowed to what this
                            // workshop services. Nothing here is stock-backed, so a catalogue
                            // entry can always be billed; the stock section below is what
                            // validation applies to.
                            val catalogue = when (drawerTab) {
                                "Spares" -> sparesCatalogue
                                "Lubes" -> lubesCatalogue
                                else -> servicesCatalogue
                            }
                            val catalogueIsService = drawerTab == "Services"

                            Text(
                                text = when (drawerTab) {
                                    "Spares" -> "Spare Parts Master"
                                    "Lubes" -> "Oil & Lubricants Master"
                                    else -> "Standard Services"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                            catalogue.forEach { entry ->
                                var qtyChoice by remember(entry.title) { mutableIntStateOf(1) }
                                DrawerCatalogueRow(
                                    title = entry.title,
                                    price = entry.price,
                                    stockOnHand = null,
                                    blocked = false,
                                    accent = accent,
                                    qty = qtyChoice,
                                    onQtyChange = { qtyChoice = it },
                                    onAdd = {
                                        addLine(entry.title, qtyChoice, entry.price, catalogueIsService, fromStock = false)
                                        Toast.makeText(context, "Added: ${entry.title}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            Text(
                                text = "From Stock (${drawerTab})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            if (stockRows.isEmpty()) {
                                Text(
                                    text = "Nothing of this type in the stock list yet. Add it under Stock first.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                stockRows.forEach { stockItem ->
                                    var qtyChoice by remember(stockItem.id) { mutableIntStateOf(1) }
                                    // With validation on, a line the shelf cannot cover cannot be added.
                                    val blocked = stockItem.tracksStock &&
                                        settings.stockValidationEnabled && stockItem.quantity < qtyChoice
                                    DrawerCatalogueRow(
                                        title = stockItem.name,
                                        price = stockItem.unitPrice,
                                        stockOnHand = if (stockItem.tracksStock) stockItem.quantity else null,
                                        blocked = blocked,
                                        accent = accent,
                                        qty = qtyChoice,
                                        onQtyChange = { qtyChoice = it },
                                        onAdd = {
                                            val added = addLine(
                                                stockItem.name,
                                                qtyChoice,
                                                stockItem.unitPrice,
                                                isService = !stockItem.tracksStock,
                                                // Labour has no shelf, so it is never checked
                                                // against stock and never deducted from it.
                                                fromStock = stockItem.tracksStock
                                            )
                                            if (added) {
                                                Toast.makeText(
                                                    context,
                                                    "Added ${stockItem.name} • stock now ${(stockItem.quantity - qtyChoice).coerceAtLeast(0)}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }

    // --- Per-line editing popup ---
    editingRow?.let { row ->
        EditInvoiceItemDialog(
            item = row,
            stockOnHand = stockRowFor(row.description)?.quantity,
            onDismiss = { editingRow = null },
            onSave = { description, qty, price, isService ->
                // A refused edit leaves the popup open with the entered values intact.
                if (updateLine(row, description, qty, price, isService)) {
                    editingRow = null
                    Toast.makeText(context, "Updated $description", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // --- Discount popup ---
    if (showDiscountDialog) {
        val gross = activeJobCard.serviceCharges + activeJobCard.partsCharges + activeJobCard.taxes
        EditDiscountDialog(
            currentDiscount = activeJobCard.discount,
            grossAmount = gross,
            onDismiss = { showDiscountDialog = false },
            onSave = { discount ->
                persist(recalculatedJob(activeJobCard, discount = discount))
                showDiscountDialog = false
            }
        )
    }

    // --- Vehicle details popup ---
    if (showVehicleDialog) {
        EditVehicleDetailsDialog(
            job = activeJobCard,
            onDismiss = { showVehicleDialog = false },
            onSave = { updated ->
                persist(updated)
                showVehicleDialog = false
                Toast.makeText(context, "Vehicle details updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // --- Reopening a closed bill is a deliberate act, so it is confirmed ---
    if (showReopenConfirm) {
        AlertDialog(
            onDismissRequest = { showReopenConfirm = false },
            title = { Text("Edit closed bill?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This invoice has already been paid and closed. Any change you make now will " +
                        "alter the closed bill and its printed total. The cash entry already " +
                        "recorded for it is not adjusted automatically.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    closedBillUnlocked = true
                    showReopenConfirm = false
                }) {
                    Text("Unlock for editing", fontWeight = FontWeight.Bold, color = Color(0xFF5B4DFF))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReopenConfirm = false }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }

    // --- Receive Payment and close Popup ---
    if (showPaymentSelector) {
        ReceivePaymentDialog(
            totalBill = activeJobCard.serviceCharges + activeJobCard.partsCharges + activeJobCard.taxes,
            existingDiscount = activeJobCard.discount,
            onDismiss = { showPaymentSelector = false },
            onPaymentConfirmed = { netReceived, discount, mode ->
                showPaymentSelector = false
                // Deliberately do NOT dismiss: the worksheet stays open and re-reads the job as
                // CLOSED, so the primary button becomes "Share SMS Invoice" and the owner can
                // send it to the customer immediately. Back returns to the Closed list.
                onPaidAndClosed()

                // 1. Record the discount on the bill and move status to CLOSED in one write, so
                //    the invoice the customer keeps shows the same deduction that was charged.
                persist(recalculatedJob(activeJobCard, discount = discount).copy(status = "CLOSED"))

                // 2. Append ledger transaction
                viewModel.addCashItem(
                    CashItemEntity(
                        type = "INCOME",
                        amount = netReceived,
                        category = "Service",
                        paymentMode = mode,
                        description = "Closed Job No. ${activeJobCard.id} for vehicle ${activeJobCard.vehiclePlate} (${activeJobCard.customerName})",
                        timestamp = System.currentTimeMillis()
                    )
                )

                Toast.makeText(context, "Job successfully paid (₹${String.format("%.2f", netReceived)} received via $mode) and closed!", Toast.LENGTH_LONG).show()
            }
        )
    }
}

// ==========================================
// OVERLAY 3: FCM NOTIFICATION SIMULATION
// ==========================================
@Composable
fun SimulatedPushDialog(
    fcmToken: String,
    onDismiss: () -> Unit,
    onTriggerNotification: (String) -> Unit
) {
    var notificationMessageInput by remember { mutableStateOf("Low parts alert: stock Mobil 1 Oil has dropped below safety limits!") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Service Push Terminal", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF0F172A))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Divider(color = Color(0xFFE2E8F0))

                Text(
                    "Simulate background push instructions sent to this tool token:",
                    fontSize = 11.sp,
                    color = CharcoalGray
                )
                Text(
                    fcmToken,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = DeepIndigo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(LightBlueChip)
                        .padding(6.dp)
                )

                OutlinedTextField(
                    value = notificationMessageInput,
                    onValueChange = { notificationMessageInput = it },
                    label = { Text("Push Alert Message") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Button(
                    onClick = {
                        if (notificationMessageInput.isNotBlank()) {
                            onTriggerNotification(notificationMessageInput)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Simulate Trigger", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// ROOM DATABASE SERVICE INTERVAL ALERTS & NOTIFICATION SYSTEM
// ==========================================
@Composable
fun ServiceIntervalsDialog(
    viewModel: GarageViewModel,
    fcmToken: String,
    onDismiss: () -> Unit,
    onCreateJobForCustomer: (customerName: String, phone: String, vehiclePlate: String, serviceType: String) -> Unit
) {
    val context = LocalContext.current
    val alerts by viewModel.serviceAlerts.collectAsState()
    var selectedTab by remember { mutableStateOf("Service Intervals") }
    var showAddAlertDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            t("rem.title"),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = DarkSlate
                        )
                        Text(
                            "Managed by Garage Local Engine",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == "Service Intervals",
                        onClick = { selectedTab = "Service Intervals" },
                        label = { Text("Service Alerts (${alerts.size})", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = selectedTab == "Push Test",
                        onClick = { selectedTab = "Push Test" },
                        label = { Text("Push Notification Channel", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTab == "Service Intervals") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t("rem.activeDue"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkSlate)
                        Button(
                            onClick = { showAddAlertDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(t("rem.newAlert"), fontSize = 11.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (alerts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(t("rem.none"), color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(alerts) { alert ->
                                ServiceAlertCard(
                                    alert = alert,
                                    onMarkServiced = {
                                        viewModel.updateServiceAlertStatus(alert.id, "SERVICED")
                                        Toast.makeText(context, "Marked ${alert.serviceType} as Serviced!", Toast.LENGTH_SHORT).show()
                                    },
                                    onDelete = {
                                        viewModel.deleteServiceAlert(alert.id)
                                    },
                                    onSendReminder = {
                                        val text = "Hello ${alert.customerName}, your vehicle (${alert.vehiclePlate}) is due for ${alert.serviceType}. Date: ${alert.dueDateString}. Please visit Garage Guru!"
                                        val url = "whatsapp://send?phone=${alert.customerPhone}&text=${UriCodec.encode(text)}"
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Reminder copied: $text", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    onCreateJob = {
                                        onCreateJobForCustomer(alert.customerName, alert.customerPhone, alert.vehiclePlate, alert.serviceType)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Push Test
                    Column(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Notification Channel Token:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(fcmToken, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = DeepIndigo, modifier = Modifier.background(LightBlueChip).padding(6.dp))
                        
                        var pushMsg by remember { mutableStateOf("Service due notification: Oil Change interval reached") }
                        OutlinedTextField(
                            value = pushMsg,
                            onValueChange = { pushMsg = it },
                            label = { Text("Test Push Payload") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                Toast.makeText(context, "Push Alert: $pushMsg", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Trigger Local Push Alert")
                        }
                    }
                }
            }
        }
    }

    if (showAddAlertDialog) {
        AddServiceAlertDialog(
            onDismiss = { showAddAlertDialog = false },
            onAddAlert = { alert ->
                viewModel.addServiceAlert(alert)
                showAddAlertDialog = false
            },
            customers = viewModel.customerProfiles.collectAsState().value,
            jobs = viewModel.jobCards.collectAsState().value
        )
    }
}

@Composable
fun ServiceAlertCard(
    alert: ServiceAlertEntity,
    onMarkServiced: () -> Unit,
    onDelete: () -> Unit,
    onSendReminder: () -> Unit,
    onCreateJob: () -> Unit
) {
    val (statusColor, statusLabel) = when (alert.status.uppercase()) {
        "OVERDUE" -> Pair(Color(0xFFEF4444), "OVERDUE")
        "DUE_SOON" -> Pair(Color(0xFFF59E0B), "DUE SOON")
        "SERVICED" -> Pair(Color(0xFF10B981), "SERVICED")
        else -> Pair(Color(0xFF3B82F6), "UPCOMING")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(statusLabel, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(alert.vehiclePlate.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkSlate)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Alert", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(alert.serviceType, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkSlate)
            Text("Customer: ${alert.customerName} (${alert.customerPhone})", fontSize = 11.sp, color = Color.Gray)
            Text("Interval: ${alert.intervalKm} KM • Due Date: ${alert.dueDateString} (${if (alert.dueDaysLeft < 0) "${-alert.dueDaysLeft} days late" else "${alert.dueDaysLeft} days left"})", fontSize = 11.sp, color = DarkSlate, fontWeight = FontWeight.Medium)
            
            if (alert.reminderNotes.isNotBlank()) {
                Text("Note: ${alert.reminderNotes}", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 2.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (alert.status != "SERVICED") {
                    OutlinedButton(
                        onClick = onMarkServiced,
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Serviced", fontSize = 10.sp)
                    }
                }
                Button(
                    onClick = onSendReminder,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Remind", fontSize = 10.sp, color = Color.White)
                }
                Button(
                    onClick = onCreateJob,
                    colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.AddCard, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Job Card", fontSize = 10.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun AddServiceAlertDialog(
    onDismiss: () -> Unit,
    onAddAlert: (ServiceAlertEntity) -> Unit,
    customers: List<CustomerProfileEntity> = emptyList(),
    jobs: List<JobCardEntity> = emptyList()
) {
    // Reminders are always for someone already on the books, so the customer is picked from a
    // list rather than retyped — that also fills in the phone and plate automatically.
    data class Contact(val name: String, val phone: String, val plate: String)
    val contacts = remember(customers, jobs) {
        (customers.map { Contact(it.name, it.phone, it.vehiclePlate) } +
            jobs.map { Contact(it.customerName, it.customerPhone, it.vehiclePlate) })
            .filter { it.name.isNotBlank() }
            .distinctBy { it.name.lowercase() to it.plate.lowercase() }
            .sortedBy { it.name.lowercase() }
    }
    var custName by remember { mutableStateOf("") }
    var custPhone by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var serviceType by remember { mutableStateOf("Engine Oil & Filter Service") }
    var intervalKm by remember { mutableStateOf("5000") }
    var dueDays by remember { mutableStateOf("7") }
    var notes by remember { mutableStateOf("Routine maintenance interval") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(t("rem.newTitle"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkSlate)
                Divider(color = Color(0xFFE2E8F0))

                if (contacts.isEmpty()) {
                    Text(
                        "No customers yet — add a customer or job card first.",
                        fontSize = 12.sp,
                        color = Color(0xFFEF4444)
                    )
                } else {
                    SimpleFormDropdown(
                        label = t("rem.customer"),
                        options = contacts.map { it.name },
                        selectedOption = custName,
                        onOptionSelected = { picked ->
                            custName = picked
                            contacts.firstOrNull { it.name == picked }?.let {
                                custPhone = it.phone
                                if (it.plate.isNotBlank()) plate = it.plate
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(value = custPhone, onValueChange = { custPhone = it }, label = { Text(t("rem.customerPhone")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = plate, onValueChange = { plate = it }, label = { Text(t("rem.plate")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = serviceType, onValueChange = { serviceType = it }, label = { Text(t("rem.serviceType")) }, modifier = Modifier.fillMaxWidth())
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = intervalKm, onValueChange = { intervalKm = it }, label = { Text(t("rem.intervalKm")) }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = dueDays, onValueChange = { dueDays = it }, label = { Text(t("rem.dueDays")) }, modifier = Modifier.weight(1f))
                }

                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(t("rem.notes")) }, modifier = Modifier.fillMaxWidth())

                Button(
                    onClick = {
                        if (custName.isNotBlank() && plate.isNotBlank()) {
                            val days = dueDays.toIntOrNull() ?: 7
                            val status = if (days <= 0) "OVERDUE" else if (days <= 3) "DUE_SOON" else "UPCOMING"
                            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }
                            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                            onAddAlert(
                                ServiceAlertEntity(
                                    customerName = custName,
                                    customerPhone = custPhone,
                                    vehiclePlate = plate,
                                    serviceType = serviceType,
                                    intervalKm = intervalKm.toIntOrNull() ?: 5000,
                                    dueDaysLeft = days,
                                    dueDateString = dateStr,
                                    status = status,
                                    reminderNotes = notes
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(t("rem.save"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// INTERACTIVE SYMPTOM DIAGNOSTIC CHAT ASSISTANT (NO GEMINI AI)
// ==========================================
data class DiagnosticChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val diagnosisResult: DiagnosticResult? = null
)

data class DiagnosticResult(
    val title: String,
    val primaryCause: String,
    val severity: String,
    val confidencePercent: Int,
    val estimatedCostMin: Double,
    val estimatedCostMax: Double,
    val recommendedTasks: List<String>,
    val recommendedParts: List<String>
)

fun analyzeCarSymptomsLocally(symptoms: String): DiagnosticResult {
    val lower = symptoms.lowercase()
    return when {
        lower.contains("brake") || lower.contains("screech") || lower.contains("squeal") || lower.contains("grind") -> {
            DiagnosticResult(
                title = "Front Brake Pad & Rotor Wear",
                primaryCause = "Friction material below critical 2mm threshold; brake wear sensor contact.",
                severity = "HIGH",
                confidencePercent = 94,
                estimatedCostMin = 120.0,
                estimatedCostMax = 280.0,
                recommendedTasks = listOf("Brake Pad Replacement", "Rotor Thickness Inspection", "Brake Line Bleed"),
                recommendedParts = listOf("Brembo Front Brake Pads - $65", "Brake Fluid DOT4 - $18")
            )
        }
        lower.contains("clutch") || lower.contains("slip") || lower.contains("gear") || lower.contains("rev") -> {
            DiagnosticResult(
                title = "Clutch Cable / Friction Plate Slippage",
                primaryCause = "Stretched clutch cable tension or worn clutch friction lining.",
                severity = "MEDIUM",
                confidencePercent = 88,
                estimatedCostMin = 85.0,
                estimatedCostMax = 220.0,
                recommendedTasks = listOf("Clutch Cable Adjustment", "Flywheel & Pressure Plate Check", "Transmission Fluid Inspection"),
                recommendedParts = listOf("Clutch Cable - $12", "Friction Plate Set - $110")
            )
        }
        lower.contains("overheat") || lower.contains("coolant") || lower.contains("radiator") || lower.contains("temp") || lower.contains("steam") -> {
            DiagnosticResult(
                title = "Cooling System Overheating & Thermostat Blockage",
                primaryCause = "Faulty thermostat valve or low radiator coolant level causing engine temperature spike.",
                severity = "CRITICAL",
                confidencePercent = 92,
                estimatedCostMin = 110.0,
                estimatedCostMax = 320.0,
                recommendedTasks = listOf("Radiator Pressure Leak Test", "Thermostat Valve Replacement", "Coolant Flush & Refill"),
                recommendedParts = listOf("OEM Thermostat Valve - $45", "Coolant Premix (4L) - $35")
            )
        }
        lower.contains("misfire") || lower.contains("smoke") || lower.contains("black") || lower.contains("plug") || lower.contains("rough") -> {
            DiagnosticResult(
                title = "Cylinder Ignition Misfire & Air Filter Clogging",
                primaryCause = "Carbonized spark plug electrode or clogged air intake restricting combustion.",
                severity = "HIGH",
                confidencePercent = 90,
                estimatedCostMin = 90.0,
                estimatedCostMax = 210.0,
                recommendedTasks = listOf("OBD-II Diagnostic Scan", "Spark Plugs Renewal", "Air Filter Replacement"),
                recommendedParts = listOf("Premium Spark Plug Set - $70", "High Flow Air Filter - $25")
            )
        }
        lower.contains("battery") || lower.contains("start") || lower.contains("click") || lower.contains("light") || lower.contains("dead") -> {
            DiagnosticResult(
                title = "12V Battery Voltage Drop / Alternator Circuit",
                primaryCause = "Battery state of charge below 11.8V or corroded terminal connections.",
                severity = "MEDIUM",
                confidencePercent = 95,
                estimatedCostMin = 50.0,
                estimatedCostMax = 180.0,
                recommendedTasks = listOf("Battery Load Test", "Terminal Corrosion Clean", "Alternator Output Scan"),
                recommendedParts = listOf("Automotive 12V Battery - $130", "Terminal Clamps - $12")
            )
        }
        lower.contains("vibration") || lower.contains("wobble") || lower.contains("steering") || lower.contains("tire") || lower.contains("alignment") -> {
            DiagnosticResult(
                title = "Wheel Imbalance & Suspension Alignment Offset",
                primaryCause = "Uneven tire tread wear or loss of wheel counterweights.",
                severity = "LOW",
                confidencePercent = 86,
                estimatedCostMin = 60.0,
                estimatedCostMax = 150.0,
                recommendedTasks = listOf("Wheel Balancing (4 Wheels)", "3D Wheel Alignment", "Suspension Bushing Inspection"),
                recommendedParts = listOf("Wheel Weights - $15")
            )
        }
        else -> {
            DiagnosticResult(
                title = "General Mechanical & Electrical Symptom Check",
                primaryCause = "Multiple mechanical system indicators present. Recommended full diagnostic scan.",
                severity = "LOW",
                confidencePercent = 82,
                estimatedCostMin = 70.0,
                estimatedCostMax = 140.0,
                recommendedTasks = listOf("Full Vehicle Diagnostic Scan", "Fluids & Hose Inspection", "Underbody Check"),
                recommendedParts = listOf("General Service Supplies - $25")
            )
        }
    }
}

@Composable
fun CarDiagnosticsChatSheet(
    onDismiss: () -> Unit,
    onCreateJobFromDiagnosis: (JobCardEntity) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            DiagnosticChatMessage(
                isUser = false,
                text = "Hello! I am your Offline Automotive Diagnostic Assistant. Tap a symptom chip below or describe your car issue to get instant diagnostic analysis, estimated costs, and recommended repair tasks!"
            )
        )
    }

    val presetChips = listOf(
        "Brake screech & grinding noise",
        "Clutch slippage & loose cable",
        "Engine overheating & steam",
        "Engine misfire & black smoke",
        "Car won't start & battery click",
        "Steering vibration at 80 km/h"
    )

    fun handleSend(userInput: String) {
        if (userInput.isBlank()) return
        messages.add(DiagnosticChatMessage(isUser = true, text = userInput))
        val result = analyzeCarSymptomsLocally(userInput)
        messages.add(
            DiagnosticChatMessage(
                isUser = false,
                text = "Based on symptom analysis: '${result.title}'. Primary cause: ${result.primaryCause}",
                diagnosisResult = result
            )
        )
        inputText = ""
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f)
                .padding(6.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DeepIndigo),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Car Symptom Assistant", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = DarkSlate)
                            Text("Rule-Based Offline Analyzer • No AI required", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFE2E8F0))

                // Quick Symptom Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    items(presetChips) { chip ->
                        SuggestionChip(
                            onClick = { handleSend(chip) },
                            label = { Text(chip, fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFFEEF2FF))
                        )
                    }
                }

                // Messages List
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { msg ->
                        DiagnosticChatBubble(
                            msg = msg,
                            onCreateJobClick = { result ->
                                val createdJob = JobCardEntity(
                                    customerName = "Walk-in Customer",
                                    customerPhone = "",
                                    vehicleName = "Vehicle Diagnostic Check",
                                    vehiclePlate = "NEW-VEHICLE",
                                    symptoms = msg.text,
                                    diagnosis = "${result.title}: ${result.primaryCause}",
                                    status = "ESTIMATE",
                                    tasks = result.recommendedTasks.joinToString(","),
                                    materials = result.recommendedParts.joinToString(","),
                                    serviceCharges = (result.estimatedCostMin * 0.4),
                                    partsCharges = (result.estimatedCostMin * 0.6),
                                    taxes = (result.estimatedCostMin * 0.08),
                                    totalAmount = result.estimatedCostMin
                                )
                                onCreateJobFromDiagnosis(createdJob)
                                onDismiss()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Describe car symptoms...", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { handleSend(inputText) },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(DeepIndigo)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticChatBubble(
    msg: DiagnosticChatMessage,
    onCreateJobClick: (DiagnosticResult) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (msg.isUser) DeepIndigo else Color(0xFFF1F5F9)
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = msg.text,
                    color = if (msg.isUser) Color.White else DarkSlate,
                    fontSize = 12.sp
                )

                if (msg.diagnosisResult != null) {
                    val result = msg.diagnosisResult
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFC7D2FE))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(result.title, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = DarkSlate)
                                Text("${result.confidencePercent}% Match", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Est Cost: ₹${result.estimatedCostMin.toInt()} - ₹${result.estimatedCostMax.toInt()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DeepIndigo)
                            
                            Text("Tasks:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                            result.recommendedTasks.forEach { task ->
                                Text("• $task", fontSize = 10.sp, color = DarkSlate)
                            }

                            Text("Parts:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                            result.recommendedParts.forEach { part ->
                                Text("• $part", fontSize = 10.sp, color = DarkSlate)
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onCreateJobClick(result) },
                                colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                                modifier = Modifier.fillMaxWidth().height(34.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Create Job Card with Diagnosis", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// CUSTOMER MANAGEMENT MODULE & SERVICE HISTORY
// ==========================================
@Composable
fun CustomersManagementView(
    viewModel: GarageViewModel,
    memberships: List<ServiceMembershipEntity>,
    customerProfiles: List<CustomerProfileEntity>,
    jobList: List<JobCardEntity>,
    searchQuery: String = "",
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("Client Profiles") }
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var showAddMembershipDialog by remember { mutableStateOf(false) }

    val filteredProfiles = remember(customerProfiles, searchQuery) {
        if (searchQuery.isBlank()) customerProfiles else {
            customerProfiles.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.phone.contains(searchQuery, ignoreCase = true) ||
                it.vehiclePlate.contains(searchQuery, ignoreCase = true) ||
                it.vehicleName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        containerColor = SwissWhite,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == "Client Profiles") showAddProfileDialog = true
                    else showAddMembershipDialog = true
                },
                containerColor = DeepIndigo,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = t("cust.title"),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = DarkSlate
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Garage Cloud Network Active",
                            fontSize = 10.sp,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tab Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == "Client Profiles",
                    onClick = { selectedTab = "Client Profiles" },
                    label = { Text("Client Profiles (${customerProfiles.size})", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedTab == "Wash Subscriptions",
                    onClick = { selectedTab = "Wash Subscriptions" },
                    label = { Text("Wash Memberships (${memberships.size})", fontSize = 11.sp) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedTab == "Client Profiles") {
                if (filteredProfiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No customer profiles found. Tap + to add one.", color = CharcoalGray)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredProfiles) { profile ->
                            val customerJobs = remember(jobList, profile) {
                                jobList.filter {
                                    it.customerName.equals(profile.name, ignoreCase = true) ||
                                    it.customerPhone.contains(profile.phone) ||
                                    it.vehiclePlate.equals(profile.vehiclePlate, ignoreCase = true)
                                }
                            }
                            CustomerProfileCard(
                                profile = profile,
                                linkedJobs = customerJobs,
                                onDelete = { viewModel.deleteCustomerProfile(profile.id) },
                                onCall = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${profile.phone}"))
                                    context.startActivity(intent)
                                },
                                onWhatsApp = {
                                    val text = "Hello ${profile.name}, greetings from Garage Guru workshop regarding your vehicle ${profile.vehicleName} (${profile.vehiclePlate})."
                                    val url = "whatsapp://send?phone=${profile.phone}&text=${UriCodec.encode(text)}"
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Copied link: $text", Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // Wash Subscriptions
                if (memberships.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No wash subscriptions registered.", color = CharcoalGray)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(memberships) { member ->
                            CustomerMembershipCard(
                                member = member,
                                onIncrementWash = { viewModel.recordMembershipWash(member) },
                                onWhatsAppClick = {
                                    val text = "Hello ${member.customerName}, your wash membership (${member.membershipType}) status: ${member.washesUsed}/${member.monthlyWashLimit} washes used."
                                    val url = "whatsapp://send?phone=${member.customerPhone}&text=${UriCodec.encode(text)}"
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Copied: $text", Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showAddProfileDialog) {
            AddCustomerProfileDialog(
                onDismiss = { showAddProfileDialog = false },
                onAddProfile = { profile ->
                    viewModel.addCustomerProfile(profile)
                    showAddProfileDialog = false
                    Toast.makeText(context, "Saved customer profile & synced with cloud!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showAddMembershipDialog) {
            AddMembershipDialog(
                onDismiss = { showAddMembershipDialog = false },
                onAddMembership = { membership ->
                    viewModel.addMembership(membership)
                    showAddMembershipDialog = false
                }
            )
        }
    }
}

@Composable
fun CustomerProfileCard(
    profile: CustomerProfileEntity,
    linkedJobs: List<JobCardEntity>,
    onDelete: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit
) {
    var isExpandedHistory by remember { mutableStateOf(false) }
    val totalSpent = remember(linkedJobs) { linkedJobs.sumOf { it.totalAmount } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(DeepIndigo),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile.name.take(2).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkSlate)
                        Text("${profile.phone} • ${profile.email}", fontSize = 11.sp, color = Color.Gray)
                        // The client's own code — printed on their invoice, so it has to be
                        // readable here too when someone quotes it over the phone.
                        if (profile.clientId.isNotBlank()) {
                            Surface(
                                color = Color(0xFFEEF2FF),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.padding(top = 3.dp)
                            ) {
                                Text(
                                    text = "ID ${profile.clientId}",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF4F46E5)
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Vehicle info strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), shape = RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = DeepIndigo, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${profile.vehicleName} (${profile.vehiclePlate})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkSlate)
                }
                Text("Spent: ₹${String.format("%.0f", totalSpent)}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF10B981))
            }

            if (profile.address.isNotBlank() || profile.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Address: ${profile.address.ifBlank { "N/A" }}", fontSize = 10.sp, color = Color.Gray)
                if (profile.notes.isNotBlank()) {
                    Text("Note: ${profile.notes}", fontSize = 10.sp, color = DarkSlate, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onCall,
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Call", fontSize = 10.sp)
                }

                Button(
                    onClick = onWhatsApp,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("WhatsApp", fontSize = 10.sp, color = Color.White)
                }

                OutlinedButton(
                    onClick = { isExpandedHistory = !isExpandedHistory },
                    modifier = Modifier.weight(1.2f).height(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(if (isExpandedHistory) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("History (${linkedJobs.size})", fontSize = 10.sp)
                }
            }

            // Expandable Service History
            AnimatedVisibility(visible = isExpandedHistory) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Divider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Linked Service History:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DeepIndigo)
                    if (linkedJobs.isEmpty()) {
                        Text("No completed job cards found for this customer.", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        linkedJobs.forEach { job ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Job #${job.id} - ${job.vehicleName} [${job.status}]", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text("Tasks: ${job.tasks.ifBlank { "N/A" }}", fontSize = 9.sp, color = Color.Gray)
                                }
                                Text("₹${String.format("%.2f", job.totalAmount)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DarkSlate)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddCustomerProfileDialog(
    onDismiss: () -> Unit,
    onAddProfile: (CustomerProfileEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var vehicleName by remember { mutableStateOf("") }
    var vehiclePlate by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("New Customer Profile", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkSlate)
                Divider(color = Color(0xFFE2E8F0))

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = vehicleName, onValueChange = { vehicleName = it }, label = { Text("Vehicle Make & Model *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = vehiclePlate, onValueChange = { vehiclePlate = it }, label = { Text("Vehicle Plate Number *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes / Preferences") }, modifier = Modifier.fillMaxWidth())

                Button(
                    onClick = {
                        if (name.isNotBlank() && phone.isNotBlank()) {
                            onAddProfile(
                                CustomerProfileEntity(
                                    name = name,
                                    phone = phone,
                                    email = email,
                                    vehicleName = vehicleName,
                                    vehiclePlate = vehiclePlate,
                                    address = address,
                                    notes = notes
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Customer Profile", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AddMembershipDialog(
    onDismiss: () -> Unit,
    onAddMembership: (ServiceMembershipEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Basic Wash Special") }
    var limit by remember { mutableStateOf("4") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Register Wash Membership", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkSlate)
                Divider(color = Color(0xFFE2E8F0))

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Client Name *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Membership Tier") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = limit, onValueChange = { limit = it }, label = { Text("Monthly Wash Limit") }, modifier = Modifier.fillMaxWidth())

                Button(
                    onClick = {
                        if (name.isNotBlank() && phone.isNotBlank()) {
                            onAddMembership(
                                ServiceMembershipEntity(
                                    customerName = name,
                                    customerPhone = phone,
                                    membershipType = type,
                                    monthlyWashLimit = limit.toIntOrNull() ?: 4,
                                    renewalDate = "2026-08-01",
                                    pendingBalance = 0.0
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Wash Plan", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- Language Selection Dialog & Localization Helper ---
@Composable
fun LanguageSelectionDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4F46E5).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Choose App Language", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                            Text("भाषा चुनें / भाषा निवडा", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Language Option 1: English
                LanguageOptionCard(
                    title = "English",
                    subtitle = "Default system language",
                    languageCode = "en",
                    isSelected = currentLanguage == "en",
                    onClick = { onLanguageSelected("en") }
                )

                // Language Option 2: Hindi (हिंदी)
                LanguageOptionCard(
                    title = "हिंदी (Hindi)",
                    subtitle = "भारतीय गैराज उपयोग के लिए",
                    languageCode = "hi",
                    isSelected = currentLanguage == "hi",
                    onClick = { onLanguageSelected("hi") }
                )

                // Language Option 3: Marathi (मराठी)
                LanguageOptionCard(
                    title = "मराठी (Marathi)",
                    subtitle = "महाराष्ट्र वर्कशॉप वापरासाठी",
                    languageCode = "mr",
                    isSelected = currentLanguage == "mr",
                    onClick = { onLanguageSelected("mr") }
                )
            }
        }
    }
}

@Composable
fun LanguageOptionCard(
    title: String,
    subtitle: String,
    languageCode: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFFEEF2FF) else Color(0xFFF8FAFC),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color(0xFF4F46E5) else Color(0xFFE2E8F0)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF0F172A)
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4F46E5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

object LanguageHelper {
    fun getTabHome(lang: String) = when (lang) {
        "hi" -> "होम"
        "mr" -> "मुख्य"
        else -> "Home"
    }

    fun getTabJobs(lang: String) = when (lang) {
        "hi" -> "जॉब कार्ड"
        "mr" -> "जॉब कार्ड्स"
        else -> "Jobs"
    }

    fun getTabMore(lang: String) = when (lang) {
        "hi" -> "अन्य"
        "mr" -> "इतर"
        else -> "More"
    }

    fun getSearchPlaceholder(lang: String, tab: String) = when (lang) {
        "hi" -> when (tab) {
            "jobs" -> "गाड़ी, प्लेट या ग्राहक नाम से खोजें..."
            "system" -> "स्पेयर पार्ट या पार्ट नंबर से खोजें..."
            "customers" -> "ग्राहक प्रोफाइल या मोबाइल नंबर से खोजें..."
            "cashbook" -> "कैशबुक एंट्री खोजें..."
            "reports" -> "रिपोर्ट अवधि नीचे चुनें..."
            else -> "गाड़ी, VIN या ग्राहक खोजें..."
        }
        "mr" -> when (tab) {
            "jobs" -> "गाडी, नंबर किंवा ग्राहकाच्या नावाने शोधा..."
            "system" -> "स्पेयर पार्ट किंवा कोडने शोधा..."
            "customers" -> "ग्राहक किंवा मोबाईल नंबरने शोधा..."
            "cashbook" -> "कॅशबुक शोधा..."
            "reports" -> "खालून रिपोर्टचा कालावधी निवडा..."
            else -> "गाडी, VIN किंवा ग्राहक शोधा..."
        }
        else -> when (tab) {
            "jobs" -> "Search jobs by vehicle, plate or customer..."
            "system" -> "Search stock parts by name or SKU..."
            "customers" -> "Search client profiles, phone, vehicle plate..."
            "cashbook" -> "Search cash transactions..."
            "reports" -> "Pick a report period below..."
            else -> "Search vehicle, VIN or customer..."
        }
    }
}
