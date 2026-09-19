package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.PharmacyHeader
import com.example.ui.components.StatMetricCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToPos: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToExpiry: () -> Unit,
    onNavigateToLowStock: () -> Unit,
    onNavigateToPurchases: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToDailyClosing: () -> Unit
) {
    val metrics by viewModel.dashboardMetrics.collectAsState()
    val settings by viewModel.pharmacySettings.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val permissions by viewModel.currentPermissions.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Header
        item {
            PharmacyHeader(
                title = "Dashboard",
                subtitle = "Welcome back, ${currentUser?.fullName ?: "Pharmacist"} (${currentUser?.role ?: "STAFF"})"
            ) {
                IconButton(onClick = { viewModel.refreshDashboardMetrics() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = PharmacyTealPrimary)
                }
            }
        }

        // Action Quick Bar
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    QuickActionChip(
                        label = "New Sale (POS)",
                        icon = Icons.Default.PointOfSale,
                        containerColor = PharmacyTealPrimary,
                        contentColor = Color.White,
                        onClick = onNavigateToPos
                    )
                }
                item {
                    QuickActionChip(
                        label = "Inventory",
                        icon = Icons.Default.Medication,
                        containerColor = PharmacyNavyDark,
                        contentColor = Color.White,
                        onClick = onNavigateToInventory
                    )
                }
                item {
                    QuickActionChip(
                        label = "Purchases",
                        icon = Icons.Default.ShoppingCart,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = onNavigateToPurchases
                    )
                }
                item {
                    QuickActionChip(
                        label = "Daily Closing",
                        icon = Icons.Default.LockClock,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = onNavigateToDailyClosing
                    )
                }
            }
        }

        // Critical Alerts Banner (Clickable)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Critical Inventory Alerts",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Real-time updates",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Expired Alert
                        AlertBadgeCard(
                            count = metrics.expiredCount,
                            label = "Expired",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error,
                            icon = Icons.Default.Dangerous,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToExpiry
                        )
                        // Expiring Soon Alert
                        AlertBadgeCard(
                            count = metrics.expiringSoonCount,
                            label = "Expiring <30d",
                            containerColor = Color(0xFFFFE0B2),
                            contentColor = Color(0xFFE65100),
                            icon = Icons.Default.Timer,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToExpiry
                        )
                        // Low Stock Alert
                        AlertBadgeCard(
                            count = metrics.lowStockCount,
                            label = "Low Stock",
                            containerColor = Color(0xFFFFF9C4),
                            contentColor = Color(0xFFF57F17),
                            icon = Icons.Default.Inventory2,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToLowStock
                        )
                    }
                }
            }
        }

        // Key Business Metrics Grid
        item {
            Text(
                text = "Today's Business Performance",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatMetricCard(
                    title = "Today's Revenue",
                    value = "${settings.currency} %.0f".format(metrics.todaySales),
                    subtitle = "${metrics.todayInvoices} Invoices",
                    icon = Icons.Default.AttachMoney,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = PharmacyTealPrimary,
                    modifier = Modifier.weight(1f)
                )

                if (permissions.viewProfit || currentUser?.role == "ADMIN") {
                    StatMetricCard(
                        title = "Today's Net Profit",
                        value = "${settings.currency} %.0f".format(metrics.todayNetIncome),
                        subtitle = "Gross: ${settings.currency} %.0f".format(metrics.todayProfit),
                        icon = Icons.Default.TrendingUp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = Color(0xFF2E7D32),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToReports
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatMetricCard(
                    title = "Total Inventory Value",
                    value = "${settings.currency} %.0f".format(metrics.totalInventoryValue),
                    subtitle = "${metrics.totalProducts} Medicines",
                    icon = Icons.Default.Warehouse,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = PharmacyNavyDark,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToInventory
                )

                StatMetricCard(
                    title = "Customer Due (Credit)",
                    value = "${settings.currency} %.0f".format(metrics.pendingCustomerDebt),
                    subtitle = "Supplier Due: ${settings.currency} %.0f".format(metrics.pendingSupplierDebt),
                    icon = Icons.Default.AccountBalanceWallet,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = Color(0xFFC2185B),
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToCustomers
                )
            }
        }

        // Cash vs Credit distribution
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Today's Payment Split",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Cash Sales", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${settings.currency} %.2f".format(metrics.todayCashSales), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PharmacyTealPrimary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Credit / Balance Due", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${settings.currency} %.2f".format(metrics.todayCreditSales), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFFC2185B))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val totalSplit = (metrics.todayCashSales + metrics.todayCreditSales).coerceAtLeast(1.0)
                    val cashFraction = (metrics.todayCashSales / totalSplit).toFloat().coerceIn(0f, 1f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.LightGray.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(cashFraction)
                                .background(PharmacyTealPrimary)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFC2185B))
                        )
                    }
                }
            }
        }

        // Top Selling Medicines
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
                            text = "Top Selling Medicines",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        TextButton(onClick = onNavigateToReports) {
                            Text("View All")
                        }
                    }
                    if (metrics.topSelling.isEmpty()) {
                        Text(
                            text = "No sales recorded yet today. Complete sales in POS to view trends.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        metrics.topSelling.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(PharmacyTealPrimary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = PharmacyTealPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = item.productName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "${item.totalSold} units sold",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = "${settings.currency} %.0f".format(item.totalRevenue),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            if (index < metrics.topSelling.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionChip(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = contentColor)
        }
    }
}

@Composable
fun AlertBadgeCard(
    count: Int,
    label: String,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = contentColor
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.85f)
            )
        }
    }
}
