package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.PharmacyDatabase
import com.example.data.db.TopSellingItem
import com.example.data.model.*
import com.example.data.repository.CashFlowReportData
import com.example.data.repository.ExpirySummary
import com.example.data.repository.PharmacyRepository
import com.example.data.repository.ProfitReportData
import com.example.data.repository.StocksValuationData
import com.example.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class DateRangeFilter(val displayName: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year"),
    ALL_TIME("All Time")
}

data class DashboardMetrics(
    val todaySales: Double = 0.0,
    val todayInvoices: Int = 0,
    val todayProfit: Double = 0.0,
    val todayExpenses: Double = 0.0,
    val todayNetIncome: Double = 0.0,
    val todayCashSales: Double = 0.0,
    val todayCreditSales: Double = 0.0,
    val totalInventoryValue: Double = 0.0,
    val totalProducts: Int = 0,
    val lowStockCount: Int = 0,
    val expiredCount: Int = 0,
    val expiringSoonCount: Int = 0,
    val pendingCustomerDebt: Double = 0.0,
    val pendingSupplierDebt: Double = 0.0,
    val topSelling: List<TopSellingItem> = emptyList(),
    val categorySales: Map<String, Double> = emptyMap()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository: PharmacyRepository

    init {
        val db = PharmacyDatabase.getDatabase(application, viewModelScope)
        repository = PharmacyRepository(db)
        NotificationHelper.createNotificationChannels(application)
    }

    // Current User & Session
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _currentPermissions = MutableStateFlow<StaffPermissions>(StaffPermissions.defaultStaff())
    val currentPermissions: StateFlow<StaffPermissions> = _currentPermissions.asStateFlow()

    // Settings
    val pharmacySettings: StateFlow<PharmacySettings> = repository.settingsFlow
        .map { it ?: PharmacySettings() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PharmacySettings())

    // All Users
    val allUsers: StateFlow<List<User>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Inventory & Products
    val productsWithStock: StateFlow<List<ProductWithStock>> = repository.productsWithStock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBatches: StateFlow<List<BatchWithProduct>> = repository.allBatchesWithProduct
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allStockAdjustments: StateFlow<List<StockAdjustment>> = repository.allStockAdjustments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Sales & POS
    val allSales: StateFlow<List<Sale>> = repository.allSales
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSalesReturns: StateFlow<List<SalesReturn>> = repository.allSalesReturns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Purchases & Suppliers
    val allPurchases: StateFlow<List<Purchase>> = repository.allPurchases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSuppliers: StateFlow<List<Supplier>> = repository.allSuppliers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Customers
    val allCustomers: StateFlow<List<Customer>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Expenses & Daily Closing
    val allExpenses: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDailyClosings: StateFlow<List<DailyClosing>> = repository.allDailyClosings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Audit Logs
    val allAuditLogs: StateFlow<List<AuditLog>> = repository.allAuditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // POS Cart State
    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _selectedCustomer = MutableStateFlow<Customer?>(null)
    val selectedCustomer: StateFlow<Customer?> = _selectedCustomer.asStateFlow()

    private val _posInvoiceDiscount = MutableStateFlow(0.0)
    val posInvoiceDiscount: StateFlow<Double> = _posInvoiceDiscount.asStateFlow()

    val lastCompletedSale = MutableStateFlow<Sale?>(null)
    val lastSaleItems = MutableStateFlow<List<SaleItem>>(emptyList())

    // Dashboard Metrics
    private val _dashboardMetrics = MutableStateFlow(DashboardMetrics())
    val dashboardMetrics: StateFlow<DashboardMetrics> = _dashboardMetrics.asStateFlow()

    // Expiry Breakdown
    private val _expirySummary = MutableStateFlow<ExpirySummary?>(null)
    val expirySummary: StateFlow<ExpirySummary?> = _expirySummary.asStateFlow()

    // Status / Messages
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // AI Product Prediction State
    private val _isAiPredicting = MutableStateFlow(false)
    val isAiPredicting: StateFlow<Boolean> = _isAiPredicting.asStateFlow()

    private val _predictedProduct = MutableStateFlow<PredictedProductInfo?>(null)
    val predictedProduct: StateFlow<PredictedProductInfo?> = _predictedProduct.asStateFlow()

    init {
        refreshDashboardMetrics()
        refreshExpiry()
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun clearMessage() {
        _snackbarMessage.value = null
    }

    // --- Authentication ---
    fun login(username: String, secret: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val user = repository.getUserByUsername(username.trim())
            if (user == null) {
                onResult(false, "User '$username' not found")
                return@launch
            }
            if (!user.isActive) {
                onResult(false, "Account is disabled. Contact administrator.")
                return@launch
            }

            val isPasswordValid = SecurityUtil.verifyPassword(secret, user.salt, user.passwordHash)
            val isPinValid = user.pinHash.isNotEmpty() && SecurityUtil.verifyPin(secret, user.pinHash)

            if (isPasswordValid || isPinValid) {
                _currentUser.value = user
                _currentPermissions.value = parsePermissions(user)
                repository.logAction(user.username, "LOGIN", "User Session", "Logged in successfully")
                refreshDashboardMetrics()
                refreshExpiry()
                onResult(true, "Welcome ${user.fullName}")
            } else {
                onResult(false, "Invalid password or PIN")
            }
        }
    }

    fun logout() {
        val user = _currentUser.value
        if (user != null) {
            viewModelScope.launch {
                repository.logAction(user.username, "LOGOUT", "User Session", "Logged out")
            }
        }
        _currentUser.value = null
        _currentPermissions.value = StaffPermissions.defaultStaff()
        clearCart()
    }

    private fun parsePermissions(user: User): StaffPermissions {
        if (user.role == "ADMIN") return StaffPermissions.adminPermissions()
        if (user.permissionsJson.isBlank()) return StaffPermissions.defaultStaff()
        return try {
            val json = JSONObject(user.permissionsJson)
            StaffPermissions(
                viewPos = json.optBoolean("viewPos", true),
                createSale = json.optBoolean("createSale", true),
                editSale = json.optBoolean("editSale", false),
                cancelSale = json.optBoolean("cancelSale", false),
                applyDiscount = json.optBoolean("applyDiscount", true),
                viewStock = json.optBoolean("viewStock", true),
                addProducts = json.optBoolean("addProducts", false),
                editProducts = json.optBoolean("editProducts", false),
                viewPurchaseRecords = json.optBoolean("viewPurchaseRecords", false),
                createPurchases = json.optBoolean("createPurchases", false),
                viewCustomers = json.optBoolean("viewCustomers", true),
                addCustomers = json.optBoolean("addCustomers", true),
                viewSuppliers = json.optBoolean("viewSuppliers", false),
                addSuppliers = json.optBoolean("addSuppliers", false),
                viewReports = json.optBoolean("viewReports", false),
                viewProfit = json.optBoolean("viewProfit", false),
                viewExpenses = json.optBoolean("viewExpenses", false),
                exportData = json.optBoolean("exportData", false),
                importData = json.optBoolean("importData", false),
                backup = json.optBoolean("backup", false),
                restore = json.optBoolean("restore", false),
                manageUsers = json.optBoolean("manageUsers", false),
                manageSettings = json.optBoolean("manageSettings", false)
            )
        } catch (_: Exception) {
            StaffPermissions.defaultStaff()
        }
    }

    fun saveUserPermissions(user: User, permissions: StaffPermissions) {
        viewModelScope.launch {
            val json = JSONObject().apply {
                put("viewPos", permissions.viewPos)
                put("createSale", permissions.createSale)
                put("editSale", permissions.editSale)
                put("cancelSale", permissions.cancelSale)
                put("applyDiscount", permissions.applyDiscount)
                put("viewStock", permissions.viewStock)
                put("addProducts", permissions.addProducts)
                put("editProducts", permissions.editProducts)
                put("viewPurchaseRecords", permissions.viewPurchaseRecords)
                put("createPurchases", permissions.createPurchases)
                put("viewCustomers", permissions.viewCustomers)
                put("addCustomers", permissions.addCustomers)
                put("viewSuppliers", permissions.viewSuppliers)
                put("addSuppliers", permissions.addSuppliers)
                put("viewReports", permissions.viewReports)
                put("viewProfit", permissions.viewProfit)
                put("viewExpenses", permissions.viewExpenses)
                put("exportData", permissions.exportData)
                put("importData", permissions.importData)
                put("backup", permissions.backup)
                put("restore", permissions.restore)
                put("manageUsers", permissions.manageUsers)
                put("manageSettings", permissions.manageSettings)
            }
            repository.updateUser(user.copy(permissionsJson = json.toString()))
            repository.logAction(
                _currentUser.value?.username ?: "Admin",
                "UPDATE_PERMISSIONS",
                "User: ${user.username}",
                "Updated staff permissions"
            )
            showMessage("Permissions updated for ${user.fullName}")
        }
    }

    fun updateStaffPin(user: User, newPin: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val trimmed = newPin.trim()
            if (trimmed.isNotBlank()) {
                if (trimmed.length < 4 || trimmed.length > 8) {
                    onResult(false, "PIN must be between 4 and 8 digits")
                    return@launch
                }
                if (!trimmed.all { it.isDigit() }) {
                    onResult(false, "PIN must contain numbers only")
                    return@launch
                }
            }

            val newPinHash = if (trimmed.isBlank()) "" else SecurityUtil.hashPin(trimmed)
            val updatedUser = user.copy(pinHash = newPinHash)
            repository.updateUser(updatedUser)

            // If updating admin account, keep pharmacySettings.adminPin in sync as well
            if (user.role == "ADMIN" && trimmed.isNotBlank()) {
                val currentSettings = pharmacySettings.value
                repository.saveSettings(currentSettings.copy(adminPin = trimmed))
            }

            if (_currentUser.value?.id == user.id) {
                _currentUser.value = updatedUser
            }

            val actionDesc = if (trimmed.isBlank()) "Removed PIN key for @${user.username}" else "Updated PIN key for @${user.username}"
            repository.logAction(
                _currentUser.value?.username ?: "Admin",
                "UPDATE_STAFF_PIN",
                "User: ${user.username}",
                actionDesc
            )

            showMessage(if (trimmed.isBlank()) "PIN removed for ${user.fullName}" else "PIN updated successfully for ${user.fullName}")
            onResult(true, "PIN updated successfully")
        }
    }

    fun verifyAdminPin(pin: String): Boolean {
        val settings = pharmacySettings.value
        return SecurityUtil.verifyPin(pin, SecurityUtil.hashPin(settings.adminPin))
    }

    // --- POS Logic & FEFO ---
    fun selectCustomer(customer: Customer?) {
        _selectedCustomer.value = customer
    }

    fun setInvoiceDiscount(discount: Double) {
        val maxAllowed = pharmacySettings.value.maxDiscountPercent
        val finalDiscount = discount.coerceIn(0.0, maxAllowed)
        _posInvoiceDiscount.value = finalDiscount
    }

    fun addProductToCart(product: Product, specificBatch: Batch? = null, qty: Int = 1) {
        viewModelScope.launch {
            val batchToUse: Batch = if (specificBatch != null) {
                specificBatch
            } else {
                // FEFO: Pick the earliest unexpired batch with stock
                val validBatches = repository.getValidFefoBatches(product.id)
                if (validBatches.isEmpty()) {
                    showMessage("Cannot add ${product.name}: No valid (unexpired) batch available in stock!")
                    return@launch
                }
                validBatches.first() // FEFO earliest valid expiry!
            }

            val currentList = _cart.value.toMutableList()
            val existingIndex = currentList.indexOfFirst {
                it.product.id == product.id && it.batch.id == batchToUse.id
            }

            if (existingIndex >= 0) {
                val existing = currentList[existingIndex]
                val newQty = existing.quantity + qty
                if (newQty > batchToUse.currentStock) {
                    showMessage("Cannot add: only ${batchToUse.currentStock} units available in Batch ${batchToUse.batchNumber}")
                    return@launch
                }
                currentList[existingIndex] = existing.copy(quantity = newQty)
            } else {
                if (qty > batchToUse.currentStock) {
                    showMessage("Cannot add: only ${batchToUse.currentStock} units available in Batch ${batchToUse.batchNumber}")
                    return@launch
                }
                currentList.add(
                    CartItem(
                        product = product,
                        batch = batchToUse,
                        quantity = qty,
                        unitPrice = batchToUse.retailPrice.takeIf { it > 0 } ?: product.retailPrice,
                        discountPercent = product.defaultDiscountPercent
                    )
                )
            }
            _cart.value = currentList
        }
    }

    fun updateCartItemQuantity(index: Int, newQty: Int) {
        val list = _cart.value.toMutableList()
        if (index in list.indices) {
            if (newQty <= 0) {
                list.removeAt(index)
            } else {
                val item = list[index]
                if (newQty > item.batch.currentStock) {
                    showMessage("Available stock in Batch ${item.batch.batchNumber} is only ${item.batch.currentStock}")
                    return
                }
                list[index] = item.copy(quantity = newQty)
            }
            _cart.value = list
        }
    }

    fun updateCartItemPrice(index: Int, newPrice: Double) {
        if (!_currentPermissions.value.editSale && _currentUser.value?.role != "ADMIN") {
            showMessage("Permission denied: cannot alter unit selling price")
            return
        }
        val list = _cart.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(unitPrice = newPrice, customPriceApplied = true)
            _cart.value = list
        }
    }

    fun updateCartItemDiscount(index: Int, discountPercent: Double) {
        val maxAllowed = pharmacySettings.value.maxDiscountPercent
        val clamped = discountPercent.coerceIn(0.0, maxAllowed)
        val list = _cart.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(discountPercent = clamped)
            _cart.value = list
        }
    }

    fun removeCartItem(index: Int) {
        val list = _cart.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _cart.value = list
        }
    }

    fun clearCart() {
        _cart.value = emptyList()
        _posInvoiceDiscount.value = 0.0
        _selectedCustomer.value = null
    }

    fun completeSale(
        paymentMethod: String,
        amountPaidInput: Double,
        mixedDetails: String = "",
        notes: String = "",
        onComplete: (Sale) -> Unit
    ) {
        val user = _currentUser.value ?: run {
            showMessage("Please log in to complete a sale")
            return
        }
        val items = _cart.value
        if (items.isEmpty()) {
            showMessage("Cart is empty")
            return
        }

        viewModelScope.launch {
            val subtotal = items.sumOf { it.subtotal }
            val invoiceDiscountAmt = subtotal * (_posInvoiceDiscount.value / 100.0)
            val settings = pharmacySettings.value
            val taxAmt = if (settings.taxEnabled) (subtotal - invoiceDiscountAmt) * (settings.defaultTaxPercent / 100.0) else 0.0
            val grandTotal = (subtotal - invoiceDiscountAmt + taxAmt).coerceAtLeast(0.0)

            val amountPaid = if (paymentMethod == "Credit") 0.0 else amountPaidInput
            val change = if (amountPaid > grandTotal) (amountPaid - grandTotal) else 0.0
            val remainingBalance = if (amountPaid < grandTotal) (grandTotal - amountPaid) else 0.0

            val customer = _selectedCustomer.value
            if (paymentMethod == "Credit" && customer == null) {
                showMessage("Credit sale requires selecting a registered customer account")
                return@launch
            }
            if (customer != null && remainingBalance > 0) {
                if ((customer.currentBalance + remainingBalance) > customer.creditLimit) {
                    showMessage("Customer credit limit exceeded! Limit: PKR ${customer.creditLimit}, Current Debt: PKR ${customer.currentBalance}")
                    return@launch
                }
            }

            val timestamp = System.currentTimeMillis()
            val invoiceNo = "INV-" + SimpleDateFormat("yyMMdd-HHmmss", Locale.getDefault()).format(Date(timestamp))

            val sale = Sale(
                invoiceNumber = invoiceNo,
                customerId = customer?.id,
                customerName = customer?.name ?: "Walk-in Customer",
                customerPhone = customer?.phone ?: "",
                cashierId = user.id,
                cashierName = user.fullName,
                saleDate = timestamp,
                subtotal = subtotal,
                discountAmount = invoiceDiscountAmt,
                discountPercent = _posInvoiceDiscount.value,
                taxAmount = taxAmt,
                grandTotal = grandTotal,
                amountPaid = amountPaid,
                changeGiven = change,
                remainingBalance = remainingBalance,
                paymentMethod = paymentMethod,
                mixedPaymentDetails = mixedDetails,
                status = "COMPLETED",
                notes = notes
            )

            val result = repository.completeSale(sale, items, user.username)
            result.onSuccess { savedSale ->
                lastCompletedSale.value = savedSale
                lastSaleItems.value = repository.getSaleItems(savedSale.id)
                clearCart()
                refreshDashboardMetrics()
                refreshExpiry()
                showMessage("Sale completed: Invoice ${savedSale.invoiceNumber}")
                onComplete(savedSale)
            }.onFailure { err ->
                showMessage("Sale failed: ${err.message}")
            }
        }
    }

    fun cancelSale(saleId: Long, reason: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repository.cancelSale(saleId, reason, user.username)
            res.onSuccess {
                showMessage("Sale #$saleId cancelled and inventory restored")
                refreshDashboardMetrics()
                refreshExpiry()
            }.onFailure {
                showMessage("Cancellation failed: ${it.message}")
            }
        }
    }

    fun processSalesReturn(
        saleId: Long,
        returnedItems: List<Pair<SaleItem, Int>>,
        reason: String,
        refundMethod: String
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repository.processSalesReturn(saleId, returnedItems, reason, refundMethod, user.username)
            res.onSuccess {
                showMessage("Return processed successfully. Stock returned to batches.")
                refreshDashboardMetrics()
                refreshExpiry()
            }.onFailure {
                showMessage("Return error: ${it.message}")
            }
        }
    }

    // --- Product & Inventory Management ---
    fun predictProductFromImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _isAiPredicting.value = true
            val result = AiProductPredictor.predictFromImage(bitmap)
            _isAiPredicting.value = false
            result.onSuccess { info ->
                _predictedProduct.value = info
                showMessage("AI Identified: ${info.productName}")
            }.onFailure { err ->
                showMessage("AI recognition failed: ${err.message}")
            }
        }
    }

    fun predictProductFromQuery(query: String) {
        viewModelScope.launch {
            _isAiPredicting.value = true
            val result = AiProductPredictor.predictFromText(query)
            _isAiPredicting.value = false
            result.onSuccess { info ->
                _predictedProduct.value = info
                showMessage("AI Suggested: ${info.productName}")
            }.onFailure { err ->
                showMessage("Suggestion failed: ${err.message}")
            }
        }
    }

    fun clearPredictedProduct() {
        _predictedProduct.value = null
    }

    fun saveProduct(product: Product, onComplete: () -> Unit = {}) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.saveProduct(product, user.username)
            refreshDashboardMetrics()
            refreshExpiry()
            showMessage("Product '${product.name}' saved")
            onComplete()
        }
    }

    fun saveProductWithInitialBatch(
        product: Product,
        initialBatch: Batch?,
        onComplete: () -> Unit = {}
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val productId = repository.saveProduct(product, user.username)
            if (initialBatch != null && initialBatch.batchNumber.isNotBlank() && initialBatch.expiryDate.isNotBlank()) {
                val batchToSave = initialBatch.copy(
                    productId = if (product.id != 0L) product.id else productId
                )
                repository.saveBatch(batchToSave, user.username)
            }
            refreshDashboardMetrics()
            refreshExpiry()
            showMessage("Product '${product.name}' saved with batch & expiry alert")
            onComplete()
        }
    }

    fun deleteProduct(productId: Long) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.softDeleteProduct(productId, user.username)
            refreshDashboardMetrics()
            showMessage("Product archived")
        }
    }

    fun saveBatch(batch: Batch, onComplete: () -> Unit = {}) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.saveBatch(batch, user.username)
            refreshDashboardMetrics()
            refreshExpiry()
            showMessage("Batch '${batch.batchNumber}' updated")
            onComplete()
        }
    }

    fun adjustStock(batchId: Long, adjustedQty: Int, reason: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val success = repository.adjustStock(batchId, adjustedQty, reason, user.username)
            if (success) {
                showMessage("Stock adjusted successfully")
                refreshDashboardMetrics()
                refreshExpiry()
            } else {
                showMessage("Adjustment failed. Check batch and stock values.")
            }
        }
    }

    // --- Purchases & Suppliers ---
    fun saveSupplier(supplier: Supplier, onComplete: () -> Unit = {}) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.saveSupplier(supplier, user.username)
            showMessage("Supplier saved: ${supplier.name}")
            onComplete()
        }
    }

    fun recordPurchase(purchase: Purchase, items: List<PurchaseItem>, onComplete: () -> Unit = {}) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repository.recordPurchase(purchase, items, user.username)
            res.onSuccess {
                showMessage("Purchase recorded. Inventory stock increased.")
                refreshDashboardMetrics()
                refreshExpiry()
                onComplete()
            }.onFailure {
                showMessage("Purchase failed: ${it.message}")
            }
        }
    }

    // --- Customers & Credit Collection ---
    fun saveCustomer(customer: Customer, onComplete: () -> Unit = {}) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.saveCustomer(customer, user.username)
            showMessage("Customer saved: ${customer.name}")
            onComplete()
        }
    }

    fun recordPartyPayment(
        partyType: String,
        partyId: Long,
        partyName: String,
        amount: Double,
        method: String,
        ref: String,
        notes: String
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.recordPayment(
                PaymentRecord(
                    partyType = partyType,
                    partyId = partyId,
                    partyName = partyName,
                    amount = amount,
                    paymentMethod = method,
                    referenceNumber = ref,
                    notes = notes,
                    recordedBy = user.fullName
                ),
                user.username
            )
            showMessage("Payment of PKR $amount recorded for $partyName")
            refreshDashboardMetrics()
        }
    }

    // --- Expenses & Daily Closing ---
    fun addExpense(expense: Expense, onComplete: () -> Unit = {}) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.addExpense(expense, user.username)
            showMessage("Expense '${expense.title}' recorded")
            refreshDashboardMetrics()
            onComplete()
        }
    }

    fun recordDailyClosing(
        openingCash: Double,
        actualCash: Double,
        notes: String,
        onComplete: (DailyClosing) -> Unit
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            val startOfDay = cal.timeInMillis
            val endOfDay = System.currentTimeMillis()

            val sales = allSales.value.filter { it.saleDate in startOfDay..endOfDay && it.status == "COMPLETED" }
            val cashSales = sales.filter { it.paymentMethod.equals("Cash", ignoreCase = true) }.sumOf { it.amountPaid }
            val expenses = allExpenses.value.filter { it.date in startOfDay..endOfDay && it.paymentMethod.equals("Cash", ignoreCase = true) }
            val cashExpenses = expenses.sumOf { it.amount }

            val expectedCash = openingCash + cashSales - cashExpenses
            val diff = actualCash - expectedCash

            val closing = DailyClosing(
                closingDate = todayStr,
                openingCash = openingCash,
                cashSales = cashSales,
                customerPaymentsCash = 0.0,
                refundsCash = 0.0,
                expensesCash = cashExpenses,
                expectedCash = expectedCash,
                actualCash = actualCash,
                cashDifference = diff,
                closedBy = user.fullName,
                notes = notes
            )
            repository.recordDailyClosing(closing, user.username)
            showMessage("Daily closing recorded. Cash variance: PKR $diff")
            onComplete(closing)
        }
    }

    // --- Expiry & Alerts ---
    fun refreshExpiry() {
        viewModelScope.launch {
            val summary = repository.getExpiryCategories()
            _expirySummary.value = summary

            val lowStockCount = productsWithStock.value.count { it.totalStock <= it.minStockLevel }
            val expiredCount = summary.expired.size
            val expiringSoonCount = summary.expiring30Days.size

            if (pharmacySettings.value.expiryNotificationEnabled) {
                NotificationHelper.showExpiryAlert(getApplication(), expiredCount, expiringSoonCount)
            }
            if (pharmacySettings.value.lowStockNotificationEnabled && lowStockCount > 0) {
                NotificationHelper.showLowStockAlert(getApplication(), lowStockCount)
            }
        }
    }

    // --- Dashboard Metrics Refresh ---
    fun refreshDashboardMetrics() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfDay = cal.timeInMillis
            val endOfDay = System.currentTimeMillis()

            val profitData = repository.calculateProfitBetween(startOfDay, endOfDay)
            val sales = allSales.value.filter { it.saleDate in startOfDay..endOfDay && it.status == "COMPLETED" }

            val cashSales = sales.filter { it.paymentMethod.equals("Cash", ignoreCase = true) }.sumOf { it.grandTotal }
            val creditSales = sales.filter { it.paymentMethod.equals("Credit", ignoreCase = true) || it.remainingBalance > 0 }.sumOf { it.remainingBalance }

            val totalInvVal = repository.getTotalInventoryValue()
            val totalProds = repository.getProductCount()
            val pendingCust = repository.getTotalPendingCustomerDebt()
            val pendingSupp = repository.getTotalPendingSupplierDebt()
            val topMed = repository.getTopSellingMedicines(6)

            val products = productsWithStock.value
            val lowStockCount = products.count { it.totalStock <= it.minStockLevel }

            val expiry = repository.getExpiryCategories()

            // Sales by category today
            val catMap = mutableMapOf<String, Double>()
            for (sale in sales) {
                val items = repository.getSaleItems(sale.id)
                for (item in items) {
                    val prod = products.find { it.id == item.productId }
                    val cat = prod?.category ?: "Other"
                    catMap[cat] = (catMap[cat] ?: 0.0) + item.subtotal
                }
            }

            _dashboardMetrics.value = DashboardMetrics(
                todaySales = profitData.totalSalesRevenue,
                todayInvoices = profitData.invoicesCount,
                todayProfit = profitData.grossProfit,
                todayExpenses = profitData.totalExpenses,
                todayNetIncome = profitData.netProfit,
                todayCashSales = cashSales,
                todayCreditSales = creditSales,
                totalInventoryValue = totalInvVal,
                totalProducts = totalProds,
                lowStockCount = lowStockCount,
                expiredCount = expiry.expired.size,
                expiringSoonCount = expiry.expiring30Days.size,
                pendingCustomerDebt = pendingCust,
                pendingSupplierDebt = pendingSupp,
                topSelling = topMed,
                categorySales = catMap
            )
        }
    }

    // --- Reports Range Calculation ---
    suspend fun getProfitReportForRange(filter: DateRangeFilter): ProfitReportData = withContext(Dispatchers.IO) {
        val (start, end) = getDateRangeTimestamps(filter)
        repository.calculateProfitBetween(start, end)
    }

    suspend fun getCashFlowReportForRange(filter: DateRangeFilter): CashFlowReportData = withContext(Dispatchers.IO) {
        val (start, end) = getDateRangeTimestamps(filter)
        repository.calculateCashFlowBetween(start, end)
    }

    suspend fun getCashFlowReportForSpecificDate(dateTimestamp: Long): CashFlowReportData = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dateTimestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis - 1
        repository.calculateCashFlowBetween(start, end)
    }

    suspend fun getStocksValuationData(): StocksValuationData = withContext(Dispatchers.IO) {
        repository.getStocksValuationData()
    }

    fun getDateRangeTimestamps(filter: DateRangeFilter): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        return when (filter) {
            DateRangeFilter.TODAY -> Pair(cal.timeInMillis, now)
            DateRangeFilter.YESTERDAY -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 1)
                Pair(start, cal.timeInMillis - 1)
            }
            DateRangeFilter.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                Pair(cal.timeInMillis, now)
            }
            DateRangeFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                Pair(cal.timeInMillis, now)
            }
            DateRangeFilter.THIS_YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                Pair(cal.timeInMillis, now)
            }
            DateRangeFilter.ALL_TIME -> Pair(0L, now)
        }
    }

    // --- Import & Export ---
    fun exportProductsCsv(context: android.content.Context): File {
        return ExcelUtil.exportProductsToCsv(context, productsWithStock.value)
    }

    fun exportSalesCsv(context: android.content.Context): File {
        return ExcelUtil.exportSalesToCsv(context, allSales.value)
    }

    fun exportExpensesCsv(context: android.content.Context): File {
        return ExcelUtil.exportExpensesToCsv(context, allExpenses.value)
    }

    fun previewCsvImport(csvText: String): ImportPreviewResult {
        val names = productsWithStock.value.map { it.name.lowercase() }.toSet()
        return ExcelUtil.parseAndValidateCsvImport(csvText, names)
    }

    fun applyCsvImport(validPairs: List<Pair<Product, Batch>>, onFinished: () -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            for ((prod, batch) in validPairs) {
                val prodId = repository.saveProduct(prod, user.username)
                repository.saveBatch(batch.copy(productId = prodId), user.username)
            }
            showMessage("Successfully imported ${validPairs.size} medicines into inventory")
            refreshDashboardMetrics()
            refreshExpiry()
            onFinished()
        }
    }

    // --- Backup & Restore ---
    fun createBackup(context: android.content.Context, onDone: (File) -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val db = PharmacyDatabase.getDatabase(getApplication(), viewModelScope)
            val file = BackupUtil.createBackup(context, db, user.username)
            showMessage("Backup created: ${file.name}")
            onDone(file)
        }
    }

    fun restoreBackup(backupFile: File, onDone: (Boolean, String) -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val db = PharmacyDatabase.getDatabase(getApplication(), viewModelScope)
            val res = BackupUtil.restoreBackup(backupFile, db, user.username)
            res.onSuccess {
                showMessage(it)
                refreshDashboardMetrics()
                refreshExpiry()
                onDone(true, it)
            }.onFailure {
                showMessage("Restore failed: ${it.message}")
                onDone(false, it.message ?: "Unknown error")
            }
        }
    }

    // --- Settings & Demo Data ---
    fun savePharmacySettings(settings: PharmacySettings) {
        viewModelScope.launch {
            repository.saveSettings(settings)
            if (settings.adminPin.isNotBlank()) {
                val adminUser = repository.getUserByUsername("admin")
                if (adminUser != null) {
                    repository.updateUser(adminUser.copy(pinHash = SecurityUtil.hashPin(settings.adminPin.trim())))
                }
            }
            showMessage("Pharmacy settings updated")
        }
    }

    fun seedDemoData() {
        viewModelScope.launch {
            repository.seedDemoData()
            refreshDashboardMetrics()
            refreshExpiry()
            showMessage("Sample pharmacy data loaded successfully!")
        }
    }
}
