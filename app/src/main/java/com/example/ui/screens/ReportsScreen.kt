package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.CashFlowReportData
import com.example.data.repository.ProfitReportData
import com.example.data.repository.StocksValuationData
import com.example.ui.DateRangeFilter
import com.example.ui.MainViewModel
import com.example.ui.components.PharmacyHeader
import com.example.ui.components.StatMetricCard
import com.example.ui.theme.PharmacyNavyDark
import com.example.ui.theme.PharmacyTealPrimary
import com.example.util.PrintUtil
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReportsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.pharmacySettings.collectAsState()
    val metrics by viewModel.dashboardMetrics.collectAsState()

    // 0: P&L Overview, 1: Money In & Out (Cashflow), 2: Stocks Valuation Breakdown
    var reportTab by remember { mutableStateOf(0) }

    var selectedFilter by remember { mutableStateOf(DateRangeFilter.TODAY) }
    var isSpecificDateSelected by remember { mutableStateOf(false) }
    var specificDateTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }

    var profitData by remember { mutableStateOf<ProfitReportData?>(null) }
    var cashFlowData by remember { mutableStateOf<CashFlowReportData?>(null) }
    var stocksData by remember { mutableStateOf<StocksValuationData?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val sdfDisplay = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val datePicker = remember {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                specificDateTimestamp = selectedCal.timeInMillis
                isSpecificDateSelected = true
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    val loadReport = {
        scope.launch {
            isLoading = true
            when (reportTab) {
                0 -> {
                    profitData = viewModel.getProfitReportForRange(selectedFilter)
                }
                1 -> {
                    cashFlowData = if (isSpecificDateSelected) {
                        viewModel.getCashFlowReportForSpecificDate(specificDateTimestamp)
                    } else {
                        viewModel.getCashFlowReportForRange(selectedFilter)
                    }
                }
                2 -> {
                    stocksData = viewModel.getStocksValuationData()
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(reportTab, selectedFilter, isSpecificDateSelected, specificDateTimestamp) {
        loadReport()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        PharmacyHeader(
            title = "Financial & Stock Reports",
            subtitle = "Cashflow tracking, stock valuation & profit analysis"
        ) {
            IconButton(onClick = {
                val csv = viewModel.exportSalesCsv(context)
                PrintUtil.sharePdf(context, csv, "Sales Report CSV")
            }) {
                Icon(Icons.Default.Download, contentDescription = "Export CSV", tint = PharmacyTealPrimary)
            }
        }

        // Section Tabs
        TabRow(
            selectedTabIndex = reportTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = PharmacyTealPrimary
        ) {
            Tab(
                selected = reportTab == 0,
                onClick = { reportTab = 0 },
                text = { Text("P&L Profit", fontWeight = if (reportTab == 0) FontWeight.Bold else FontWeight.Normal) },
                icon = { Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = reportTab == 1,
                onClick = { reportTab = 1 },
                text = { Text("Money In & Out", fontWeight = if (reportTab == 1) FontWeight.Bold else FontWeight.Normal) },
                icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = reportTab == 2,
                onClick = { reportTab = 2 },
                text = { Text("Stocks Value", fontWeight = if (reportTab == 2) FontWeight.Bold else FontWeight.Normal) },
                icon = { Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filters bar (for Tabs 0 and 1)
        if (reportTab != 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(DateRangeFilter.values()) { filter ->
                        FilterChip(
                            selected = !isSpecificDateSelected && selectedFilter == filter,
                            onClick = {
                                isSpecificDateSelected = false
                                selectedFilter = filter
                            },
                            label = { Text(filter.displayName, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PharmacyTealPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Specific Date Filter Button (Only for Money In & Out or Profit)
                FilterChip(
                    selected = isSpecificDateSelected,
                    onClick = {
                        datePicker.show()
                    },
                    label = {
                        Text(
                            if (isSpecificDateSelected) sdfDisplay.format(Date(specificDateTimestamp)) else "Specific Date",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PharmacyTealPrimary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PharmacyTealPrimary)
            }
        } else {
            when (reportTab) {
                0 -> {
                    // P&L Overview
                    val data = profitData
                    if (data != null) {
                        val marginPercent = if (data.totalSalesRevenue > 0) (data.netProfit / data.totalSalesRevenue) * 100 else 0.0

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 70.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    StatMetricCard(
                                        title = "Gross Profit",
                                        value = "${settings.currency} %.2f".format(data.grossProfit),
                                        subtitle = "Revenue - COGS",
                                        icon = Icons.Default.TrendingUp,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = Color(0xFF2E7D32),
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatMetricCard(
                                        title = "Net Income",
                                        value = "${settings.currency} %.2f".format(data.netProfit),
                                        subtitle = "Margin: %.1f%%".format(marginPercent),
                                        icon = Icons.Default.AccountBalance,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = PharmacyTealPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Profit & Loss Statement (${selectedFilter.displayName})",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        ReportLineRow("Total Gross Sales Revenue", "${settings.currency} %.2f".format(data.totalSalesRevenue))
                                        ReportLineRow("Cost of Goods Sold (COGS from Batches)", "- ${settings.currency} %.2f".format(data.totalCogs), isNegative = true)
                                        if (data.totalDiscounts > 0) {
                                            ReportLineRow("Customer Discounts Given", "- ${settings.currency} %.2f".format(data.totalDiscounts), isNegative = true)
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                        ReportLineRow("Gross Profit", "${settings.currency} %.2f".format(data.grossProfit), isBold = true)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        ReportLineRow("Operating Expenses (Shop/Bills/Salary)", "- ${settings.currency} %.2f".format(data.totalExpenses), isNegative = true)
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                        ReportLineRow("Net Profit (Take Home)", "${settings.currency} %.2f".format(data.netProfit), isBold = true, isHighlight = true)
                                    }
                                }
                            }

                            item {
                                ExportButtonsRow(viewModel = viewModel, context = context)
                            }
                        }
                    }
                }

                1 -> {
                    // Money In & Out (Cashflow Ledger)
                    val cf = cashFlowData
                    if (cf != null) {
                        val filterLabel = if (isSpecificDateSelected) sdfDisplay.format(Date(specificDateTimestamp)) else selectedFilter.displayName

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 70.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Top Summary Cards: Total In, Total Out, Net Balance
                            item {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatMetricCard(
                                        title = "Money In",
                                        value = "${settings.currency} %.2f".format(cf.totalMoneyIn),
                                        subtitle = "Sales & Debt Paid",
                                        icon = Icons.Default.ArrowDownward,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = Color(0xFF2E7D32),
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatMetricCard(
                                        title = "Money Out",
                                        value = "${settings.currency} %.2f".format(cf.totalMoneyOut),
                                        subtitle = "Expenses & Buying",
                                        icon = Icons.Default.ArrowUpward,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = Color(0xFFC2185B),
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatMetricCard(
                                        title = "Net Cashflow",
                                        value = "${settings.currency} %.2f".format(cf.netCashFlow),
                                        subtitle = if (cf.netCashFlow >= 0) "Surplus" else "Deficit",
                                        icon = Icons.Default.AccountBalanceWallet,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = if (cf.netCashFlow >= 0) PharmacyTealPrimary else Color(0xFFC2185B),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Money IN Breakdown Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFF2E7D32))
                                                Text("Money Inflow ($filterLabel)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                            }
                                            Text(
                                                "+ ${settings.currency} %.2f".format(cf.totalMoneyIn),
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        ReportLineRow("Counter Cash Sales", "${settings.currency} %.2f".format(cf.cashSales))
                                        ReportLineRow("Digital / Card / Bank Sales", "${settings.currency} %.2f".format(cf.digitalBankSales))
                                        ReportLineRow("Customer Credit Ledger Collections", "${settings.currency} %.2f".format(cf.customerDebtCollected))
                                    }
                                }
                            }

                            // Money OUT Breakdown Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFFC2185B))
                                                Text("Money Outflow ($filterLabel)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                            }
                                            Text(
                                                "- ${settings.currency} %.2f".format(cf.totalMoneyOut),
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFC2185B))
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        ReportLineRow("Pharmacy Operating Expenses", "${settings.currency} %.2f".format(cf.expensesTotal))
                                        ReportLineRow("Purchases Paid in Cash", "${settings.currency} %.2f".format(cf.purchasesCashPaid))
                                        ReportLineRow("Supplier Debt Ledger Payments", "${settings.currency} %.2f".format(cf.supplierDebtPaid))

                                        if (cf.expensesByCategory.isNotEmpty()) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                            Text("Expense Category Distribution", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            cf.expensesByCategory.forEach { (category, amount) ->
                                                ReportLineRow("• $category", "${settings.currency} %.2f".format(amount))
                                            }
                                        }
                                    }
                                }
                            }

                            // Net Cash Summary Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        ReportLineRow("Total Inflow", "${settings.currency} %.2f".format(cf.totalMoneyIn), isBold = true)
                                        ReportLineRow("Total Outflow", "- ${settings.currency} %.2f".format(cf.totalMoneyOut), isBold = true, isNegative = true)
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                        ReportLineRow(
                                            "Net Cash Balance ($filterLabel)",
                                            "${settings.currency} %.2f".format(cf.netCashFlow),
                                            isBold = true,
                                            isHighlight = true
                                        )
                                    }
                                }
                            }

                            item {
                                ExportButtonsRow(viewModel = viewModel, context = context)
                            }
                        }
                    }
                }

                2 -> {
                    // Stocks Valuation Section
                    val stocks = stocksData
                    if (stocks != null) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 70.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Top KPI Cards
                            item {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatMetricCard(
                                        title = "Retail Value",
                                        value = "${settings.currency} %.2f".format(stocks.totalRetailValue),
                                        subtitle = "${stocks.totalUnitsInStock} total units",
                                        icon = Icons.Default.Sell,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = PharmacyTealPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatMetricCard(
                                        title = "Purchase Cost",
                                        value = "${settings.currency} %.2f".format(stocks.totalCostValue),
                                        subtitle = "Batch investment",
                                        icon = Icons.Default.LocalShipping,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = Color(0xFF1976D2),
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatMetricCard(
                                        title = "Unrealized Profit",
                                        value = "${settings.currency} %.2f".format(stocks.potentialProfit),
                                        subtitle = "Margin: %.1f%%".format(stocks.potentialMarginPercent),
                                        icon = Icons.Default.Savings,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = Color(0xFF2E7D32),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Overview Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Stock Inventory Valuation Summary",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        ReportLineRow("Total Catalog Products", "${stocks.totalProductsCount} items")
                                        ReportLineRow("Active In-Stock Batches", "${stocks.totalActiveBatches} batches")
                                        ReportLineRow("Total Stock Physical Units", "${stocks.totalUnitsInStock} units")
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                        ReportLineRow("Inventory Cost Valuation (Purchase Price)", "${settings.currency} %.2f".format(stocks.totalCostValue))
                                        ReportLineRow("Inventory Sale Valuation (Retail Price)", "${settings.currency} %.2f".format(stocks.totalRetailValue), isBold = true)
                                        ReportLineRow("Estimated Gross Profit in Stock", "${settings.currency} %.2f".format(stocks.potentialProfit), isBold = true, isHighlight = true)
                                        ReportLineRow("Projected Profit Margin", "%.2f %%".format(stocks.potentialMarginPercent))
                                    }
                                }
                            }

                            // Category Breakdown Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Stock Value by Category",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                "${stocks.categoryBreakdown.size} Categories",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        stocks.categoryBreakdown.forEach { cat ->
                                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(cat.category, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                                    Text(
                                                        "${settings.currency} %.2f".format(cat.retailValue),
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = PharmacyTealPrimary)
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        "${cat.totalUnits} units in stock",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        "Cost: ${settings.currency} %.2f".format(cat.costValue),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                HorizontalDivider(modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            }
                                        }
                                    }
                                }
                            }

                            // Credit & Supplier balances
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Working Capital & Debt Balances",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        ReportLineRow("Customer Accounts Receivable (Credit Given)", "${settings.currency} %.2f".format(metrics.pendingCustomerDebt))
                                        ReportLineRow("Supplier Accounts Payable (Unpaid Invoices)", "${settings.currency} %.2f".format(metrics.pendingSupplierDebt))
                                    }
                                }
                            }

                            item {
                                ExportButtonsRow(viewModel = viewModel, context = context)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExportButtonsRow(viewModel: MainViewModel, context: android.content.Context) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                val file = viewModel.exportSalesCsv(context)
                PrintUtil.sharePdf(context, file, "Sales Report")
            },
            colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.TableChart, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Export Sales (Excel)")
        }

        OutlinedButton(
            onClick = {
                val file = viewModel.exportExpensesCsv(context)
                PrintUtil.sharePdf(context, file, "Expenses Report")
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Receipt, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Export Expenses")
        }
    }
}

@Composable
fun ReportLineRow(
    title: String,
    value: String,
    isBold: Boolean = false,
    isNegative: Boolean = false,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = if (isBold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodySmall,
            color = if (isHighlight) PharmacyTealPrimary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = if (isBold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodySmall,
            color = if (isHighlight) PharmacyTealPrimary else if (isNegative) Color(0xFFC2185B) else MaterialTheme.colorScheme.onSurface
        )
    }
}
