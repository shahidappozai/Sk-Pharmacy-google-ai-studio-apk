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
import com.example.data.model.*
import com.example.ui.MainViewModel
import com.example.ui.components.PharmacyHeader
import com.example.ui.components.SearchInputBar
import com.example.ui.components.StatusBadge
import com.example.ui.theme.PharmacyTealPrimary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(viewModel: MainViewModel) {
    val purchases by viewModel.allPurchases.collectAsState()
    val suppliers by viewModel.allSuppliers.collectAsState()
    val products by viewModel.productsWithStock.collectAsState()
    val settings by viewModel.pharmacySettings.collectAsState()
    val permissions by viewModel.currentPermissions.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showNewPurchaseDialog by remember { mutableStateOf(false) }
    var selectedPurchase by remember { mutableStateOf<Purchase?>(null) }
    var purchaseItems by remember { mutableStateOf<List<PurchaseItem>>(emptyList()) }

    val filteredPurchases = purchases.filter { p ->
        searchQuery.isBlank() ||
                p.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                p.supplierName.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        floatingActionButton = {
            if (permissions.createPurchases || currentUser?.role == "ADMIN") {
                FloatingActionButton(
                    onClick = { showNewPurchaseDialog = true },
                    containerColor = PharmacyTealPrimary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = "New Purchase")
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
                title = "Purchases & Orders",
                subtitle = "${purchases.size} Purchase Invoices from Distributors"
            )

            SearchInputBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search supplier, invoice #..."
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredPurchases.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No purchase records found", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 70.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPurchases) { p ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPurchase = p
                                    // load items
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(p.supplierName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        Text("Invoice #: ${p.invoiceNumber}", style = MaterialTheme.typography.labelSmall, color = PharmacyTealPrimary)
                                    }
                                    Text(
                                        "${settings.currency} %.2f".format(p.grandTotal),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = PharmacyTealPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(p.purchaseDate)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                    if (p.balanceDue > 0) {
                                        Text("Due: ${settings.currency} %.2f".format(p.balanceDue), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFC2185B))
                                    } else {
                                        StatusBadge(text = "PAID", containerColor = PharmacyTealPrimary.copy(alpha = 0.15f), contentColor = PharmacyTealPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewPurchaseDialog) {
        NewPurchaseOrderDialog(
            suppliers = suppliers,
            products = products,
            onDismiss = { showNewPurchaseDialog = false },
            onSave = { purchase, items ->
                showNewPurchaseDialog = false
                viewModel.recordPurchase(purchase, items)
            }
        )
    }
}

@Composable
fun NewPurchaseOrderDialog(
    suppliers: List<Supplier>,
    products: List<ProductWithStock>,
    onDismiss: () -> Unit,
    onSave: (Purchase, List<PurchaseItem>) -> Unit
) {
    var selectedSupplier by remember { mutableStateOf(suppliers.firstOrNull()) }
    var invoiceNumber by remember { mutableStateOf("PO-${System.currentTimeMillis() % 100000}") }
    var amountPaidStr by remember { mutableStateOf("0") }

    val items = remember { mutableStateListOf<PurchaseItem>() }

    var selectedProduct by remember { mutableStateOf(products.firstOrNull()) }
    var batchNumber by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("2027-12-31") }
    var purchasePriceStr by remember { mutableStateOf("") }
    var retailPriceStr by remember { mutableStateOf("") }
    var qtyStr by remember { mutableStateOf("10") }

    val subtotal = items.sumOf { it.total }
    val grandTotal = subtotal
    val amountPaid = amountPaidStr.toDoubleOrNull() ?: 0.0
    val balanceDue = (grandTotal - amountPaid).coerceAtLeast(0.0)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Record Purchase Order", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = invoiceNumber,
                        onValueChange = { invoiceNumber = it },
                        label = { Text("Distributor Invoice #") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Line item entry section
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Add Item to Purchase", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(4.dp))

                        Text("Medicine: ${selectedProduct?.name ?: "None"}", style = MaterialTheme.typography.bodySmall)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = batchNumber,
                                onValueChange = { batchNumber = it },
                                label = { Text("Batch #") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = expiryDate,
                                onValueChange = { expiryDate = it },
                                label = { Text("Exp (YYYY-MM-DD)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = purchasePriceStr,
                                onValueChange = { purchasePriceStr = it },
                                label = { Text("Cost") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = qtyStr,
                                onValueChange = { qtyStr = it },
                                label = { Text("Qty") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val p = selectedProduct
                                    val cost = purchasePriceStr.toDoubleOrNull() ?: 0.0
                                    val q = qtyStr.toIntOrNull() ?: 0
                                    if (p != null && batchNumber.isNotBlank() && q > 0) {
                                        items.add(
                                            PurchaseItem(
                                                purchaseId = 0,
                                                productId = p.id,
                                                productName = p.name,
                                                batchNumber = batchNumber,
                                                manufacturingDate = "2025-01-01",
                                                expiryDate = expiryDate,
                                                purchasePrice = cost,
                                                retailPrice = p.retailPrice,
                                                quantity = q,
                                                total = cost * q
                                            )
                                        )
                                        batchNumber = ""
                                        purchasePriceStr = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Add")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Items list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${item.productName} (${item.quantity} x ${item.purchasePrice})", style = MaterialTheme.typography.bodySmall)
                            Text("%.2f".format(item.total), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                Text("Total: PKR %.2f | Due: PKR %.2f".format(grandTotal, balanceDue), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val sup = selectedSupplier ?: suppliers.firstOrNull()
                            if (sup != null && items.isNotEmpty()) {
                                val purchase = Purchase(
                                    supplierId = sup.id,
                                    supplierName = sup.name,
                                    invoiceNumber = invoiceNumber,
                                    purchaseDate = System.currentTimeMillis(),
                                    subtotal = subtotal,
                                    grandTotal = grandTotal,
                                    paidAmount = amountPaid,
                                    balanceDue = balanceDue,
                                    status = if (balanceDue <= 0) "RECEIVED_PAID" else "RECEIVED_CREDIT",
                                    createdBy = "Admin"
                                )
                                onSave(purchase, items.toList())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary)
                    ) {
                        Text("Save & Add Stock")
                    }
                }
            }
        }
    }
}
