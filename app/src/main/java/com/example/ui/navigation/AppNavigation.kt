package com.example.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.screens.*
import com.example.ui.theme.PharmacyNavyDark
import com.example.ui.theme.PharmacyTealPrimary
import java.io.File

enum class AppDestination(
    val title: String,
    val icon: ImageVector,
    val requiresAdmin: Boolean = false
) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    POS("POS Checkout", Icons.Default.PointOfSale),
    INVENTORY("Inventory", Icons.Default.Inventory2),
    SALES("Sales Invoices", Icons.Default.ReceiptLong),
    EXPIRY("Expiry & FEFO", Icons.Default.HourglassTop),
    PURCHASES("Purchases", Icons.Default.ShoppingCart),
    PARTIES("Ledgers", Icons.Default.People),
    CLOSING("Shift & Expenses", Icons.Default.AccountBalanceWallet),
    REPORTS("Reports & Profit", Icons.Default.Assessment),
    USERS("Staff Access", Icons.Default.AdminPanelSettings, requiresAdmin = true),
    SETTINGS("Settings", Icons.Default.Settings, requiresAdmin = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppNavigation(viewModel: MainViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val settings by viewModel.pharmacySettings.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val alertState by viewModel.snackbarMessage.collectAsState()
    val metrics by viewModel.dashboardMetrics.collectAsState()

    var currentDestination by remember { mutableStateOf(AppDestination.DASHBOARD) }
    var showMoreMenu by remember { mutableStateOf(false) }

    LaunchedEffect(alertState) {
        alertState?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    if (currentUser == null) {
        LoginScreen(
            viewModel = viewModel,
            onLoginSuccess = {
                currentDestination = AppDestination.DASHBOARD
            }
        )
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        val logoBitmap = remember(settings.logoUri) {
                            if (settings.logoUri.isNotBlank()) {
                                val f = File(settings.logoUri)
                                if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
                            } else null
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (logoBitmap != null) {
                                Image(
                                    bitmap = logoBitmap.asImageBitmap(),
                                    contentDescription = "Logo",
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column {
                                Text(
                                    text = settings.pharmacyName.ifEmpty { "SK Pharmacy" },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "${currentDestination.title} • Currency: ${settings.currency}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    },
                    actions = {
                        // Alerts indicator
                        if (metrics.expiringSoonCount > 0 || metrics.lowStockCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text("${metrics.expiringSoonCount + metrics.lowStockCount}")
                                    }
                                }
                            ) {
                                IconButton(onClick = { currentDestination = AppDestination.EXPIRY }) {
                                    Icon(Icons.Default.Notifications, contentDescription = "Alerts", tint = Color.White)
                                }
                            }
                        }

                        // More modules menu
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.Apps, contentDescription = "All Modules", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Purchases (Inward Stock)") },
                                    leadingIcon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                                    onClick = {
                                        currentDestination = AppDestination.PURCHASES
                                        showMoreMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Customer & Supplier Ledgers") },
                                    leadingIcon = { Icon(Icons.Default.People, contentDescription = null) },
                                    onClick = {
                                        currentDestination = AppDestination.PARTIES
                                        showMoreMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Expenses & Shift Closing") },
                                    leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                                    onClick = {
                                        currentDestination = AppDestination.CLOSING
                                        showMoreMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Expiry & FEFO Batches") },
                                    leadingIcon = { Icon(Icons.Default.HourglassTop, contentDescription = null) },
                                    onClick = {
                                        currentDestination = AppDestination.EXPIRY
                                        showMoreMenu = false
                                    }
                                )
                                if (currentUser?.role == "ADMIN") {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Staff Access & Roles") },
                                        leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                                        onClick = {
                                            currentDestination = AppDestination.USERS
                                            showMoreMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings & Backup") },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        onClick = {
                                            currentDestination = AppDestination.SETTINGS
                                            showMoreMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // User badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = currentUser!!.username,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "(${currentUser!!.role})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }

                        IconButton(onClick = { viewModel.logout() }) {
                            Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PharmacyNavyDark)
                )
            },
            bottomBar = {
                val primaryDestinations = listOf(
                    AppDestination.DASHBOARD,
                    AppDestination.POS,
                    AppDestination.INVENTORY,
                    AppDestination.SALES,
                    AppDestination.REPORTS
                )

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = PharmacyTealPrimary
                ) {
                    primaryDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentDestination == dest,
                            onClick = { currentDestination = dest },
                            icon = { Icon(dest.icon, contentDescription = dest.title) },
                            label = { Text(dest.title, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PharmacyTealPrimary,
                                selectedTextColor = PharmacyTealPrimary,
                                indicatorColor = PharmacyTealPrimary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentDestination) {
                    AppDestination.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToPos = { currentDestination = AppDestination.POS },
                        onNavigateToInventory = { currentDestination = AppDestination.INVENTORY },
                        onNavigateToExpiry = { currentDestination = AppDestination.EXPIRY },
                        onNavigateToLowStock = { currentDestination = AppDestination.INVENTORY },
                        onNavigateToPurchases = { currentDestination = AppDestination.PURCHASES },
                        onNavigateToReports = { currentDestination = AppDestination.REPORTS },
                        onNavigateToCustomers = { currentDestination = AppDestination.PARTIES },
                        onNavigateToDailyClosing = { currentDestination = AppDestination.CLOSING }
                    )
                    AppDestination.POS -> PosScreen(viewModel = viewModel)
                    AppDestination.INVENTORY -> InventoryScreen(viewModel = viewModel)
                    AppDestination.SALES -> SalesScreen(viewModel = viewModel)
                    AppDestination.EXPIRY -> ExpiryScreen(viewModel = viewModel)
                    AppDestination.PURCHASES -> PurchasesScreen(viewModel = viewModel)
                    AppDestination.PARTIES -> PartiesScreen(viewModel = viewModel)
                    AppDestination.CLOSING -> ExpensesAndClosingScreen(viewModel = viewModel)
                    AppDestination.REPORTS -> ReportsScreen(viewModel = viewModel)
                    AppDestination.USERS -> UsersScreen(viewModel = viewModel)
                    AppDestination.SETTINGS -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
