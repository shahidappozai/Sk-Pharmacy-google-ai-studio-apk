package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val fullName: String,
    val role: String, // ADMIN, STAFF_1, STAFF_2, STAFF_3
    val passwordHash: String,
    val salt: String,
    val pinHash: String = "",
    val isActive: Boolean = true,
    val permissionsJson: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class StaffPermissions(
    val viewPos: Boolean = true,
    val createSale: Boolean = true,
    val editSale: Boolean = false,
    val cancelSale: Boolean = false,
    val applyDiscount: Boolean = true,
    val viewStock: Boolean = true,
    val addProducts: Boolean = false,
    val editProducts: Boolean = false,
    val viewPurchaseRecords: Boolean = false,
    val createPurchases: Boolean = false,
    val viewCustomers: Boolean = true,
    val addCustomers: Boolean = true,
    val viewSuppliers: Boolean = false,
    val addSuppliers: Boolean = false,
    val viewReports: Boolean = false,
    val viewProfit: Boolean = false,
    val viewExpenses: Boolean = false,
    val exportData: Boolean = false,
    val importData: Boolean = false,
    val backup: Boolean = false,
    val restore: Boolean = false,
    val manageUsers: Boolean = false,
    val manageSettings: Boolean = false
) {
    companion object {
        fun defaultStaff(): StaffPermissions = StaffPermissions()
        fun adminPermissions(): StaffPermissions = StaffPermissions(
            viewPos = true,
            createSale = true,
            editSale = true,
            cancelSale = true,
            applyDiscount = true,
            viewStock = true,
            addProducts = true,
            editProducts = true,
            viewPurchaseRecords = true,
            createPurchases = true,
            viewCustomers = true,
            addCustomers = true,
            viewSuppliers = true,
            addSuppliers = true,
            viewReports = true,
            viewProfit = true,
            viewExpenses = true,
            exportData = true,
            importData = true,
            backup = true,
            restore = true,
            manageUsers = true,
            manageSettings = true
        )
    }
}

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["barcode"]),
        Index(value = ["sku"]),
        Index(value = ["name"]),
        Index(value = ["genericName"])
    ]
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String = "",
    val sku: String = "",
    val name: String,
    val genericName: String = "",
    val brand: String = "",
    val category: String = "Tablets",
    val dosageForm: String = "Tablet",
    val strength: String = "",
    val packSize: String = "",
    val manufacturer: String = "",
    val supplierName: String = "",
    val retailPrice: Double = 0.0,
    val mrp: Double = 0.0,
    val wholesalePrice: Double = 0.0,
    val minStockLevel: Int = 10,
    val maxStockLevel: Int = 500,
    val rackLocation: String = "",
    val taxPercent: Double = 0.0,
    val defaultDiscountPercent: Double = 0.0,
    val notes: String = "",
    val isDeleted: Boolean = false,
    val expiryAlertDays: Int = 90,
    val imageUri: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "batches",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["productId"]),
        Index(value = ["expiryDate"]),
        Index(value = ["batchNumber"])
    ]
)
data class Batch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val batchNumber: String,
    val manufacturingDate: String = "", // YYYY-MM-DD
    val expiryDate: String, // YYYY-MM-DD
    val alertDate: String = "", // Date of Expiry Alert (YYYY-MM-DD)
    val purchasePrice: Double,
    val retailPrice: Double,
    val currentStock: Int,
    val supplierId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sales",
    indices = [
        Index(value = ["invoiceNumber"], unique = true),
        Index(value = ["saleDate"]),
        Index(value = ["cashierId"])
    ]
)
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,
    val customerId: Long? = null,
    val customerName: String = "Walk-in Customer",
    val customerPhone: String = "",
    val cashierId: Long,
    val cashierName: String,
    val saleDate: Long = System.currentTimeMillis(),
    val subtotal: Double,
    val discountAmount: Double = 0.0,
    val discountPercent: Double = 0.0,
    val taxAmount: Double = 0.0,
    val grandTotal: Double,
    val amountPaid: Double,
    val changeGiven: Double = 0.0,
    val remainingBalance: Double = 0.0,
    val paymentMethod: String = "Cash", // Cash, Card, Bank Transfer, JazzCash, Easypaisa, Credit, Mixed
    val mixedPaymentDetails: String = "",
    val status: String = "COMPLETED", // COMPLETED, CANCELLED, REFUNDED, PARTIALLY_REFUNDED
    val cancellationReason: String = "",
    val cancelledBy: String = "",
    val notes: String = ""
)

@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = Sale::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["saleId"]),
        Index(value = ["productId"]),
        Index(value = ["batchId"])
    ]
)
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    val productName: String,
    val batchId: Long,
    val batchNumber: String,
    val expiryDate: String,
    val quantity: Int,
    val returnedQuantity: Int = 0,
    val unitPrice: Double,
    val unitPurchaseCost: Double, // TRUE batch purchase cost for COGS & profit
    val discountAmount: Double = 0.0,
    val taxAmount: Double = 0.0,
    val subtotal: Double
)

@Entity(
    tableName = "purchases",
    indices = [
        Index(value = ["purchaseDate"]),
        Index(value = ["supplierId"])
    ]
)
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierId: Long,
    val supplierName: String,
    val invoiceNumber: String,
    val purchaseDate: Long = System.currentTimeMillis(),
    val subtotal: Double,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val grandTotal: Double,
    val paidAmount: Double,
    val balanceDue: Double = 0.0,
    val paymentMethod: String = "Cash",
    val status: String = "RECEIVED", // RECEIVED, CANCELLED
    val createdBy: String
)

@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(
            entity = Purchase::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["purchaseId"]),
        Index(value = ["productId"])
    ]
)
data class PurchaseItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val productId: Long,
    val productName: String,
    val batchNumber: String,
    val manufacturingDate: String = "",
    val expiryDate: String,
    val quantity: Int,
    val purchasePrice: Double,
    val retailPrice: Double,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double
)

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["phone"])
    ]
)
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val email: String = "",
    val openingBalance: Double = 0.0,
    val creditLimit: Double = 50000.0,
    val currentBalance: Double = 0.0, // Amount owed by customer
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val company: String = "",
    val phone: String = "",
    val address: String = "",
    val email: String = "",
    val ntn: String = "",
    val licenseInfo: String = "",
    val openingBalance: Double = 0.0,
    val currentBalance: Double = 0.0, // Amount pharmacy owes to supplier
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payments",
    indices = [
        Index(value = ["partyType", "partyId"]),
        Index(value = ["paymentDate"])
    ]
)
data class PaymentRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyType: String, // CUSTOMER or SUPPLIER
    val partyId: Long,
    val partyName: String,
    val amount: Double,
    val paymentDate: Long = System.currentTimeMillis(),
    val paymentMethod: String = "Cash",
    val referenceNumber: String = "",
    val notes: String = "",
    val recordedBy: String
)

@Entity(
    tableName = "sales_returns",
    indices = [
        Index(value = ["saleId"]),
        Index(value = ["returnDate"])
    ]
)
data class SalesReturn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val invoiceNumber: String,
    val customerId: Long? = null,
    val customerName: String = "",
    val returnDate: Long = System.currentTimeMillis(),
    val totalRefundAmount: Double,
    val refundMethod: String = "Cash",
    val reason: String = "",
    val processedBy: String
)

@Entity(
    tableName = "sales_return_items",
    foreignKeys = [
        ForeignKey(
            entity = SalesReturn::class,
            parentColumns = ["id"],
            childColumns = ["returnId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["returnId"])]
)
data class SalesReturnItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val returnId: Long,
    val saleItemId: Long,
    val productId: Long,
    val productName: String,
    val batchId: Long,
    val batchNumber: String,
    val quantityReturned: Int,
    val unitPrice: Double,
    val refundSubtotal: Double
)

@Entity(
    tableName = "purchase_returns",
    indices = [Index(value = ["purchaseId"])]
)
data class PurchaseReturn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val supplierId: Long,
    val supplierName: String,
    val returnDate: Long = System.currentTimeMillis(),
    val totalRefundAmount: Double,
    val reason: String = "",
    val processedBy: String
)

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["date"]), Index(value = ["category"])]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String, // Electricity, Rent, Salary, Internet, Transport, Maintenance, Misc
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val paymentMethod: String = "Cash",
    val description: String = "",
    val recordedBy: String = "Admin"
)

@Entity(
    tableName = "daily_closings",
    indices = [Index(value = ["closingDate"], unique = true)]
)
data class DailyClosing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val closingDate: String, // YYYY-MM-DD
    val timestamp: Long = System.currentTimeMillis(),
    val openingCash: Double,
    val cashSales: Double,
    val customerPaymentsCash: Double,
    val refundsCash: Double,
    val expensesCash: Double,
    val expectedCash: Double,
    val actualCash: Double,
    val cashDifference: Double,
    val closedBy: String,
    val notes: String = ""
)

@Entity(
    tableName = "stock_adjustments",
    indices = [Index(value = ["productId"]), Index(value = ["date"])]
)
data class StockAdjustment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val batchId: Long,
    val batchNumber: String,
    val systemStock: Int,
    val adjustedQuantity: Int, // Difference (+/-)
    val newStock: Int,
    val reason: String, // Damaged, Expired, Lost, Physical count correction, Sample, Other
    val adjustedBy: String,
    val date: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "audit_logs",
    indices = [Index(value = ["timestamp"])]
)
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val action: String,
    val affectedRecord: String,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "pharmacy_settings")
data class PharmacySettings(
    @PrimaryKey val id: Int = 1,
    val pharmacyName: String = "SK Pharmacy",
    val address: String = "Main Boulevard, Gulberg, Lahore, Pakistan",
    val phone: String = "+92 300 1234567",
    val email: String = "contact@skpharmacy.pk",
    val website: String = "www.skpharmacy.pk",
    val licenseNumber: String = "PHARM-LHR-2024-8891",
    val ntnNumber: String = "NTN-9843210-7",
    val currency: String = "PKR",
    val invoiceFooter: String = "Thank you for visiting SK Pharmacy. Get well soon!",
    val receiptHeader: String = "SK Pharmacy — Quality Medicines & Trusted Healthcare",
    val taxEnabled: Boolean = false,
    val defaultTaxPercent: Double = 0.0,
    val maxDiscountPercent: Double = 25.0,
    val expiryWarningDays: Int = 90,
    val autoBackupEnabled: Boolean = true,
    val autoLogoutMinutes: Int = 30,
    val lowStockNotificationEnabled: Boolean = true,
    val expiryNotificationEnabled: Boolean = true,
    val adminPin: String = "1234",
    val logoUri: String = ""
)

// DTOs for aggregated queries
data class ProductWithStock(
    val id: Long,
    val barcode: String,
    val sku: String,
    val name: String,
    val genericName: String,
    val brand: String,
    val category: String,
    val dosageForm: String,
    val strength: String,
    val packSize: String,
    val manufacturer: String,
    val supplierName: String,
    val retailPrice: Double,
    val mrp: Double,
    val wholesalePrice: Double,
    val minStockLevel: Int,
    val maxStockLevel: Int,
    val rackLocation: String,
    val taxPercent: Double,
    val defaultDiscountPercent: Double,
    val notes: String,
    val isDeleted: Boolean,
    val totalStock: Int,
    val earliestExpiry: String?,
    val expiryAlertDays: Int = 90,
    val imageUri: String = "",
    val earliestAlertDate: String? = null
)

data class BatchWithProduct(
    val batchId: Long,
    val productId: Long,
    val productName: String,
    val genericName: String,
    val category: String,
    val batchNumber: String,
    val manufacturingDate: String,
    val expiryDate: String,
    val purchasePrice: Double,
    val retailPrice: Double,
    val currentStock: Int,
    val alertDate: String = ""
)

data class TopSellingItem(
    val productId: Long,
    val productName: String,
    val totalSold: Int,
    val totalRevenue: Double
)

data class CartItem(
    val product: Product,
    val batch: Batch,
    var quantity: Int,
    var unitPrice: Double,
    var discountPercent: Double = 0.0,
    var customPriceApplied: Boolean = false
) {
    val discountAmount: Double get() = (unitPrice * quantity) * (discountPercent / 100.0)
    val subtotal: Double get() = (unitPrice * quantity) - discountAmount
}

fun ProductWithStock.toProduct(): Product = Product(
    id = id,
    barcode = barcode,
    sku = sku,
    name = name,
    genericName = genericName,
    brand = brand,
    category = category,
    dosageForm = dosageForm,
    strength = strength,
    packSize = packSize,
    manufacturer = manufacturer,
    supplierName = supplierName,
    retailPrice = retailPrice,
    mrp = mrp,
    wholesalePrice = wholesalePrice,
    minStockLevel = minStockLevel,
    maxStockLevel = maxStockLevel,
    rackLocation = rackLocation,
    taxPercent = taxPercent,
    defaultDiscountPercent = defaultDiscountPercent,
    notes = notes,
    isDeleted = isDeleted,
    expiryAlertDays = expiryAlertDays,
    imageUri = imageUri
)

