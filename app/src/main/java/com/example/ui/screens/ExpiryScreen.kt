package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BatchWithProduct
import com.example.ui.MainViewModel
import com.example.ui.components.PharmacyHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiryScreen(viewModel: MainViewModel) {
    val expirySummary by viewModel.expirySummary.collectAsState()
    val settings by viewModel.pharmacySettings.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    var selectedBatchForAction by remember { mutableStateOf<BatchWithProduct?>(null) }
    var showDisposalDialog by remember { mutableStateOf(false) }

    val tabs = listOf(
        "Expired (${expirySummary?.expired?.size ?: 0})",
        "Today (${expirySummary?.expiringToday?.size ?: 0})",
        "< 7 Days (${expirySummary?.expiring7Days?.size ?: 0})",
        "< 30 Days (${expirySummary?.expiring30Days?.size ?: 0})",
        "< 60 Days (${expirySummary?.expiring60Days?.size ?: 0})",
        "< 90 Days (${expirySummary?.expiring90Days?.size ?: 0})"
    )

    val currentList: List<BatchWithProduct> = when (selectedTabIndex) {
        0 -> expirySummary?.expired ?: emptyList()
        1 -> expirySummary?.expiringToday ?: emptyList()
        2 -> expirySummary?.expiring7Days ?: emptyList()
        3 -> expirySummary?.expiring30Days ?: emptyList()
        4 -> expirySummary?.expiring60Days ?: emptyList()
        else -> expirySummary?.expiring90Days ?: emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        PharmacyHeader(
            title = "Expiry Management & FEFO",
            subtitle = "Track, quarantine, or return expiring batches"
        ) {
            IconButton(onClick = { viewModel.refreshExpiry() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = PharmacyTealPrimary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (currentList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = PharmacyTealPrimary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No medicines in this expiry category", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(currentList) { batch ->
                    ExpiryBatchCard(
                        batch = batch,
                        currency = settings.currency,
                        isExpired = selectedTabIndex == 0,
                        onDispose = {
                            selectedBatchForAction = batch
                            showDisposalDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showDisposalDialog && selectedBatchForAction != null) {
        AlertDialog(
            onDismissRequest = { showDisposalDialog = false },
            title = { Text("Quarantine / Dispose Batch") },
            text = {
                Text("Are you sure you want to write off ${selectedBatchForAction!!.currentStock} units of ${selectedBatchForAction!!.productName} (Batch: ${selectedBatchForAction!!.batchNumber})?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val b = selectedBatchForAction!!
                        viewModel.adjustStock(b.batchId, -b.currentStock, "Expired Medicine Quarantine / Disposal")
                        showDisposalDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Disposal")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisposalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ExpiryBatchCard(
    batch: BatchWithProduct,
    currency: String,
    isExpired: Boolean,
    onDispose: () -> Unit
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(batch.productName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text("Batch: ${batch.batchNumber}", style = MaterialTheme.typography.labelMedium, color = PharmacyTealPrimary)
                    if (batch.alertDate.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = PharmacyTealPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Alert Date: ${batch.alertDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PharmacyTealPrimary
                            )
                        }
                    }
                }
                StatusBadge(
                    text = if (isExpired) "EXPIRED" else "Exp: ${batch.expiryDate}",
                    containerColor = if (isExpired) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Remaining Stock: ${batch.currentStock} units", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    Text("Purchase Value: $currency %.2f".format(batch.purchasePrice * batch.currentStock), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }

                Button(
                    onClick = onDispose,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Write Off", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
