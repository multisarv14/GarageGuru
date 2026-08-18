package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.GarageSession
import com.example.data.api.GarageSettings
import com.example.data.api.VehicleMasterStore
import com.example.data.catalogue.VehicleCatalogue
import com.example.data.model.InventoryItemEntity
import com.example.data.model.JobCardEntity
import com.example.data.model.TeamMemberEntity
import com.example.data.permissions.AppModule
import com.example.data.permissions.PermissionAction
import com.example.ui.GarageViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Indigo = Color(0xFF5B4DFF)
private val Ink = Color(0xFF0F172A)
private val Muted = Color(0xFF64748B)
private val Line = Color(0xFFE2E8F0)
private val Canvas = Color(0xFFF8FAFC)

/** Every field on the page is this tall, so nothing sits a few pixels off its neighbour. */
private val FieldHeight = 58.dp

/**
 * New Job Card — a page rather than a popup.
 *
 * A dialog could not give the two steps room to breathe on a phone, and its fixed height fought
 * the keyboard on every field. As a page it gets the whole screen, one scroll, and a footer that
 * stays put.
 *
 * Both steps' state lives here, above the step switch, so moving between them keeps everything
 * already typed — going back to correct a registration number does not cost the services already
 * chosen.
 */
@Composable
fun NewJobCardScreen(
    viewModel: GarageViewModel,
    inventory: List<InventoryItemEntity>,
    teamMembers: List<TeamMemberEntity>,
    initialJob: JobCardEntity? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settings by GarageSettings.state.collectAsState()
    val signedIn by GarageSession.signedInUser.collectAsState()

    var step by rememberSaveable { mutableIntStateOf(1) }

    // --- Step 1 ---
    var mobile by rememberSaveable { mutableStateOf(initialJob?.customerPhone.orEmpty()) }
    var customerName by rememberSaveable { mutableStateOf(initialJob?.customerName.orEmpty()) }
    var customerSerial by rememberSaveable { mutableStateOf(initialJob?.customerSerial.orEmpty()) }
    var useSerial by rememberSaveable { mutableStateOf(false) }
    var vehicleType by rememberSaveable { mutableStateOf(initialJob?.vehicleType.orEmpty()) }
    var brand by rememberSaveable { mutableStateOf(initialJob?.vehicleMake.orEmpty()) }
    var model by rememberSaveable { mutableStateOf(initialJob?.vehicleModel.orEmpty()) }
    var variant by rememberSaveable { mutableStateOf(initialJob?.vehicleVariant.orEmpty()) }
    var vehicleNumber by rememberSaveable { mutableStateOf(initialJob?.vehiclePlate.orEmpty()) }
    var odometer by rememberSaveable { mutableStateOf(initialJob?.vehicleKm.orEmpty()) }
    var fuelLevel by rememberSaveable { mutableStateOf(initialJob?.vehicleFuel.orEmpty()) }

    // --- Step 2 ---
    // Not rememberSaveable: a Set has no Bundle representation. Step changes do not
    // dispose this composable, so moving between steps keeps the selection regardless.
    var selectedServices by remember { mutableStateOf(setOf<String>()) }
    var complaints by rememberSaveable { mutableStateOf(initialJob?.symptoms.orEmpty()) }
    var mechanic by rememberSaveable { mutableStateOf(signedIn.name) }
    var expectedDelivery by rememberSaveable { mutableStateOf(0L) }
    var remarks by rememberSaveable { mutableStateOf("") }

    var error by remember { mutableStateOf("") }

    // The vehicle type drives everything below it, so a stale brand or model is dropped the
    // moment it stops being offered rather than silently saved onto the card.
    // Sourced from the published vehicle master, which falls back to the bundled catalogue when
    // the cloud copy has not been fetched — so the dropdowns are never empty.
    val master by VehicleMasterStore.entries.collectAsState()
    val brands = remember(vehicleType, master) { VehicleMasterStore.brandsFor(vehicleType) }
    val models = remember(vehicleType, brand, master) { VehicleMasterStore.modelsFor(vehicleType, brand) }
    val variants = remember(vehicleType, brand, model, master) {
        VehicleMasterStore.variantsFor(vehicleType, brand, model)
    }

    LaunchedEffect(vehicleType) {
        if (brand.isNotBlank() && brand !in brands) { brand = ""; model = ""; variant = "" }
    }
    LaunchedEffect(brand) {
        if (model.isNotBlank() && model !in models) { model = ""; variant = "" }
    }
    LaunchedEffect(model) {
        if (variant.isNotBlank() && variant !in variants) variant = ""
    }

    // Services come from the Service Master the workshop maintains under Stock. A brand new
    // garage has none yet, so the built-in list stands in rather than showing an empty picker.
    val masterServices = remember(inventory, settings.servicingType) {
        inventory.filter { it.isService }.map { it.name to it.unitPrice }.sortedBy { it.first }
    }
    val fallbackServices = remember(settings.servicingType) {
        VehicleCatalogue.services(settings.servicingType).map { it.title to it.price }
    }
    val serviceOptions = if (masterServices.isNotEmpty()) masterServices else fallbackServices
    val usingFallback = masterServices.isEmpty()

    // Only somebody who may edit job cards can hand the work to a different mechanic.
    val canReassign = signedIn.can(AppModule.JOB_CARDS, PermissionAction.EDIT)
    val mechanicOptions = remember(teamMembers) {
        teamMembers.filter { it.isActive }.map { it.name }.ifEmpty { listOf(signedIn.name) }
    }

    fun validateStep1(): String = when {
        mobile.filter(Char::isDigit).length < 10 -> "Enter a valid 10-digit mobile number"
        !useSerial && customerName.isBlank() -> "Customer name is required, or switch to a serial number"
        useSerial && customerSerial.isBlank() -> "Enter the customer serial number"
        vehicleType.isBlank() -> "Select the vehicle type"
        brand.isBlank() -> "Select the vehicle brand"
        model.isBlank() -> "Select the vehicle model"
        vehicleNumber.isBlank() -> "Vehicle number is required"
        else -> ""
    }

    fun validateStep2(): String = when {
        selectedServices.isEmpty() -> "Select at least one service"
        complaints.isBlank() -> "Job complaints are required"
        mechanic.isBlank() -> "Assign a mechanic"
        else -> ""
    }

    Scaffold(
        containerColor = Canvas,
        topBar = {
            Surface(color = Color.White, shadowElevation = 2.dp) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (step == 2) { step = 1; error = "" } else onDismiss()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Ink)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("New Job Card", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Ink)
                            Text(
                                if (step == 1) "Step 1 of 2 • Customer & Vehicle" else "Step 2 of 2 • Job Details",
                                fontSize = 11.sp,
                                color = Muted
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Muted)
                        }
                    }

                    // Tapping a completed step goes back to it with everything intact.
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        StepChip("1", "Customer & Vehicle", step == 1, step > 1) { step = 1; error = "" }
                        Spacer(modifier = Modifier.width(8.dp))
                        StepChip("2", "Job Details", step == 2, false) {
                            val problem = validateStep1()
                            if (problem.isEmpty()) { step = 2; error = "" } else error = problem
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        },
        bottomBar = {
            Surface(color = Color.White, shadowElevation = 12.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (step == 2) { step = 1; error = "" } else onDismiss() },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Line)
                    ) {
                        Text(if (step == 2) "Back" else "Cancel", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
                    }
                    Button(
                        onClick = {
                            if (step == 1) {
                                val problem = validateStep1()
                                if (problem.isNotEmpty()) { error = problem; return@Button }
                                error = ""
                                step = 2
                            } else {
                                val problem = validateStep2()
                                if (problem.isNotEmpty()) { error = problem; return@Button }
                                error = ""

                                val descriptive = listOf(brand, model, variant).filter { it.isNotBlank() }.joinToString(" ")
                                viewModel.addJobCard(
                                    JobCardEntity(
                                        customerName = if (useSerial) "Customer #$customerSerial" else customerName.trim(),
                                        customerSerial = if (useSerial) customerSerial.trim() else "",
                                        customerPhone = mobile.trim(),
                                        vehicleName = "$vehicleType: $descriptive",
                                        vehiclePlate = vehicleNumber.trim().uppercase(),
                                        symptoms = complaints.trim(),
                                        diagnosis = "",
                                        status = "OPEN",
                                        // Each service is written with the price it was offered
                                        // at. Storing only the name would send the invoice looking
                                        // it up in the *built-in* catalogue, which knows nothing
                                        // about services this workshop defined itself — a ₹800
                                        // custom wash would have been billed at the ₹100 default.
                                        tasks = selectedServices.mapNotNull { title ->
                                            serviceOptions.firstOrNull { it.first == title }
                                                ?.let { formatInvoiceItem(title, 1, it.second) }
                                        }.joinToString(","),
                                        vehicleType = vehicleType,
                                        vehicleMake = brand,
                                        vehicleModel = model,
                                        vehicleVariant = variant,
                                        vehicleKm = odometer.trim(),
                                        vehicleFuel = fuelLevel.trim(),
                                        mechanicName = mechanic,
                                        expectedDelivery = expectedDelivery,
                                        customerRemarks = remarks.trim()
                                    )
                                )
                                viewModel.clearDiagnostics()
                                Toast.makeText(context, "Job card created", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                        modifier = Modifier.weight(1.4f).height(46.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (step == 1) "Continue" else "Create Job Card",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (step == 1) {
                SectionLabel("CUSTOMER", Icons.Outlined.Person)

                JobField(mobile, { mobile = it.filter(Char::isDigit).take(10); error = "" },
                    "Mobile Number *", keyboardType = KeyboardType.Phone, leading = Icons.Default.Phone)

                // A walk-in who will not give a name still needs to be identifiable on the card.
                if (!useSerial) {
                    JobField(customerName, { customerName = it; error = "" }, "Customer Name *", leading = Icons.Default.Badge)
                } else {
                    JobField(customerSerial, { customerSerial = it; error = "" },
                        "Customer Serial Number *", leading = Icons.Default.Tag)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { useSerial = !useSerial; error = "" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useSerial,
                        onCheckedChange = { useSerial = it; error = "" },
                        colors = CheckboxDefaults.colors(checkedColor = Indigo)
                    )
                    Text("Name not available — use a serial number instead", fontSize = 12.sp, color = Muted)
                }

                SectionLabel("VEHICLE", Icons.Outlined.DirectionsCar)

                JobDropdown(
                    "Vehicle Type *", VehicleMasterStore.vehicleTypes(settings.servicingType),
                    vehicleType, { vehicleType = it; error = "" }
                )
                JobDropdown(
                    "Vehicle Brand *", brands, brand, { brand = it; error = "" },
                    enabledHint = if (vehicleType.isBlank()) "Choose a vehicle type first" else null
                )
                JobDropdown(
                    "Vehicle Model *", models, model, { model = it; error = "" },
                    enabledHint = if (brand.isBlank()) "Choose a brand first" else null
                )
                JobDropdown(
                    "Vehicle Variant", variants, variant, { variant = it },
                    enabledHint = if (model.isBlank()) "Choose a model first" else null
                )
                JobField(vehicleNumber, { vehicleNumber = it.uppercase(); error = "" },
                    "Vehicle Number *", leading = Icons.Default.ConfirmationNumber)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    JobField(odometer, { odometer = it.filter(Char::isDigit) }, "Odometer Reading",
                        modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number)
                    JobField(fuelLevel, { fuelLevel = it.filter(Char::isDigit).take(3) }, "Fuel Level (%)",
                        modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number)
                }
            } else {
                SectionLabel("SERVICES", Icons.Outlined.Build)

                if (usingFallback) {
                    Text(
                        "No services in your Service Master yet — showing the standard list. " +
                            "Add your own under Stock → Add Service.",
                        fontSize = 11.sp,
                        color = Color(0xFF92400E),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEF3C7))
                            .padding(10.dp)
                    )
                }

                Text(
                    "Services * — ${selectedServices.size} selected",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selectedServices.isEmpty()) Muted else Indigo
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Line, RoundedCornerShape(12.dp))
                        .background(Color.White)
                ) {
                    serviceOptions.forEach { (title, price) ->
                        val checked = title in selectedServices
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedServices = if (checked) selectedServices - title else selectedServices + title
                                    error = ""
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selectedServices = if (checked) selectedServices - title else selectedServices + title
                                    error = ""
                                },
                                colors = CheckboxDefaults.colors(checkedColor = Indigo)
                            )
                            Text(
                                title, fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text("₹${String.format("%.0f", price)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
                        }
                    }
                }

                SectionLabel("JOB DETAILS", Icons.Outlined.Assignment)

                JobField(complaints, { complaints = it; error = "" }, "Job Complaints *",
                    singleLine = false, minLines = 3)

                JobDropdown(
                    "Mechanic *", mechanicOptions, mechanic, { mechanic = it; error = "" },
                    enabledHint = if (!canReassign) "Assigned to you — you cannot reassign this" else null
                )

                DateTimeField(
                    label = "Expected Delivery Date & Time",
                    millis = expectedDelivery,
                    onPicked = { expectedDelivery = it },
                    onClear = { expectedDelivery = 0L }
                )

                JobField(remarks, { remarks = it }, "Customer Remarks", singleLine = false, minLines = 2)
            }

            if (error.isNotEmpty()) {
                Text(error, color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ----------------------------------------------------------------------
// Shared field shapes — one definition each, so heights and padding match
// ----------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(icon, contentDescription = null, tint = Indigo, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Indigo, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun StepChip(number: String, label: String, active: Boolean, done: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) Indigo else if (done) Color(0xFFEEF2FF) else Color(0xFFF1F5F9))
            .clickable(enabled = active || done, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (done) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Indigo, modifier = Modifier.size(13.dp))
        } else {
            Text(number, fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (active) Color.White else Muted)
        }
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            label, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = if (active) Color.White else if (done) Indigo else Muted
        )
    }
}

@Composable
private fun JobField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    leading: ImageVector? = null,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = leading?.let {
            { Icon(it, contentDescription = null, tint = Indigo, modifier = Modifier.size(17.dp)) }
        },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Indigo,
            focusedLabelColor = Indigo,
            unfocusedBorderColor = Line
        ),
        textStyle = TextStyle(fontSize = 13.sp),
        shape = RoundedCornerShape(10.dp),
        modifier = if (singleLine) modifier.heightIn(min = FieldHeight) else modifier
    )
}

/**
 * A dropdown that says *why* it is empty.
 *
 * A brand list with nothing in it because no vehicle type was chosen looks broken; saying so is
 * what makes the type → brand → model chain understandable rather than mysterious.
 */
@Composable
private fun JobDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    enabledHint: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val enabled = enabledHint == null && options.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FieldHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (enabled) Color.White else Color(0xFFF8FAFC))
                    .border(1.dp, Line, RoundedCornerShape(10.dp))
                    .clickable(enabled = enabled) { expanded = true }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, fontSize = 10.sp, color = Muted)
                    Text(
                        selected.ifBlank { "Select" },
                        fontSize = 13.sp,
                        fontWeight = if (selected.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (selected.isBlank()) Color(0xFF94A3B8) else Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.Default.ArrowDropDown, contentDescription = null,
                    tint = if (enabled) Indigo else Color(0xFFCBD5E1)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, fontSize = 13.sp) },
                        onClick = { onSelect(option); expanded = false }
                    )
                }
            }
        }
        if (enabledHint != null) {
            Text(enabledHint, fontSize = 10.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(start = 4.dp, top = 2.dp))
        }
    }
}

/** Date then time, in one control, because a promise time needs both to mean anything. */
@Composable
private fun DateTimeField(
    label: String,
    millis: Long,
    onPicked: (Long) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val formatted = remember(millis) {
        if (millis <= 0L) "" else SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FieldHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(10.dp))
            .clickable {
                val now = Calendar.getInstance().apply { if (millis > 0) timeInMillis = millis }
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                val picked = Calendar.getInstance().apply {
                                    set(year, month, day, hour, minute, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                onPicked(picked.timeInMillis)
                            },
                            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false
                        ).show()
                    },
                    now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)
                ).apply {
                    // A vehicle cannot be promised back before it arrived.
                    datePicker.minDate = System.currentTimeMillis() - 1000
                }.show()
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Event, contentDescription = null, tint = Indigo, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 10.sp, color = Muted)
            Text(
                formatted.ifBlank { "Not set" },
                fontSize = 13.sp,
                fontWeight = if (formatted.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                color = if (formatted.isBlank()) Color(0xFF94A3B8) else Ink
            )
        }
        if (millis > 0L) {
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Muted, modifier = Modifier.size(16.dp))
            }
        }
    }
}
