package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.ui.MainViewModel
import com.example.ui.components.SearchInputBar
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.util.PdfGenerator
import com.example.util.PrintUtil
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val productsWithStock by viewModel.productsWithStock.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val selectedCustomer by viewModel.selectedCustomer.collectAsState()
    val invoiceDiscount by viewModel.posInvoiceDiscount.collectAsState()
    val settings by viewModel.pharmacySettings.collectAsState()
    val customers by viewModel.allCustomers.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var showBarcodeDialog by remember { mutableStateOf(false) }
    var showCompletedSaleDialog by remember { mutableStateOf(false) }
    var completedSaleRecord by remember { mutableStateOf<Sale?>(null) }
    var completedSaleItems by remember { mutableStateOf<List<SaleItem>>(emptyList()) }

    val categories = listOf("All", "Tablets", "Capsules", "Syrups", "Suspensions", "Creams", "Injections", "Supplements")

    // Filter products
    val filteredProducts = productsWithStock.filter { p ->
        val matchesCategory = selectedCategory == "All" || p.category.equals(selectedCategory, ignoreCase = true)
        val matchesQuery = searchQuery.isBlank() ||
                p.name.contains(searchQuery, ignoreCase = true) ||
                p.genericName.contains(searchQuery, ignoreCase = true) ||
                p.brand.contains(searchQuery, ignoreCase = true) ||
                p.barcode.equals(searchQuery.trim(), ignoreCase = true)
        matchesCategory && matchesQuery
    }

    // Totals
    val subtotal = cart.sumOf { it.subtotal }
    val invoiceDiscountAmt = subtotal * (invoiceDiscount / 100.0)
    val taxAmt = if (settings.taxEnabled) (subtotal - invoiceDiscountAmt) * (settings.defaultTaxPercent / 100.0) else 0.0
    val grandTotal = (subtotal - invoiceDiscountAmt + taxAmt).coerceAtLeast(0.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Search & Barcode Row
        SearchInputBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Scan barcode or search medicine name...",
            onBarcodeScanClick = { showBarcodeDialog = true },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Category Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PharmacyTealPrimary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Split view: Left Catalog & Right Cart
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Medicine Catalog List
            Card(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                if (filteredProducts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No medicines found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredProducts) { item ->
                            ProductCatalogItemRow(
                                product = item,
                                currency = settings.currency,
                                onAddToCart = {
                                    viewModel.addProductToCart(item.toProduct())
                                }
                            )
                        }
                    }
                }
            }

            // POS Cart Panel
            Card(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    // Customer & Cart Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showCustomerPicker = true }
                                .padding(4.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = PharmacyTealPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = selectedCustomer?.name ?: "Walk-in Customer",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }

                        if (cart.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearCart() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    if (selectedCustomer != null && selectedCustomer!!.currentBalance > 0) {
                        Text(
                            text = "Due Balance: ${settings.currency} %.2f (Limit: %.0f)".format(
                                selectedCustomer!!.currentBalance,
                                selectedCustomer!!.creditLimit
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFC2185B)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    // Cart Items List
                    if (cart.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Cart is empty", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Tap medicines on the left to add", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(cart) { index, item ->
                                CartItemCard(
                                    item = item,
                                    currency = settings.currency,
                                    onQtyChange = { newQty -> viewModel.updateCartItemQuantity(index, newQty) },
                                    onRemove = { viewModel.removeCartItem(index) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    // Cart Summary & Checkout
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Subtotal", style = MaterialTheme.typography.bodySmall)
                            Text("${settings.currency} %.2f".format(subtotal), style = MaterialTheme.typography.bodySmall)
                        }

                        if (invoiceDiscountAmt > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Discount ($invoiceDiscount%)", style = MaterialTheme.typography.bodySmall, color = PharmacyTealPrimary)
                                Text("-${settings.currency} %.2f".format(invoiceDiscountAmt), style = MaterialTheme.typography.bodySmall, color = PharmacyTealPrimary)
                            }
                        }

                        if (taxAmt > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tax (${settings.defaultTaxPercent}%)", style = MaterialTheme.typography.bodySmall)
                                Text("+${settings.currency} %.2f".format(taxAmt), style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Grand Total", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                "${settings.currency} %.2f".format(grandTotal),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = PharmacyTealPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { showCheckoutDialog = true },
                            enabled = cart.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Checkout (${cart.sumOf { it.quantity }} items)")
                        }
                    }
                }
            }
        }
    }

    // Checkout Dialog
    if (showCheckoutDialog) {
        CheckoutDialog(
            grandTotal = grandTotal,
            currency = settings.currency,
            selectedCustomer = selectedCustomer,
            onDismiss = { showCheckoutDialog = false },
            onConfirm = { method, paidAmount, notes ->
                showCheckoutDialog = false
                viewModel.completeSale(
                    paymentMethod = method,
                    amountPaidInput = paidAmount,
                    notes = notes
                ) { sale ->
                    completedSaleRecord = sale
                    completedSaleItems = viewModel.lastSaleItems.value
                    showCompletedSaleDialog = true
                }
            }
        )
    }

    // Customer Picker Dialog
    if (showCustomerPicker) {
        CustomerPickerDialog(
            customers = customers,
            selectedCustomer = selectedCustomer,
            onSelect = { cust ->
                viewModel.selectCustomer(cust)
                showCustomerPicker = false
            },
            onDismiss = { showCustomerPicker = false }
        )
    }

    // Barcode Simulator Dialog
    if (showBarcodeDialog) {
        BarcodeScanDialog(
            onBarcodeEntered = { code ->
                showBarcodeDialog = false
                val match = productsWithStock.find { it.barcode.equals(code.trim(), ignoreCase = true) }
                if (match != null) {
                    viewModel.addProductToCart(match.toProduct())
                    viewModel.showMessage("Added ${match.name} via Barcode scan")
                } else {
                    viewModel.showMessage("No medicine found matching barcode '$code'")
                }
            },
            onDismiss = { showBarcodeDialog = false }
        )
    }

    // Completed Sale Dialog
    if (showCompletedSaleDialog && completedSaleRecord != null) {
        CompletedSaleReceiptDialog(
            sale = completedSaleRecord!!,
            items = completedSaleItems,
            settings = settings,
            context = context,
            onDismiss = {
                showCompletedSaleDialog = false
                completedSaleRecord = null
            }
        )
    }
}

@Composable
fun ProductCatalogItemRow(
    product: ProductWithStock,
    currency: String,
    onAddToCart: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAddToCart() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${product.genericName} • ${product.category}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Stock: ${product.totalStock}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (product.totalStock <= product.minStockLevel) MaterialTheme.colorScheme.error else PharmacyTealPrimary
                    )
                    if (product.earliestExpiry != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Exp: ${product.earliestExpiry}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$currency %.1f".format(product.retailPrice),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = PharmacyTealPrimary
                )
                IconButton(
                    onClick = onAddToCart,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(PharmacyTealPrimary.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = PharmacyTealPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun CartItemCard(
    item: CartItem,
    currency: String,
    onQtyChange: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.product.name,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Batch: ${item.batch.batchNumber} (Exp: ${item.batch.expiryDate})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "$currency %.1f".format(item.subtotal),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
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
                    text = "$currency %.1f each".format(item.unitPrice),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onQtyChange(item.quantity - 1) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Minus", modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "${item.quantity}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(
                        onClick = { onQtyChange(item.quantity + 1) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Plus", modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Red, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CheckoutDialog(
    grandTotal: Double,
    currency: String,
    selectedCustomer: Customer?,
    onDismiss: () -> Unit,
    onConfirm: (paymentMethod: String, amountPaid: Double, notes: String) -> Unit
) {
    var paymentMethod by remember { mutableStateOf("Cash") }
    var amountPaidStr by remember { mutableStateOf("%.0f".format(grandTotal)) }
    var notes by remember { mutableStateOf("") }

    val amountPaid = amountPaidStr.toDoubleOrNull() ?: 0.0
    val change = if (amountPaid > grandTotal) (amountPaid - grandTotal) else 0.0
    val balanceDue = if (amountPaid < grandTotal) (grandTotal - amountPaid) else 0.0

    val paymentMethods = listOf("Cash", "Card", "JazzCash", "Easypaisa", "Bank Transfer", "Credit")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Complete Payment",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Total Payable: $currency %.2f".format(grandTotal),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PharmacyTealPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("Payment Method", style = MaterialTheme.typography.labelMedium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    items(paymentMethods) { method ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = {
                                paymentMethod = method
                                if (method == "Credit") amountPaidStr = "0"
                            },
                            label = { Text(method, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PharmacyTealPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                if (paymentMethod != "Credit") {
                    OutlinedTextField(
                        value = amountPaidStr,
                        onValueChange = { amountPaidStr = it },
                        label = { Text("Amount Paid ($currency)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick denomination buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(500, 1000, 5000).forEach { note ->
                            AssistChip(
                                onClick = { amountPaidStr = "$note" },
                                label = { Text("$currency $note", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    if (change > 0) {
                        Surface(
                            color = PharmacyTealPrimary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Return Change: $currency %.2f".format(change),
                                color = PharmacyTealPrimary,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                if (balanceDue > 0) {
                    Text(
                        text = "Remaining Balance Due: $currency %.2f".format(balanceDue),
                        color = Color(0xFFC2185B),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Doctor Prescription Ref") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(paymentMethod, amountPaid, notes) },
                        colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Confirm & Print")
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerPickerDialog(
    customers: List<Customer>,
    selectedCustomer: Customer?,
    onSelect: (Customer?) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = customers.filter { it.name.contains(search, ignoreCase = true) || it.phone.contains(search) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Customer Account", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search customer name or phone...") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Walk-in option
                Surface(
                    onClick = { onSelect(null) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedCustomer == null) PharmacyTealPrimary.copy(alpha = 0.15f) else Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Walk-in Customer (No Credit)", modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered) { c ->
                        Surface(
                            onClick = { onSelect(c) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedCustomer?.id == c.id) PharmacyTealPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(c.name, fontWeight = FontWeight.SemiBold)
                                    Text(c.phone, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                                Text(
                                    text = "Due: %.0f".format(c.currentBalance),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (c.currentBalance > 0) Color(0xFFC2185B) else Color.DarkGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BarcodeScanDialog(
    onBarcodeEntered: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var barcodeInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = PharmacyTealPrimary, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Scan Medicine Barcode", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text("Use camera / scanner or enter barcode digits", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = barcodeInput,
                    onValueChange = { barcodeInput = it },
                    placeholder = { Text("e.g. 896400011221") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Quick sample barcode chips for instant testing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { barcodeInput = "896400011221" },
                        label = { Text("Panadol", style = MaterialTheme.typography.labelSmall) }
                    )
                    AssistChip(
                        onClick = { barcodeInput = "896400011222" },
                        label = { Text("Augmentin", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (barcodeInput.isNotBlank()) onBarcodeEntered(barcodeInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary)
                    ) {
                        Text("Add to Cart")
                    }
                }
            }
        }
    }
}

@Composable
fun CompletedSaleReceiptDialog(
    sale: Sale,
    items: List<SaleItem>,
    settings: PharmacySettings,
    context: Context,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PharmacyTealPrimary, modifier = Modifier.size(54.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Sale Completed Successfully", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text("Invoice: ${sale.invoiceNumber}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(
                    "${settings.currency} %.2f".format(sale.grandTotal),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = PharmacyTealPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Button(
                    onClick = {
                        val pdf = PdfGenerator.generateInvoicePdf(context, sale, items, settings, isThermal = true)
                        PrintUtil.printPdf(context, pdf, "Invoice_${sale.invoiceNumber}")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Print Receipt (Thermal 80mm)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        val pdf = PdfGenerator.generateInvoicePdf(context, sale, items, settings, isThermal = false)
                        PrintUtil.sharePdf(context, pdf, "Invoice ${sale.invoiceNumber}")
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share PDF / WhatsApp")
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onDismiss) {
                    Text("Done / Next Customer")
                }
            }
        }
    }
}
