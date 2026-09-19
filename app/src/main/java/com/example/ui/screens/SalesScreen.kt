package com.example.ui.screens

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Sale
import com.example.data.model.SaleItem
import com.example.ui.MainViewModel
import com.example.ui.components.PharmacyHeader
import com.example.ui.components.SearchInputBar
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.util.PdfGenerator
import com.example.util.PrintUtil
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SalesScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allSales by viewModel.allSales.collectAsState()
    val settings by viewModel.pharmacySettings.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val permissions by viewModel.currentPermissions.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedSale by remember { mutableStateOf<Sale?>(null) }
    var saleItemsForSelected by remember { mutableStateOf<List<SaleItem>>(emptyList()) }
    var showReturnDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    val filteredSales = allSales.filter { s ->
        searchQuery.isBlank() ||
                s.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                s.customerName.contains(searchQuery, ignoreCase = true) ||
                s.cashierName.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        PharmacyHeader(
            title = "Sales Invoices",
            subtitle = "${allSales.size} Invoices Recorded"
        )

        SearchInputBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Search by invoice #, customer, cashier..."
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredSales.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No sales invoices found", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredSales) { sale ->
                    SaleInvoiceCard(
                        sale = sale,
                        currency = settings.currency,
                        onClick = {
                            selectedSale = sale
                            scope.launch {
                                saleItemsForSelected = viewModel.repository.getSaleItems(sale.id)
                            }
                        }
                    )
                }
            }
        }
    }

    // Invoice Detail & Action Dialog
    if (selectedSale != null) {
        Dialog(onDismissRequest = { selectedSale = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(selectedSale!!.invoiceNumber, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(selectedSale!!.saleDate)),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        StatusBadge(
                            text = selectedSale!!.status,
                            containerColor = when (selectedSale!!.status) {
                                "CANCELLED" -> MaterialTheme.colorScheme.errorContainer
                                "PARTIALLY_REFUNDED" -> Color(0xFFFFE0B2)
                                else -> PharmacyTealPrimary.copy(alpha = 0.15f)
                            },
                            contentColor = when (selectedSale!!.status) {
                                "CANCELLED" -> MaterialTheme.colorScheme.error
                                "PARTIALLY_REFUNDED" -> Color(0xFFE65100)
                                else -> PharmacyTealPrimary
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Customer: ${selectedSale!!.customerName} • Cashier: ${selectedSale!!.cashierName}", style = MaterialTheme.typography.bodySmall)
                    Text("Payment: ${selectedSale!!.paymentMethod} • Total: ${settings.currency} %.2f".format(selectedSale!!.grandTotal), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text("Invoice Items:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(saleItemsForSelected) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                                    Text("Batch: ${item.batchNumber} (Exp: ${item.expiryDate})", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    if (item.returnedQuantity > 0) {
                                        Text("Returned: ${item.returnedQuantity} units", style = MaterialTheme.typography.labelSmall, color = Color.Red)
                                    }
                                }
                                Text("${item.quantity} x %.1f = %.1f".format(item.unitPrice, item.subtotal), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Print & Share
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val pdf = PdfGenerator.generateInvoicePdf(context, selectedSale!!, saleItemsForSelected, settings, isThermal = true)
                                PrintUtil.printPdf(context, pdf, "Invoice_${selectedSale!!.invoiceNumber}")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Thermal", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = {
                                val pdf = PdfGenerator.generateInvoicePdf(context, selectedSale!!, saleItemsForSelected, settings, isThermal = false)
                                PrintUtil.sharePdf(context, pdf, "Invoice_${selectedSale!!.invoiceNumber}")
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share PDF", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Return & Cancel Actions
                    if (selectedSale!!.status != "CANCELLED") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showReturnDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Process Return", style = MaterialTheme.typography.labelSmall)
                            }

                            if (permissions.cancelSale || currentUser?.role == "ADMIN") {
                                OutlinedButton(
                                    onClick = { showCancelDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Void / Cancel", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Process Return Dialog
    if (showReturnDialog && selectedSale != null) {
        SalesReturnDialog(
            sale = selectedSale!!,
            items = saleItemsForSelected,
            onDismiss = { showReturnDialog = false },
            onConfirmReturn = { returnedList, reason, refundMethod ->
                showReturnDialog = false
                viewModel.processSalesReturn(selectedSale!!.id, returnedList, reason, refundMethod)
                selectedSale = null
            }
        )
    }

    // Cancel Sale Dialog
    if (showCancelDialog && selectedSale != null) {
        var cancelReason by remember { mutableStateOf("Customer returned or wrong invoice entry") }
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Void & Cancel Sale") },
            text = {
                Column {
                    Text("Cancelling will restore medicine stock into the batches and reverse any customer credit charge.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        label = { Text("Cancellation Reason *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelDialog = false
                        viewModel.cancelSale(selectedSale!!.id, cancelReason)
                        selectedSale = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Cancel")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun SaleInvoiceCard(
    sale: Sale,
    currency: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
                    Text(sale.invoiceNumber, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(sale.saleDate)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                Text(
                    "$currency %.2f".format(sale.grandTotal),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PharmacyTealPrimary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${sale.customerName} • ${sale.paymentMethod}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusBadge(
                    text = sale.status,
                    containerColor = when (sale.status) {
                        "CANCELLED" -> MaterialTheme.colorScheme.errorContainer
                        "PARTIALLY_REFUNDED" -> Color(0xFFFFE0B2)
                        else -> PharmacyTealPrimary.copy(alpha = 0.15f)
                    },
                    contentColor = when (sale.status) {
                        "CANCELLED" -> MaterialTheme.colorScheme.error
                        "PARTIALLY_REFUNDED" -> Color(0xFFE65100)
                        else -> PharmacyTealPrimary
                    }
                )
            }
        }
    }
}

@Composable
fun SalesReturnDialog(
    sale: Sale,
    items: List<SaleItem>,
    onDismiss: () -> Unit,
    onConfirmReturn: (returnedList: List<Pair<SaleItem, Int>>, reason: String, refundMethod: String) -> Unit
) {
    val returnQuantities = remember {
        mutableStateMapOf<Long, Int>().apply {
            items.forEach { this[it.id] = 0 }
        }
    }
    var reason by remember { mutableStateOf("Patient no longer needs medicine") }
    var refundMethod by remember { mutableStateOf("Cash") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Process Return: ${sale.invoiceNumber}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items) { item ->
                        val maxReturnable = item.quantity - item.returnedQuantity
                        val currentReturn = returnQuantities[item.id] ?: 0

                        Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text("Purchased: ${item.quantity} (Returnable: $maxReturnable)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (currentReturn > 0) returnQuantities[item.id] = currentReturn - 1
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Minus", modifier = Modifier.size(16.dp))
                                    }
                                    Text("$currentReturn", modifier = Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
                                    IconButton(
                                        onClick = {
                                            if (currentReturn < maxReturnable) returnQuantities[item.id] = currentReturn + 1
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Plus", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for Return") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val list = items.mapNotNull { item ->
                                val qty = returnQuantities[item.id] ?: 0
                                if (qty > 0) Pair(item, qty) else null
                            }
                            if (list.isNotEmpty()) {
                                onConfirmReturn(list, reason, refundMethod)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary)
                    ) {
                        Text("Confirm Return & Restock")
                    }
                }
            }
        }
    }
}
