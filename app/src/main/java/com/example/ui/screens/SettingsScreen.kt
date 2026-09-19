package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.AuditLog
import com.example.data.model.PharmacySettings
import com.example.ui.MainViewModel
import com.example.ui.components.PharmacyHeader
import com.example.ui.theme.PharmacyNavyDark
import com.example.ui.theme.PharmacyTealPrimary
import com.example.util.ExcelUtil
import com.example.util.ImportPreviewResult
import com.example.util.PrintUtil
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val currentSettings by viewModel.pharmacySettings.collectAsState()
    val auditLogs by viewModel.allAuditLogs.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Pharmacy Profile & Tax, 1: Backup/Restore, 2: CSV Import/Export, 3: Audit Trail

    // Form state
    var pharmacyName by remember(currentSettings) { mutableStateOf(currentSettings.pharmacyName) }
    var address by remember(currentSettings) { mutableStateOf(currentSettings.address) }
    var phone by remember(currentSettings) { mutableStateOf(currentSettings.phone) }
    var email by remember(currentSettings) { mutableStateOf(currentSettings.email) }
    var website by remember(currentSettings) { mutableStateOf(currentSettings.website) }
    var licenseNumber by remember(currentSettings) { mutableStateOf(currentSettings.licenseNumber) }
    var ntnNumber by remember(currentSettings) { mutableStateOf(currentSettings.ntnNumber) }
    var currency by remember(currentSettings) { mutableStateOf(currentSettings.currency) }
    var taxEnabled by remember(currentSettings) { mutableStateOf(currentSettings.taxEnabled) }
    var defaultTaxPercentStr by remember(currentSettings) { mutableStateOf("${currentSettings.defaultTaxPercent}") }
    var maxDiscountPercentStr by remember(currentSettings) { mutableStateOf("${currentSettings.maxDiscountPercent}") }
    var invoiceFooter by remember(currentSettings) { mutableStateOf(currentSettings.invoiceFooter) }
    var receiptHeader by remember(currentSettings) { mutableStateOf(currentSettings.receiptHeader) }
    var adminPin by remember(currentSettings) { mutableStateOf(currentSettings.adminPin) }
    var logoUri by remember(currentSettings) { mutableStateOf(currentSettings.logoUri) }
    var logoBitmap by remember(currentSettings.logoUri) {
        mutableStateOf<Bitmap?>(
            if (currentSettings.logoUri.isNotBlank()) {
                val f = File(currentSettings.logoUri)
                if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
            } else null
        )
    }

    val logoPickerLauncher = rememberLauncherForActivityResult(
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
                    val file = File(context.filesDir, "pharmacy_logo_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                    logoUri = file.absolutePath
                    logoBitmap = bitmap
                }
            } catch (_: Exception) {}
        }
    }

    var showImportDialog by remember { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<ImportPreviewResult?>(null) }
    var showBackupSuccessDialog by remember { mutableStateOf<File?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        PharmacyHeader(
            title = "Admin & Settings",
            subtitle = "Configure pharmacy branding, taxes, backup, and audit trail"
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = PharmacyTealPrimary
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Profile & Tax") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Backup & Restore") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("CSV / Excel") })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Audit Trail (${auditLogs.size})") })
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (selectedTab) {
            0 -> {
                // Profile & Tax Form
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Pharmacy Logo & Branding", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(1.dp, PharmacyTealPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (logoBitmap != null) {
                                            Image(
                                                bitmap = logoBitmap!!.asImageBitmap(),
                                                contentDescription = "Pharmacy Logo",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.LocalPharmacy,
                                                contentDescription = null,
                                                tint = PharmacyTealPrimary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (logoBitmap != null) "Custom Pharmacy Logo Active" else "No Custom Logo Uploaded",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            "Shown on receipts, invoices & app header",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    logoPickerLauncher.launch(
                                                        androidx.activity.result.PickVisualMediaRequest(
                                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                                        )
                                                    )
                                                },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(if (logoBitmap != null) "Change" else "Choose Logo", style = MaterialTheme.typography.labelMedium)
                                            }

                                            if (logoBitmap != null) {
                                                IconButton(
                                                    onClick = {
                                                        logoUri = ""
                                                        logoBitmap = null
                                                    }
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Remove logo", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Pharmacy Identity", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(value = pharmacyName, onValueChange = { pharmacyName = it }, label = { Text("Pharmacy Name *") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.weight(1f))
                                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.weight(1f))
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = licenseNumber, onValueChange = { licenseNumber = it }, label = { Text("Drug License #") }, modifier = Modifier.weight(1f))
                                    OutlinedTextField(value = ntnNumber, onValueChange = { ntnNumber = it }, label = { Text("NTN #") }, modifier = Modifier.weight(1f))
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(value = website, onValueChange = { website = it }, label = { Text("Website") }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                    item {
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Currency & Fiscal Settings", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(value = currency, onValueChange = { currency = it }, label = { Text("Currency (e.g. PKR)") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Enable Sales Tax", style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = taxEnabled, onCheckedChange = { taxEnabled = it })
                                }

                                if (taxEnabled) {
                                    OutlinedTextField(
                                        value = defaultTaxPercentStr,
                                        onValueChange = { defaultTaxPercentStr = it },
                                        label = { Text("Default Tax %") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                OutlinedTextField(
                                    value = maxDiscountPercentStr,
                                    onValueChange = { maxDiscountPercentStr = it },
                                    label = { Text("Maximum Allowed Discount %") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = adminPin,
                                    onValueChange = { adminPin = it },
                                    label = { Text("Admin Security PIN (for resets/voids)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    item {
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Invoice & Receipt Messages", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(value = receiptHeader, onValueChange = { receiptHeader = it }, label = { Text("Receipt Header Note") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(value = invoiceFooter, onValueChange = { invoiceFooter = it }, label = { Text("Invoice Footer Message") }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                viewModel.savePharmacySettings(
                                    currentSettings.copy(
                                        pharmacyName = pharmacyName,
                                        address = address,
                                        phone = phone,
                                        email = email,
                                        website = website,
                                        licenseNumber = licenseNumber,
                                        ntnNumber = ntnNumber,
                                        currency = currency,
                                        taxEnabled = taxEnabled,
                                        defaultTaxPercent = defaultTaxPercentStr.toDoubleOrNull() ?: 0.0,
                                        maxDiscountPercent = maxDiscountPercentStr.toDoubleOrNull() ?: 20.0,
                                        invoiceFooter = invoiceFooter,
                                        receiptHeader = receiptHeader,
                                        adminPin = adminPin,
                                        logoUri = logoUri
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Pharmacy Settings")
                        }
                    }
                }
            }

            1 -> {
                // Backup & Restore
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Create Database Backup", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    "Creates a portable snapshot of all medicines, batches, customers, sales history, and configuration.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        viewModel.createBackup(context) { file ->
                                            showBackupSuccessDialog = file
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Backup, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Export Complete Backup Now")
                                }
                            }
                        }
                    }

                    item {
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Restore Database", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    "Restore previous pharmacy data from a verified backup JSON file.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = {
                                        val backupDir = File(context.filesDir, "backups")
                                        val latest = backupDir.listFiles()?.sortedByDescending { it.lastModified() }?.firstOrNull()
                                        if (latest != null) {
                                            viewModel.restoreBackup(latest) { _, _ -> }
                                        } else {
                                            viewModel.showMessage("No prior backup files found in storage")
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Restore Latest Backup")
                                }
                            }
                        }
                    }

                    item {
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Demo Data & Reset", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    "Populate test medicines with multiple batches, sales, and accounts for demonstration.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { viewModel.seedDemoData() },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Load Demo Retail Pharmacy Data")
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // CSV / Excel Import & Export
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Export to Spreadsheets", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text("Download formatted CSV files compatible with Microsoft Excel and Google Sheets.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val file = viewModel.exportProductsCsv(context)
                                            PrintUtil.sharePdf(context, file, "Medicine Inventory CSV")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Export Products")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val file = viewModel.exportSalesCsv(context)
                                            PrintUtil.sharePdf(context, file, "Sales Invoices CSV")
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Export Sales")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Import Medicines from CSV", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text("Bulk upload medicines and batch records with pre-validation.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = { showImportDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = PharmacyNavyDark),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open CSV Import Wizard")
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                // Audit Trail
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(auditLogs) { log ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${log.action}: ${log.affectedRecord}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(log.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                                Text("User: @${log.username} • ${log.details}", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                            }
                        }
                    }
                }
            }
        }
    }

    // CSV Import Dialog with Pre-validation
    if (showImportDialog) {
        CsvImportWizardDialog(
            onDismiss = { showImportDialog = false },
            onPreview = { csvText -> viewModel.previewCsvImport(csvText) },
            onApply = { validList ->
                viewModel.applyCsvImport(validList) {
                    showImportDialog = false
                }
            }
        )
    }

    if (showBackupSuccessDialog != null) {
        AlertDialog(
            onDismissRequest = { showBackupSuccessDialog = null },
            title = { Text("Backup Generated Successfully") },
            text = { Text("Backup file saved:\n${showBackupSuccessDialog!!.name}\n\nSize: ${showBackupSuccessDialog!!.length() / 1024} KB") },
            confirmButton = {
                Button(onClick = {
                    PrintUtil.sharePdf(context, showBackupSuccessDialog!!, "SK Pharmacy Database Backup")
                    showBackupSuccessDialog = null
                }) {
                    Text("Share / Save File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupSuccessDialog = null }) { Text("Done") }
            }
        )
    }
}

@Composable
fun CsvImportWizardDialog(
    onDismiss: () -> Unit,
    onPreview: (String) -> ImportPreviewResult,
    onApply: (List<Pair<com.example.data.model.Product, com.example.data.model.Batch>>) -> Unit
) {
    var csvText by remember { mutableStateOf(ExcelUtil.getSampleTemplateCsv()) }
    var previewResult by remember { mutableStateOf<ImportPreviewResult?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("CSV Import & Pre-Validation", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text("Paste or edit CSV records below. Format: Barcode, SKU, Name, Generic, Brand, Category, Strength, Pack, Manufacturer, Batch, Mfg, Exp, Cost, Price, Stock, MinStock, Supplier", style = MaterialTheme.typography.labelSmall, color = Color.Gray)

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = csvText,
                    onValueChange = {
                        csvText = it
                        previewResult = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    maxLines = 8
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { previewResult = onPreview(csvText) },
                    colors = ButtonDefaults.buttonColors(containerColor = PharmacyNavyDark)
                ) {
                    Text("Validate & Preview Records")
                }

                if (previewResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (previewResult!!.invalidRows.isEmpty()) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "Valid Records: ${previewResult!!.validProducts.size} | Duplicates: ${previewResult!!.duplicateCount} | Errors: ${previewResult!!.invalidRows.size}",
                                fontWeight = FontWeight.Bold,
                                color = if (previewResult!!.invalidRows.isEmpty()) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                            )
                            if (previewResult!!.invalidRows.isNotEmpty()) {
                                previewResult!!.invalidRows.take(3).forEach { err ->
                                    Text("• $err", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (previewResult != null && previewResult!!.validProducts.isNotEmpty()) {
                                onApply(previewResult!!.validProducts)
                            }
                        },
                        enabled = previewResult != null && previewResult!!.validProducts.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary)
                    ) {
                        Text("Import Valid Records")
                    }
                }
            }
        }
    }
}
