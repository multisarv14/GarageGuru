package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CashItemEntity
import com.example.data.model.InventoryItemEntity
import com.example.data.model.JobCardEntity
import com.example.data.model.ServiceMembershipEntity
import com.example.ui.GarageViewModel
import com.example.ui.theme.CoolGrayBorder
import com.example.ui.theme.DarkSlate
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.t
import com.example.ui.theme.DeepIndigo
import java.util.Calendar

// ==========================================
// VIEW 6: REPORTS (business summary over a period)
// ==========================================

enum class ReportPeriod(val label: String, val key: String) {
    TODAY("Today", "rep.today"),
    WEEK("7 Days", "rep.week"),
    MONTH("This Month", "rep.month"),
    ALL("All Time", "rep.allTime")
}

/** Epoch millis the period starts at; 0 means "everything". */
private fun ReportPeriod.startMillis(): Long {
    if (this == ReportPeriod.ALL) return 0L
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    when (this) {
        ReportPeriod.TODAY -> Unit
        ReportPeriod.WEEK -> cal.add(Calendar.DAY_OF_YEAR, -6)
        ReportPeriod.MONTH -> cal.set(Calendar.DAY_OF_MONTH, 1)
        ReportPeriod.ALL -> Unit
    }
    return cal.timeInMillis
}

private fun money(value: Double): String = "₹${String.format("%,.0f", value)}"

data class ReportFigures(
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val jobsCreated: Int = 0,
    val jobsCompleted: Int = 0,
    val jobsClosed: Int = 0,
    val invoiced: Double = 0.0,
    val labour: Double = 0.0,
    val parts: Double = 0.0,
    val taxes: Double = 0.0,
    val byPaymentMode: List<Pair<String, Double>> = emptyList(),
    val byCategory: List<Pair<String, Double>> = emptyList(),
    val topCustomers: List<Pair<String, Double>> = emptyList(),
    val stockValue: Double = 0.0,
    val lowStockCount: Int = 0,
    val membershipDues: Double = 0.0,
    val activeMemberships: Int = 0
) {
    val net: Double get() = income - expense
}

/**
 * Everything on this screen is derived from the same Room tables the rest of the app reads,
 * which are themselves a mirror of Firestore — so the numbers always agree with the Jobs,
 * Cashbook and Stock tabs rather than being a second, drifting source of truth.
 */
fun buildReport(
    period: ReportPeriod,
    jobs: List<JobCardEntity>,
    cash: List<CashItemEntity>,
    inventory: List<InventoryItemEntity>,
    memberships: List<ServiceMembershipEntity>
): ReportFigures {
    val from = period.startMillis()
    val cashInPeriod = cash.filter { it.timestamp >= from }
    val jobsInPeriod = jobs.filter { it.creationTime >= from }

    val income = cashInPeriod.filter { it.type == "INCOME" }.sumOf { it.amount }
    val expense = cashInPeriod.filter { it.type == "EXPENSE" }.sumOf { it.amount }

    val byMode = cashInPeriod.filter { it.type == "INCOME" }
        .groupBy { it.paymentMode.ifBlank { "Other" } }
        .map { (mode, rows) -> mode to rows.sumOf { it.amount } }
        .sortedByDescending { it.second }

    val byCategory = cashInPeriod.filter { it.type == "INCOME" }
        .groupBy { it.category.ifBlank { "Other" } }
        .map { (cat, rows) -> cat to rows.sumOf { it.amount } }
        .sortedByDescending { it.second }

    val topCustomers = jobsInPeriod
        .filter { it.customerName.isNotBlank() }
        .groupBy { it.customerName }
        .map { (name, rows) -> name to rows.sumOf { it.totalAmount } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(5)

    return ReportFigures(
        income = income,
        expense = expense,
        jobsCreated = jobsInPeriod.size,
        jobsCompleted = jobsInPeriod.count { it.status.equals("COMPLETED", true) },
        jobsClosed = jobsInPeriod.count { it.status.equals("CLOSED", true) },
        invoiced = jobsInPeriod.sumOf { it.totalAmount },
        labour = jobsInPeriod.sumOf { it.serviceCharges },
        parts = jobsInPeriod.sumOf { it.partsCharges },
        taxes = jobsInPeriod.sumOf { it.taxes },
        byPaymentMode = byMode,
        byCategory = byCategory,
        topCustomers = topCustomers,
        stockValue = inventory.sumOf { it.quantity * it.unitPrice },
        lowStockCount = inventory.count { it.quantity <= it.minStockAlert },
        membershipDues = memberships.sumOf { it.pendingBalance },
        activeMemberships = memberships.size
    )
}

@Composable
fun ReportsView(
    viewModel: GarageViewModel,
    jobs: List<JobCardEntity>,
    cash: List<CashItemEntity>,
    inventory: List<InventoryItemEntity>,
    memberships: List<ServiceMembershipEntity>,
    canSeeFinancials: Boolean = true
) {
    var period by remember { mutableStateOf(ReportPeriod.MONTH) }
    val isSyncing by viewModel.isSyncing.collectAsState()

    // Pull the latest cloud state once on open, so a report run on this phone reflects
    // entries made on another device.
    LaunchedEffect(Unit) { viewModel.syncWithCloud() }

    val figures = remember(period, jobs, cash, inventory, memberships) {
        buildReport(period, jobs, cash, inventory, memberships)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 90.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(t("rep.title"), fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E293B))
                    Text(
                        text = if (isSyncing) t("rep.syncing") else t("rep.subtitle"),
                        fontSize = 11.sp,
                        color = if (isSyncing) DeepIndigo else Color(0xFF64748B)
                    )
                }
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = DeepIndigo
                    )
                }
            }
        }

        // Period selector — same capsule style as the Jobs tab
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReportPeriod.entries.forEach { option ->
                    val selected = period == option
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) DeepIndigo else Color.White)
                            .border(
                                width = 1.dp,
                                color = if (selected) DeepIndigo else CoolGrayBorder,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { period = option }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = t(option.key),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else Color(0xFF64748B)
                        )
                    }
                }
            }
        }

        if (!canSeeFinancials) {
            item { RestrictedCard() }
            return@LazyColumn
        }

        // Net position for the period
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF4338CA))
                        )
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        t("rep.netFor") + " " + period.label.uppercase(),
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFC7D2FE)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        money(figures.net),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniStat(t("cash.income"), money(figures.income), Color(0xFF34D399), Modifier.weight(1f))
                        MiniStat(t("cash.expense"), money(figures.expense), Color(0xFFFCA5A5), Modifier.weight(1f))
                    }
                }
            }
        }

        // Job throughput
        item {
            ReportCard(t("rep.jobCards")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    CountTile("${figures.jobsCreated}", t("rep.created"), Color(0xFF2563EB), Modifier.weight(1f))
                    CountTile("${figures.jobsCompleted}", "COMPLETED", Color(0xFF10B981), Modifier.weight(1f))
                    CountTile("${figures.jobsClosed}", "CLOSED", Color(0xFF334155), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                StatRow(t("rep.totalInvoiced"), money(figures.invoiced), bold = true)
                StatRow(t("rep.labour"), money(figures.labour))
                StatRow(t("rep.parts"), money(figures.parts))
                StatRow(t("rep.tax"), money(figures.taxes))
            }
        }

        if (figures.byPaymentMode.isNotEmpty()) {
            item {
                ReportCard(t("rep.byPaymentMode")) {
                    val top = figures.byPaymentMode.first().second
                    figures.byPaymentMode.forEach { (mode, amount) ->
                        BarRow(mode, money(amount), if (top > 0) (amount / top).toFloat() else 0f, DeepIndigo)
                    }
                }
            }
        }

        if (figures.byCategory.isNotEmpty()) {
            item {
                ReportCard(t("rep.byCategory")) {
                    val top = figures.byCategory.first().second
                    figures.byCategory.forEach { (cat, amount) ->
                        BarRow(cat, money(amount), if (top > 0) (amount / top).toFloat() else 0f, Color(0xFF10B981))
                    }
                }
            }
        }

        if (figures.topCustomers.isNotEmpty()) {
            item {
                ReportCard(t("rep.topCustomers")) {
                    figures.topCustomers.forEachIndexed { index, (name, amount) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEEF2FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${index + 1}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepIndigo
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                name,
                                fontSize = 13.sp,
                                color = DarkSlate,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(money(amount), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkSlate)
                        }
                    }
                }
            }
        }

        // Stock and subscriptions are position figures, not period figures.
        item {
            ReportCard(t("rep.stockSubs")) {
                StatRow(t("rep.stockValue"), money(figures.stockValue), bold = true)
                StatRow(
                    t("rep.belowAlert"),
                    "${figures.lowStockCount}",
                    valueColor = if (figures.lowStockCount > 0) Color(0xFFEF4444) else Color(0xFF10B981)
                )
                StatRow(t("rep.activeMemberships"), "${figures.activeMemberships}")
                StatRow(
                    t("rep.duesPending"),
                    money(figures.membershipDues),
                    valueColor = if (figures.membershipDues > 0) Color(0xFFEF4444) else DarkSlate
                )
            }
        }

        if (figures.jobsCreated == 0 && figures.income == 0.0 && figures.expense == 0.0) {
            item { EmptyPeriodCard(period) }
        }
    }
}

@Composable
private fun ReportCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                title,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                color = Color(0xFF94A3B8)
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(10.dp)
    ) {
        Column {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC7D2FE))
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = accent)
        }
    }
}

@Composable
private fun CountTile(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: Color = DarkSlate
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = Color(0xFF64748B))
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
private fun BarRow(label: String, value: String, fraction: Float, accent: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 12.sp, color = DarkSlate, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkSlate)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFF1F5F9))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent)
            )
        }
    }
}

@Composable
private fun RestrictedCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text("Restricted to Manager / Owner", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
        }
    }
}

@Composable
private fun EmptyPeriodCard(period: ReportPeriod) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.BarChart, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text("No activity in ${period.label.lowercase()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
            Text("Create a job card or log a cash entry to see it here.", fontSize = 11.sp, color = Color(0xFF94A3B8))
        }
    }
}
