package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.model.CustomerProfileEntity
import com.example.data.model.InventoryItemEntity
import com.example.data.model.JobCardEntity
import com.example.data.model.ServiceMembershipEntity
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

enum class ScanMode {
    ALL, VIN_PLATE, INVENTORY_SKU, CUSTOMER
}

data class ScanResult(
    val rawCode: String,
    val matchedJob: JobCardEntity? = null,
    val matchedInventory: InventoryItemEntity? = null,
    val matchedCustomer: CustomerProfileEntity? = null,
    val matchedMembership: ServiceMembershipEntity? = null,
    val detectedType: String = "Code"
)

@Composable
fun QrCodeScannerModal(
    jobCards: List<JobCardEntity> = emptyList(),
    inventoryItems: List<InventoryItemEntity> = emptyList(),
    customerProfiles: List<CustomerProfileEntity> = emptyList(),
    memberships: List<ServiceMembershipEntity> = emptyList(),
    onDismiss: () -> Unit,
    onCreateJobWithPlate: (plate: String, customerName: String, phone: String) -> Unit = { _, _, _ -> },
    onSelectInventoryItem: (InventoryItemEntity) -> Unit = {},
    onSelectCustomer: (CustomerProfileEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboardManager = LocalClipboardManager.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
            if (!isGranted) {
                Toast.makeText(context, "Camera permission required for live scanning", Toast.LENGTH_SHORT).show()
            }
        }
    )

    var currentMode by remember { mutableStateOf(ScanMode.ALL) }
    var isTorchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var scannedResult by remember { mutableStateOf<ScanResult?>(null) }
    var manualInputCode by remember { mutableStateOf("") }

    // Laser Animation Effect
    val infiniteTransition = rememberInfiniteTransition(label = "Laser")
    val laserYOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserPos"
    )

    fun processScannedCode(code: String) {
        val cleanCode = code.trim()
        if (cleanCode.isEmpty()) return

        // 1. Match Job Cards by Vehicle Plate or Symptoms/ID
        val matchedJob = jobCards.find {
            it.vehiclePlate.equals(cleanCode, ignoreCase = true) ||
                    it.vehiclePlate.replace("-", "").replace(" ", "").equals(cleanCode.replace("-", "").replace(" ", ""), ignoreCase = true) ||
                    "JOB-${it.id}".equals(cleanCode, ignoreCase = true)
        }

        // 2. Match Inventory by Name/SKU
        val matchedInventory = inventoryItems.find {
            it.name.equals(cleanCode, ignoreCase = true) ||
                    it.name.contains(cleanCode, ignoreCase = true) ||
                    "SKU-${it.id}".equals(cleanCode, ignoreCase = true) ||
                    "ITEM-${it.id}".equals(cleanCode, ignoreCase = true)
        }

        // 3. Match Customer Profiles by Phone / Plate / Name
        val matchedCust = customerProfiles.find {
            it.phone.equals(cleanCode, ignoreCase = true) ||
                    it.vehiclePlate.equals(cleanCode, ignoreCase = true) ||
                    it.name.equals(cleanCode, ignoreCase = true) ||
                    "CUST-${it.id}".equals(cleanCode, ignoreCase = true)
        }

        // 4. Match Membership
        val matchedMemb = memberships.find {
            it.customerPhone.equals(cleanCode, ignoreCase = true) ||
                    it.customerName.equals(cleanCode, ignoreCase = true) ||
                    "MEMB-${it.id}".equals(cleanCode, ignoreCase = true)
        }

        val type = when {
            matchedJob != null -> "Vehicle Job Card"
            matchedInventory != null -> "Inventory Part SKU"
            matchedCust != null -> "Customer Profile"
            matchedMemb != null -> "Service Membership"
            cleanCode.contains("MH") || cleanCode.contains("DL") || cleanCode.contains("KA") || cleanCode.contains("-") -> "Vehicle VIN / Plate"
            cleanCode.contains("OIL") || cleanCode.contains("PAD") || cleanCode.contains("FILTER") || cleanCode.contains("SPARK") -> "Part Barcode"
            else -> "QR / Barcode Code"
        }

        scannedResult = ScanResult(
            rawCode = cleanCode,
            matchedJob = matchedJob,
            matchedInventory = matchedInventory,
            matchedCustomer = matchedCust,
            matchedMembership = matchedMemb,
            detectedType = type
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // --- Live Camera Preview View ---
                if (hasCameraPermission) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            }
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setTargetResolution(Size(1280, 720))
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                imageAnalysis.setAnalyzer(
                                    Executors.newSingleThreadExecutor(),
                                    QrCodeAnalyzer { barcodeValue ->
                                        if (scannedResult == null) {
                                            processScannedCode(barcodeValue)
                                        }
                                    }
                                )

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                try {
                                    cameraProvider.unbindAll()
                                    camera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Fallback when permission is not granted
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Camera Access Needed",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Grant camera access to scan vehicle VIN plates, barcodes & stock QR tags in real-time.",
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Grant Camera Permission")
                            }
                        }
                    }
                }

                // --- Scanner Frame Overlay & Controls ---
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Bar Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                                )
                            )
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "QR & VIN Scanner",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Align QR code / VIN within box",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }

                        IconButton(
                            onClick = {
                                isTorchOn = !isTorchOn
                                camera?.cameraControl?.enableTorch(isTorchOn)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isTorchOn) Color(0xFFEAB308) else Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Flashlight",
                                tint = if (isTorchOn) Color.Black else Color.White
                            )
                        }
                    }

                    // Mode Selector Tabs
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        items(ScanMode.values()) { mode ->
                            val label = when (mode) {
                                ScanMode.ALL -> "Auto Detect"
                                ScanMode.VIN_PLATE -> "Vehicle VIN"
                                ScanMode.INVENTORY_SKU -> "Part SKU"
                                ScanMode.CUSTOMER -> "Customer QR"
                            }
                            val isSelected = currentMode == mode
                            Surface(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clickable { currentMode = mode },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) Color(0xFF6366F1) else Color.Black.copy(alpha = 0.6f),
                                border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Center Target Box
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .align(Alignment.CenterHorizontally)
                            .border(2.dp, Color(0xFF6366F1), RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    ) {
                        // Scanner Corner Indicators
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            // Animated Laser Line
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .offset(y = (laserYOffset * 230).dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color(0xFF22C55E),
                                                Color(0xFF6366F1),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // --- Demo Presets & Manual Test Input Bar ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            "Quick Demo Scan Chips:",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                DemoChip(
                                    label = "🚗 MH12-AB-3456",
                                    onClick = { processScannedCode("MH12-AB-3456") }
                                )
                            }
                            item {
                                DemoChip(
                                    label = "📦 Engine Oil 5W30",
                                    onClick = { processScannedCode("Engine Oil 5W30") }
                                )
                            }
                            item {
                                DemoChip(
                                    label = "🔧 Brake Pads Pro",
                                    onClick = { processScannedCode("Brake Pads Pro") }
                                )
                            }
                            item {
                                DemoChip(
                                    label = "👤 Rajesh Kumar",
                                    onClick = { processScannedCode("Rajesh Kumar") }
                                )
                            }
                            item {
                                DemoChip(
                                    label = "⚡ VIN987654321",
                                    onClick = { processScannedCode("VIN987654321") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Manual Entry Input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = manualInputCode,
                                onValueChange = { manualInputCode = it },
                                placeholder = { Text("Type VIN / SKU manually...", fontSize = 12.sp, color = Color.Gray) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF6366F1),
                                    unfocusedBorderColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF1E293B),
                                    unfocusedContainerColor = Color(0xFF1E293B)
                                ),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (manualInputCode.isNotBlank()) {
                                        processScannedCode(manualInputCode)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                modifier = Modifier.height(50.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan", tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.navigationBarsPadding())
                    }
                }

                // --- Scan Result Sheet Modal ---
                scannedResult?.let { result ->
                    ScanResultDialog(
                        result = result,
                        onDismiss = { scannedResult = null },
                        onCreateJobWithPlate = { p, n, ph ->
                            scannedResult = null
                            onDismiss()
                            onCreateJobWithPlate(p, n, ph)
                        },
                        onSelectInventoryItem = { item ->
                            scannedResult = null
                            onDismiss()
                            onSelectInventoryItem(item)
                        },
                        onSelectCustomer = { cust ->
                            scannedResult = null
                            onDismiss()
                            onSelectCustomer(cust)
                        },
                        onCopyCode = {
                            clipboardManager.setText(AnnotatedString(result.rawCode))
                            Toast.makeText(context, "Copied code: ${result.rawCode}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF334155),
        border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ScanResultDialog(
    result: ScanResult,
    onDismiss: () -> Unit,
    onCreateJobWithPlate: (plate: String, customerName: String, phone: String) -> Unit,
    onSelectInventoryItem: (InventoryItemEntity) -> Unit,
    onSelectCustomer: (CustomerProfileEntity) -> Unit,
    onCopyCode: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF22C55E).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Code Scanned!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(result.detectedType, fontSize = 12.sp, color = Color.Gray)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                // Raw Scanned Tag Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SCANNED VALUE", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(
                                result.rawCode,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF1E293B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onCopyCode) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF6366F1))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Detailed Match Results
                when {
                    result.matchedJob != null -> {
                        val job = result.matchedJob
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                            border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("FOUND ACTIVE VEHICLE JOB CARD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4338CA))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("${job.vehicleName} (${job.vehiclePlate})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Customer: ${job.customerName} • ${job.customerPhone}", fontSize = 12.sp, color = Color.DarkGray)
                                Text("Status: ${job.status} • Total: ₹${job.totalAmount}", fontSize = 12.sp, color = Color(0xFF059669), fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                onCreateJobWithPlate(job.vehiclePlate, job.customerName, job.customerPhone)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open / Edit Job Card")
                        }
                    }

                    result.matchedInventory != null -> {
                        val item = result.matchedInventory
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("MATCHED INVENTORY PART", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("In Stock: ${item.quantity} units (Alert threshold: ${item.minStockAlert})", fontSize = 12.sp, color = Color.DarkGray)
                                Text("Unit Price: ₹${item.unitPrice}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { onSelectInventoryItem(item) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select Part for Billing / Stock")
                        }
                    }

                    result.matchedCustomer != null -> {
                        val cust = result.matchedCustomer
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("MATCHED CUSTOMER PROFILE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(cust.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Phone: ${cust.phone}", fontSize = 12.sp)
                                if (cust.vehiclePlate.isNotBlank()) {
                                    Text("Registered Vehicle: ${cust.vehicleName} (${cust.vehiclePlate})", fontSize = 12.sp, color = Color.DarkGray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { onSelectCustomer(cust) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("View Profile", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { onCreateJobWithPlate(cust.vehiclePlate, cust.name, cust.phone) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Create Job", fontSize = 12.sp)
                            }
                        }
                    }

                    else -> {
                        // Unmatched / Generic QR Scanned
                        Text(
                            "This scanned tag is not directly linked to existing database records yet.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { onCreateJobWithPlate(result.rawCode, "New Customer", "") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Job with Plate '${result.rawCode}'")
                        }
                    }
                }
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(18.dp)
    )
}
@OptIn(ExperimentalGetImage::class)
private class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()
    private var isScanning = true

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null && isScanning) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { rawValue ->
                            if (rawValue.isNotBlank()) {
                                isScanning = false
                                onQrCodeScanned(rawValue)
                                break
                            }
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
