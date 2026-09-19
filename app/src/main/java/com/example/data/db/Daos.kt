package com.example.data.db

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

data class TopSellingItem(
    val productId: Long,
    val productName: String,
    val totalSold: Int,
    val totalRevenue: Double
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY id ASC")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Long): User?

    @Query("SELECT * FROM users WHERE username = :username COLLATE NOCASE LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User): Long

    @Update
    suspend fun update(user: User)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllActiveProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE isDeleted = 0")
    suspend fun getAllActiveProductsList(): List<Product>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): Product?

    @Query("SELECT * FROM products WHERE barcode = :barcode AND isDeleted = 0 LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): Product?

    @Query("SELECT * FROM products WHERE (name LIKE '%' || :query || '%' OR genericName LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%' OR barcode = :query OR sku = :query) AND isDeleted = 0 ORDER BY name ASC")
    fun searchProducts(query: String): Flow<List<Product>>

    @Query("""
        SELECT p.id, p.barcode, p.sku, p.name, p.genericName, p.brand, p.category, 
               p.dosageForm, p.strength, p.packSize, p.manufacturer, p.supplierName, 
               p.retailPrice, p.mrp, p.wholesalePrice, p.minStockLevel, p.maxStockLevel, 
               p.rackLocation, p.taxPercent, p.defaultDiscountPercent, p.notes, p.isDeleted,
               p.expiryAlertDays, p.imageUri,
               COALESCE(SUM(b.currentStock), 0) as totalStock,
               MIN(b.expiryDate) as earliestExpiry,
               MIN(b.alertDate) as earliestAlertDate
        FROM products p
        LEFT JOIN batches b ON p.id = b.productId
        WHERE p.isDeleted = 0
        GROUP BY p.id
        ORDER BY p.name ASC
    """)
    fun getProductsWithStock(): Flow<List<ProductWithStock>>

    @Query("""
        SELECT p.id, p.barcode, p.sku, p.name, p.genericName, p.brand, p.category, 
               p.dosageForm, p.strength, p.packSize, p.manufacturer, p.supplierName, 
               p.retailPrice, p.mrp, p.wholesalePrice, p.minStockLevel, p.maxStockLevel, 
               p.rackLocation, p.taxPercent, p.defaultDiscountPercent, p.notes, p.isDeleted,
               p.expiryAlertDays, p.imageUri,
               COALESCE(SUM(b.currentStock), 0) as totalStock,
               MIN(b.expiryDate) as earliestExpiry,
               MIN(b.alertDate) as earliestAlertDate
        FROM products p
        LEFT JOIN batches b ON p.id = b.productId
        WHERE p.isDeleted = 0
        GROUP BY p.id
        ORDER BY p.name ASC
    """)
    suspend fun getProductsWithStockList(): List<ProductWithStock>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: Product): Long

    @Update
    suspend fun update(product: Product)

    @Query("UPDATE products SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteProduct(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM products WHERE isDeleted = 0")
    suspend fun getProductCount(): Int
}

@Dao
interface BatchDao {
    @Query("SELECT * FROM batches WHERE productId = :productId ORDER BY expiryDate ASC")
    fun getBatchesForProduct(productId: Long): Flow<List<Batch>>

    @Query("SELECT * FROM batches WHERE productId = :productId ORDER BY expiryDate ASC")
    suspend fun getBatchesForProductList(productId: Long): List<Batch>

    // FEFO: First Expiry, First Out. Excludes expired batches (expiryDate >= currentDate) and currentStock > 0
    @Query("SELECT * FROM batches WHERE productId = :productId AND expiryDate >= :currentDate AND currentStock > 0 ORDER BY expiryDate ASC")
    suspend fun getValidFefoBatches(productId: Long, currentDate: String): List<Batch>

    @Query("""
        SELECT b.id as batchId, b.productId, p.name as productName, p.genericName, p.category,
               b.batchNumber, b.manufacturingDate, b.expiryDate, b.purchasePrice, b.retailPrice, b.currentStock,
               b.alertDate
        FROM batches b
        INNER JOIN products p ON b.productId = p.id
        WHERE p.isDeleted = 0
        ORDER BY b.expiryDate ASC
    """)
    fun getAllBatchesWithProduct(): Flow<List<BatchWithProduct>>

    @Query("""
        SELECT b.id as batchId, b.productId, p.name as productName, p.genericName, p.category,
               b.batchNumber, b.manufacturingDate, b.expiryDate, b.purchasePrice, b.retailPrice, b.currentStock,
               b.alertDate
        FROM batches b
        INNER JOIN products p ON b.productId = p.id
        WHERE p.isDeleted = 0
        ORDER BY b.expiryDate ASC
    """)
    suspend fun getAllBatchesWithProductList(): List<BatchWithProduct>

    @Query("SELECT * FROM batches WHERE id = :batchId LIMIT 1")
    suspend fun getBatchById(batchId: Long): Batch?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(batch: Batch): Long

    @Update
    suspend fun update(batch: Batch)

    @Delete
    suspend fun delete(batch: Batch)

    @Query("UPDATE batches SET currentStock = :newStock WHERE id = :batchId")
    suspend fun updateStock(batchId: Long, newStock: Int)

    @Query("SELECT SUM(currentStock * purchasePrice) FROM batches")
    suspend fun getTotalInventoryPurchaseValue(): Double?

    @Query("SELECT SUM(currentStock * retailPrice) FROM batches")
    suspend fun getTotalInventoryRetailValue(): Double?
}

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY saleDate DESC")
    fun getAllSales(): Flow<List<Sale>>

    @Query("SELECT * FROM sales ORDER BY saleDate DESC")
    suspend fun getAllSalesList(): List<Sale>

    @Query("SELECT * FROM sales WHERE id = :id LIMIT 1")
    suspend fun getSaleById(id: Long): Sale?

    @Query("SELECT * FROM sales WHERE invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun getSaleByInvoice(invoiceNumber: String): Sale?

    @Query("SELECT * FROM sales WHERE saleDate >= :startDate AND saleDate <= :endDate ORDER BY saleDate DESC")
    fun getSalesBetween(startDate: Long, endDate: Long): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE saleDate >= :startDate AND saleDate <= :endDate ORDER BY saleDate DESC")
    suspend fun getSalesListBetween(startDate: Long, endDate: Long): List<Sale>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: Sale): Long

    @Update
    suspend fun updateSale(sale: Sale)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItem>)

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getSaleItems(saleId: Long): List<SaleItem>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    fun getSaleItemsFlow(saleId: Long): Flow<List<SaleItem>>

    @Query("""
        SELECT sale_items.productId as productId, sale_items.productName as productName, CAST(SUM(sale_items.quantity - sale_items.returnedQuantity) AS INTEGER) as totalSold, SUM(sale_items.subtotal) as totalRevenue
        FROM sale_items
        INNER JOIN sales ON sale_items.saleId = sales.id
        WHERE sales.status = 'COMPLETED'
        GROUP BY sale_items.productId, sale_items.productName
        ORDER BY totalSold DESC
        LIMIT :limit
    """)
    suspend fun getTopSellingMedicines(limit: Int = 6): List<TopSellingItem>

    @Query("SELECT COUNT(*) FROM sales WHERE saleDate >= :startOfDay AND saleDate <= :endOfDay AND status = 'COMPLETED'")
    suspend fun getTodayInvoicesCount(startOfDay: Long, endOfDay: Long): Int
}

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchases ORDER BY purchaseDate DESC")
    fun getAllPurchases(): Flow<List<Purchase>>

    @Query("SELECT * FROM purchases ORDER BY purchaseDate DESC")
    suspend fun getAllPurchasesList(): List<Purchase>

    @Query("SELECT * FROM purchases WHERE id = :id LIMIT 1")
    suspend fun getPurchaseById(id: Long): Purchase?

    @Query("SELECT * FROM purchases WHERE purchaseDate >= :startDate AND purchaseDate <= :endDate ORDER BY purchaseDate DESC")
    fun getPurchasesBetween(startDate: Long, endDate: Long): Flow<List<Purchase>>

    @Query("SELECT * FROM purchases WHERE purchaseDate >= :startDate AND purchaseDate <= :endDate ORDER BY purchaseDate DESC")
    suspend fun getPurchasesListBetween(startDate: Long, endDate: Long): List<Purchase>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: Purchase): Long

    @Update
    suspend fun updatePurchase(purchase: Purchase)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseItems(items: List<PurchaseItem>)

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun getPurchaseItems(purchaseId: Long): List<PurchaseItem>
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers ORDER BY name ASC")
    suspend fun getAllCustomersList(): List<Customer>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customer: Customer): Long

    @Update
    suspend fun update(customer: Customer)

    @Query("UPDATE customers SET currentBalance = currentBalance + :diff WHERE id = :id")
    suspend fun updateBalance(id: Long, diff: Double)

    @Query("SELECT SUM(currentBalance) FROM customers WHERE currentBalance > 0")
    suspend fun getTotalPendingCustomerDebt(): Double?
}

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    suspend fun getAllSuppliersList(): List<Supplier>

    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    suspend fun getSupplierById(id: Long): Supplier?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(supplier: Supplier): Long

    @Update
    suspend fun update(supplier: Supplier)

    @Query("UPDATE suppliers SET currentBalance = currentBalance + :diff WHERE id = :id")
    suspend fun updateBalance(id: Long, diff: Double)

    @Query("SELECT SUM(currentBalance) FROM suppliers WHERE currentBalance > 0")
    suspend fun getTotalPendingSupplierDebt(): Double?
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE partyType = :partyType AND partyId = :partyId ORDER BY paymentDate DESC")
    fun getPaymentsForParty(partyType: String, partyId: Long): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payments WHERE paymentDate >= :startDate AND paymentDate <= :endDate ORDER BY paymentDate DESC")
    suspend fun getPaymentsBetween(startDate: Long, endDate: Long): List<PaymentRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentRecord): Long
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getExpensesBetween(startDate: Long, endDate: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    suspend fun getExpensesListBetween(startDate: Long, endDate: Long): List<Expense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Delete
    suspend fun deleteExpense(expense: Expense)
}

@Dao
interface ReturnDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalesReturn(returnRecord: SalesReturn): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalesReturnItems(items: List<SalesReturnItem>)

    @Query("SELECT * FROM sales_returns ORDER BY returnDate DESC")
    fun getAllSalesReturns(): Flow<List<SalesReturn>>

    @Query("SELECT * FROM sales_return_items WHERE returnId = :returnId")
    suspend fun getSalesReturnItems(returnId: Long): List<SalesReturnItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseReturn(returnRecord: PurchaseReturn): Long

    @Query("SELECT * FROM purchase_returns ORDER BY returnDate DESC")
    fun getAllPurchaseReturns(): Flow<List<PurchaseReturn>>
}

@Dao
interface StockAdjustmentDao {
    @Query("SELECT * FROM stock_adjustments ORDER BY date DESC")
    fun getAllAdjustments(): Flow<List<StockAdjustment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdjustment(adjustment: StockAdjustment): Long
}

@Dao
interface DailyClosingDao {
    @Query("SELECT * FROM daily_closings ORDER BY closingDate DESC")
    fun getAllClosings(): Flow<List<DailyClosing>>

    @Query("SELECT * FROM daily_closings WHERE closingDate = :date LIMIT 1")
    suspend fun getClosingForDate(date: String): DailyClosing?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClosing(closing: DailyClosing): Long
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLog): Long
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM pharmacy_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<PharmacySettings?>

    @Query("SELECT * FROM pharmacy_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): PharmacySettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: PharmacySettings)
}
