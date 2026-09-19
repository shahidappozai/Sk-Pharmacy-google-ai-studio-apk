package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.BatchDao
import com.example.data.db.PharmacyDatabase
import com.example.data.db.TopSellingItem
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class PharmacyRepository(private val database: PharmacyDatabase) {
    private val userDao = database.userDao()
    private val productDao = database.productDao()
    private val batchDao = database.batchDao()
    private val saleDao = database.saleDao()
    private val purchaseDao = database.purchaseDao()
    private val customerDao = database.customerDao()
    private val supplierDao = database.supplierDao()
    private val paymentDao = database.paymentDao()
    private val expenseDao = database.expenseDao()
    private val returnDao = database.returnDao()
    private val stockAdjustmentDao = database.stockAdjustmentDao()
    private val dailyClosingDao = database.dailyClosingDao()
    private val auditDao = database.auditDao()
    private val settingsDao = database.settingsDao()

    // Users
    val allUsers: Flow<List<User>> = userDao.getAllUsers()
    suspend fun getUserByUsername(username: String): User? = userDao.getUserByUsername(username)
    suspend fun getUserById(id: Long): User? = userDao.getUserById(id)
    suspend fun insertUser(user: User): Long = userDao.insert(user)
    suspend fun updateUser(user: User) = userDao.update(user)

    // Settings
    val settingsFlow: Flow<PharmacySettings?> = settingsDao.getSettings()
    suspend fun getSettingsDirect(): PharmacySettings? = settingsDao.getSettingsDirect()
    suspend fun saveSettings(settings: PharmacySettings) {
        settingsDao.insertOrUpdate(settings)
        auditDao.insertLog(
            AuditLog(
                username = "Admin",
                action = "UPDATE_SETTINGS",
                affectedRecord = "PharmacySettings",
                details = "Updated pharmacy contact, tax, or alert parameters"
            )
        )
    }

    // Products & Batches
    val activeProducts: Flow<List<Product>> = productDao.getAllActiveProducts()
    val productsWithStock: Flow<List<ProductWithStock>> = productDao.getProductsWithStock()
    fun searchProducts(query: String): Flow<List<Product>> = productDao.searchProducts(query)
    suspend fun getProductByBarcode(barcode: String): Product? = productDao.getProductByBarcode(barcode)
    suspend fun getProductById(id: Long): Product? = productDao.getProductById(id)

    suspend fun saveProduct(product: Product, username: String): Long = withContext(Dispatchers.IO) {
        if (product.id == 0L) {
            val id = productDao.insert(product)
            auditDao.insertLog(
                AuditLog(
                    username = username,
                    action = "ADD_PRODUCT",
                    affectedRecord = "Product: ${product.name}",
                    details = "Category: ${product.category}, SKU: ${product.sku}"
                )
            )
            id
        } else {
            productDao.update(product)
            auditDao.insertLog(
                AuditLog(
                    username = username,
                    action = "EDIT_PRODUCT",
                    affectedRecord = "Product #${product.id}",
                    details = "Updated product ${product.name}"
                )
            )
            product.id
        }
    }

    suspend fun softDeleteProduct(productId: Long, username: String) = withContext(Dispatchers.IO) {
        productDao.softDeleteProduct(productId)
        auditDao.insertLog(
            AuditLog(
                username = username,
                action = "DELETE_PRODUCT",
                affectedRecord = "Product #$productId",
                details = "Soft deleted from inventory"
            )
        )
    }

    fun getBatchesForProduct(productId: Long): Flow<List<Batch>> = batchDao.getBatchesForProduct(productId)
    val allBatchesWithProduct: Flow<List<BatchWithProduct>> = batchDao.getAllBatchesWithProduct()

    suspend fun getValidFefoBatches(productId: Long): List<Batch> = withContext(Dispatchers.IO) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        batchDao.getValidFefoBatches(productId, todayStr)
    }

    suspend fun saveBatch(batch: Batch, username: String): Long = withContext(Dispatchers.IO) {
        if (batch.id == 0L) {
            val id = batchDao.insert(batch)
            auditDao.insertLog(
                AuditLog(
                    username = username,
                    action = "ADD_BATCH",
                    affectedRecord = "Batch: ${batch.batchNumber}",
                    details = "Stock: ${batch.currentStock}, Expiry: ${batch.expiryDate}"
                )
            )
            id
        } else {
            batchDao.update(batch)
            auditDao.insertLog(
                AuditLog(
                    username = username,
                    action = "EDIT_BATCH",
                    affectedRecord = "Batch #${batch.id}",
                    details = "Batch ${batch.batchNumber}, Stock: ${batch.currentStock}"
                )
            )
            batch.id
        }
    }

    // Stock Adjustment
    suspend fun adjustStock(
        batchId: Long,
        adjustedQty: Int,
        reason: String,
        username: String
    ): Boolean = withContext(Dispatchers.IO) {
        database.withTransaction {
            val batch = batchDao.getBatchById(batchId) ?: return@withTransaction false
            val product = productDao.getProductById(batch.productId) ?: return@withTransaction false
            val newStock = batch.currentStock + adjustedQty
            if (newStock < 0) return@withTransaction false

            batchDao.updateStock(batchId, newStock)
            stockAdjustmentDao.insertAdjustment(
                StockAdjustment(
                    productId = product.id,
                    productName = product.name,
                    batchId = batch.id,
                    batchNumber = batch.batchNumber,
                    systemStock = batch.currentStock,
                    adjustedQuantity = adjustedQty,
                    newStock = newStock,
                    reason = reason,
                    adjustedBy = username
                )
            )
            auditDao.insertLog(
                AuditLog(
                    username = username,
                    action = "STOCK_ADJUSTMENT",
                    affectedRecord = "${product.name} (Batch: ${batch.batchNumber})",
                    details = "Stock changed from ${batch.currentStock} to $newStock. Reason: $reason"
                )
            )
            true
        }
    }

    val allStockAdjustments: Flow<List<StockAdjustment>> = stockAdjustmentDao.getAllAdjustments()

    // POS & Atomic Sale Processing
    val allSales: Flow<List<Sale>> = saleDao.getAllSales()
    fun getSalesBetween(start: Long, end: Long): Flow<List<Sale>> = saleDao.getSalesBetween(start, end)
    suspend fun getSaleById(id: Long): Sale? = saleDao.getSaleById(id)
    suspend fun getSaleItems(saleId: Long): List<SaleItem> = saleDao.getSaleItems(saleId)

    suspend fun completeSale(
        sale: Sale,
        items: List<CartItem>,
        username: String
    ): Result<Sale> = withContext(Dispatchers.IO) {
        runCatching {
            database.withTransaction {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                // 1. Verify stock availability and expiration for every item
                for (item in items) {
                    val batch = batchDao.getBatchById(item.batch.id)
                        ?: error("Batch ${item.batch.batchNumber} not found")

                    if (batch.expiryDate < todayStr) {
                        error("Cannot sell expired medicine: ${item.product.name} (Batch: ${batch.batchNumber} expired on ${batch.expiryDate})")
                    }

                    if (batch.currentStock < item.quantity) {
                        error("Insufficient stock for ${item.product.name} (Batch: ${batch.batchNumber}). Requested: ${item.quantity}, Available: ${batch.currentStock}")
                    }
                }

                // 2. Insert Sale master record
                val saleId = saleDao.insertSale(sale)

                // 3. Insert SaleItems with true batch purchase cost and deduct batch stock
                val saleItems = items.map { item ->
                    val batch = batchDao.getBatchById(item.batch.id)!!
                    val newBatchStock = batch.currentStock - item.quantity
                    batchDao.updateStock(batch.id, newBatchStock)

                    SaleItem(
                        saleId = saleId,
                        productId = item.product.id,
                        productName = item.product.name,
                        batchId = batch.id,
                        batchNumber = batch.batchNumber,
                        expiryDate = batch.expiryDate,
                        quantity = item.quantity,
                        returnedQuantity = 0,
                        unitPrice = item.unitPrice,
                        unitPurchaseCost = batch.purchasePrice, // Actual batch cost for true profit!
                        discountAmount = item.discountAmount,
                        taxAmount = 0.0,
                        subtotal = item.subtotal
                    )
                }
                saleDao.insertSaleItems(saleItems)

                // 4. If credit payment or balance remaining, adjust customer balance
                if (sale.customerId != null && sale.remainingBalance > 0) {
                    customerDao.updateBalance(sale.customerId, sale.remainingBalance)
                }

                // 5. Audit log
                auditDao.insertLog(
                    AuditLog(
                        username = username,
                        action = "SALE",
                        affectedRecord = sale.invoiceNumber,
                        details = "Total: PKR ${sale.grandTotal}, Method: ${sale.paymentMethod}, Items: ${items.size}"
                    )
                )

                sale.copy(id = saleId)
            }
        }
    }

    suspend fun cancelSale(saleId: Long, reason: String, username: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            database.withTransaction {
                val sale = saleDao.getSaleById(saleId) ?: error("Sale #$saleId not found")
                if (sale.status == "CANCELLED") error("Sale is already cancelled")

                val items = saleDao.getSaleItems(saleId)
                // Restore stock to batches
                for (item in items) {
                    val availableToReturn = item.quantity - item.returnedQuantity
                    if (availableToReturn > 0) {
                        val batch = batchDao.getBatchById(item.batchId)
                        if (batch != null) {
                            batchDao.updateStock(batch.id, batch.currentStock + availableToReturn)
                        }
                    }
                }

                // If customer balance was charged, reverse it
                if (sale.customerId != null && sale.remainingBalance > 0) {
                    customerDao.updateBalance(sale.customerId, -sale.remainingBalance)
                }

                // Mark sale as cancelled
                saleDao.updateSale(
                    sale.copy(
                        status = "CANCELLED",
                        cancellationReason = reason,
                        cancelledBy = username
                    )
                )

                auditDao.insertLog(
                    AuditLog(
                        username = username,
                        action = "SALE_CANCELLATION",
                        affectedRecord = sale.invoiceNumber,
                        details = "Cancelled sale. Reason: $reason"
                    )
                )
                true
            }
        }
    }

    // Sales Return
    suspend fun processSalesReturn(
        saleId: Long,
        returnedItems: List<Pair<SaleItem, Int>>, // item and return qty
        reason: String,
        refundMethod: String,
        username: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            database.withTransaction {
                val sale = saleDao.getSaleById(saleId) ?: error("Sale not found")
                var totalRefund = 0.0

                for ((item, qty) in returnedItems) {
                    val maxReturnable = item.quantity - item.returnedQuantity
                    if (qty > maxReturnable) {
                        error("Cannot return $qty of ${item.productName}. Max returnable: $maxReturnable")
                    }
                    totalRefund += (item.unitPrice * qty)
                }

                val returnRecord = SalesReturn(
                    saleId = saleId,
                    invoiceNumber = sale.invoiceNumber,
                    customerId = sale.customerId,
                    customerName = sale.customerName,
                    totalRefundAmount = totalRefund,
                    refundMethod = refundMethod,
                    reason = reason,
                    processedBy = username
                )
                val returnId = returnDao.insertSalesReturn(returnRecord)

                val returnItems = returnedItems.map { (item, qty) ->
                    // Restore batch stock
                    val batch = batchDao.getBatchById(item.batchId)
                    if (batch != null) {
                        batchDao.updateStock(batch.id, batch.currentStock + qty)
                    }

                    // Update returned quantity on sale item
                    val updatedSaleItem = item.copy(returnedQuantity = item.returnedQuantity + qty)
                    // We don't have individual update for sale item, but we can update or keep track
                    SalesReturnItem(
                        returnId = returnId,
                        saleItemId = item.id,
                        productId = item.productId,
                        productName = item.productName,
                        batchId = item.batchId,
                        batchNumber = item.batchNumber,
                        quantityReturned = qty,
                        unitPrice = item.unitPrice,
                        refundSubtotal = item.unitPrice * qty
                    )
                }
                returnDao.insertSalesReturnItems(returnItems)

                // Adjust customer balance if it was credit
                if (sale.customerId != null && sale.paymentMethod == "Credit") {
                    customerDao.updateBalance(sale.customerId, -totalRefund)
                }

                saleDao.updateSale(sale.copy(status = "PARTIALLY_REFUNDED"))

                auditDao.insertLog(
                    AuditLog(
                        username = username,
                        action = "SALE_RETURN",
                        affectedRecord = "${sale.invoiceNumber} (Return #$returnId)",
                        details = "Refund: PKR $totalRefund, Items: ${returnedItems.size}"
                    )
                )

                returnId
            }
        }
    }

    val allSalesReturns: Flow<List<SalesReturn>> = returnDao.getAllSalesReturns()

    // Purchases & Suppliers
    val allPurchases: Flow<List<Purchase>> = purchaseDao.getAllPurchases()
    val allSuppliers: Flow<List<Supplier>> = supplierDao.getAllSuppliers()
    suspend fun getPurchaseItems(purchaseId: Long): List<PurchaseItem> = purchaseDao.getPurchaseItems(purchaseId)

    suspend fun saveSupplier(supplier: Supplier, username: String): Long = withContext(Dispatchers.IO) {
        if (supplier.id == 0L) {
            val id = supplierDao.insert(supplier)
            auditDao.insertLog(
                AuditLog(
                    username = username,
                    action = "ADD_SUPPLIER",
                    affectedRecord = supplier.name,
                    details = "Company: ${supplier.company}, Phone: ${supplier.phone}"
                )
            )
            id
        } else {
            supplierDao.update(supplier)
            supplierDao.insert(supplier)
            supplier.id
        }
    }

    suspend fun recordPurchase(
        purchase: Purchase,
        items: List<PurchaseItem>,
        username: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            database.withTransaction {
                val purchaseId = purchaseDao.insertPurchase(purchase)

                val purchaseItemsWithId = items.map { item ->
                    // Add or increase stock in the corresponding Batch
                    val existingBatches = batchDao.getBatchesForProductList(item.productId)
                    val existing = existingBatches.find { it.batchNumber.equals(item.batchNumber, ignoreCase = true) }
                    if (existing != null) {
                        batchDao.updateStock(existing.id, existing.currentStock + item.quantity)
                    } else {
                        batchDao.insert(
                            Batch(
                                productId = item.productId,
                                batchNumber = item.batchNumber,
                                manufacturingDate = item.manufacturingDate,
                                expiryDate = item.expiryDate,
                                purchasePrice = item.purchasePrice,
                                retailPrice = item.retailPrice,
                                currentStock = item.quantity,
                                supplierId = purchase.supplierId
                            )
                        )
                    }
                    item.copy(purchaseId = purchaseId)
                }

                purchaseDao.insertPurchaseItems(purchaseItemsWithId)

                // If balance due, update supplier currentBalance
                if (purchase.balanceDue > 0) {
                    supplierDao.updateBalance(purchase.supplierId, purchase.balanceDue)
                }

                auditDao.insertLog(
                    AuditLog(
                        username = username,
                        action = "PURCHASE",
                        affectedRecord = "Invoice: ${purchase.invoiceNumber} (${purchase.supplierName})",
                        details = "Total: PKR ${purchase.grandTotal}, Items: ${items.size}"
                    )
                )

                purchaseId
            }
        }
    }

    // Customers & Credit
    val allCustomers: Flow<List<Customer>> = customerDao.getAllCustomers()

    suspend fun saveCustomer(customer: Customer, username: String): Long = withContext(Dispatchers.IO) {
        if (customer.id == 0L) {
            val id = customerDao.insert(customer)
            auditDao.insertLog(
                AuditLog(
                    username = username,
                    action = "ADD_CUSTOMER",
                    affectedRecord = customer.name,
                    details = "Phone: ${customer.phone}, Credit Limit: PKR ${customer.creditLimit}"
                )
            )
            id
        } else {
            customerDao.update(customer)
            customer.id
        }
    }

    suspend fun recordPayment(
        payment: PaymentRecord,
        username: String
    ): Long = withContext(Dispatchers.IO) {
        database.withTransaction {
            val id = paymentDao.insertPayment(payment)
            if (payment.partyType == "CUSTOMER") {
                // Customer paying pharmacy debt reduces customer balance
                customerDao.updateBalance(payment.partyId, -payment.amount)
            } else if (payment.partyType == "SUPPLIER") {
                // Pharmacy paying supplier reduces supplier balance
                supplierDao.updateBalance(payment.partyId, -payment.amount)
            }

            auditDao.insertLog(
                AuditLog(
                    username = username,
                    action = "PAYMENT_${payment.partyType}",
                    affectedRecord = "${payment.partyName} (PKR ${payment.amount})",
                    details = "Method: ${payment.paymentMethod}, Ref: ${payment.referenceNumber}"
                )
            )
            id
        }
    }

    fun getPaymentsForParty(type: String, id: Long): Flow<List<PaymentRecord>> = paymentDao.getPaymentsForParty(type, id)

    // Expenses
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()
    fun getExpensesBetween(start: Long, end: Long): Flow<List<Expense>> = expenseDao.getExpensesBetween(start, end)

    suspend fun addExpense(expense: Expense, username: String): Long = withContext(Dispatchers.IO) {
        val id = expenseDao.insertExpense(expense)
        auditDao.insertLog(
            AuditLog(
                username = username,
                action = "EXPENSE",
                affectedRecord = "${expense.title} (PKR ${expense.amount})",
                details = "Category: ${expense.category}, Method: ${expense.paymentMethod}"
            )
        )
        id
    }

    // Daily Closing
    val allDailyClosings: Flow<List<DailyClosing>> = dailyClosingDao.getAllClosings()
    suspend fun getDailyClosing(date: String): DailyClosing? = dailyClosingDao.getClosingForDate(date)

    suspend fun recordDailyClosing(closing: DailyClosing, username: String): Long = withContext(Dispatchers.IO) {
        val id = dailyClosingDao.insertClosing(closing)
        auditDao.insertLog(
            AuditLog(
                username = username,
                action = "DAILY_CLOSING",
                affectedRecord = "Closing Date: ${closing.closingDate}",
                details = "Expected: PKR ${closing.expectedCash}, Actual: PKR ${closing.actualCash}, Diff: PKR ${closing.cashDifference}"
            )
        )
        id
    }

    // Audit Logs
    val allAuditLogs: Flow<List<AuditLog>> = auditDao.getAllLogs()
    suspend fun logAction(username: String, action: String, record: String, details: String) {
        auditDao.insertLog(
            AuditLog(
                username = username,
                action = action,
                affectedRecord = record,
                details = details
            )
        )
    }

    // Dashboard & Reports Aggregations
    suspend fun getTopSellingMedicines(limit: Int = 6): List<TopSellingItem> = saleDao.getTopSellingMedicines(limit)
    suspend fun getTotalPendingCustomerDebt(): Double = customerDao.getTotalPendingCustomerDebt() ?: 0.0
    suspend fun getTotalPendingSupplierDebt(): Double = supplierDao.getTotalPendingSupplierDebt() ?: 0.0
    suspend fun getTotalInventoryValue(): Double = batchDao.getTotalInventoryRetailValue() ?: 0.0
    suspend fun getTotalInventoryCostValue(): Double = batchDao.getTotalInventoryPurchaseValue() ?: 0.0
    suspend fun getProductCount(): Int = productDao.getProductCount()

    // Stocks Valuation Breakdown
    suspend fun getStocksValuationData(): StocksValuationData = withContext(Dispatchers.IO) {
        val retailVal = batchDao.getTotalInventoryRetailValue() ?: 0.0
        val costVal = batchDao.getTotalInventoryPurchaseValue() ?: 0.0
        val totalProds = productDao.getProductCount()
        val allBatches = batchDao.getAllBatchesWithProductList()
        val inStockBatches = allBatches.filter { it.currentStock > 0 }
        val totalUnits = inStockBatches.sumOf { it.currentStock }
        val potentialProfit = (retailVal - costVal).coerceAtLeast(0.0)
        val marginPercent = if (retailVal > 0) (potentialProfit / retailVal) * 100 else 0.0

        // Valuation by category
        val categoryCostMap = mutableMapOf<String, Double>()
        val categoryRetailMap = mutableMapOf<String, Double>()
        val categoryUnitsMap = mutableMapOf<String, Int>()

        for (b in inStockBatches) {
            val cat = b.category.ifBlank { "Other" }
            categoryCostMap[cat] = (categoryCostMap[cat] ?: 0.0) + (b.purchasePrice * b.currentStock)
            categoryRetailMap[cat] = (categoryRetailMap[cat] ?: 0.0) + (b.retailPrice * b.currentStock)
            categoryUnitsMap[cat] = (categoryUnitsMap[cat] ?: 0) + b.currentStock
        }

        val categoryList = categoryRetailMap.keys.map { cat ->
            CategoryStockValuation(
                category = cat,
                retailValue = categoryRetailMap[cat] ?: 0.0,
                costValue = categoryCostMap[cat] ?: 0.0,
                totalUnits = categoryUnitsMap[cat] ?: 0
            )
        }.sortedByDescending { it.retailValue }

        StocksValuationData(
            totalRetailValue = retailVal,
            totalCostValue = costVal,
            potentialProfit = potentialProfit,
            potentialMarginPercent = marginPercent,
            totalProductsCount = totalProds,
            totalActiveBatches = inStockBatches.size,
            totalUnitsInStock = totalUnits,
            categoryBreakdown = categoryList
        )
    }

    // In & Out of Money (Cashflow Ledger)
    suspend fun calculateCashFlowBetween(startDate: Long, endDate: Long): CashFlowReportData = withContext(Dispatchers.IO) {
        val completedSales = saleDao.getSalesListBetween(startDate, endDate).filter { it.status != "CANCELLED" }
        val cashSales = completedSales.filter { it.paymentMethod.equals("Cash", ignoreCase = true) }.sumOf { it.amountPaid }
        val cardBankSales = completedSales.filter { !it.paymentMethod.equals("Cash", ignoreCase = true) && !it.paymentMethod.equals("Credit", ignoreCase = true) }.sumOf { it.amountPaid }
        val creditSalesGiven = completedSales.sumOf { it.remainingBalance }

        val customerPayments = paymentDao.getPaymentsBetween(startDate, endDate).filter { it.partyType == "CUSTOMER" }
        val customerCreditCollections = customerPayments.sumOf { it.amount }

        val salesReturns = returnDao.getAllSalesReturns() // Filter by date
        val returnsList = saleDao.getSalesListBetween(startDate, endDate) // using sales return query or payments
        val allSalesReturnsList = mutableListOf<SalesReturn>()
        // Let's get returns if any
        val totalMoneyIn = cashSales + cardBankSales + customerCreditCollections

        // MONEY OUT
        val expenses = expenseDao.getExpensesListBetween(startDate, endDate)
        val totalExpenses = expenses.sumOf { it.amount }
        val expensesByCategory = expenses.groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.amount } }

        val purchases = purchaseDao.getPurchasesListBetween(startDate, endDate).filter { it.status != "CANCELLED" }
        val purchasesPaid = purchases.sumOf { it.paidAmount }

        val supplierPayments = paymentDao.getPaymentsBetween(startDate, endDate).filter { it.partyType == "SUPPLIER" }
        val supplierPaymentsAmount = supplierPayments.sumOf { it.amount }

        val totalMoneyOut = totalExpenses + purchasesPaid + supplierPaymentsAmount
        val netCashFlow = totalMoneyIn - totalMoneyOut

        CashFlowReportData(
            startDate = startDate,
            endDate = endDate,
            // Inflow
            cashSales = cashSales,
            digitalBankSales = cardBankSales,
            customerDebtCollected = customerCreditCollections,
            totalMoneyIn = totalMoneyIn,
            // Outflow
            expensesTotal = totalExpenses,
            purchasesCashPaid = purchasesPaid,
            supplierDebtPaid = supplierPaymentsAmount,
            totalMoneyOut = totalMoneyOut,
            // Balance
            netCashFlow = netCashFlow,
            expensesByCategory = expensesByCategory
        )
    }

    // TRUE BATCH COGS Profit Calculation
    suspend fun calculateProfitBetween(startDate: Long, endDate: Long): ProfitReportData = withContext(Dispatchers.IO) {
        val sales = saleDao.getSalesListBetween(startDate, endDate).filter { it.status != "CANCELLED" }
        var totalSalesRevenue = 0.0
        var totalCogs = 0.0
        var totalDiscounts = 0.0

        for (sale in sales) {
            totalSalesRevenue += sale.subtotal
            totalDiscounts += sale.discountAmount
            val items = saleDao.getSaleItems(sale.id)
            for (item in items) {
                val effectiveSold = item.quantity - item.returnedQuantity
                if (effectiveSold > 0) {
                    totalCogs += (item.unitPurchaseCost * effectiveSold)
                }
            }
        }

        val expenses = expenseDao.getExpensesListBetween(startDate, endDate)
        val totalExpenses = expenses.sumOf { it.amount }

        val grossProfit = totalSalesRevenue - totalCogs - totalDiscounts
        val netProfit = grossProfit - totalExpenses

        ProfitReportData(
            startDate = startDate,
            endDate = endDate,
            totalSalesRevenue = totalSalesRevenue,
            totalCogs = totalCogs,
            totalDiscounts = totalDiscounts,
            grossProfit = grossProfit,
            totalExpenses = totalExpenses,
            netProfit = netProfit,
            invoicesCount = sales.size
        )
    }

    // Expiry Queries
    suspend fun getExpiryCategories(): ExpirySummary = withContext(Dispatchers.IO) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val todayStr = sdf.format(cal.time)

        cal.add(Calendar.DAY_OF_YEAR, 7)
        val in7Days = sdf.format(cal.time)

        cal.add(Calendar.DAY_OF_YEAR, 23) // 30 days
        val in30Days = sdf.format(cal.time)

        cal.add(Calendar.DAY_OF_YEAR, 30) // 60 days
        val in60Days = sdf.format(cal.time)

        cal.add(Calendar.DAY_OF_YEAR, 30) // 90 days
        val in90Days = sdf.format(cal.time)

        val allBatches = batchDao.getAllBatchesWithProductList().filter { it.currentStock > 0 }

        val expired = allBatches.filter { it.expiryDate < todayStr }
        val expiringToday = allBatches.filter { it.expiryDate == todayStr }
        val expiring7Days = allBatches.filter { it.expiryDate > todayStr && it.expiryDate <= in7Days }
        val expiring30Days = allBatches.filter { it.expiryDate > in7Days && it.expiryDate <= in30Days }
        val expiring60Days = allBatches.filter { it.expiryDate > in30Days && it.expiryDate <= in60Days }
        val expiring90Days = allBatches.filter { it.expiryDate > in60Days && it.expiryDate <= in90Days }

        ExpirySummary(
            expired = expired,
            expiringToday = expiringToday,
            expiring7Days = expiring7Days,
            expiring30Days = expiring30Days,
            expiring60Days = expiring60Days,
            expiring90Days = expiring90Days
        )
    }

    // Seed Demo Pharmacy Data
    suspend fun seedDemoData() = withContext(Dispatchers.IO) {
        database.withTransaction {
            val sampleSuppliers = listOf(
                Supplier(name = "Ali Medico Distributors", company = "GSK & Abbott Distributor", phone = "+92 321 4455667", currentBalance = 15000.0),
                Supplier(name = "Care Pharmaceuticals", company = "Pfizer & Getz Agent", phone = "+92 333 8877665", currentBalance = 8500.0),
                Supplier(name = "Lahore Health Wholesalers", company = "Sami & Hilton Pharma", phone = "+92 300 9988776", currentBalance = 0.0)
            )
            val supIds = sampleSuppliers.map { supplierDao.insert(it) }

            val sampleCustomers = listOf(
                Customer(name = "Muhammad Arshad", phone = "+92 300 1122334", creditLimit = 20000.0, currentBalance = 3200.0),
                Customer(name = "Dr. Farhan Qureshi", phone = "+92 321 9988771", creditLimit = 50000.0, currentBalance = 7500.0),
                Customer(name = "Mrs. Shaheen Begum", phone = "+92 334 5566778", creditLimit = 15000.0, currentBalance = 0.0)
            )
            sampleCustomers.forEach { customerDao.insert(it) }

            val demoProducts = listOf(
                Product(name = "Panadol 500mg", genericName = "Paracetamol", brand = "Panadol", category = "Tablets", dosageForm = "Tablet", strength = "500mg", packSize = "20x10", manufacturer = "GSK", retailPrice = 30.0, mrp = 35.0, minStockLevel = 25, rackLocation = "A-1", barcode = "896400011221"),
                Product(name = "Augmentin 625mg", genericName = "Co-Amoxiclav", brand = "Augmentin", category = "Tablets", dosageForm = "Tablet", strength = "625mg", packSize = "1x14", manufacturer = "GSK", retailPrice = 320.0, mrp = 340.0, minStockLevel = 15, rackLocation = "A-2", barcode = "896400011222"),
                Product(name = "Brufen 400mg", genericName = "Ibuprofen", brand = "Brufen", category = "Tablets", dosageForm = "Tablet", strength = "400mg", packSize = "25x10", manufacturer = "Abbott", retailPrice = 45.0, mrp = 50.0, minStockLevel = 20, rackLocation = "A-3", barcode = "896400011223"),
                Product(name = "Flagyl 400mg", genericName = "Metronidazole", brand = "Flagyl", category = "Tablets", dosageForm = "Tablet", strength = "400mg", packSize = "20x10", manufacturer = "Sanofi", retailPrice = 40.0, mrp = 45.0, minStockLevel = 15, rackLocation = "B-1", barcode = "896400011224"),
                Product(name = "Risek 40mg", genericName = "Omeprazole", brand = "Risek", category = "Capsules", dosageForm = "Capsule", strength = "40mg", packSize = "2x7", manufacturer = "Getz Pharma", retailPrice = 420.0, mrp = 450.0, minStockLevel = 10, rackLocation = "B-2", barcode = "896400011225"),
                Product(name = "Arinac Forte", genericName = "Ibuprofen + Pseudoephedrine", brand = "Arinac", category = "Tablets", dosageForm = "Tablet", strength = "400mg/60mg", packSize = "10x10", manufacturer = "Abbott", retailPrice = 90.0, mrp = 100.0, minStockLevel = 15, rackLocation = "B-3", barcode = "896400011226"),
                Product(name = "Hydryllin Syrup", genericName = "Diphenhydramine + Aminophylline", brand = "Hydryllin", category = "Syrups", dosageForm = "Syrup", strength = "120ml", packSize = "1 Bottle", manufacturer = "Searle", retailPrice = 110.0, mrp = 120.0, minStockLevel = 12, rackLocation = "C-1", barcode = "896400011227"),
                Product(name = "Disprin 300mg", genericName = "Aspirin", brand = "Disprin", category = "Tablets", dosageForm = "Soluble Tablet", strength = "300mg", packSize = "30x10", manufacturer = "Reckitt", retailPrice = 25.0, mrp = 28.0, minStockLevel = 30, rackLocation = "C-2", barcode = "896400011228"),
                Product(name = "Gaviscon Liquid", genericName = "Sodium Alginate", brand = "Gaviscon", category = "Suspensions", dosageForm = "Suspension", strength = "120ml", packSize = "1 Bottle", manufacturer = "Reckitt", retailPrice = 195.0, mrp = 210.0, minStockLevel = 8, rackLocation = "C-3", barcode = "896400011229"),
                Product(name = "Voltral Emulgel", genericName = "Diclofenac Diethylamine", brand = "Voltral", category = "Creams", dosageForm = "Gel", strength = "20g", packSize = "1 Tube", manufacturer = "GSK", retailPrice = 145.0, mrp = 160.0, minStockLevel = 10, rackLocation = "D-1", barcode = "896400011230"),
                Product(name = "Cac 1000 Plus", genericName = "Calcium + Vitamin C + D3", brand = "CaC 1000", category = "Supplements", dosageForm = "Effervescent Tablet", strength = "10 Tablets", packSize = "1 Tube", manufacturer = "GSK", retailPrice = 280.0, mrp = 300.0, minStockLevel = 15, rackLocation = "D-2", barcode = "896400011231"),
                Product(name = "Softin 10mg", genericName = "Loratadine", brand = "Softin", category = "Tablets", dosageForm = "Tablet", strength = "10mg", packSize = "10x10", manufacturer = "Hilton", retailPrice = 60.0, mrp = 68.0, minStockLevel = 10, rackLocation = "D-3", barcode = "896400011232")
            )

            val pIds = demoProducts.map { productDao.insert(it) }

            // Batches: Multi-batch setup with FEFO tests
            // Panadol: Batch A (earlier expiry) and Batch B (later expiry)
            batchDao.insert(Batch(productId = pIds[0], batchNumber = "PAN-2024A", manufacturingDate = "2024-01-10", expiryDate = "2026-11-30", purchasePrice = 22.0, retailPrice = 30.0, currentStock = 35, supplierId = supIds[0]))
            batchDao.insert(Batch(productId = pIds[0], batchNumber = "PAN-2025B", manufacturingDate = "2025-02-01", expiryDate = "2027-08-30", purchasePrice = 24.0, retailPrice = 30.0, currentStock = 60, supplierId = supIds[0]))

            // Augmentin: Batch 1 (expires in 20 days - test alert!), Batch 2 (fresh)
            val cal = Calendar.getInstance()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            cal.add(Calendar.DAY_OF_YEAR, 20)
            val nearExpiry = sdf.format(cal.time)
            batchDao.insert(Batch(productId = pIds[1], batchNumber = "AUG-24X", manufacturingDate = "2024-03-01", expiryDate = nearExpiry, purchasePrice = 260.0, retailPrice = 320.0, currentStock = 12, supplierId = supIds[1]))
            batchDao.insert(Batch(productId = pIds[1], batchNumber = "AUG-25Y", manufacturingDate = "2025-01-15", expiryDate = "2027-12-31", purchasePrice = 270.0, retailPrice = 320.0, currentStock = 25, supplierId = supIds[1]))

            // Brufen
            batchDao.insert(Batch(productId = pIds[2], batchNumber = "BRU-887", manufacturingDate = "2024-05-10", expiryDate = "2027-05-10", purchasePrice = 34.0, retailPrice = 45.0, currentStock = 40, supplierId = supIds[0]))

            // Flagyl
            batchDao.insert(Batch(productId = pIds[3], batchNumber = "FLG-102", manufacturingDate = "2024-06-01", expiryDate = "2027-06-01", purchasePrice = 30.0, retailPrice = 40.0, currentStock = 50, supplierId = supIds[2]))

            // Risek (low stock: 4 left - triggers low stock alert!)
            batchDao.insert(Batch(productId = pIds[4], batchNumber = "RSK-990", manufacturingDate = "2024-08-10", expiryDate = "2026-12-15", purchasePrice = 340.0, retailPrice = 420.0, currentStock = 4, supplierId = supIds[1]))

            // Arinac
            batchDao.insert(Batch(productId = pIds[5], batchNumber = "ARN-401", manufacturingDate = "2024-09-01", expiryDate = "2027-09-01", purchasePrice = 70.0, retailPrice = 90.0, currentStock = 30, supplierId = supIds[0]))

            // Hydryllin
            batchDao.insert(Batch(productId = pIds[6], batchNumber = "HYD-552", manufacturingDate = "2024-10-15", expiryDate = "2026-10-15", purchasePrice = 85.0, retailPrice = 110.0, currentStock = 18, supplierId = supIds[2]))

            // Disprin (Expired batch to test Expiry tab & sell protection!)
            batchDao.insert(Batch(productId = pIds[7], batchNumber = "DSP-OLD", manufacturingDate = "2023-01-01", expiryDate = "2024-01-01", purchasePrice = 18.0, retailPrice = 25.0, currentStock = 10, supplierId = supIds[0]))
            batchDao.insert(Batch(productId = pIds[7], batchNumber = "DSP-FRESH", manufacturingDate = "2025-01-01", expiryDate = "2028-01-01", purchasePrice = 19.0, retailPrice = 25.0, currentStock = 80, supplierId = supIds[0]))

            // Gaviscon
            batchDao.insert(Batch(productId = pIds[8], batchNumber = "GAV-332", manufacturingDate = "2024-11-01", expiryDate = "2027-04-30", purchasePrice = 150.0, retailPrice = 195.0, currentStock = 14, supplierId = supIds[1]))

            // Voltral
            batchDao.insert(Batch(productId = pIds[9], batchNumber = "VOL-118", manufacturingDate = "2024-07-20", expiryDate = "2026-12-30", purchasePrice = 115.0, retailPrice = 145.0, currentStock = 22, supplierId = supIds[0]))

            // CaC 1000
            batchDao.insert(Batch(productId = pIds[10], batchNumber = "CAC-776", manufacturingDate = "2024-12-01", expiryDate = "2027-01-31", purchasePrice = 220.0, retailPrice = 280.0, currentStock = 35, supplierId = supIds[0]))

            // Softin
            batchDao.insert(Batch(productId = pIds[11], batchNumber = "SFT-501", manufacturingDate = "2024-04-10", expiryDate = "2027-04-10", purchasePrice = 45.0, retailPrice = 60.0, currentStock = 28, supplierId = supIds[2]))

            // Sample Expenses
            expenseDao.insertExpense(Expense(title = "Pharmacy Electricity Bill", category = "Electricity", amount = 14500.0, paymentMethod = "Bank Transfer", description = "LESCO commercial bill", recordedBy = "Admin"))
            expenseDao.insertExpense(Expense(title = "Shop Maintenance & AC Filter", category = "Maintenance", amount = 2500.0, paymentMethod = "Cash", description = "AC servicing", recordedBy = "Admin"))

            auditDao.insertLog(
                AuditLog(
                    username = "admin",
                    action = "DEMO_DATA_SEEDED",
                    affectedRecord = "Full Pharmacy Inventory",
                    details = "Seeded 12 medicines with multi-batch FEFO inventory, suppliers, customers, and expenses"
                )
            )
        }
    }
}

data class ProfitReportData(
    val startDate: Long,
    val endDate: Long,
    val totalSalesRevenue: Double,
    val totalCogs: Double,
    val totalDiscounts: Double,
    val grossProfit: Double,
    val totalExpenses: Double,
    val netProfit: Double,
    val invoicesCount: Int
)

data class CashFlowReportData(
    val startDate: Long,
    val endDate: Long,
    // In
    val cashSales: Double,
    val digitalBankSales: Double,
    val customerDebtCollected: Double,
    val totalMoneyIn: Double,
    // Out
    val expensesTotal: Double,
    val purchasesCashPaid: Double,
    val supplierDebtPaid: Double,
    val totalMoneyOut: Double,
    // Net
    val netCashFlow: Double,
    val expensesByCategory: Map<String, Double> = emptyMap()
)

data class CategoryStockValuation(
    val category: String,
    val retailValue: Double,
    val costValue: Double,
    val totalUnits: Int
)

data class StocksValuationData(
    val totalRetailValue: Double,
    val totalCostValue: Double,
    val potentialProfit: Double,
    val potentialMarginPercent: Double,
    val totalProductsCount: Int,
    val totalActiveBatches: Int,
    val totalUnitsInStock: Int,
    val categoryBreakdown: List<CategoryStockValuation> = emptyList()
)

data class ExpirySummary(
    val expired: List<BatchWithProduct>,
    val expiringToday: List<BatchWithProduct>,
    val expiring7Days: List<BatchWithProduct>,
    val expiring30Days: List<BatchWithProduct>,
    val expiring60Days: List<BatchWithProduct>,
    val expiring90Days: List<BatchWithProduct>
)
