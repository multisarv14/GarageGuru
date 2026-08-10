package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.ui.components.QrCodeScannerModal
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.model.CashItemEntity
import com.example.data.model.CustomerProfileEntity
import com.example.data.model.InventoryItemEntity
import com.example.data.model.JobCardEntity
import com.example.data.model.ServiceAlertEntity
import com.example.data.model.ServiceMembershipEntity
import com.example.ui.GarageViewModel
import com.example.data.api.FirebaseAuthHelper
import com.example.data.api.GarageLogoStore
import com.example.data.api.GarageSession
import kotlinx.coroutines.launch
import com.example.ui.components.MechanicalGLogo
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

data class TeamUserEntity(
    val id: String,
    val name: String,
    val role: String, // "Owner", "Manager", "Service Advisor", "Technician"
    val phone: String,
    val pin: String = "1234"
)

/**
 * A workshop starts with exactly one team member: the owner who signed up. Extra mechanics are
 * added by hand from Garage Team & Mechanics — nothing is invented for them.
 */
fun ownerTeamMember(
    name: String = GarageSession.ownerName,
    phone: String = GarageSession.ownerPhone
) = TeamUserEntity(
    id = OWNER_MEMBER_ID,
    name = name.ifBlank { "Owner" },
    role = "Owner",
    phone = phone
)

const val OWNER_MEMBER_ID = "owner"

object RolePermissions {
    fun canAccessFinancials(role: String): Boolean = role in listOf("Owner", "Manager")
    fun canAccessCashbook(role: String): Boolean = role in listOf("Owner", "Manager", "Service Advisor")
    fun canAccessSystem(role: String): Boolean = role in listOf("Owner", "Manager")
    fun canAccessCustomers(role: String): Boolean = role in listOf("Owner", "Manager", "Service Advisor")
    fun canCreateJob(role: String): Boolean = true
    fun canCloseJob(role: String): Boolean = role in listOf("Owner", "Manager", "Service Advisor")
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
    var teamMembersList by remember { mutableStateOf(listOf(ownerTeamMember())) }
    var currentActiveUser by remember { mutableStateOf(ownerTeamMember()) }
    var currentOwnerName by remember { mutableStateOf(GarageSession.ownerName.ifBlank { currentActiveUser.name }) }
    var currentGarageName by remember { mutableStateOf(GarageSession.garageName) }
    // Logo bytes come from the Firestore Blob, cached on disk by GarageLogoStore.
    val currentGarageLogo by GarageLogoStore.logo.collectAsState()
    val logoScope = rememberCoroutineScope()
    var currentOwnerPhone by remember { mutableStateOf(GarageSession.ownerPhone.ifBlank { currentActiveUser.phone }) }
    var showTeamManagementSheet by remember { mutableStateOf(false) }

    // App Language State (en = English, hi = Hindi, mr = Marathi)
    var selectedLanguage by remember { mutableStateOf("en") }
    var showLanguageDialog by remember { mutableStateOf(false) }

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
    var showQrScannerSheet by remember { mutableStateOf(false) }
    var prefilledJobForCreation by remember { mutableStateOf<JobCardEntity?>(null) }

    // System Back-Button Registry
    androidx.activity.compose.BackHandler(
        enabled = showCreateJobSheet || selectedOrderForInvoice != null || showNotificationDialog || showMoreBottomSheet || showDiagnosticsChatSheet || showQrScannerSheet || activeTab != "dashboard"
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
        } else if (showQrScannerSheet) {
            showQrScannerSheet = false
        } else if (activeTab != "dashboard") {
            currentTab = "dashboard"
        }
    }

    // Start connectivity monitor
    LaunchedEffect(Unit) {
        viewModel.startNetworkMonitoring(context)
    }

    // The owner's real name/phone arrive from Firestore a moment after sign in; keep the
    // owner row in the team list in step with them.
    LaunchedEffect(currentOwnerName, currentOwnerPhone) {
        val owner = ownerTeamMember(currentOwnerName, currentOwnerPhone)
        teamMembersList = listOf(owner) + teamMembersList.filter { it.id != OWNER_MEMBER_ID }
        if (currentActiveUser.id == OWNER_MEMBER_ID) currentActiveUser = owner
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
                viewModel.syncWithCloud()
            }
        }
    }

    if (!isLoggedIn) {
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
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (selectedOrderForInvoice == null) {
                GarageBottomBar(
                    currentTab = activeTab,
                    selectedLanguage = selectedLanguage,
                    userRole = currentActiveUser.role,
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
                .padding(if (selectedOrderForInvoice != null) PaddingValues(0.dp) else innerPadding)
        ) {
            if (selectedOrderForInvoice != null) {
                InvoiceBreakdownPage(
                    viewModel = viewModel,
                    jobCard = selectedOrderForInvoice!!,
                    onDismiss = { selectedOrderForInvoice = null },
                    onSavePdf = { job ->
                        viewModel.saveInvoicePdf(job, context)
                    }
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
                                text = "OFFLINE - Swiss Diagnostic Hub is working locally",
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
                        onScanQrClick = { showQrScannerSheet = true },
                        currentTab = activeTab,
                        userInitials = userInitials,
                        pendingAlertCount = serviceAlertsList.count { it.status != "SERVICED" && it.status != "COMPLETED" }
                    )

                    // --- Tab Content Router ---
                    Box(modifier = Modifier.weight(1f)) {
                        when (activeTab) {
                            "dashboard" -> DashboardView(
                                viewModel = viewModel,
                                jobList = jobList,
                                cashList = cashList,
                                inventoryList = inventoryList,
                                membershipList = membershipList,
                                customerProfiles = customerProfilesList,
                                onTabNavigate = { currentTab = it },
                                onCreateJobClick = { showCreateJobSheet = true },
                                onOpenScanner = { showQrScannerSheet = true },
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
                                onOpenScanner = { showQrScannerSheet = true }
                            )
                            "reports" -> ReportsView(
                                viewModel = viewModel,
                                jobs = jobList,
                                cash = cashList,
                                inventory = inventoryList,
                                memberships = membershipList,
                                canSeeFinancials = RolePermissions.canAccessFinancials(currentActiveUser.role)
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
                                onOpenScanner = { showQrScannerSheet = true }
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
                                            onFailure = { e -> "Logo upload failed: ${e.message}" }
                                        )
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                ownerPhone = currentOwnerPhone,
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

            // --- Multi-Step Job Card Creation Sheet ---
            if (showCreateJobSheet) {
                CreateJobCardSheet(
                    viewModel = viewModel,
                    inventory = inventoryList,
                    initialJob = prefilledJobForCreation,
                    onDismiss = {
                        showCreateJobSheet = false
                        prefilledJobForCreation = null
                    },
                    onScanQrClick = { showQrScannerSheet = true }
                )
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

            // --- CameraX QR Code & VIN Scanner Sheet ---
            if (showQrScannerSheet) {
                QrCodeScannerModal(
                    jobCards = jobList,
                    inventoryItems = inventoryList,
                    customerProfiles = customerProfilesList,
                    memberships = membershipList,
                    onDismiss = { showQrScannerSheet = false },
                    onCreateJobWithPlate = { plate, customerName, phone ->
                        showQrScannerSheet = false
                        prefilledJobForCreation = JobCardEntity(
                            vehiclePlate = plate,
                            customerName = customerName,
                            customerPhone = phone,
                            vehicleName = "Scanned Vehicle ($plate)",
                            symptoms = "VIN/Plate Scanned: $plate",
                            status = "OPEN"
                        )
                        showCreateJobSheet = true
                    },
                    onSelectInventoryItem = { item ->
                        showQrScannerSheet = false
                        currentTab = "system"
                    },
                    onSelectCustomer = { cust ->
                        showQrScannerSheet = false
                        currentTab = "customers"
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

            // --- Admin Garage Team & Role Management Dialog ---
            if (showTeamManagementSheet) {
                GarageTeamManagementSheet(
                    teamMembers = teamMembersList,
                    activeUser = currentActiveUser,
                    onSelectActiveUser = { selectedUser ->
                        currentActiveUser = selectedUser
                        currentOwnerName = selectedUser.name
                        currentOwnerPhone = selectedUser.phone
                    },
                    onAddTeamMember = { newMember ->
                        teamMembersList = teamMembersList + newMember
                    },
                    onRemoveTeamMember = { id ->
                        teamMembersList = teamMembersList.filter { it.id != id }
                    },
                    onDismiss = { showTeamManagementSheet = false }
                )
            }

            // --- Language Selection Dialog ---
            if (showLanguageDialog) {
                LanguageSelectionDialog(
                    currentLanguage = selectedLanguage,
                    onLanguageSelected = { lang ->
                        selectedLanguage = lang
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
    onScanQrClick: () -> Unit = {},
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

                    // QR Scan Button
                    IconButton(
                        onClick = onScanQrClick,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("qr_topbar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan VIN / QR",
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(20.dp)
                        )
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
    userRole: String = "Owner",
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
                        if (!RolePermissions.canAccessSystem(userRole)) {
                            Toast.makeText(context, "Role Restriction: $userRole cannot access System & Stock Settings.", Toast.LENGTH_SHORT).show()
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
                    text = when (selectedLanguage) {
                        "hi" -> "स्टॉक"
                        "mr" -> "साठा"
                        else -> "Stock"
                    },
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
    onOpenScanner: () -> Unit = {},
    currentActiveUser: TeamUserEntity = ownerTeamMember(),
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
        // Section 1: Bento Action Buttons (+ NEW JOB CARD, QUICK BILL, SCAN QR)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button 1: + NEW JOB CARD
                Button(
                    onClick = onCreateJobClick,
                    modifier = Modifier
                        .weight(1.1f)
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "NEW JOB", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
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
                        Text(text = "BILLING", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // Button 3: SCAN QR / VIN
                Button(
                    onClick = onOpenScanner,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "SCAN QR", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
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
                        Text(text = "ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
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
                        Text(text = "DONE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
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
                        Text(text = "CLOSED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
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
                    title = "STOCK",
                    onClick = { onTabNavigate("system") }
                )
                QuickActionIconCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.AccountBalanceWallet,
                    iconColor = Color.Red,
                    title = "CASH",
                    onClick = { onTabNavigate("cashbook") }
                )
                QuickActionIconCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.BarChart,
                    iconColor = DeepIndigo,
                    title = "REPORTS",
                    onClick = { onTabNavigate("reports") }
                )
                QuickActionIconCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.People,
                    iconColor = Color(0xFF2563EB),
                    title = "CLIENTS",
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
                        text = "SERVICE PLANS & MEMBERSHIPS",
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
                            text = "Plan",
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
                            title = "ACTIVE SUBS",
                            value = "${membershipList.size}",
                            subtitle = "TOTAL CUSTOMERS"
                        )
                        SubscriptionStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.CalendarToday,
                            iconColor = Color(0xFFF59E0B),
                            title = "EXPIRING SOON",
                            value = "0",
                            subtitle = "NEXT 3 DAYS"
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubscriptionStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.AccessTime,
                            iconColor = Color(0xFF10B981),
                            title = "TODAY'S LOAD",
                            value = "$openCount",
                            subtitle = "SCHEDULED"
                        )
                        SubscriptionStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Payment,
                            iconColor = Color(0xFF3B82F6),
                            title = "SUB REVENUE",
                            value = "₹${String.format("%.0f", totalIncome)}",
                            subtitle = "THIS MONTH"
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
                            text = "TOTAL REVENUE",
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
                            if (!RolePermissions.canAccessFinancials(currentActiveUser.role)) {
                                Text(
                                    text = "🔒 Restricted to Manager / Owner",
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
                                text = "COMBINED INCOME",
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

        // Section 6.5: CUSTOMER DIRECTORY & VEHICLE HISTORY BENTO CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
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
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = "Customers",
                                    tint = DeepIndigo,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "CLIENT DIRECTORY & VEHICLE HISTORY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = DarkSlate,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "${customerProfiles.size} Registered Clients • ${jobList.size} Total Visits",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { showAddProfileDialogInDashboard = true },
                                colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Client", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "ALL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepIndigo,
                                modifier = Modifier.clickable { onTabNavigate("customers") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (customerProfiles.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("No client contact details stored yet.", fontSize = 11.sp, color = CharcoalGray, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { showAddProfileDialogInDashboard = true },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text("+ Add First Customer Profile", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        val recentClients = customerProfiles.take(3)
                        val context = LocalContext.current

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recentClients.forEach { profile ->
                                val linkedJobs = remember(jobList, profile) {
                                    jobList.filter {
                                        it.customerName.equals(profile.name, ignoreCase = true) ||
                                        it.customerPhone.contains(profile.phone) ||
                                        it.vehiclePlate.equals(profile.vehiclePlate, ignoreCase = true)
                                    }
                                }
                                var isExpandedHistory by remember { mutableStateOf(false) }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(DeepIndigo),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = profile.name.take(2).uppercase(),
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkSlate)
                                                    Text("📞 ${profile.phone}", fontSize = 10.sp, color = Color.Gray)
                                                }
                                            }

                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = profile.vehiclePlate.ifBlank { profile.vehicleName },
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1D4ED8),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "🚗 ${profile.vehicleName} • ${linkedJobs.size} Service Visit(s)",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF475569)
                                            )

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                IconButton(
                                                    onClick = {
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${profile.phone}"))
                                                        context.startActivity(intent)
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(Icons.Default.Phone, contentDescription = "Call", tint = Color(0xFF2563EB), modifier = Modifier.size(14.dp))
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val text = "Hello ${profile.name}, greetings from garage workshop regarding your vehicle ${profile.vehicleName} (${profile.vehiclePlate})."
                                                        val url = "whatsapp://send?phone=${profile.phone}&text=${UriCodec.encode(text)}"
                                                        try {
                                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Link: $text", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                                }

                                                IconButton(
                                                    onClick = { isExpandedHistory = !isExpandedHistory },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isExpandedHistory) Icons.Default.ExpandLess else Icons.Default.History,
                                                        contentDescription = "History",
                                                        tint = DeepIndigo,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Collapsible Vehicle History
                                        if (isExpandedHistory) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Divider(color = Color(0xFFE2E8F0))
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("VEHICLE SERVICE HISTORY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DeepIndigo)
                                            Spacer(modifier = Modifier.height(4.dp))

                                            if (linkedJobs.isEmpty()) {
                                                Text("No past job cards found for this vehicle.", fontSize = 10.sp, color = Color.Gray)
                                            } else {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    linkedJobs.forEach { job ->
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text("Job #${job.id} • ${job.vehicleName}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DarkSlate)
                                                                Text(job.symptoms.ifBlank { job.tasks.ifBlank { "General Inspection" } }, fontSize = 9.sp, color = Color.Gray, maxLines = 1)
                                                            }
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                Text("₹${String.format("%.0f", job.totalAmount)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                                                Card(
                                                                    colors = CardDefaults.cardColors(
                                                                        containerColor = if (job.status == "OPEN") Color(0xFFEFF6FF) else Color(0xFFECFDF5)
                                                                    )
                                                                ) {
                                                                    Text(
                                                                        job.status,
                                                                        fontSize = 8.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (job.status == "OPEN") Color(0xFF2563EB) else Color(0xFF10B981),
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
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
                    }
                }
            }
        }

        // Section 7: RECENT ACTIVE JOB CARDS (Work log items)
        item {
            val activeJobs = jobList.filter { it.status == "OPEN" || it.status == "COMPLETED" }.take(5)
            if (activeJobs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECENT ACTIVE JOBS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CharcoalGray,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "VIEW ALL",
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
    ownerPhone: String = "+91 9876543210",
    selectedLanguage: String = "en",
    onOpenLanguageDialog: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onOpenTeamManagement: () -> Unit = {}
) {
    val context = LocalContext.current
    var isEditingGarageName by remember { mutableStateOf(false) }
    var editedGarageName by remember(garageName) { mutableStateOf(garageName) }
    val hasLogo = garageLogoBytes != null && garageLogoBytes.isNotEmpty()

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
                text = "Account & Settings",
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
                        text = "GARAGE DETAILS & LOGO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4F46E5)
                    )
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "VERIFIED WORKSHOP",
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
                                IconButton(
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

                OutlinedButton(
                    onClick = { logoLauncher.launch("image/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF4F46E5)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4F46E5))
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = "Upload Logo", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (hasLogo) "Change Garage Logo Image" else "Upload Garage Logo Image",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
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
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Garage Owner & Admin",
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
                    Text("Log Out of Garage", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
        Text("WORKSHOP MODULES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), modifier = Modifier.padding(start = 4.dp))

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
                    Text("Garage Team & Mechanics", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                    Text("Manage Mechanics, Technicians & Roles", fontSize = 12.sp, color = Color(0xFF64748B))
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
                    Text("Stock Inventory", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
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
                    Text("Clients & Wash", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
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
                    Text("Ledger Book", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
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
                    Text("Service Alerts", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                }
            }
        }

        // Section 2: Preferences
        Text("PREFERENCES & SYSTEM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), modifier = Modifier.padding(start = 4.dp))

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
                        .clickable { onOpenLanguageDialog() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when (selectedLanguage) {
                            "hi" -> "ऐप की भाषा (Language)"
                            "mr" -> "ॲपची भाषा (Language)"
                            else -> "App Language"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = when (selectedLanguage) {
                            "hi" -> "हिंदी"
                            "mr" -> "मराठी"
                            else -> "English"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4F46E5)
                    )
                }
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Database Sync", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF0F172A), modifier = Modifier.weight(1f))
                    Text("Online & Synced", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
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
                    Text("Garage Guru Version", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF0F172A), modifier = Modifier.weight(1f))
                    Text("v3.2.0-clean", fontSize = 12.sp, color = Color(0xFF64748B))
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
                                    text = "Garage Owner & Admin",
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
                            Text("Garage Team & Mechanics", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
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
                            Text("Clients & Wash", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
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
                            Text("Ledger Book", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
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
                            Text("Stock Inventory", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
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
                            Text("Service Alerts", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
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

    // Sign Up fields
    var signUpGarageName by remember { mutableStateOf("") }
    var signUpOwnerName by remember { mutableStateOf("") }
    var signUpPhone by remember { mutableStateOf("") }
    var signUpPassword by remember { mutableStateOf("") }
    var signUpCity by remember { mutableStateOf("") }

    val context = LocalContext.current

    // The theme asks for dark status-bar icons because the app is light, but this screen sits on
    // a dark navy gradient — dark-on-dark makes the clock, battery and signal icons invisible.
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
        // Re-assert after every recomposition so the theme's global setting cannot win the frame.
        SideEffect {
            val window = (authView.context as? android.app.Activity)?.window
            window?.let {
                androidx.core.view.WindowCompat.getInsetsController(it, authView)
                    .isAppearanceLightStatusBars = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF312E81))
                )
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Row: Logo & Language Selector Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MechanicalGLogo(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Garage Guru",
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = Color(0xFF0F172A)
                        )
                    }

                    Surface(
                        onClick = onOpenLanguageDialog,
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEEF2FF),
                        border = BorderStroke(1.dp, Color(0xFFC7D2FE))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = "Language", tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when(selectedLanguage) {
                                    "hi" -> "हिंदी"
                                    "mr" -> "मराठी"
                                    else -> "English"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5)
                            )
                        }
                    }
                }

                Text(
                    text = if (isSignUpTab) {
                        when (selectedLanguage) {
                            "hi" -> "अपने गैराज अकाउंट का पंजीकरण करें"
                            "mr" -> "तुमच्या गॅरेज खात्याची नोंदणी करा"
                            else -> "Register Your Garage Account"
                        }
                    } else {
                        when (selectedLanguage) {
                            "hi" -> "स्वागत है! गैराज में लॉगिन करें"
                            "mr" -> "पुन्हा स्वागत आहे! गॅरेजमध्ये लॉगिन करा"
                            else -> "Welcome Back! Login to Garage"
                        }
                    },
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )

                // Tab Switcher (Login vs Sign Up)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isSignUpTab) Color(0xFF4F46E5) else Color.Transparent)
                            .clickable { isSignUpTab = false }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (selectedLanguage) {
                                "hi" -> "मोबाइल लॉगिन"
                                "mr" -> "मोबाईल लॉगिन"
                                else -> "Mobile Login"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (!isSignUpTab) Color.White else Color(0xFF64748B)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSignUpTab) Color(0xFF4F46E5) else Color.Transparent)
                            .clickable { isSignUpTab = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (selectedLanguage) {
                                "hi" -> "गैराज साइन अप"
                                "mr" -> "गॅरेज साइन अप"
                                else -> "Garage Sign Up"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isSignUpTab) Color.White else Color(0xFF64748B)
                        )
                    }
                }

                if (!isSignUpTab) {
                    // Mobile Login Form
                    OutlinedTextField(
                        value = loginPhone,
                        onValueChange = { loginPhone = it },
                        label = {
                            Text(when (selectedLanguage) {
                                "hi" -> "मोबाइल नंबर"
                                "mr" -> "मोबाईल नंबर"
                                else -> "Mobile Number"
                            })
                        },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF4F46E5)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = { loginPassword = it },
                        label = {
                            Text(when (selectedLanguage) {
                                "hi" -> "पासवर्ड"
                                "mr" -> "पासवर्ड"
                                else -> "Password"
                            })
                        },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF4F46E5)) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                FirebaseAuthHelper.sendPasswordReset(loginPhone) { _, message ->
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = when (selectedLanguage) {
                                    "hi" -> "पासवर्ड भूल गए?"
                                    "mr" -> "पासवर्ड विसरलात?"
                                    else -> "Forgot Password?"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4F46E5)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (loginPhone.isBlank() || loginPassword.isBlank()) {
                                val msg = when (selectedLanguage) {
                                    "hi" -> "कृपया मोबाइल नंबर और पासवर्ड दर्ज करें"
                                    "mr" -> "कृपया मोबाईल नंबर आणि पासवर्ड टाका"
                                    else -> "Please enter mobile number and password"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            } else {
                                FirebaseAuthHelper.signIn(loginPhone, loginPassword) { success, owner, garage, message ->
                                    if (success) {
                                        val msg = when (selectedLanguage) {
                                            "hi" -> "लॉगिन सफल रहा!"
                                            "mr" -> "लॉगिन यशस्वी झाले!"
                                            else -> "Logged in successfully!"
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        onLoginSuccess(owner ?: "Owner", garage ?: "Garage Guru", loginPhone)
                                    } else {
                                        val err = message ?: "Login failed"
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (selectedLanguage) {
                                "hi" -> "साइन इन करें"
                                "mr" -> "साइन इन करा"
                                else -> "SIGN IN"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    // Garage Sign Up Form
                    OutlinedTextField(
                        value = signUpGarageName,
                        onValueChange = { signUpGarageName = it },
                        label = {
                            Text(when (selectedLanguage) {
                                "hi" -> "गैराज / वर्कशॉप का नाम"
                                "mr" -> "गॅरेज / वर्कशॉपचे नाव"
                                else -> "Garage / Workshop Name"
                            })
                        },
                        leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = Color(0xFF4F46E5)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = signUpOwnerName,
                        onValueChange = { signUpOwnerName = it },
                        label = {
                            Text(when (selectedLanguage) {
                                "hi" -> "मालिक / एडमिन का नाम"
                                "mr" -> "मालक / ॲडमिनचे नाव"
                                else -> "Owner / Admin Name"
                            })
                        },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF4F46E5)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = signUpPhone,
                        onValueChange = { signUpPhone = it },
                        label = {
                            Text(when (selectedLanguage) {
                                "hi" -> "मोबाइल नंबर"
                                "mr" -> "मोबाईल नंबर"
                                else -> "Mobile Number"
                            })
                        },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF4F46E5)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = signUpCity,
                        onValueChange = { signUpCity = it },
                        label = {
                            Text(when (selectedLanguage) {
                                "hi" -> "शहर / पता"
                                "mr" -> "शहर / पत्ता"
                                else -> "City / Location"
                            })
                        },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF4F46E5)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = signUpPassword,
                        onValueChange = { signUpPassword = it },
                        label = {
                            Text(when (selectedLanguage) {
                                "hi" -> "पासवर्ड बनाएं"
                                "mr" -> "पासवर्ड तयार करा"
                                else -> "Create Password"
                            })
                        },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF4F46E5)) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            if (signUpGarageName.isBlank() || signUpOwnerName.isBlank() || signUpPhone.isBlank() || signUpPassword.isBlank()) {
                                val msg = when (selectedLanguage) {
                                    "hi" -> "कृपया गैराज बनाने के लिए सभी जानकारी भरें"
                                    "mr" -> "कृपया गॅरेज तयार करण्यासाठी सर्व माहिती भरा"
                                    else -> "Please fill all details to create garage"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            } else {
                                FirebaseAuthHelper.signUp(signUpGarageName, signUpOwnerName, signUpPhone, signUpPassword, signUpCity) { success, message ->
                                    if (success) {
                                        val msg = when (selectedLanguage) {
                                            "hi" -> "गैराज खाता पंजीकृत हो गया!"
                                            "mr" -> "गॅरेज खाते नोंदणीकृत झाले!"
                                            else -> "Garage Account Registered!"
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        onLoginSuccess(signUpOwnerName, signUpGarageName, signUpPhone)
                                    } else {
                                        val err = message ?: "Sign up failed"
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Default.AppRegistration, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (selectedLanguage) {
                                "hi" -> "रजिस्टर करें और शुरू करें"
                                "mr" -> "नोंदणी करा आणि सुरू करा"
                                else -> "REGISTER & START"
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

// --- Admin Team & Role Management Dialog ---
@Composable
fun GarageTeamManagementSheet(
    teamMembers: List<TeamUserEntity>,
    activeUser: TeamUserEntity,
    onSelectActiveUser: (TeamUserEntity) -> Unit,
    onAddTeamMember: (TeamUserEntity) -> Unit,
    onRemoveTeamMember: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showAddMemberDialog by remember { mutableStateOf(false) }

    // Form fields for adding new team member
    var newMemberName by remember { mutableStateOf("") }
    var newMemberRole by remember { mutableStateOf("Technician") }
    var newMemberPhone by remember { mutableStateOf("") }
    var newMemberPin by remember { mutableStateOf("1234") }

    val roleOptions = listOf("Owner", "Manager", "Service Advisor", "Technician")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Team & Role Management", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF0F172A))
                        Text("Switch Active Profile or Add Team Members", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Button(
                    onClick = { showAddMemberDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add New Team Member / Role", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Divider(color = Color(0xFFE2E8F0))

                Text("TAP TO SWITCH ACTIVE ROLE / USER", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4F46E5), letterSpacing = 0.5.sp)

                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(teamMembers) { member ->
                        val isActive = member.id == activeUser.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectActiveUser(member)
                                    Toast.makeText(context, "Active Role Switched to: ${member.name} (${member.role})", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                            colors = CardDefaults.cardColors(containerColor = if (isActive) Color(0xFFEEF2FF) else Color(0xFFF8FAFC)),
                            border = BorderStroke(if (isActive) 1.5.dp else 1.dp, if (isActive) Color(0xFF4F46E5) else Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isActive) Color(0xFF4F46E5) else Color(0xFFE2E8F0)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Person,
                                            contentDescription = null,
                                            tint = if (isActive) Color.White else Color(0xFF64748B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(member.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                            if (isActive) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(color = Color(0xFF10B981), shape = RoundedCornerShape(4.dp)) {
                                                    Text("ACTIVE", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                        Text("Role: ${member.role} • Ph: ${member.phone}", fontSize = 11.sp, color = Color(0xFF64748B))
                                    }
                                }

                                if (teamMembers.size > 1 && !isActive) {
                                    IconButton(
                                        onClick = {
                                            onRemoveTeamMember(member.id)
                                            Toast.makeText(context, "Removed ${member.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddMemberDialog) {
        Dialog(onDismissRequest = { showAddMemberDialog = false }) {
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
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Add Team Member & Assign Role", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))

                    OutlinedTextField(
                        value = newMemberName,
                        onValueChange = { newMemberName = it },
                        label = { Text("Member Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Text("SELECT ROLE:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        roleOptions.take(2).forEach { r ->
                            FilterChip(
                                selected = newMemberRole == r,
                                onClick = { newMemberRole = r },
                                label = { Text(r, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        roleOptions.drop(2).forEach { r ->
                            FilterChip(
                                selected = newMemberRole == r,
                                onClick = { newMemberRole = r },
                                label = { Text(r, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newMemberPhone,
                        onValueChange = { newMemberPhone = it },
                        label = { Text("Mobile Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddMemberDialog = false }) {
                            Text("Cancel", color = Color(0xFF64748B))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newMemberName.isNotBlank() && newMemberPhone.isNotBlank()) {
                                    val newMember = TeamUserEntity(
                                        id = "tm_${System.currentTimeMillis()}",
                                        name = newMemberName,
                                        role = newMemberRole,
                                        phone = newMemberPhone,
                                        pin = newMemberPin
                                    )
                                    onAddTeamMember(newMember)
                                    Toast.makeText(context, "Added ${newMemberName} (${newMemberRole})!", Toast.LENGTH_SHORT).show()
                                    showAddMemberDialog = false
                                    newMemberName = ""
                                    newMemberPhone = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                        ) {
                            Text("Add Member")
                        }
                    }
                }
            }
        }
    }
}

// Utility for path drawing
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
                            text = "Cashbook Ledger",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Track garage income, expenses & daily cashflow",
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
                                    "NET CASH BALANCE",
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
@Composable
fun SystemCoordinatorView(
    viewModel: GarageViewModel,
    inventory: List<InventoryItemEntity>,
    fcmToken: String,
    searchQuery: String = "",
    onCreateJobClick: () -> Unit,
    onOpenScanner: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedStockTab by remember { mutableStateOf("ALL") }

    var showAddStockDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var inputPartName by remember { mutableStateOf("") }
    var inputQty by remember { mutableStateOf("") }
    var inputPrice by remember { mutableStateOf("") }
    var inputMinAlert by remember { mutableStateOf("3") }

    val clipboardManager = LocalClipboardManager.current

    val openAddStockDialog = { itemToEdit: InventoryItemEntity? ->
        editingItem = itemToEdit
        if (itemToEdit != null) {
            inputPartName = itemToEdit.name
            inputQty = itemToEdit.quantity.toString()
            inputPrice = if (itemToEdit.unitPrice > 0) itemToEdit.unitPrice.toString() else ""
            inputMinAlert = itemToEdit.minStockAlert.toString()
        } else {
            inputPartName = ""
            inputQty = ""
            inputPrice = ""
            inputMinAlert = "3"
        }
        showAddStockDialog = true
    }

    // Filter stock dynamically based on search query and selected filter tab
    val filteredInventory = remember(searchQuery, inventory, selectedStockTab) {
        inventory.filter { part ->
            val matchesSearch = part.name.contains(searchQuery, ignoreCase = true)
            val matchesTab = when (selectedStockTab) {
                "LOW STOCK" -> part.quantity <= part.minStockAlert
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

            // Filter Tabs Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Capsule stock filter tabs
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("ALL", "LOW STOCK").forEach { tab ->
                        val isActive = selectedStockTab == tab
                        val bg = if (isActive) Color(0xFF5B4DFF) else Color(0xFFE2E8F0)
                        val tc = if (isActive) Color.White else Color(0xFF475569)

                        val count = when (tab) {
                            "ALL" -> inventory.size
                            "LOW STOCK" -> inventory.count { it.quantity <= it.minStockAlert }
                            else -> 0
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
                if (filteredInventory.isEmpty()) {
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
                        val isLow = part.quantity <= part.minStockAlert
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
                                        Text(
                                            text = "₹${String.format("%.0f", part.unitPrice)}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF0F172A)
                                        )
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
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isLow) Color(0xFFFEE2E2) else Color(0xFFECFDF5))
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = "Qty: ${part.quantity}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isLow) Color(0xFFEF4444) else Color(0xFF10B981)
                                                )
                                            }

                                            Text(
                                                text = "Alert under ${part.minStockAlert}",
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }

                                        // Quick Action Controls (+ / - / Edit / Delete)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            // Decrease Quantity (-1)
                                            IconButton(
                                                onClick = {
                                                    val newQty = (part.quantity - 1).coerceAtLeast(0)
                                                    viewModel.addInventoryItem(part.copy(quantity = newQty))
                                                },
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .background(Color(0xFFF1F5F9), shape = RoundedCornerShape(6.dp))
                                            ) {
                                                Text("-", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF475569))
                                            }

                                            // Increase Quantity (+1)
                                            IconButton(
                                                onClick = {
                                                    viewModel.addInventoryItem(part.copy(quantity = part.quantity + 1))
                                                },
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .background(Color(0xFFEEF2FF), shape = RoundedCornerShape(6.dp))
                                            ) {
                                                Text("+", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFF5B4DFF))
                                            }

                                            Spacer(modifier = Modifier.width(4.dp))

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

    if (showAddStockDialog) {
        Dialog(onDismissRequest = { showAddStockDialog = false }) {
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (editingItem != null) "Edit Stock Item" else "Add Stock Item",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = Color(0xFF0F172A)
                        )
                        IconButton(onClick = { showAddStockDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                        }
                    }

                    Divider(color = Color(0xFFE2E8F0))

                    OutlinedTextField(
                        value = inputPartName,
                        onValueChange = { inputPartName = it },
                        label = { Text("Part / Item Name") },
                        placeholder = { Text("e.g. Engine Oil 1L") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1E293B),
                            unfocusedTextColor = Color(0xFF1E293B),
                            focusedBorderColor = Color(0xFF5B4DFF),
                            unfocusedBorderColor = Color(0xFFE2E8F0)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = inputQty,
                            onValueChange = { inputQty = it },
                            label = { Text("Quantity") },
                            placeholder = { Text("e.g. 10") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1E293B),
                                unfocusedTextColor = Color(0xFF1E293B),
                                focusedBorderColor = Color(0xFF5B4DFF),
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = inputPrice,
                            onValueChange = { inputPrice = it },
                            label = { Text("Unit Price (₹)") },
                            placeholder = { Text("e.g. 450") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1E293B),
                                unfocusedTextColor = Color(0xFF1E293B),
                                focusedBorderColor = Color(0xFF5B4DFF),
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    OutlinedTextField(
                        value = inputMinAlert,
                        onValueChange = { inputMinAlert = it },
                        label = { Text("Low Stock Alert Threshold") },
                        placeholder = { Text("e.g. 3") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1E293B),
                            unfocusedTextColor = Color(0xFF1E293B),
                            focusedBorderColor = Color(0xFF5B4DFF),
                            unfocusedBorderColor = Color(0xFFE2E8F0)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            val qty = inputQty.toIntOrNull() ?: 0
                            val price = inputPrice.toDoubleOrNull() ?: 0.0
                            val minAlert = inputMinAlert.toIntOrNull() ?: 3

                            if (inputPartName.isNotBlank() && qty >= 0) {
                                val existingMatch = inventory.find { it.name.trim().equals(inputPartName.trim(), ignoreCase = true) }

                                if (editingItem != null) {
                                    // Updating existing item
                                    viewModel.addInventoryItem(
                                        editingItem!!.copy(
                                            name = inputPartName.trim(),
                                            quantity = qty,
                                            unitPrice = price,
                                            minStockAlert = minAlert
                                        )
                                    )
                                } else if (existingMatch != null) {
                                    // Same item name exists, add quantity to existing item
                                    viewModel.addInventoryItem(
                                        existingMatch.copy(
                                            quantity = existingMatch.quantity + qty,
                                            unitPrice = if (price > 0) price else existingMatch.unitPrice,
                                            minStockAlert = minAlert
                                        )
                                    )
                                } else {
                                    // Create brand new item
                                    viewModel.addInventoryItem(
                                        InventoryItemEntity(
                                            name = inputPartName.trim(),
                                            quantity = qty,
                                            minStockAlert = minAlert,
                                            unitPrice = price
                                        )
                                    )
                                }

                                showAddStockDialog = false
                                editingItem = null
                                inputPartName = ""
                                inputQty = ""
                                inputPrice = ""
                                inputMinAlert = "3"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B4DFF)),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (editingItem != null) "Save Changes" else "Add Stock",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
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

@Composable
fun CreateJobCardSheet(
    viewModel: GarageViewModel,
    inventory: List<InventoryItemEntity>,
    initialJob: JobCardEntity? = null,
    onDismiss: () -> Unit,
    onScanQrClick: () -> Unit = {}
) {
    // Current Wizard Step: 1 -> Vehicle, Customer, Symptoms; 2 -> Work done, Parts Used, and Billing Rates (Saves directly)
    var step by remember { mutableIntStateOf(1) }

    // Step 1 Fields
    var vehicleType by remember { mutableStateOf("Bike") }
    var vehiclePlate by remember { mutableStateOf(initialJob?.vehiclePlate ?: "") } // Vehicle Number
    var vehicleMake by remember { mutableStateOf("Honda") }
    var vehicleModel by remember { mutableStateOf("Splendor") }
    var vehicleVariant by remember { mutableStateOf("Splendor Pro") }
    var kmReading by remember { mutableStateOf("0") }
    var fuelLevel by remember { mutableStateOf("50") }

    var customerName by remember { mutableStateOf(initialJob?.customerName ?: "") }
    var customerPhone by remember { mutableStateOf(initialJob?.customerPhone ?: "") }
    var symptoms by remember { mutableStateOf(initialJob?.symptoms ?: "") }

    var validationError by remember { mutableStateOf("") }

    // Dropdown constants and structure map
    val vehicleTypes = listOf("Bike", "Scooter", "Car")
    val makes = listOf("Honda", "Hero", "Bajaj", "Yamaha", "TVS", "Suzuki", "Maruti Suzuki", "Hyundai", "Tata", "Mahindra")
    
    val modelsMap = mapOf(
        "Honda" to listOf("Splendor", "Shine", "Activa", "Unicorn", "City", "Dio", "Amaze", "Civic"),
        "Hero" to listOf("Splendor", "Splendor Plus", "HF Deluxe", "Passion Pro", "Glamour", "Xpulse"),
        "Bajaj" to listOf("Pulsar", "Platina", "Avenger", "Discover", "Dominar"),
        "Yamaha" to listOf("FZ", "R15", "MT-15", "Fascino", "RayZR"),
        "TVS" to listOf("Apache", "Jupiter", "Ntorq", "Raider", "Radeon"),
        "Suzuki" to listOf("Access 125", "Burgman", "Gixxer", "Avenis"),
        "Maruti Suzuki" to listOf("Swift", "Baleno", "WagonR", "Alto", "Brezza", "Ertiga"),
        "Hyundai" to listOf("i20", "Creta", "Verna", "Venue", "Grand i10", "Exter"),
        "Tata" to listOf("Nexon", "Punch", "Tiago", "Altroz", "Harrier", "Safari"),
        "Mahindra" to listOf("Scorpio", "Thar", "XUV700", "Bolero", "XUV300")
    )

    val currentModels = modelsMap[vehicleMake] ?: listOf("Splendor", "Shine", "Activa", "i20", "Swift")

    val variantsMap = mapOf(
        "Splendor" to listOf("Splendor Pro", "Standard", "Self Start", "iSmart", "Sports"),
        "Splendor Plus" to listOf("Standard", "Self Start", "i3S", "Black Accent"),
        "Shine" to listOf("SP 125", "Standard", "Disc", "Drum"),
        "Activa" to listOf("Activa 6G", "Activa 125", "DLX", "Standard"),
        "Pulsar" to listOf("Pulsar 150", "Pulsar 220", "NS200", "N160"),
        "Swift" to listOf("LXI", "VXI", "ZXI", "ZXI+"),
        "i20" to listOf("Asta", "Magna", "Sportz", "Era"),
        "Nexon" to listOf("XM", "XT", "XZ+", "XZ")
    )

    val currentVariants = variantsMap[vehicleModel] ?: listOf("Splendor Pro", "Standard", "Base", "Pro", "Plus", "Top Model")

    // Dynamic reset effects to ensure model matches make and variant matches model
    LaunchedEffect(vehicleMake) {
        val models = modelsMap[vehicleMake] ?: listOf("Splendor")
        if (vehicleModel !in models) {
            vehicleModel = models.firstOrNull() ?: "Splendor"
        }
    }

    LaunchedEffect(vehicleModel) {
        val variants = variantsMap[vehicleModel] ?: listOf("Splendor Pro")
        if (vehicleVariant !in variants) {
            vehicleVariant = variants.firstOrNull() ?: "Splendor Pro"
        }
    }

    // Step 2 Fields (Calculated catalog charges)
    var tasksSelected by remember { mutableStateOf("") }
    var partsListedSelection by remember { mutableStateOf("") }
    var serviceChargesInput by remember { mutableStateOf("") }
    var partsChargesInput by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Panel - Slimmed Down & Highly Polish
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeepIndigo)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "New Job Card",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Step $step of 2 • " + (if (step == 1) "Vehicle & Owner Particulars" else "Tasks, Spares & Billing"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Step Bar Progress Line
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color(0xFFE5E7EB))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (step == 1) 0.5f else 1.0f)
                            .fillMaxHeight()
                            .background(DeepIndigo)
                    )
                }

                // Scrollable Form content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .background(Color(0xFFF9FAFB))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (step == 1) {
                        // SECTION 1: VEHICLE DETAILS
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.DirectionsCar,
                                contentDescription = null,
                                tint = DeepIndigo,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "VEHICLE INFORMATION",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = DeepIndigo,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Compact side-by-side elements
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SimpleFormDropdown(
                                label = "Vehicle Type *",
                                options = vehicleTypes,
                                selectedOption = vehicleType,
                                onOptionSelected = { vehicleType = it },
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = vehiclePlate,
                                onValueChange = { 
                                    vehiclePlate = it 
                                    validationError = ""
                                },
                                label = { Text("Plate Number *", fontSize = 12.sp) },
                                placeholder = { Text("MH12AB1234", fontSize = 12.sp) },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = onScanQrClick) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan VIN", tint = DeepIndigo, modifier = Modifier.size(18.dp))
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DeepIndigo,
                                    focusedLabelColor = DeepIndigo,
                                    unfocusedLabelColor = Color.Gray,
                                    cursorColor = DeepIndigo
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                modifier = Modifier.weight(1.2f).testTag("job_plate_field")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SimpleFormDropdown(
                                label = "Make",
                                options = makes,
                                selectedOption = vehicleMake,
                                onOptionSelected = { vehicleMake = it },
                                modifier = Modifier.weight(1f)
                            )

                            SimpleFormDropdown(
                                label = "Model",
                                options = currentModels,
                                selectedOption = vehicleModel,
                                onOptionSelected = { vehicleModel = it },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SimpleFormDropdown(
                                label = "Variant",
                                options = currentVariants,
                                selectedOption = vehicleVariant,
                                onOptionSelected = { vehicleVariant = it },
                                modifier = Modifier.weight(1.1f)
                            )

                            OutlinedTextField(
                                value = kmReading,
                                onValueChange = { kmReading = it },
                                label = { Text("KM Reading", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DeepIndigo,
                                    focusedLabelColor = DeepIndigo,
                                    unfocusedLabelColor = Color.Gray,
                                    cursorColor = DeepIndigo
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                modifier = Modifier.weight(0.9f)
                            )
                        }

                        OutlinedTextField(
                            value = fuelLevel,
                            onValueChange = { fuelLevel = it },
                            label = { Text("Fuel Level %", fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DeepIndigo,
                                focusedLabelColor = DeepIndigo,
                                unfocusedLabelColor = Color.Gray,
                                cursorColor = DeepIndigo
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // SECTION 2: CUSTOMER DETAILS
                        Divider(color = Color(0xFFE5E7EB), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = DeepIndigo,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CUSTOMER OWNER DETAILS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = DeepIndigo,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { 
                                    customerName = it 
                                    validationError = ""
                                },
                                label = { Text("Full Name *", fontSize = 12.sp) },
                                placeholder = { Text("Customer Name", fontSize = 12.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DeepIndigo,
                                    focusedLabelColor = DeepIndigo,
                                    unfocusedLabelColor = Color.Gray,
                                    cursorColor = DeepIndigo
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                modifier = Modifier.weight(1.2f).testTag("job_customer_field")
                            )

                            OutlinedTextField(
                                value = customerPhone,
                                onValueChange = { 
                                    customerPhone = it 
                                    validationError = ""
                                },
                                label = { Text("Phone Number *", fontSize = 12.sp) },
                                placeholder = { Text("10 Digit Phone", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DeepIndigo,
                                    focusedLabelColor = DeepIndigo,
                                    unfocusedLabelColor = Color.Gray,
                                    cursorColor = DeepIndigo
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                modifier = Modifier.weight(0.9f)
                            )
                        }

                        // SECTION 3: REPAIR COMPLAINTS (Extremely compact)
                        Divider(color = Color(0xFFE5E7EB), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Assignment,
                                contentDescription = null,
                                tint = DeepIndigo,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "JOB COMPLAINTS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = DeepIndigo,
                                letterSpacing = 0.5.sp
                            )
                        }

                        OutlinedTextField(
                            value = symptoms,
                            onValueChange = { symptoms = it },
                            label = { Text("Description of Symptoms", fontSize = 12.sp) },
                            placeholder = { Text("Oil leaking, engine rattle...", fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DeepIndigo,
                                focusedLabelColor = DeepIndigo,
                                unfocusedLabelColor = Color.Gray,
                                cursorColor = DeepIndigo
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(75.dp),
                            maxLines = 2
                        )

                        if (validationError.isNotEmpty()) {
                            Text(
                                text = validationError,
                                color = Color.Red,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    } else {
                        // STEP 2: WORK MAINTENANCE & PRICING
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Build,
                                contentDescription = null,
                                tint = DeepIndigo,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "WORK & MATERIALS RECORD",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = DeepIndigo,
                                letterSpacing = 0.5.sp
                            )
                        }

                        OutlinedTextField(
                            value = tasksSelected,
                            onValueChange = { tasksSelected = it },
                            label = { Text("Completed Maintenance Tasks", fontSize = 12.sp) },
                            placeholder = { Text("Tyre balance, Spark plug replacement", fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DeepIndigo,
                                focusedLabelColor = DeepIndigo,
                                cursorColor = DeepIndigo
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = partsListedSelection,
                            onValueChange = { partsListedSelection = it },
                            label = { Text("Materials/Parts Used", fontSize = 12.sp) },
                            placeholder = { Text("Brake Pads - ₹1800.0, Oil - ₹450.0", fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DeepIndigo,
                                focusedLabelColor = DeepIndigo,
                                cursorColor = DeepIndigo
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Divider(color = Color(0xFFE5E7EB), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Payments,
                                contentDescription = null,
                                tint = DeepIndigo,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "BILLING DETAILS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = DeepIndigo,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = serviceChargesInput,
                                onValueChange = { serviceChargesInput = it },
                                label = { Text("Labor Charges (₹)", fontSize = 12.sp) },
                                placeholder = { Text("0.00", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DeepIndigo,
                                    focusedLabelColor = DeepIndigo,
                                    unfocusedLabelColor = Color.Gray,
                                    cursorColor = DeepIndigo
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = partsChargesInput,
                                onValueChange = { partsChargesInput = it },
                                label = { Text("Spares Cost (₹)", fontSize = 12.sp) },
                                placeholder = { Text("0.00", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DeepIndigo,
                                    focusedLabelColor = DeepIndigo,
                                    unfocusedLabelColor = Color.Gray,
                                    cursorColor = DeepIndigo
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Bottom Footer Panel Block - Cleaned, Slim
                Divider(color = Color(0xFFE5E7EB), thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step > 1) {
                        OutlinedButton(
                            onClick = { step -= 1 },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DeepIndigo),
                            border = BorderStroke(1.dp, DeepIndigo),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text("Back", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = {
                            if (step == 1) {
                                if (vehiclePlate.isBlank() || customerName.isBlank() || customerPhone.isBlank()) {
                                    validationError = "Please fill out all required fields marked with *"
                                } else if (customerPhone.filter { it.isDigit() }.length < 10) {
                                    validationError = "Please enter a valid 10-digit Phone Number"
                                } else {
                                    validationError = ""
                                    step = 2
                                }
                            } else {
                                // Calculations & Save Direct
                                val sc = serviceChargesInput.toDoubleOrNull() ?: 0.0
                                val pc = partsChargesInput.toDoubleOrNull() ?: 0.0
                                val tax = (sc + pc) * 0.077 // retaining standard 7.7% GST calculation
                                val total = sc + pc + tax

                                val dynamicVehicleName = "$vehicleType: $vehicleMake $vehicleModel $vehicleVariant (KM: $kmReading, Fuel: $fuelLevel%)"

                                viewModel.addJobCard(
                                    JobCardEntity(
                                        customerName = customerName,
                                        customerPhone = customerPhone,
                                        vehicleName = dynamicVehicleName,
                                        vehiclePlate = vehiclePlate,
                                        symptoms = symptoms,
                                        diagnosis = "Standard Checkup",
                                        status = "OPEN",
                                        tasks = tasksSelected.ifEmpty { "Inspection Check" },
                                        materials = partsListedSelection.ifEmpty { "Lubricants Oil Refill" },
                                        serviceCharges = sc,
                                        partsCharges = pc,
                                        taxes = tax,
                                        totalAmount = total
                                    )
                                )
                                viewModel.clearDiagnostics()
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(if (step < 2) "Continue" else "Save Job Card", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// OVERLAY 2: DOUBLE BREAKDOWN INVOICE PREVIEW & INTERACTIVE EDITING
// ==========================================
data class MasterItem(
    val title: String,
    val price: Double,
    val stock: Int? = null
)

data class InvoiceRowItem(
    val id: Int,
    val description: String,
    val qty: Int,
    val price: Double,
    val isService: Boolean
) {
    val total: Double get() = qty * price
}

fun getPriceForService(title: String): Double {
    val clean = title.replace(Regex("\\(.*\\)"), "").trim()
    return when {
        clean.contains("Engine Oil Change", true) -> 300.0
        clean.contains("Oil Filter Replacement", true) -> 150.0
        clean.contains("Air Filter Cleaning", true) -> 80.0
        clean.contains("Air Filter Replacement", true) -> 200.0
        clean.contains("Spark Plug Cleaning", true) -> 50.0
        clean.contains("Spark Plug Replacement", true) -> 120.0
        clean.contains("Chain Cleaning", true) -> 120.0
        clean.contains("Chain Lubrication", true) -> 100.0
        clean.contains("Chain Adjustment", true) -> 80.0
        else -> 100.0
    }
}

fun getPriceForMaterial(title: String): Double {
    val clean = title.replace(Regex("\\(.*\\)"), "").replace(Regex("(?i)-\\s*₹?\\s*\\d+"), "").trim()
    return when {
        clean.contains("Clutch Cable", true) -> 150.0
        clean.contains("Throttle Assembly", true) -> 400.0
        clean.contains("Speedometer Gear", true) -> 300.0
        clean.contains("Speedometer", true) -> 1200.0
        clean.contains("Odometer Cable", true) -> 150.0
        clean.contains("Digital Meter", true) -> 2000.0
        clean.contains("Indicator Buzzer", true) -> 80.0
        clean.contains("Flasher Relay", true) -> 120.0
        clean.contains("Wiring Harness", true) -> 800.0
        clean.contains("Mineral", true) -> 350.0
        clean.contains("Semi Synthetic", true) -> 600.0
        clean.contains("Fully Synthetic", true) -> 900.0
        else -> 150.0
    }
}

fun parseInvoiceItems(tasks: String, materials: String): List<InvoiceRowItem> {
    val rowItems = mutableListOf<InvoiceRowItem>()
    var index = 1

    // Parse tasks (services)
    if (tasks.isNotBlank()) {
        tasks.split(",").forEach { s ->
            val trimmed = s.trim()
            if (trimmed.isNotBlank()) {
                val price = getPriceForService(trimmed)
                rowItems.add(InvoiceRowItem(index++, trimmed, 1, price, true))
            }
        }
    }

    // Parse materials (spares/lubes)
    if (materials.isNotBlank()) {
        materials.split(",").forEach { m ->
            val trimmed = m.trim()
            if (trimmed.isNotBlank()) {
                try {
                    var name = trimmed
                    var qty = 1
                    var price = 0.0

                    // Parse Qty: e.g. "Clutch Cable (2)" or similar
                    val qtyMatcher = java.util.regex.Pattern.compile("\\((\\d+)\\)").matcher(trimmed)
                    if (qtyMatcher.find()) {
                        qty = qtyMatcher.group(1)?.toIntOrNull() ?: 1
                        name = trimmed.substring(0, qtyMatcher.start()).trim()
                    }

                    // Parse Price format: " - ₹150" or " - $150"
                    val priceParts = trimmed.split("-")
                    if (priceParts.size > 1) {
                        val priceStr = priceParts[1].replace("$", "").replace("₹", "").trim()
                        price = priceStr.toDoubleOrNull() ?: 0.0
                        name = priceParts[0].replace(Regex("\\(.*\\)"), "").trim()
                    }

                    if (price == 0.0) {
                        price = getPriceForMaterial(name)
                    }

                    rowItems.add(InvoiceRowItem(index++, name, qty, price, false))
                } catch (e: Exception) {
                    rowItems.add(InvoiceRowItem(index++, trimmed, 1, 150.0, false))
                }
            }
        }
    }
    return rowItems
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

// Dialog: Add Service Block
@Composable
fun SelectServiceDialog(
    onDismiss: () -> Unit,
    viewModel: GarageViewModel,
    jobCard: JobCardEntity,
    onServiceAdded: (String) -> Unit
) {
    val aiResult by viewModel.aiResult.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()

    val servicesList: List<MasterItem> = listOf(
        MasterItem("Engine Oil Change", 300.0),
        MasterItem("Oil Filter Replacement", 150.0),
        MasterItem("Air Filter Cleaning", 80.0),
        MasterItem("Air Filter Replacement", 200.0),
        MasterItem("Spark Plug Cleaning", 50.0),
        MasterItem("Spark Plug Replacement", 120.0),
        MasterItem("Chain Cleaning", 120.0),
        MasterItem("Chain Lubrication", 100.0),
        MasterItem("Chain Adjustment", 80.0)
    )

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 550.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Service", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Divider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 12.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Prompt AI response box
                    if (isAiLoading || aiResult.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF5FF)),
                            border = BorderStroke(1.dp, Color(0xFFE9D5FF).copy(0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (isAiLoading) "Analyzing symptoms with AI..." else "Suggested Repairs:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFF8B5CF6)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isAiLoading) "Invoking model endpoint..." else aiResult,
                                    fontSize = 12.sp,
                                    color = Color(0xFF1E1B4B)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text("Standard Services List", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF475569))

                    servicesList.forEach { service ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(service.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                                Text("Price: ₹${String.format("%.0f", service.price)}", fontSize = 12.sp, color = Color(0xFF64748B))
                            }
                            IconButton(
                                onClick = { onServiceAdded(service.title) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF5B4DFF))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Dialog: Add Spare Block
@Composable
fun SelectSpareDialog(
    onDismiss: () -> Unit,
    onSpareAdded: (String, Int, Double) -> Unit
) {
    val sparesList: List<MasterItem> = listOf(
        MasterItem("Clutch Cable", 150.0, 2),
        MasterItem("Throttle Assembly", 400.0, 2),
        MasterItem("Speedometer", 1200.0, 1),
        MasterItem("Speedometer Gear", 300.0, 5),
        MasterItem("Odometer Cable", 150.0, 3),
        MasterItem("Digital Meter", 2000.0, 2),
        MasterItem("Indicator Buzzer", 80.0, 12),
        MasterItem("Flasher Relay", 120.0, 7),
        MasterItem("Wiring Harness", 800.0, 2)
    )

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 550.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Spare", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Divider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 12.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    sparesList.forEach { spare ->
                        var qtyChoice by remember(spare.title) { mutableIntStateOf(1) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(spare.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                                Text("Price: ₹${String.format("%.0f", spare.price)} • stock: ${spare.stock ?: 0}", fontSize = 12.sp, color = Color(0xFF64748B))

                                // Simple plus-minus selector for Quantity
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Qty: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                    IconButton(
                                        onClick = { if (qtyChoice > 1) qtyChoice-- },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text("-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Text("$qtyChoice", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
                                    IconButton(
                                        onClick = { qtyChoice++ },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text("+", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }

                            IconButton(
                                onClick = { onSpareAdded(spare.title, qtyChoice, spare.price) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFFF97316)) // Orange accent
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Dialog: Add Lube Block
@Composable
fun SelectLubeDialog(
    onDismiss: () -> Unit,
    onLubeAdded: (String, Int, Double) -> Unit
) {
    val lubesList: List<MasterItem> = listOf(
        MasterItem("Engine Oil (1L Mineral)", 350.0, 5),
        MasterItem("Engine Oil (1L Semi Synthetic)", 600.0, 3),
        MasterItem("Engine Oil (1L Fully Synthetic)", 900.0, 4)
    )

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 550.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Lube", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Divider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 12.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    lubesList.forEach { lube ->
                        var qtyChoice by remember(lube.title) { mutableIntStateOf(1) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(lube.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                                Text("Price: ₹${String.format("%.0f", lube.price)} • stock: ${lube.stock ?: 0}", fontSize = 12.sp, color = Color(0xFF64748B))

                                // Simple plus-minus selector for Quantity
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Qty: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                    IconButton(
                                        onClick = { if (qtyChoice > 1) qtyChoice-- },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text("-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Text("$qtyChoice", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
                                    IconButton(
                                        onClick = { qtyChoice++ },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text("+", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }

                            IconButton(
                                onClick = { onLubeAdded(lube.title, qtyChoice, lube.price) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF10B981)) // Green accent
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Dialog: Receive Payment Block (Double verification & saving entry)
@Composable
fun ReceivePaymentDialog(
    totalBill: Double,
    onDismiss: () -> Unit,
    onPaymentConfirmed: (Double, String) -> Unit
) {
    var discountInput by remember { mutableStateOf("0") }
    var selectedPaymentMode by remember { mutableStateOf("Online") } // Online or Cash

    val totalDouble = totalBill
    val discountDouble = discountInput.trim().toDoubleOrNull() ?: 0.0
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
                    onClick = { onPaymentConfirmed(finalPayable, selectedPaymentMode) },
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

@Composable
fun InvoiceBreakdownPage(
    viewModel: GarageViewModel,
    jobCard: JobCardEntity,
    onDismiss: () -> Unit,
    onSavePdf: (JobCardEntity) -> Unit
) {
    val context = LocalContext.current

    // Live state connection for instant reactivity on item add/delete
    val allJobs by viewModel.jobCards.collectAsState()
    val activeJobCard = allJobs.find { it.id == jobCard.id } ?: jobCard

    // Custom item state
    var customItemName by remember { mutableStateOf("") }
    var customItemPrice by remember { mutableStateOf("") }
    var customIsService by remember { mutableStateOf(true) }

    // Drawer state
    var showAddItemDrawer by remember { mutableStateOf(false) }
    var drawerTab by remember { mutableStateOf("Services") }
    var showPaymentSelector by remember { mutableStateOf(false) }

    // Parse list of tasks & components
    val rowItems = remember(activeJobCard.tasks, activeJobCard.materials) {
        parseInvoiceItems(activeJobCard.tasks, activeJobCard.materials)
    }

    val aiResult by viewModel.aiResult.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()

    val servicesList: List<MasterItem> = listOf(
        MasterItem("Engine Oil Change", 300.0),
        MasterItem("Oil Filter Replacement", 150.0),
        MasterItem("Air Filter Cleaning", 80.0),
        MasterItem("Air Filter Replacement", 200.0),
        MasterItem("Spark Plug Cleaning", 50.0),
        MasterItem("Spark Plug Replacement", 120.0),
        MasterItem("Chain Cleaning", 120.0),
        MasterItem("Chain Lubrication", 100.0),
        MasterItem("Chain Adjustment", 80.0)
    )

    val sparesList: List<MasterItem> = listOf(
        MasterItem("Clutch Cable", 150.0, 2),
        MasterItem("Throttle Assembly", 400.0, 2),
        MasterItem("Speedometer", 1200.0, 1),
        MasterItem("Speedometer Gear", 300.0, 5),
        MasterItem("Odometer Cable", 150.0, 3),
        MasterItem("Digital Meter", 2000.0, 2),
        MasterItem("Indicator Buzzer", 80.0, 12),
        MasterItem("Flasher Relay", 120.0, 7),
        MasterItem("Wiring Harness", 800.0, 2)
    )

    val lubesList: List<MasterItem> = listOf(
        MasterItem("Engine Oil (1L Mineral)", 350.0, 5),
        MasterItem("Engine Oil (1L Semi Synthetic)", 600.0, 3),
        MasterItem("Engine Oil (1L Fully Synthetic)", 900.0, 4)
    )

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
                                    val file = com.example.util.InvoicePdfGenerator.generatePdfFile(context, activeJobCard)
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
                                    val file = com.example.util.InvoicePdfGenerator.generatePdfFile(context, activeJobCard)
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
                                    val file = com.example.util.InvoicePdfGenerator.generatePdfFile(context, activeJobCard)
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Invoice paper center worksheet (Scrollable content)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp) // more compact container padding
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                                        if (GarageSession.ownerName.isNotBlank()) {
                                            Text(GarageSession.ownerName, fontSize = 10.sp, color = Color(0xFF64748B))
                                        }
                                        if (GarageSession.ownerPhone.isNotBlank()) {
                                            Text("Contact: ${GarageSession.ownerPhone}", fontSize = 10.sp, color = Color(0xFF64748B))
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
                                    Text("Document #: GG-${activeJobCard.id}", fontSize = 10.sp, color = Color(0xFF94A3B8))
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
                                    Text("BILL TO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(activeJobCard.customerName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                    Text("Phone: ${activeJobCard.customerPhone}", fontSize = 11.sp, color = Color(0xFF64748B))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text("VEHICLE INFO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(activeJobCard.vehiclePlate.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                    val cleanVehicle = activeJobCard.vehicleName.replace(Regex("(?i)Bike:|Scooter:|Car:"), "").trim()
                                    Text(cleanVehicle, fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp)) // compact spacer
                            Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(8.dp)) // compact spacer

                            // Single + button design requested
                            if (activeJobCard.status.uppercase() != "CLOSED") {
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
                                    Text("Add Service / Spare / Lube", color = Color(0xFF5B4DFF), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }

                                Spacer(modifier = Modifier.height(8.dp)) // compact spacer
                                Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(8.dp)) // compact spacer
                            }

                            // Row 4: Itemized Billing Table Layout
                            Text("ITEMIZED BILLING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), modifier = Modifier.padding(bottom = 6.dp))

                            // Table Headers Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC))
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("#", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.width(24.dp))
                                Text("ITEM DESCRIPTION", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.weight(1f))
                                Text("QTY", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                                Text("PRICE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.width(54.dp), textAlign = TextAlign.End)
                                Text("TOTAL", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
                                if (activeJobCard.status.uppercase() != "CLOSED") {
                                    Spacer(modifier = Modifier.width(32.dp))
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
                                        Text("${item.id}", fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.width(24.dp))
                                        Text(item.description, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B), modifier = Modifier.weight(1f))
                                        Text("${item.qty}", fontSize = 11.sp, color = Color(0xFF1E293B), modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                                        Text("₹${String.format("%.0f", item.price)}", fontSize = 11.sp, color = Color(0xFF1E293B), modifier = Modifier.width(54.dp), textAlign = TextAlign.End)
                                        Text("₹${String.format("%.0f", item.total)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), modifier = Modifier.width(64.dp), textAlign = TextAlign.End)

                                        if (activeJobCard.status.uppercase() != "CLOSED") {
                                            IconButton(
                                                onClick = {
                                                    if (item.isService) {
                                                        val taskList = activeJobCard.tasks.split(",")
                                                            .map { it.trim() }
                                                            .filter { it.isNotBlank() }
                                                            .toMutableList()
                                                        val removeIdx = taskList.indexOfFirst { it == item.description }
                                                        if (removeIdx != -1) taskList.removeAt(removeIdx)
                                                        val finalTasks = taskList.joinToString(",")

                                                        val leftoverRows = parseInvoiceItems(finalTasks, activeJobCard.materials)
                                                        val sc = leftoverRows.filter { itemRow -> itemRow.isService }.sumOf { itemRow -> itemRow.total }
                                                        val pc = leftoverRows.filter { itemRow -> !itemRow.isService }.sumOf { itemRow -> itemRow.total }
                                                        val tax = (sc + pc) * 0.18
                                                        val totalVal = sc + pc + tax

                                                        viewModel.addJobCard(
                                                            activeJobCard.copy(
                                                                tasks = finalTasks,
                                                                serviceCharges = sc,
                                                                partsCharges = pc,
                                                                totalAmount = totalVal,
                                                                taxes = tax
                                                            )
                                                        )
                                                        Toast.makeText(context, "Removed service item", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        val matList = activeJobCard.materials.split(",")
                                                            .map { it.trim() }
                                                            .filter { it.isNotBlank() }
                                                            .toMutableList()
                                                        val removeIdx = matList.indexOfFirst { it.contains(item.description) }
                                                        if (removeIdx != -1) matList.removeAt(removeIdx)
                                                        val finalMaterials = matList.joinToString(",")

                                                        val leftoverRows = parseInvoiceItems(activeJobCard.tasks, finalMaterials)
                                                        val sc = leftoverRows.filter { itemRow -> itemRow.isService }.sumOf { itemRow -> itemRow.total }
                                                        val pc = leftoverRows.filter { itemRow -> !itemRow.isService }.sumOf { itemRow -> itemRow.total }
                                                        val tax = (sc + pc) * 0.18
                                                        val totalVal = sc + pc + tax

                                                        viewModel.addJobCard(
                                                            activeJobCard.copy(
                                                                materials = finalMaterials,
                                                                serviceCharges = sc,
                                                                partsCharges = pc,
                                                                totalAmount = totalVal,
                                                                taxes = tax
                                                            )
                                                        )
                                                        Toast.makeText(context, "Removed part item", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier
                                                    .padding(start = 8.dp)
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

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("GST/Taxes (18%)", fontSize = 12.sp, color = Color(0xFF64748B))
                                    Text("₹${String.format("%.2f", activeJobCard.taxes)}", fontSize = 12.sp, color = Color(0xFF1E293B))
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
                            Text("TERMS & CONDITIONS:", fontWeight = FontWeight.Bold, fontSize = 8.sp, color = Color(0xFF94A3B8))
                            Text("1. Goods once sold or installed will not be taken back.\n2. Warranty on spare parts as per manufacturer specifications.", fontSize = 8.sp, color = Color(0xFF94A3B8))

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
                                    val pdfFile = com.example.util.InvoicePdfGenerator.generatePdfFile(context, activeJobCard)
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
                                    val pdfFile = com.example.util.InvoicePdfGenerator.generatePdfFile(context, activeJobCard)
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
        }

        // --- sliding right-to-left drawer overlay ---
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
                    .widthIn(max = 350.dp)
                    .fillMaxWidth(0.85f),
                color = Color.White,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 16.dp)
                ) {
                    // Drawer Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Add Item",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Color(0xFF1E293B)
                        )
                        IconButton(onClick = { showAddItemDrawer = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                        }
                    }

                    Divider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 12.dp))

                    // Drawer Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tabs = listOf("Services", "Spares", "Lubes")
                        tabs.forEach { tab ->
                            val isSel = drawerTab == tab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Color(0xFF5B4DFF) else Color(0xFFF1F5F9))
                                    .clickable { drawerTab = tab }
                                    .padding(vertical = 8.dp),
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
                            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Custom Item Input section
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Add Custom Item / Service", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = customItemName,
                                        onValueChange = { customItemName = it },
                                        label = { Text("Item Name", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.2f)
                                    )
                                    OutlinedTextField(
                                        value = customItemPrice,
                                        onValueChange = { customItemPrice = it },
                                        label = { Text("Price (₹)", fontSize = 10.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(0.8f)
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
                                            label = { Text("Part / Lube", fontSize = 10.sp) }
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            val priceVal = customItemPrice.toDoubleOrNull() ?: 0.0
                                            if (customItemName.isNotBlank() && priceVal > 0) {
                                                if (customIsService) {
                                                    val currentTasks = activeJobCard.tasks
                                                    val finalTasks = if (currentTasks.isBlank()) customItemName else "$currentTasks,$customItemName"
                                                    val rowItemsCurrent = parseInvoiceItems(finalTasks, activeJobCard.materials)
                                                    val sc = rowItemsCurrent.filter { itemRow -> itemRow.isService }.sumOf { itemRow -> itemRow.total }
                                                    val pc = rowItemsCurrent.filter { itemRow -> !itemRow.isService }.sumOf { itemRow -> itemRow.total }
                                                    val tax = (sc + pc) * 0.18
                                                    val totalVal = sc + pc + tax

                                                    viewModel.addJobCard(
                                                        activeJobCard.copy(
                                                            tasks = finalTasks,
                                                            serviceCharges = sc,
                                                            partsCharges = pc,
                                                            totalAmount = totalVal,
                                                            taxes = tax
                                                        )
                                                    )
                                                } else {
                                                    val addedPart = "$customItemName (1) - ₹${String.format("%.0f", priceVal)}"
                                                    val currentMaterials = activeJobCard.materials
                                                    val finalMaterials = if (currentMaterials.isBlank()) addedPart else "$currentMaterials,$addedPart"
                                                    val rowItemsCurrent = parseInvoiceItems(activeJobCard.tasks, finalMaterials)
                                                    val sc = rowItemsCurrent.filter { itemRow -> itemRow.isService }.sumOf { itemRow -> itemRow.total }
                                                    val pc = rowItemsCurrent.filter { itemRow -> !itemRow.isService }.sumOf { itemRow -> itemRow.total }
                                                    val tax = (sc + pc) * 0.18
                                                    val totalVal = sc + pc + tax

                                                    viewModel.addJobCard(
                                                        activeJobCard.copy(
                                                            materials = finalMaterials,
                                                            serviceCharges = sc,
                                                            partsCharges = pc,
                                                            totalAmount = totalVal,
                                                            taxes = tax
                                                        )
                                                    )
                                                }
                                                Toast.makeText(context, "Added: $customItemName", Toast.LENGTH_SHORT).show()
                                                customItemName = ""
                                                customItemPrice = ""
                                            } else {
                                                Toast.makeText(context, "Enter valid item name & price", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        when (drawerTab) {
                            "Services" -> {
                                Text("Standard Services", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF475569))

                                servicesList.forEach { service ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF8FAFC))
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(service.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                            Text("Price: ₹${String.format("%.0f", service.price)}", fontSize = 11.sp, color = Color(0xFF64748B))
                                        }
                                        IconButton(
                                            onClick = {
                                                val currentTasks = if (activeJobCard.tasks.isBlank()) "" else activeJobCard.tasks
                                                val finalTasks = if (currentTasks.isBlank()) service.title else "$currentTasks,${service.title}"
                                                
                                                val rowItemsCurrent = parseInvoiceItems(finalTasks, activeJobCard.materials)
                                                val sc = rowItemsCurrent.filter { itemRow -> itemRow.isService }.sumOf { itemRow -> itemRow.total }
                                                val pc = rowItemsCurrent.filter { itemRow -> !itemRow.isService }.sumOf { itemRow -> itemRow.total }
                                                val tax = (sc + pc) * 0.18
                                                val totalVal = sc + pc + tax

                                                viewModel.addJobCard(
                                                    activeJobCard.copy(
                                                        tasks = finalTasks,
                                                        serviceCharges = sc,
                                                        partsCharges = pc,
                                                        totalAmount = totalVal,
                                                        taxes = tax
                                                    )
                                                )
                                                Toast.makeText(context, "Added Service: ${service.title}", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(Color(0xFF5B4DFF))
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                            "Spares" -> {
                                sparesList.forEach { spare ->
                                    var qtyChoice by remember(spare.title) { mutableStateOf(1) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF8FAFC))
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(spare.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                            Text("Price: ₹${String.format("%.0f", spare.price)} • stock: ${spare.stock ?: 0}", fontSize = 11.sp, color = Color(0xFF64748B))

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Qty: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                                IconButton(
                                                    onClick = { if (qtyChoice > 1) qtyChoice-- },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Text("-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                }
                                                Text("$qtyChoice", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
                                                IconButton(
                                                    onClick = { qtyChoice++ },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Text("+", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                }
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                val addedPart = "${spare.title} ($qtyChoice) - ₹${String.format("%.0f", qtyChoice * spare.price)}"
                                                val currentMaterials = if (activeJobCard.materials.isBlank()) "" else activeJobCard.materials
                                                val finalMaterials = if (currentMaterials.isBlank()) addedPart else "$currentMaterials,$addedPart"

                                                val rowItemsCurrent = parseInvoiceItems(activeJobCard.tasks, finalMaterials)
                                                val sc = rowItemsCurrent.filter { itemRow -> itemRow.isService }.sumOf { itemRow -> itemRow.total }
                                                val pc = rowItemsCurrent.filter { itemRow -> !itemRow.isService }.sumOf { itemRow -> itemRow.total }
                                                val tax = (sc + pc) * 0.18
                                                val totalVal = sc + pc + tax

                                                viewModel.addJobCard(
                                                    activeJobCard.copy(
                                                        materials = finalMaterials,
                                                        serviceCharges = sc,
                                                        partsCharges = pc,
                                                        totalAmount = totalVal,
                                                        taxes = tax
                                                    )
                                                )
                                                Toast.makeText(context, "Added part: ${spare.title}", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(Color(0xFFF97316))
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                            "Lubes" -> {
                                lubesList.forEach { lube ->
                                    var qtyChoice by remember(lube.title) { mutableStateOf(1) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF8FAFC))
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(lube.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                            Text("Price: ₹${String.format("%.0f", lube.price)} • stock: ${lube.stock ?: 0}", fontSize = 11.sp, color = Color(0xFF64748B))

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Qty: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                                IconButton(
                                                    onClick = { if (qtyChoice > 1) qtyChoice-- },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Text("-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                }
                                                Text("$qtyChoice", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
                                                IconButton(
                                                    onClick = { qtyChoice++ },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Text("+", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                }
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                val addedLube = "${lube.title} ($qtyChoice) - ₹${String.format("%.0f", qtyChoice * lube.price)}"
                                                val currentMaterials = if (activeJobCard.materials.isBlank()) "" else activeJobCard.materials
                                                val finalMaterials = if (currentMaterials.isBlank()) addedLube else "$currentMaterials,$addedLube"

                                                val rowItemsCurrent = parseInvoiceItems(activeJobCard.tasks, finalMaterials)
                                                val sc = rowItemsCurrent.filter { itemRow -> itemRow.isService }.sumOf { itemRow -> itemRow.total }
                                                val pc = rowItemsCurrent.filter { itemRow -> !itemRow.isService }.sumOf { itemRow -> itemRow.total }
                                                val tax = (sc + pc) * 0.18
                                                val totalVal = sc + pc + tax

                                                viewModel.addJobCard(
                                                    activeJobCard.copy(
                                                        materials = finalMaterials,
                                                        serviceCharges = sc,
                                                        partsCharges = pc,
                                                        totalAmount = totalVal,
                                                        taxes = tax
                                                    )
                                                )
                                                Toast.makeText(context, "Added lube: ${lube.title}", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(Color(0xFF10B981))
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(14.dp))
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

    // --- Receive Payment and close Popup ---
    if (showPaymentSelector) {
        ReceivePaymentDialog(
            totalBill = activeJobCard.totalAmount,
            onDismiss = { showPaymentSelector = false },
            onPaymentConfirmed = { netReceived, mode ->
                showPaymentSelector = false
                onDismiss() // close main worksheet

                // 1. Move status to CLOSED
                viewModel.updateJobCardStatus(activeJobCard, "CLOSED")

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
                            "Service Alerts & Reminders",
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
                        Text("Active Intervals Due", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkSlate)
                        Button(
                            onClick = { showAddAlertDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Interval Alert", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (alerts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No upcoming service alerts registered.", color = Color.Gray)
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
            }
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
    onAddAlert: (ServiceAlertEntity) -> Unit
) {
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
                Text("New Service Interval Reminder", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkSlate)
                Divider(color = Color(0xFFE2E8F0))

                OutlinedTextField(value = custName, onValueChange = { custName = it }, label = { Text("Customer Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = custPhone, onValueChange = { custPhone = it }, label = { Text("Customer Phone") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = plate, onValueChange = { plate = it }, label = { Text("Vehicle Plate") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = serviceType, onValueChange = { serviceType = it }, label = { Text("Service Type") }, modifier = Modifier.fillMaxWidth())
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = intervalKm, onValueChange = { intervalKm = it }, label = { Text("Interval KM") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = dueDays, onValueChange = { dueDays = it }, label = { Text("Due in Days") }, modifier = Modifier.weight(1f))
                }

                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Reminder Notes") }, modifier = Modifier.fillMaxWidth())

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
                    Text("Save Service Alert", color = Color.White, fontWeight = FontWeight.Bold)
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
    onOpenScanner: () -> Unit = {}
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
                        text = "Customer Management",
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
