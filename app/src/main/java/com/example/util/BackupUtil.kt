package com.example.util

import android.content.Context
import androidx.room.withTransaction
import com.example.data.db.PharmacyDatabase
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object BackupUtil {

    suspend fun createBackup(context: Context, database: PharmacyDatabase, username: String): File = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "SK Pharmacy")
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())
        root.put("createdDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        root.put("createdBy", username)

        // Settings
        val settings = database.settingsDao().getSettingsDirect()
        if (settings != null) {
            val sObj = JSONObject().apply {
                put("pharmacyName", settings.pharmacyName)
                put("address", settings.address)
                put("phone", settings.phone)
                put("email", settings.email)
                put("website", settings.website)
                put("licenseNumber", settings.licenseNumber)
                put("ntnNumber", settings.ntnNumber)
                put("currency", settings.currency)
                put("invoiceFooter", settings.invoiceFooter)
                put("receiptHeader", settings.receiptHeader)
            }
            root.put("settings", sObj)
        }

        // Products & Batches
        val products = database.productDao().getAllActiveProductsList()
        val prodArray = JSONArray()
        for (p in products) {
            val pObj = JSONObject().apply {
                put("id", p.id)
                put("barcode", p.barcode)
                put("sku", p.sku)
                put("name", p.name)
                put("genericName", p.genericName)
                put("brand", p.brand)
                put("category", p.category)
                put("dosageForm", p.dosageForm)
                put("strength", p.strength)
                put("packSize", p.packSize)
                put("manufacturer", p.manufacturer)
                put("supplierName", p.supplierName)
                put("retailPrice", p.retailPrice)
                put("mrp", p.mrp)
                put("minStockLevel", p.minStockLevel)
                put("rackLocation", p.rackLocation)
            }
            prodArray.put(pObj)
        }
        root.put("products", prodArray)

        // Batches
        val batches = database.batchDao().getAllBatchesWithProductList()
        val batchArray = JSONArray()
        for (b in batches) {
            val bObj = JSONObject().apply {
                put("batchId", b.batchId)
                put("productId", b.productId)
                put("batchNumber", b.batchNumber)
                put("manufacturingDate", b.manufacturingDate)
                put("expiryDate", b.expiryDate)
                put("purchasePrice", b.purchasePrice)
                put("retailPrice", b.retailPrice)
                put("currentStock", b.currentStock)
            }
            batchArray.put(bObj)
        }
        root.put("batches", batchArray)

        // Customers
        val customers = database.customerDao().getAllCustomersList()
        val custArray = JSONArray()
        for (c in customers) {
            val cObj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("phone", c.phone)
                put("address", c.address)
                put("creditLimit", c.creditLimit)
                put("currentBalance", c.currentBalance)
            }
            custArray.put(cObj)
        }
        root.put("customers", custArray)

        // Suppliers
        val suppliers = database.supplierDao().getAllSuppliersList()
        val supArray = JSONArray()
        for (s in suppliers) {
            val sObj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("company", s.company)
                put("phone", s.phone)
                put("currentBalance", s.currentBalance)
            }
            supArray.put(sObj)
        }
        root.put("suppliers", supArray)

        val backupDir = File(context.filesDir, "backups")
        backupDir.mkdirs()
        val file = File(backupDir, "sk_pharmacy_backup_${System.currentTimeMillis()}.json")
        FileOutputStream(file).use { out ->
            out.write(root.toString(2).toByteArray())
        }

        database.auditDao().insertLog(
            AuditLog(
                username = username,
                action = "BACKUP_CREATED",
                affectedRecord = file.name,
                details = "Created full database JSON snapshot (${file.length() / 1024} KB)"
            )
        )
        file
    }

    suspend fun restoreBackup(
        backupFile: File,
        database: PharmacyDatabase,
        username: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val content = backupFile.readText()
            val root = JSONObject(content)
            if (!root.has("app") || root.getString("app") != "SK Pharmacy") {
                error("Invalid backup file: header verification failed")
            }

            database.withTransaction {
                // Restore Settings
                if (root.has("settings")) {
                    val sObj = root.getJSONObject("settings")
                    val current = database.settingsDao().getSettingsDirect() ?: PharmacySettings()
                    database.settingsDao().insertOrUpdate(
                        current.copy(
                            pharmacyName = sObj.optString("pharmacyName", current.pharmacyName),
                            address = sObj.optString("address", current.address),
                            phone = sObj.optString("phone", current.phone),
                            email = sObj.optString("email", current.email),
                            website = sObj.optString("website", current.website),
                            licenseNumber = sObj.optString("licenseNumber", current.licenseNumber),
                            ntnNumber = sObj.optString("ntnNumber", current.ntnNumber),
                            currency = sObj.optString("currency", current.currency)
                        )
                    )
                }

                // Restore Customers
                if (root.has("customers")) {
                    val custArray = root.getJSONArray("customers")
                    for (i in 0 until custArray.length()) {
                        val cObj = custArray.getJSONObject(i)
                        database.customerDao().insert(
                            Customer(
                                id = cObj.optLong("id", 0),
                                name = cObj.getString("name"),
                                phone = cObj.optString("phone", ""),
                                address = cObj.optString("address", ""),
                                creditLimit = cObj.optDouble("creditLimit", 50000.0),
                                currentBalance = cObj.optDouble("currentBalance", 0.0)
                            )
                        )
                    }
                }

                // Restore Suppliers
                if (root.has("suppliers")) {
                    val supArray = root.getJSONArray("suppliers")
                    for (i in 0 until supArray.length()) {
                        val sObj = supArray.getJSONObject(i)
                        database.supplierDao().insert(
                            Supplier(
                                id = sObj.optLong("id", 0),
                                name = sObj.getString("name"),
                                company = sObj.optString("company", ""),
                                phone = sObj.optString("phone", ""),
                                currentBalance = sObj.optDouble("currentBalance", 0.0)
                            )
                        )
                    }
                }

                // Restore Products
                if (root.has("products")) {
                    val prodArray = root.getJSONArray("products")
                    for (i in 0 until prodArray.length()) {
                        val pObj = prodArray.getJSONObject(i)
                        database.productDao().insert(
                            Product(
                                id = pObj.optLong("id", 0),
                                barcode = pObj.optString("barcode", ""),
                                sku = pObj.optString("sku", ""),
                                name = pObj.getString("name"),
                                genericName = pObj.optString("genericName", ""),
                                brand = pObj.optString("brand", ""),
                                category = pObj.optString("category", "Tablets"),
                                dosageForm = pObj.optString("dosageForm", "Tablet"),
                                strength = pObj.optString("strength", ""),
                                packSize = pObj.optString("packSize", ""),
                                manufacturer = pObj.optString("manufacturer", ""),
                                supplierName = pObj.optString("supplierName", ""),
                                retailPrice = pObj.optDouble("retailPrice", 0.0),
                                mrp = pObj.optDouble("mrp", 0.0),
                                minStockLevel = pObj.optInt("minStockLevel", 10),
                                rackLocation = pObj.optString("rackLocation", "")
                            )
                        )
                    }
                }

                // Restore Batches
                if (root.has("batches")) {
                    val batchArray = root.getJSONArray("batches")
                    for (i in 0 until batchArray.length()) {
                        val bObj = batchArray.getJSONObject(i)
                        database.batchDao().insert(
                            Batch(
                                id = bObj.optLong("batchId", 0),
                                productId = bObj.getLong("productId"),
                                batchNumber = bObj.getString("batchNumber"),
                                manufacturingDate = bObj.optString("manufacturingDate", ""),
                                expiryDate = bObj.getString("expiryDate"),
                                purchasePrice = bObj.optDouble("purchasePrice", 0.0),
                                retailPrice = bObj.optDouble("retailPrice", 0.0),
                                currentStock = bObj.optInt("currentStock", 0)
                            )
                        )
                    }
                }

                database.auditDao().insertLog(
                    AuditLog(
                        username = username,
                        action = "RESTORE_COMPLETED",
                        affectedRecord = backupFile.name,
                        details = "Restored database from ${backupFile.name}"
                    )
                )
            }
            "Database restored successfully from ${backupFile.name}"
        }
    }
}
