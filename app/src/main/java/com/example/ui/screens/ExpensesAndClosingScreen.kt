package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.DailyClosing
import com.example.data.model.Expense
import com.example.ui.MainViewModel
import com.example.ui.components.PharmacyHeader
import com.example.ui.components.StatMetricCard
import com.example.ui.theme.PharmacyTealPrimary
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExpensesAndClosingScreen(viewModel: MainViewModel) {
    val expenses by viewModel.allExpenses.collectAsState()
    val closings by viewModel.allDailyClosings.collectAsState()
    val settings by viewModel.pharmacySettings.collectAsState()
    val metrics by viewModel.dashboardMetrics.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Expenses, 1: Daily Shift Closing
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showClosingDialog by remember { mutableStateOf(false) }

    val totalExpenses = expenses.sumOf { it.amount }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showAddExpenseDialog = true },
                    containerColor = PharmacyTealPrimary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Expense")
                }
            } else {
                FloatingActionButton(
                    onClick = { showClosingDialog = true },
                    containerColor = Color(0xFFC2185B),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.LockClock, contentDescription = "Close Shift")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            PharmacyHeader(
                title = if (selectedTab == 0) "Expenses & Overhead" else "Daily Shift Closing",
                subtitle = if (selectedTab == 0) "Total Recorded: ${settings.currency} %.2f".format(totalExpenses) else "Reconcile register cash and drawer balance"
            )

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = PharmacyTealPrimary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Expenses (${expenses.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Shift Closings (${closings.size})") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                // Expenses list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 70.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(expenses) { exp ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(exp.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("${exp.category} • ${exp.paymentMethod}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    if (exp.description.isNotEmpty()) {
                                        Text(exp.description, style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                                    }
                                }
                                Text(
                                    "${settings.currency} %.2f".format(exp.amount),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            } else {
                // Daily closings
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 70.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(closings) { c ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(c.closingDate, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        text = "Variance: ${settings.currency} %.2f".format(c.cashDifference),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (c.cashDifference == 0.0) Color(0xFF2E7D32) else if (c.cashDifference > 0) PharmacyTealPrimary else MaterialTheme.colorScheme.error
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Expected: %.1f | Actual: %.1f (Closed by: ${c.closedBy})".format(c.expectedCash, c.actualCash),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                if (c.notes.isNotEmpty()) {
                                    Text("Notes: ${c.notes}", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddExpenseDialog) {
        AddExpenseDialog(
            onDismiss = { showAddExpenseDialog = false },
            onSave = { expense ->
                showAddExpenseDialog = false
                viewModel.addExpense(expense)
            }
        )
    }

    if (showClosingDialog) {
        DailyClosingDialog(
            todayCashSales = metrics.todayCashSales,
            currency = settings.currency,
            onDismiss = { showClosingDialog = false },
            onSave = { opening, actual, notes ->
                showClosingDialog = false
                viewModel.recordDailyClosing(opening, actual, notes) {}
            }
        )
    }
}

@Composable
fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Electricity") }
    var amountStr by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Cash") }
    var desc by remember { mutableStateOf("") }

    val categories = listOf("Electricity", "Rent", "Staff Salary", "Internet/Phone", "Maintenance", "Transport", "Packaging", "Tea & Refreshments", "Other")

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Record Pharmacy Expense", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Expense Title *") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount (PKR) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text("Category", style = MaterialTheme.typography.labelSmall)
                // Dropdown or text
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = paymentMethod, onValueChange = { paymentMethod = it }, label = { Text("Payment Method (Cash / Bank)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description / Bill Reference") }, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountStr.toDoubleOrNull() ?: 0.0
                            if (title.isNotBlank() && amt > 0) {
                                onSave(Expense(title = title, category = category, amount = amt, paymentMethod = paymentMethod, description = desc))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary)
                    ) {
                        Text("Record Expense")
                    }
                }
            }
        }
    }
}

@Composable
fun DailyClosingDialog(
    todayCashSales: Double,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (opening: Double, actual: Double, notes: String) -> Unit
) {
    var openingCashStr by remember { mutableStateOf("5000") }
    var actualCashStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val opening = openingCashStr.toDoubleOrNull() ?: 0.0
    val actual = actualCashStr.toDoubleOrNull() ?: 0.0
    val expected = opening + todayCashSales
    val diff = actual - expected

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("End of Shift / Daily Closing", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = openingCashStr,
                    onValueChange = { openingCashStr = it },
                    label = { Text("Opening Cash Drawer ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))

                Text("Today's Cash Sales: $currency %.2f".format(todayCashSales), style = MaterialTheme.typography.bodySmall, color = PharmacyTealPrimary)
                Text("Expected Cash in Drawer: $currency %.2f".format(expected), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = actualCashStr,
                    onValueChange = { actualCashStr = it },
                    label = { Text("Actual Cash Counted ($currency) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                if (actualCashStr.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (diff == 0.0) Color(0xFFE8F5E9) else if (diff > 0) Color(0xFFE0F2F1) else Color(0xFFFFEBEE),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (diff == 0.0) "Cash Reconciled Perfectly" else if (diff > 0) "Cash Excess: +$currency %.2f".format(diff) else "Cash Shortage: $currency %.2f".format(diff),
                            color = if (diff >= 0) Color(0xFF2E7D32) else Color(0xFFC2185B),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Closing Notes / Handover") }, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (actualCashStr.isNotBlank()) {
                                onSave(opening, actual, notes)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary)
                    ) {
                        Text("Confirm Shift Closing")
                    }
                }
            }
        }
    }
}
