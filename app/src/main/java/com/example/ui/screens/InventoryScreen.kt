package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.ui.MainViewModel
import com.example.ui.components.PharmacyHeader
import com.example.ui.components.SearchInputBar
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.util.AiProductPredictor
import com.example.util.PredictedProductInfo
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

fun calculateAlertDate(expiryDateStr: String, daysBefore: Int): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(expiryDateStr) ?: return ""
        val cal = Calendar.getInstance()
        cal.time = date
        cal.add(Calendar.DAY_OF_YEAR, -daysBefore)
        sdf.format(cal.time)
    } catch (_: Exception) {
        ""
    }
}

fun getFutureDateString(monthsAhead: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MONTH, monthsAhead)
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
}

fun getTodayDateString(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(viewModel: MainViewModel) {
    val products by viewModel.productsWithStock.collectAsState()
    val batches by viewModel.allBatches.collectAsState()
    val adjustments by viewModel.allStockAdjustments.collectAsState()
    val settings by viewModel.pharmacySettings.collectAsState()
    val permissions by viewModel.currentPermissions.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Medicines, 1: Multi-Batch View, 2: Stock Adjustments
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    var showAddMedicineDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var adjustingBatch by remember { mutableStateOf<BatchWithProduct?>(null) }
    var addingBatchForProduct by remember { mutableStateOf<ProductWithStock?>(null) }

    val categories = listOf("All", "Tablets", "Capsules", "Syrups", "Suspensions", "Creams", "Injections", "Supplements")

    val filteredProducts = products.filter { p ->
        val matchesCat = selectedCategory == "All" || p.category.equals(selectedCategory, ignoreCase = true)
        val matchesQuery = searchQuery.isBlank() ||
                p.name.contains(searchQuery, ignoreCase = true) ||
                p.genericName.contains(searchQuery, ignoreCase = true) ||
                p.brand.contains(searchQuery, ignoreCase = true) ||
                p.rackLocation.contains(searchQuery, ignoreCase = true)
        matchesCat && matchesQuery
    }

    val filteredBatches = batches.filter { b ->
        searchQuery.isBlank() ||
                b.productName.contains(searchQuery, ignoreCase = true) ||
                b.batchNumber.contains(searchQuery, ignoreCase = true) ||
                b.genericName.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0 && (permissions.addProducts || currentUser?.role == "ADMIN")) {
                FloatingActionButton(
                    onClick = {
                        editingProduct = null
                        showAddMedicineDialog = true
                    },
                    containerColor = PharmacyTealPrimary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Medicine")
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
                title = "Inventory & Medicines",
                subtitle = "${products.size} Active Products in Stock"
            )

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = PharmacyTealPrimary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Medicines", style = MaterialTheme.typography.labelMedium) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Batches (${batches.size})", style = MaterialTheme.typography.labelMedium) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Adjustments", style = MaterialTheme.typography.labelMedium) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            SearchInputBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = if (selectedTab == 1) "Search batch number or medicine..." else "Search medicine, generic, brand, rack..."
            )

            if (selectedTab == 0) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                0 -> {
                    // Medicines List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 70.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredProducts) { item ->
                            MedicineCard(
                                product = item,
                                currency = settings.currency,
                                onEdit = {
                                    editingProduct = item.toProduct()
                                    showAddMedicineDialog = true
                                },
                                onAddBatch = {
                                    addingBatchForProduct = item
                                }
                            )
                        }
                    }
                }
                1 -> {
                    // Multi-Batch View
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 70.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredBatches) { batch ->
                            BatchCard(
                                batch = batch,
                                currency = settings.currency,
                                onAdjustStock = { adjustingBatch = batch }
                            )
                        }
                    }
                }
                2 -> {
                    // Stock Adjustments History
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 70.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(adjustments) { adj ->
                            AdjustmentHistoryCard(adjustment = adj)
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Medicine Dialog
    if (showAddMedicineDialog) {
        AddEditMedicineDialog(
            existing = editingProduct,
            viewModel = viewModel,
            onDismiss = {
                showAddMedicineDialog = false
                viewModel.clearPredictedProduct()
            },
            onSave = { product, initialBatch ->
                showAddMedicineDialog = false
                viewModel.clearPredictedProduct()
                if (initialBatch != null) {
                    viewModel.saveProductWithInitialBatch(product, initialBatch)
                } else {
                    viewModel.saveProduct(product)
                }
            },
            onDelete = { id ->
                showAddMedicineDialog = false
                viewModel.clearPredictedProduct()
                viewModel.deleteProduct(id)
            }
        )
    }

    // Add Batch Dialog
    if (addingBatchForProduct != null) {
        AddBatchDialog(
            product = addingBatchForProduct!!,
            onDismiss = { addingBatchForProduct = null },
            onSave = { batch ->
                addingBatchForProduct = null
                viewModel.saveBatch(batch)
            }
        )
    }

    // Stock Adjustment Dialog
    if (adjustingBatch != null) {
        val targetBatch = adjustingBatch!!
        StockAdjustmentDialog(
            batch = targetBatch,
            onDismiss = { adjustingBatch = null },
            onConfirm = { qty, reason ->
                adjustingBatch = null
                viewModel.adjustStock(targetBatch.batchId, qty, reason)
            }
        )
    }
}

@Composable
fun MedicineCard(
    product: ProductWithStock,
    currency: String,
    onEdit: () -> Unit,
    onAddBatch: () -> Unit
) {
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
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (product.imageUri.isNotBlank()) {
                        AsyncImage(
                            model = File(product.imageUri),
                            contentDescription = product.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PharmacyTealPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Medication,
                                contentDescription = null,
                                tint = PharmacyTealPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Column {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${product.genericName} • ${product.category} (${product.dosageForm})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = "$currency %.1f".format(product.retailPrice),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PharmacyTealPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expiry & Alert info chip row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(
                    text = "Stock: ${product.totalStock}",
                    containerColor = if (product.totalStock <= product.minStockLevel) MaterialTheme.colorScheme.errorContainer else PharmacyTealPrimary.copy(alpha = 0.15f),
                    contentColor = if (product.totalStock <= product.minStockLevel) MaterialTheme.colorScheme.error else PharmacyTealPrimary
                )

                if (!product.earliestExpiry.isNullOrBlank()) {
                    StatusBadge(
                        text = "Exp: ${product.earliestExpiry}",
                        containerColor = Color(0xFFFFF3E0),
                        contentColor = Color(0xFFE65100)
                    )
                }

                if (!product.earliestAlertDate.isNullOrBlank()) {
                    StatusBadge(
                        text = "Alert: ${product.earliestAlertDate}",
                        containerColor = Color(0xFFEDE7F6),
                        contentColor = Color(0xFF512DA8)
                    )
                }

                if (product.rackLocation.isNotEmpty()) {
                    Text("Rack: ${product.rackLocation}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onAddBatch) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Batch", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun BatchCard(
    batch: BatchWithProduct,
    currency: String,
    onAdjustStock: () -> Unit
) {
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
                Text(batch.productName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                Text("Batch #: ${batch.batchNumber}", style = MaterialTheme.typography.labelMedium, color = PharmacyTealPrimary)
                Text("Exp: ${batch.expiryDate} | Cost: $currency %.1f".format(batch.purchasePrice), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                if (batch.alertDate.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Alert Date: ${batch.alertDate}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Stock: ${batch.currentStock}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                TextButton(onClick = onAdjustStock) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Adjust", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun AdjustmentHistoryCard(adjustment: StockAdjustment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(adjustment.productName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                val sign = if (adjustment.adjustedQuantity >= 0) "+${adjustment.adjustedQuantity}" else "${adjustment.adjustedQuantity}"
                Text(
                    text = sign,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (adjustment.adjustedQuantity >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
            }
            Text("Batch: ${adjustment.batchNumber} | Reason: ${adjustment.reason}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("By: ${adjustment.adjustedBy} • New Stock: ${adjustment.newStock}", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
        }
    }
}

@Composable
fun AddEditMedicineDialog(
    existing: Product?,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSave: (Product, Batch?) -> Unit,
    onDelete: (Long) -> Unit
) {
    val context = LocalContext.current
    val predictedProduct by viewModel.predictedProduct.collectAsState()
    val isAiPredicting by viewModel.isAiPredicting.collectAsState()

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var genericName by remember { mutableStateOf(existing?.genericName ?: "") }
    var brand by remember { mutableStateOf(existing?.brand ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "Tablets") }
    var dosageForm by remember { mutableStateOf(existing?.dosageForm ?: "Tablet") }
    var strength by remember { mutableStateOf(existing?.strength ?: "") }
    var packSize by remember { mutableStateOf(existing?.packSize ?: "") }
    var manufacturer by remember { mutableStateOf(existing?.manufacturer ?: "") }
    var retailPriceStr by remember { mutableStateOf(existing?.retailPrice?.let { if (it > 0) it.toString() else "" } ?: "") }
    var mrpStr by remember { mutableStateOf(existing?.mrp?.let { if (it > 0) it.toString() else "" } ?: "") }
    var minStockStr by remember { mutableStateOf(existing?.minStockLevel?.toString() ?: "10") }
    var rackLocation by remember { mutableStateOf(existing?.rackLocation ?: "") }
    var barcode by remember { mutableStateOf(existing?.barcode ?: "") }
    var expiryAlertDays by remember { mutableStateOf(existing?.expiryAlertDays ?: 90) }
    var imageUriString by remember { mutableStateOf(existing?.imageUri ?: "") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Expiry & FEFO Batch Section States
    var addInitialBatch by remember { mutableStateOf(existing == null) }
    var batchNumber by remember {
        mutableStateOf("B-" + SimpleDateFormat("yyMM", Locale.getDefault()).format(Date()) + "-" + (100..999).random())
    }
    var mfgDate by remember { mutableStateOf(getTodayDateString()) }
    var expiryDate by remember { mutableStateOf(getFutureDateString(24)) }
    var initialStockStr by remember { mutableStateOf("50") }
    var purchasePriceStr by remember { mutableStateOf("") }

    // Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            previewBitmap = bitmap
            try {
                val file = File(context.filesDir, "med_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                imageUriString = file.absolutePath
            } catch (_: Exception) {}
            viewModel.predictProductFromImage(bitmap)
        }
    }

    // Photo Picker Launcher (Zero-permission)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                if (bitmap != null) {
                    previewBitmap = bitmap
                    val file = File(context.filesDir, "med_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    imageUriString = file.absolutePath
                    viewModel.predictProductFromImage(bitmap)
                }
            } catch (_: Exception) {}
        }
    }

    // Offline predictive instant suggestions while typing name
    val instantMatches = remember(name) {
        if (name.length >= 2) {
            AiProductPredictor.OFFLINE_MEDICINE_ENCYCLOPEDIA.filter {
                it.productName.contains(name, ignoreCase = true) ||
                it.genericName.contains(name, ignoreCase = true)
            }.take(4)
        } else {
            emptyList<PredictedProductInfo>()
        }
    }

    fun applyPrediction(info: PredictedProductInfo) {
        name = info.productName
        if (info.genericName.isNotBlank()) genericName = info.genericName
        if (info.category.isNotBlank()) category = info.category
        if (info.dosageForm.isNotBlank()) dosageForm = info.dosageForm
        if (info.strength.isNotBlank()) strength = info.strength
        if (info.packSize.isNotBlank()) packSize = info.packSize
        if (info.manufacturer.isNotBlank()) manufacturer = info.manufacturer
        if (info.suggestedRetailPrice > 0) {
            retailPriceStr = "%.1f".format(info.suggestedRetailPrice)
            mrpStr = "%.1f".format(info.suggestedRetailPrice * 1.05)
            if (purchasePriceStr.isBlank()) {
                purchasePriceStr = "%.1f".format(info.suggestedRetailPrice * 0.8)
            }
        }
        if (info.detectedBatchNumber.isNotBlank()) batchNumber = info.detectedBatchNumber
        if (info.detectedExpiryDate.isNotBlank()) expiryDate = info.detectedExpiryDate
        if (info.expiryAlertDays > 0) expiryAlertDays = info.expiryAlertDays
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
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
                            text = if (existing == null) "Add Medicine" else "Edit Medicine",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = PharmacyNavyDark
                        )
                        Text(
                            text = "AI-assisted medicine entry with FEFO expiry tracking",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // --- AI PREDICTION & IMAGE SECTION ---
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = PharmacyTealPrimary.copy(alpha = 0.08f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PharmacyTealPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = PharmacyTealPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "AI Product Scanner & Predictor",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = PharmacyTealPrimary
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Camera
                                        FilledTonalIconButton(
                                            onClick = { cameraLauncher.launch(null) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.PhotoCamera, contentDescription = "Scan with Camera", modifier = Modifier.size(18.dp))
                                        }
                                        // Photo Gallery
                                        FilledTonalIconButton(
                                            onClick = {
                                                galleryLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Upload Image", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                Text(
                                    text = "Scan medicine box/blister or type name to auto-predict formulation, price & expiry",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 2.dp)
                                )

                                // Image thumbnail if present
                                if (previewBitmap != null || imageUriString.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (previewBitmap != null) {
                                            Image(
                                                bitmap = previewBitmap!!.asImageBitmap(),
                                                contentDescription = "Medicine Image",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                            )
                                        } else {
                                            AsyncImage(
                                                model = File(imageUriString),
                                                contentDescription = "Medicine Image",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Medicine Image Attached", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                            Text("Used for AI detection & product catalog", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        }
                                        IconButton(
                                            onClick = {
                                                previewBitmap = null
                                                imageUriString = ""
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove Image", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }

                                // AI Loading Indicator
                                if (isAiPredicting) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = PharmacyTealPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Gemini AI analyzing medicine packaging...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PharmacyTealPrimary
                                        )
                                    }
                                }

                                // AI Prediction Result Card
                                if (predictedProduct != null) {
                                    val pred = predictedProduct!!
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "AI Suggested: ${pred.productName}",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color(0xFF1B5E20)
                                                )
                                                StatusBadge(
                                                    text = if (pred.confidenceNotes.isNotBlank()) pred.confidenceNotes else "AI Match",
                                                    containerColor = Color(0xFFC8E6C9),
                                                    contentColor = Color(0xFF2E7D32)
                                                )
                                            }
                                            Text(
                                                text = "${pred.genericName} • ${pred.strength} • ${pred.packSize} • ${pred.manufacturer}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF388E3C)
                                            )
                                            if (pred.suggestedRetailPrice > 0) {
                                                val batchExp = listOfNotNull(
                                                    pred.detectedBatchNumber.takeIf { it.isNotBlank() }?.let { "Batch: $it" },
                                                    pred.detectedExpiryDate.takeIf { it.isNotBlank() }?.let { "Exp: $it" }
                                                ).joinToString(" | ")
                                                Text(
                                                    text = "Suggested Retail: PKR %.1f ${if (batchExp.isNotBlank()) " | $batchExp" else ""}".format(pred.suggestedRetailPrice),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF2E7D32)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Button(
                                                onClick = { applyPrediction(pred) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Apply AI Suggestions to Form", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }

                                // Instant typeahead prediction chips
                                if (instantMatches.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Quick AI Match:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        for (match in instantMatches) {
                                            AssistChip(
                                                onClick = { applyPrediction(match) },
                                                label = { Text(match.productName, style = MaterialTheme.typography.labelSmall) },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Medication, contentDescription = null, modifier = Modifier.size(14.dp))
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- BASIC MEDICINE INFO ---
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Brand / Medicine Name *") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    if (name.isNotBlank()) viewModel.predictProductFromQuery(name)
                                }
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search AI", tint = PharmacyTealPrimary)
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = genericName,
                            onValueChange = { genericName = it },
                            label = { Text("Generic Name / Salt Formulation") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = category,
                                onValueChange = { category = it },
                                label = { Text("Category") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = dosageForm,
                                onValueChange = { dosageForm = it },
                                label = { Text("Dosage Form") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = strength,
                                onValueChange = { strength = it },
                                label = { Text("Strength (e.g. 500mg)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = packSize,
                                onValueChange = { packSize = it },
                                label = { Text("Pack Size (e.g. 10x10)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = manufacturer,
                            onValueChange = { manufacturer = it },
                            label = { Text("Pharma Manufacturer") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = retailPriceStr,
                                onValueChange = { retailPriceStr = it },
                                label = { Text("Retail Price (PKR) *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = mrpStr,
                                onValueChange = { mrpStr = it },
                                label = { Text("MRP (PKR)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = minStockStr,
                                onValueChange = { minStockStr = it },
                                label = { Text("Min Stock Alert") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = rackLocation,
                                onValueChange = { rackLocation = it },
                                label = { Text("Rack / Shelf Location") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = barcode,
                            onValueChange = { barcode = it },
                            label = { Text("Barcode Digits / SKU") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // --- SECTION OF EXPIRY & DATE OF EXPIRY ALERT ---
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.HourglassTop,
                                            contentDescription = null,
                                            tint = PharmacyTealPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "EXPIRY & DATE OF ALERT SECTION",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "FEFO multi-batch tracking & early warning alert",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (existing != null) {
                                        Switch(
                                            checked = addInitialBatch,
                                            onCheckedChange = { addInitialBatch = it }
                                        )
                                    }
                                }

                                if (addInitialBatch) {
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Batch Number & Mfg Date
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = batchNumber,
                                            onValueChange = { batchNumber = it },
                                            label = { Text("Batch Number *") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1.2f)
                                        )
                                        OutlinedTextField(
                                            value = mfgDate,
                                            onValueChange = { mfgDate = it },
                                            label = { Text("Mfg Date") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Expiry Date
                                    OutlinedTextField(
                                        value = expiryDate,
                                        onValueChange = { expiryDate = it },
                                        label = { Text("Expiry Date (YYYY-MM-DD) *") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Quick Expiry Presets
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Quick Expiry:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        for ((label, months) in listOf("+6 Mos" to 6, "+1 Year" to 12, "+2 Years" to 24, "+3 Years" to 36)) {
                                            AssistChip(
                                                onClick = { expiryDate = getFutureDateString(months) },
                                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Expiry Alert Timing (Days before)
                                    Text(
                                        text = "Expiry Alert Window (Days Before Expiry):",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        for (days in listOf(30, 60, 90, 180)) {
                                            FilterChip(
                                                selected = expiryAlertDays == days,
                                                onClick = { expiryAlertDays = days },
                                                label = {
                                                    Text(
                                                        if (days == 90) "90 Days (Rec.)" else "$days Days",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    // Calculated Date of Expiry Alert Display
                                    val alertDate = remember(expiryDate, expiryAlertDays) {
                                        calculateAlertDate(expiryDate, expiryAlertDays)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.NotificationsActive,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "Date of Expiry Alert: ${if (alertDate.isNotBlank()) alertDate else "Set valid expiry date"}",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "SK Pharmacy will trigger automatic alert notifications on this date ($expiryAlertDays days before expiry) to prevent financial loss.",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Opening stock and purchase price
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = initialStockStr,
                                            onValueChange = { initialStockStr = it },
                                            label = { Text("Initial Stock Units *") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = purchasePriceStr,
                                            onValueChange = { purchasePriceStr = it },
                                            label = { Text("Purchase Cost (PKR)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (existing != null) {
                        TextButton(
                            onClick = { onDelete(existing.id) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Archive")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    val rPrice = retailPriceStr.toDoubleOrNull() ?: 0.0
                                    val productToSave = (existing ?: Product(name = name)).copy(
                                        name = name.trim(),
                                        genericName = genericName.trim(),
                                        brand = brand.trim(),
                                        category = category.trim(),
                                        dosageForm = dosageForm.trim(),
                                        strength = strength.trim(),
                                        packSize = packSize.trim(),
                                        manufacturer = manufacturer.trim(),
                                        retailPrice = rPrice,
                                        mrp = mrpStr.toDoubleOrNull() ?: rPrice,
                                        minStockLevel = minStockStr.toIntOrNull() ?: 10,
                                        rackLocation = rackLocation.trim(),
                                        barcode = barcode.trim(),
                                        expiryAlertDays = expiryAlertDays,
                                        imageUri = imageUriString
                                    )

                                    val batchToSave = if (addInitialBatch && batchNumber.isNotBlank() && expiryDate.isNotBlank()) {
                                        val calAlert = calculateAlertDate(expiryDate, expiryAlertDays)
                                        val pCost = purchasePriceStr.toDoubleOrNull() ?: (rPrice * 0.8)
                                        Batch(
                                            productId = existing?.id ?: 0L,
                                            batchNumber = batchNumber.trim(),
                                            manufacturingDate = mfgDate.trim(),
                                            expiryDate = expiryDate.trim(),
                                            purchasePrice = pCost,
                                            retailPrice = rPrice,
                                            currentStock = initialStockStr.toIntOrNull() ?: 0,
                                            alertDate = calAlert
                                        )
                                    } else {
                                        null
                                    }

                                    onSave(productToSave, batchToSave)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary)
                        ) {
                            Text(if (existing == null) "Save Medicine & Batch" else "Update Medicine")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddBatchDialog(
    product: ProductWithStock,
    onDismiss: () -> Unit,
    onSave: (Batch) -> Unit
) {
    var batchNumber by remember { mutableStateOf("") }
    var mfgDate by remember { mutableStateOf(getTodayDateString()) }
    var expiryDate by remember { mutableStateOf(getFutureDateString(24)) }
    var expiryAlertDays by remember { mutableStateOf(product.expiryAlertDays) }
    var purchasePriceStr by remember { mutableStateOf("") }
    var retailPriceStr by remember { mutableStateOf("${product.retailPrice}") }
    var initialStockStr by remember { mutableStateOf("20") }

    val alertDate = remember(expiryDate, expiryAlertDays) {
        calculateAlertDate(expiryDate, expiryAlertDays)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Add Batch for ${product.name}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = batchNumber,
                    onValueChange = { batchNumber = it },
                    label = { Text("Batch Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mfgDate,
                        onValueChange = { mfgDate = it },
                        label = { Text("Mfg Date") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = expiryDate,
                        onValueChange = { expiryDate = it },
                        label = { Text("Expiry (YYYY-MM-DD) *") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quick Expiry Presets
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for ((label, months) in listOf("+6 Mos" to 6, "+1 Year" to 12, "+2 Years" to 24, "+3 Years" to 36)) {
                        AssistChip(
                            onClick = { expiryDate = getFutureDateString(months) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Date of Expiry Alert notification card
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Date of Expiry Alert: ${if (alertDate.isNotBlank()) alertDate else "N/A"} ($expiryAlertDays days before expiry)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = purchasePriceStr,
                        onValueChange = { purchasePriceStr = it },
                        label = { Text("Cost (PKR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = retailPriceStr,
                        onValueChange = { retailPriceStr = it },
                        label = { Text("Retail (PKR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = initialStockStr,
                    onValueChange = { initialStockStr = it },
                    label = { Text("Stock Quantity (Units) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (batchNumber.isNotBlank() && expiryDate.isNotBlank()) {
                                onSave(
                                    Batch(
                                        productId = product.id,
                                        batchNumber = batchNumber.trim(),
                                        manufacturingDate = mfgDate.trim(),
                                        expiryDate = expiryDate.trim(),
                                        purchasePrice = purchasePriceStr.toDoubleOrNull() ?: (product.retailPrice * 0.8),
                                        retailPrice = retailPriceStr.toDoubleOrNull() ?: product.retailPrice,
                                        currentStock = initialStockStr.toIntOrNull() ?: 0,
                                        alertDate = alertDate
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary)
                    ) {
                        Text("Add Batch")
                    }
                }
            }
        }
    }
}

@Composable
fun StockAdjustmentDialog(
    batch: BatchWithProduct,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var adjustQtyStr by remember { mutableStateOf("") }
    var isAddition by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("Physical count correction") }

    val reasons = listOf("Physical count correction", "Damaged / Broken", "Expired disposal", "Lost / Pilferage", "Sample / Promotion", "Other")

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Stock Adjustment", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text("${batch.productName} (Batch: ${batch.batchNumber})", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text("Current Stock: ${batch.currentStock} units", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isAddition,
                        onClick = { isAddition = false },
                        label = { Text("Deduct Stock (-)") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.errorContainer)
                    )
                    FilterChip(
                        selected = isAddition,
                        onClick = { isAddition = true },
                        label = { Text("Add Stock (+)") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PharmacyTealPrimary.copy(alpha = 0.2f))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = adjustQtyStr,
                    onValueChange = { adjustQtyStr = it },
                    label = { Text("Quantity to Adjust") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Reason for adjustment *", style = MaterialTheme.typography.labelSmall)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    items(reasons) { r ->
                        FilterChip(
                            selected = reason == r,
                            onClick = { reason = r },
                            label = { Text(r, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val qty = adjustQtyStr.toIntOrNull() ?: 0
                            if (qty > 0) {
                                val finalQty = if (isAddition) qty else -qty
                                onConfirm(finalQty, reason)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary)
                    ) {
                        Text("Apply Adjustment")
                    }
                }
            }
        }
    }
}
