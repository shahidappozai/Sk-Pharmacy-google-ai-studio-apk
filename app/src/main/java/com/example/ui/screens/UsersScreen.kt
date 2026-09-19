package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.StaffPermissions
import com.example.data.model.User
import com.example.ui.MainViewModel
import com.example.ui.components.PharmacyHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.PharmacyNavyDark
import com.example.ui.theme.PharmacyTealPrimary
import org.json.JSONObject

@Composable
fun UsersScreen(viewModel: MainViewModel) {
    val allUsers by viewModel.allUsers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    var editingUser by remember { mutableStateOf<User?>(null) }
    var pinEditingUser by remember { mutableStateOf<User?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        PharmacyHeader(
            title = "Staff & Access Control",
            subtitle = "Manage staff credentials, PIN keys, and granular POS permissions"
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(allUsers) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (user.role == "ADMIN") PharmacyNavyDark.copy(alpha = 0.12f) else PharmacyTealPrimary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (user.role == "ADMIN") Icons.Default.AdminPanelSettings else Icons.Default.Badge,
                                            contentDescription = null,
                                            tint = if (user.role == "ADMIN") PharmacyNavyDark else PharmacyTealPrimary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(user.fullName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Username: @${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            StatusBadge(
                                text = user.role,
                                containerColor = if (user.role == "ADMIN") PharmacyNavyDark.copy(alpha = 0.15f) else PharmacyTealPrimary.copy(alpha = 0.15f),
                                contentColor = if (user.role == "ADMIN") PharmacyNavyDark else PharmacyTealPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Security PIN Status Row
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Pin,
                                        contentDescription = null,
                                        tint = if (user.pinHash.isNotBlank()) PharmacyTealPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (user.pinHash.isNotBlank()) "Security PIN: Active (••••)" else "Security PIN: Not configured",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (user.pinHash.isNotBlank()) PharmacyTealPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (user.pinHash.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (user.pinHash.isNotBlank()) PharmacyTealPrimary.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (user.pinHash.isNotBlank()) "PIN Login Enabled" else "Password Only",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (user.pinHash.isNotBlank()) PharmacyTealPrimary else Color.Gray,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (user.role == "ADMIN") "Full System Control" else "Custom Staff Privileges",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (currentUser?.role == "ADMIN") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Set or Change PIN Key Button
                                    OutlinedButton(
                                        onClick = { pinEditingUser = user },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (user.pinHash.isBlank()) "Set PIN" else "Change PIN",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }

                                    if (user.role != "ADMIN") {
                                        Button(
                                            onClick = { editingUser = user },
                                            colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Permissions", style = MaterialTheme.typography.labelSmall)
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

    // Set / Change PIN Dialog
    if (pinEditingUser != null) {
        StaffPinDialog(
            user = pinEditingUser!!,
            onDismiss = { pinEditingUser = null },
            onSavePin = { newPin ->
                viewModel.updateStaffPin(pinEditingUser!!, newPin) { success, _ ->
                    if (success) {
                        pinEditingUser = null
                    }
                }
            }
        )
    }

    if (editingUser != null) {
        PermissionsEditDialog(
            user = editingUser!!,
            onDismiss = { editingUser = null },
            onOpenChangePin = {
                val target = editingUser
                editingUser = null
                pinEditingUser = target
            },
            onSave = { updatedPerms ->
                editingUser?.let { u ->
                    viewModel.saveUserPermissions(u, updatedPerms)
                }
                editingUser = null
            }
        )
    }
}

@Composable
fun StaffPinDialog(
    user: User,
    onDismiss: () -> Unit,
    onSavePin: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isPinVisible by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val hasExistingPin = user.pinHash.isNotBlank()

    val handleSave = {
        val trimmedPin = pin.trim()
        val trimmedConfirm = confirmPin.trim()

        if (trimmedPin.length < 4 || trimmedPin.length > 8) {
            validationError = "PIN must be between 4 and 8 digits."
        } else if (!trimmedPin.all { it.isDigit() }) {
            validationError = "PIN must contain only numbers."
        } else if (trimmedPin != trimmedConfirm) {
            validationError = "PIN confirmation does not match."
        } else {
            validationError = null
            onSavePin(trimmedPin)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = PharmacyTealPrimary.copy(alpha = 0.15f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = PharmacyTealPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (hasExistingPin) "Change PIN Key" else "Set Staff PIN Key",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Admin Access Control",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Staff Information Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = user.fullName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Username: @${user.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(
                            text = user.role,
                            containerColor = if (user.role == "ADMIN") PharmacyNavyDark.copy(alpha = 0.15f) else PharmacyTealPrimary.copy(alpha = 0.15f),
                            contentColor = if (user.role == "ADMIN") PharmacyNavyDark else PharmacyTealPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (hasExistingPin) {
                        "This staff member already has an active PIN configured. Enter a new 4 to 6 digit numeric PIN to replace it."
                    } else {
                        "Assign a 4 to 6 digit numeric PIN key so this staff member can quickly log in to the POS terminal."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (validationError != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = validationError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // New PIN Field
                OutlinedTextField(
                    value = pin,
                    onValueChange = { input ->
                        if (input.length <= 8 && input.all { it.isDigit() }) {
                            pin = input
                            validationError = null
                        }
                    },
                    label = { Text("New PIN Key (4-8 Digits) *") },
                    placeholder = { Text("e.g. 1234") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { isPinVisible = !isPinVisible }) {
                            Icon(
                                if (isPinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Visibility"
                            )
                        }
                    },
                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Confirm PIN Field
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { input ->
                        if (input.length <= 8 && input.all { it.isDigit() }) {
                            confirmPin = input
                            validationError = null
                        }
                    },
                    label = { Text("Confirm New PIN *") },
                    placeholder = { Text("Re-enter new PIN") },
                    leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null) },
                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { handleSave() }),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Match feedback indicator
                if (pin.isNotEmpty() && confirmPin.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        if (pin == confirmPin) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PINs match", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                        } else {
                            Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PINs do not match", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Helper Tools
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val randomPin = (1000..9999).random().toString()
                            pin = randomPin
                            confirmPin = randomPin
                            isPinVisible = true
                            validationError = null
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate Random PIN", style = MaterialTheme.typography.labelMedium)
                    }

                    if (hasExistingPin) {
                        TextButton(
                            onClick = {
                                onSavePin("")
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove PIN", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { handleSave() },
                        enabled = pin.length >= 4 && confirmPin == pin,
                        colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save PIN Key")
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionsEditDialog(
    user: User,
    onDismiss: () -> Unit,
    onOpenChangePin: () -> Unit,
    onSave: (StaffPermissions) -> Unit
) {
    val initial = remember(user) {
        try {
            val j = JSONObject(user.permissionsJson)
            StaffPermissions(
                viewPos = j.optBoolean("viewPos", true),
                createSale = j.optBoolean("createSale", true),
                editSale = j.optBoolean("editSale", false),
                cancelSale = j.optBoolean("cancelSale", false),
                applyDiscount = j.optBoolean("applyDiscount", true),
                viewStock = j.optBoolean("viewStock", true),
                addProducts = j.optBoolean("addProducts", false),
                editProducts = j.optBoolean("editProducts", false),
                viewPurchaseRecords = j.optBoolean("viewPurchaseRecords", false),
                createPurchases = j.optBoolean("createPurchases", false),
                viewCustomers = j.optBoolean("viewCustomers", true),
                addCustomers = j.optBoolean("addCustomers", true),
                viewSuppliers = j.optBoolean("viewSuppliers", false),
                addSuppliers = j.optBoolean("addSuppliers", false),
                viewReports = j.optBoolean("viewReports", false),
                viewProfit = j.optBoolean("viewProfit", false),
                viewExpenses = j.optBoolean("viewExpenses", false),
                exportData = j.optBoolean("exportData", false),
                importData = j.optBoolean("importData", false),
                backup = j.optBoolean("backup", false),
                restore = j.optBoolean("restore", false),
                manageUsers = j.optBoolean("manageUsers", false),
                manageSettings = j.optBoolean("manageSettings", false)
            )
        } catch (_: Exception) {
            StaffPermissions.defaultStaff()
        }
    }

    var perms by remember { mutableStateOf(initial) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Edit Permissions: ${user.fullName}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Role: ${user.role} (@${user.username})", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Shortcut card to change staff PIN
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = PharmacyTealPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Security PIN Key", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    text = if (user.pinHash.isNotBlank()) "Configured (••••)" else "Not set",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenChangePin,
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Change PIN", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item { Text("Sales & POS", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = PharmacyTealPrimary) }
                    item { PermissionToggleRow("Create Sale", perms.createSale) { perms = perms.copy(createSale = it) } }
                    item { PermissionToggleRow("Cancel / Void Sale", perms.cancelSale) { perms = perms.copy(cancelSale = it) } }
                    item { PermissionToggleRow("Alter Selling Price", perms.editSale) { perms = perms.copy(editSale = it) } }
                    item { PermissionToggleRow("Apply Invoice Discount", perms.applyDiscount) { perms = perms.copy(applyDiscount = it) } }

                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                    item { Text("Inventory & Medicines", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = PharmacyTealPrimary) }
                    item { PermissionToggleRow("Add Medicines", perms.addProducts) { perms = perms.copy(addProducts = it) } }
                    item { PermissionToggleRow("Edit Medicines & Batches", perms.editProducts) { perms = perms.copy(editProducts = it) } }

                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                    item { Text("Purchasing & Distributors", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = PharmacyTealPrimary) }
                    item { PermissionToggleRow("Create Purchase Orders", perms.createPurchases) { perms = perms.copy(createPurchases = it) } }
                    item { PermissionToggleRow("View Supplier Balances", perms.viewSuppliers) { perms = perms.copy(viewSuppliers = it) } }

                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                    item { Text("Reports & Financials", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = PharmacyTealPrimary) }
                    item { PermissionToggleRow("View Reports", perms.viewReports) { perms = perms.copy(viewReports = it) } }
                    item { PermissionToggleRow("View Profit & True COGS", perms.viewProfit) { perms = perms.copy(viewProfit = it) } }
                    item { PermissionToggleRow("Record Expenses", perms.viewExpenses) { perms = perms.copy(viewExpenses = it) } }

                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                    item { Text("Data & System Operations", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = PharmacyTealPrimary) }
                    item { PermissionToggleRow("Export Excel/CSV", perms.exportData) { perms = perms.copy(exportData = it) } }
                    item { PermissionToggleRow("Import CSV Products", perms.importData) { perms = perms.copy(importData = it) } }
                    item { PermissionToggleRow("Database Backup", perms.backup) { perms = perms.copy(backup = it) } }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(perms) },
                        colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary)
                    ) {
                        Text("Save Permissions")
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodySmall)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = PharmacyTealPrimary)
        )
    }
}

