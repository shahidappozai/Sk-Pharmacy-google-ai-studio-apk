package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*
import com.example.util.SecurityUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(
    entities = [
        User::class,
        Product::class,
        Batch::class,
        Sale::class,
        SaleItem::class,
        Purchase::class,
        PurchaseItem::class,
        Customer::class,
        Supplier::class,
        PaymentRecord::class,
        SalesReturn::class,
        SalesReturnItem::class,
        PurchaseReturn::class,
        Expense::class,
        DailyClosing::class,
        StockAdjustment::class,
        AuditLog::class,
        PharmacySettings::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PharmacyDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun productDao(): ProductDao
    abstract fun batchDao(): BatchDao
    abstract fun saleDao(): SaleDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun customerDao(): CustomerDao
    abstract fun supplierDao(): SupplierDao
    abstract fun paymentDao(): PaymentDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun returnDao(): ReturnDao
    abstract fun stockAdjustmentDao(): StockAdjustmentDao
    abstract fun dailyClosingDao(): DailyClosingDao
    abstract fun auditDao(): AuditDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: PharmacyDatabase? = null

        fun getDatabase(
            context: Context,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ): PharmacyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PharmacyDatabase::class.java,
                    "sk_pharmacy_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }
        }

        suspend fun populateInitialData(database: PharmacyDatabase) {
            val settingsDao = database.settingsDao()
            if (settingsDao.getSettingsDirect() == null) {
                settingsDao.insertOrUpdate(
                    PharmacySettings(
                        id = 1,
                        pharmacyName = "SK Pharmacy",
                        address = "Commercial Zone, Main Boulevard, Lahore",
                        phone = "+92 300 1234567",
                        email = "info@skpharmacy.pk",
                        website = "www.skpharmacy.pk",
                        licenseNumber = "PHARM-LHR-2024-8891",
                        ntnNumber = "NTN-9843210-7",
                        currency = "PKR",
                        invoiceFooter = "Thank you for visiting SK Pharmacy. Medicines once sold cannot be returned without original receipt.",
                        receiptHeader = "SK Pharmacy — Quality Healthcare & Genuine Medicines",
                        adminPin = "1234"
                    )
                )
            }

            val userDao = database.userDao()
            if (userDao.getUserCount() == 0) {
                val adminSalt = SecurityUtil.generateSalt()
                val adminHash = SecurityUtil.hashPassword("admin123", adminSalt)
                val adminPinHash = SecurityUtil.hashPin("1234")

                // Admin
                userDao.insert(
                    User(
                        username = "admin",
                        fullName = "Chief Pharmacist (Admin)",
                        role = "ADMIN",
                        passwordHash = adminHash,
                        salt = adminSalt,
                        pinHash = adminPinHash,
                        isActive = true,
                        permissionsJson = ""
                    )
                )

                // Staff 1
                val s1Salt = SecurityUtil.generateSalt()
                userDao.insert(
                    User(
                        username = "staff1",
                        fullName = "Ali Raza (Cashier)",
                        role = "STAFF_1",
                        passwordHash = SecurityUtil.hashPassword("staff123", s1Salt),
                        salt = s1Salt,
                        pinHash = SecurityUtil.hashPin("1111"),
                        isActive = true,
                        permissionsJson = ""
                    )
                )

                // Staff 2
                val s2Salt = SecurityUtil.generateSalt()
                userDao.insert(
                    User(
                        username = "staff2",
                        fullName = "Usman Khan (Dispenser)",
                        role = "STAFF_2",
                        passwordHash = SecurityUtil.hashPassword("staff123", s2Salt),
                        salt = s2Salt,
                        pinHash = SecurityUtil.hashPin("2222"),
                        isActive = true,
                        permissionsJson = ""
                    )
                )

                // Staff 3
                val s3Salt = SecurityUtil.generateSalt()
                userDao.insert(
                    User(
                        username = "staff3",
                        fullName = "Ayesha Bibi (Assistant)",
                        role = "STAFF_3",
                        passwordHash = SecurityUtil.hashPassword("staff123", s3Salt),
                        salt = s3Salt,
                        pinHash = SecurityUtil.hashPin("3333"),
                        isActive = true,
                        permissionsJson = ""
                    )
                )

                database.auditDao().insertLog(
                    AuditLog(
                        username = "system",
                        action = "INITIAL_SETUP",
                        affectedRecord = "Users & Settings",
                        details = "Database initialized with Admin and 3 Staff accounts"
                    )
                )
            }
        }
    }
}
