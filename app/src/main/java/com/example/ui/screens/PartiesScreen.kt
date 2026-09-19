package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import com.example.data.model.Customer
import com.example.data.model.Supplier
import com.example.ui.MainViewModel
import com.example.ui.components.PharmacyHeader
import com.example.ui.components.SearchInputBar
import com.example.ui.theme.PharmacyTealPrimary

@Composable
fun PartiesScreen(viewModel: MainViewModel) {
    val customers by viewModel.allCustomers.collectAsState()
    val suppliers by viewModel.allSuppliers.collectAsState()
    val settings by viewModel.pharmacySettings.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Customers, 1: Suppliers
    var searchQuery by remember { mutableStateOf("") }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var showAddSupplierDialog by remember { mutableStateOf(false) }
    var payingCustomer by remember { mutableStateOf<Customer?>(null) }
    var payingSupplier by remember { mutableStateOf<Supplier?>(null) }

    val filteredCustomers = customers.filter { c ->
        searchQuery.isBlank() || c.name.contains(searchQuery, ignoreCase = true) || c.phone.contains(searchQuery)
    }

    val filteredSuppliers = suppliers.filter { s ->
        searchQuery.isBlank() || s.name.contains(searchQuery, ignoreCase = true) || s.company.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) showAddCustomerDialog = true else showAddSupplierDialog = true
                },
                containerColor = PharmacyTealPrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add")
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
                title = if (selectedTab == 0) "Customer Accounts & Credit" else "Suppliers & Distributors",
                subtitle = "Manage ledger balances and contact directories"
            )

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = PharmacyTealPrimary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Customers (${customers.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Suppliers (${suppliers.size})") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            SearchInputBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = if (selectedTab == 0) "Search customer by name or phone..." else "Search supplier or company..."
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedTab == 0) {
                // Customers
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 70.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCustomers) { c ->
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
                                    Text(c.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("Phone: ${c.phone.ifEmpty { "N/A" }}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    Text("Credit Limit: ${settings.currency} %.0f".format(c.creditLimit), style = MaterialTheme.typography.labelSmall)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Due: ${settings.currency} %.2f".format(c.currentBalance),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (c.currentBalance > 0) Color(0xFFC2185B) else Color(0xFF2E7D32)
                                    )
                                    if (c.currentBalance > 0) {
                                        Button(
                                            onClick = { payingCustomer = c },
                                            colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Text("Receive Payment", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Suppliers
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 70.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSuppliers) { s ->
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
                                    Text(s.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text(s.company, style = MaterialTheme.typography.bodySmall, color = PharmacyTealPrimary)
                                    Text("Phone: ${s.phone.ifEmpty { "N/A" }}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Payable: ${settings.currency} %.2f".format(s.currentBalance),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (s.currentBalance > 0) Color(0xFFC2185B) else Color(0xFF2E7D32)
                                    )
                                    if (s.currentBalance > 0) {
                                        Button(
                                            onClick = { payingSupplier = s },
                                            colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Text("Pay Supplier", style = MaterialTheme.typography.labelSmall)
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

    // Add Customer Dialog
    if (showAddCustomerDialog) {
        AddCustomerDialog(
            onDismiss = { showAddCustomerDialog = false },
            onSave = { cust ->
                showAddCustomerDialog = false
                viewModel.saveCustomer(cust)
            }
        )
    }

    // Add Supplier Dialog
    if (showAddSupplierDialog) {
        AddSupplierDialog(
            onDismiss = { showAddSupplierDialog = false },
            onSave = { sup ->
                showAddSupplierDialog = false
                viewModel.saveSupplier(sup)
            }
        )
    }

    // Pay Customer/Supplier Dialog
    if (payingCustomer != null) {
        PaymentRecordDialog(
            title = "Receive Debt Payment from ${payingCustomer!!.name}",
            dueAmount = payingCustomer!!.currentBalance,
            currency = settings.currency,
            onDismiss = { payingCustomer = null },
            onConfirm = { amount, method, ref, notes ->
                viewModel.recordPartyPayment("CUSTOMER", payingCustomer!!.id, payingCustomer!!.name, amount, method, ref, notes)
                payingCustomer = null
            }
        )
    }

    if (payingSupplier != null) {
        PaymentRecordDialog(
            title = "Pay Due Balance to ${payingSupplier!!.name}",
            dueAmount = payingSupplier!!.currentBalance,
            currency = settings.currency,
            onDismiss = { payingSupplier = null },
            onConfirm = { amount, method, ref, notes ->
                viewModel.recordPartyPayment("SUPPLIER", payingSupplier!!.id, payingSupplier!!.name, amount, method, ref, notes)
                payingSupplier = null
            }
        )
    }
}

@Composable
fun AddCustomerDialog(
    onDismiss: () -> Unit,
    onSave: (Customer) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var limitStr by remember { mutableStateOf("20000") }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Add Registered Customer", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name *") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = limitStr,
                    onValueChange = { limitStr = it },
                    label = { Text("Credit Limit (PKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(Customer(name = name, phone = phone, address = address, creditLimit = limitStr.toDoubleOrNull() ?: 20000.0))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary)
                    ) {
                        Text("Save Customer")
                    }
                }
            }
        }
    }
}

@Composable
fun AddSupplierDialog(
    onDismiss: () -> Unit,
    onSave: (Supplier) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Add Medicine Supplier / Distributor", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Contact Person Name *") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("Company / Agency Name *") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(Supplier(name = name, company = company, phone = phone))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary)
                    ) {
                        Text("Save Supplier")
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentRecordDialog(
    title: String,
    dueAmount: Double,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, method: String, ref: String, notes: String) -> Unit
) {
    var amountStr by remember { mutableStateOf("%.0f".format(dueAmount)) }
    var method by remember { mutableStateOf("Cash") }
    var ref by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text("Current Due: $currency %.2f".format(dueAmount), style = MaterialTheme.typography.bodySmall, color = Color(0xFFC2185B))
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount Paid ($currency) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = method, onValueChange = { method = it }, label = { Text("Payment Method (Cash / Bank / JazzCash)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = ref, onValueChange = { ref = it }, label = { Text("Transaction Reference #") }, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountStr.toDoubleOrNull() ?: 0.0
                            if (amt > 0) onConfirm(amt, method, ref, notes)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary)
                    ) {
                        Text("Record Payment")
                    }
                }
            }
        }
    }
}
