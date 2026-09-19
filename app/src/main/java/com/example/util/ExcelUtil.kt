package com.example.util

import android.content.Context
import com.example.data.model.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class ImportPreviewResult(
    val validProducts: List<Pair<Product, Batch>>,
    val duplicateCount: Int,
    val invalidRows: List<String>,
    val totalRows: Int
)

object ExcelUtil {

    fun exportProductsToCsv(context: Context, products: List<ProductWithStock>): File {
        val file = File(context.cacheDir, "products_inventory_${System.currentTimeMillis()}.csv")
        FileOutputStream(file).bufferedWriter().use { writer ->
            writer.write("Barcode,SKU,Product Name,Generic Name,Brand,Category,Dosage Form,Strength,Pack Size,Manufacturer,Supplier,Retail Price,MRP,Min Stock,Rack Location,Total Stock,Earliest Expiry\n")
            for (p in products) {
                writer.write(
                    listOf(
                        escapeCsv(p.barcode),
                        escapeCsv(p.sku),
                        escapeCsv(p.name),
                        escapeCsv(p.genericName),
                        escapeCsv(p.brand),
                        escapeCsv(p.category),
                        escapeCsv(p.dosageForm),
                        escapeCsv(p.strength),
                        escapeCsv(p.packSize),
                        escapeCsv(p.manufacturer),
                        escapeCsv(p.supplierName),
                        p.retailPrice.toString(),
                        p.mrp.toString(),
                        p.minStockLevel.toString(),
                        escapeCsv(p.rackLocation),
                        p.totalStock.toString(),
                        escapeCsv(p.earliestExpiry ?: "")
                    ).joinToString(",") + "\n"
                )
            }
        }
        return file
    }

    fun exportSalesToCsv(context: Context, sales: List<Sale>): File {
        val file = File(context.cacheDir, "sales_report_${System.currentTimeMillis()}.csv")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        FileOutputStream(file).bufferedWriter().use { writer ->
            writer.write("Invoice #,Date,Cashier,Customer,Subtotal,Discount,Tax,Grand Total,Paid,Balance,Payment Method,Status\n")
            for (s in sales) {
                writer.write(
                    listOf(
                        escapeCsv(s.invoiceNumber),
                        escapeCsv(sdf.format(Date(s.saleDate))),
                        escapeCsv(s.cashierName),
                        escapeCsv(s.customerName),
                        s.subtotal.toString(),
                        s.discountAmount.toString(),
                        s.taxAmount.toString(),
                        s.grandTotal.toString(),
                        s.amountPaid.toString(),
                        s.remainingBalance.toString(),
                        escapeCsv(s.paymentMethod),
                        escapeCsv(s.status)
                    ).joinToString(",") + "\n"
                )
            }
        }
        return file
    }

    fun exportExpensesToCsv(context: Context, expenses: List<Expense>): File {
        val file = File(context.cacheDir, "expenses_report_${System.currentTimeMillis()}.csv")
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        FileOutputStream(file).bufferedWriter().use { writer ->
            writer.write("Title,Category,Amount,Date,Payment Method,Recorded By,Description\n")
            for (e in expenses) {
                writer.write(
                    listOf(
                        escapeCsv(e.title),
                        escapeCsv(e.category),
                        e.amount.toString(),
                        escapeCsv(sdf.format(Date(e.date))),
                        escapeCsv(e.paymentMethod),
                        escapeCsv(e.recordedBy),
                        escapeCsv(e.description)
                    ).joinToString(",") + "\n"
                )
            }
        }
        return file
    }

    fun parseAndValidateCsvImport(
        csvContent: String,
        existingProductNames: Set<String>
    ): ImportPreviewResult {
        val lines = csvContent.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ImportPreviewResult(emptyList(), 0, listOf("File is empty"), 0)
        }

        val validList = mutableListOf<Pair<Product, Batch>>()
        val errors = mutableListOf<String>()
        var duplicates = 0

        // Skip header if line 1 starts with Barcode or Product
        val dataLines = if (lines[0].contains("Product", ignoreCase = true) || lines[0].contains("Barcode", ignoreCase = true)) {
            lines.drop(1)
        } else {
            lines
        }

        dataLines.forEachIndexed { index, line ->
            val rowNum = index + 2
            val cols = parseCsvLine(line)
            if (cols.size < 6) {
                errors.add("Row $rowNum: Insufficient columns (found ${cols.size}, expected at least 6)")
                return@forEachIndexed
            }

            // Expected format:
            // 0: Barcode, 1: SKU, 2: Name, 3: GenericName, 4: Brand, 5: Category,
            // 6: Strength, 7: PackSize, 8: Manufacturer, 9: Batch, 10: MfgDate, 11: ExpDate,
            // 12: PurchasePrice, 13: RetailPrice, 14: Stock, 15: MinStock, 16: Supplier

            val name = cols.getOrNull(2)?.trim().orEmpty()
            if (name.isBlank()) {
                errors.add("Row $rowNum: Missing Product Name")
                return@forEachIndexed
            }

            if (existingProductNames.contains(name.lowercase())) {
                duplicates++
            }

            val batchNumber = cols.getOrNull(9)?.trim()?.ifEmpty { null } ?: "BATCH-${System.currentTimeMillis() % 10000}"
            val expiryDate = cols.getOrNull(11)?.trim()?.ifEmpty { null } ?: "2027-12-31"
            if (!expiryDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                errors.add("Row $rowNum ($name): Invalid expiry date format '$expiryDate'. Must be YYYY-MM-DD")
                return@forEachIndexed
            }

            val purchasePrice = cols.getOrNull(12)?.toDoubleOrNull() ?: 0.0
            val retailPrice = cols.getOrNull(13)?.toDoubleOrNull() ?: 0.0
            val stock = cols.getOrNull(14)?.toIntOrNull() ?: 10
            val minStock = cols.getOrNull(15)?.toIntOrNull() ?: 10

            val product = Product(
                barcode = cols.getOrNull(0)?.trim().orEmpty(),
                sku = cols.getOrNull(1)?.trim().orEmpty(),
                name = name,
                genericName = cols.getOrNull(3)?.trim().orEmpty(),
                brand = cols.getOrNull(4)?.trim().orEmpty(),
                category = cols.getOrNull(5)?.trim()?.ifEmpty { null } ?: "Tablets",
                strength = cols.getOrNull(6)?.trim().orEmpty(),
                packSize = cols.getOrNull(7)?.trim().orEmpty(),
                manufacturer = cols.getOrNull(8)?.trim().orEmpty(),
                supplierName = cols.getOrNull(16)?.trim().orEmpty(),
                retailPrice = retailPrice,
                minStockLevel = minStock
            )

            val batch = Batch(
                productId = 0,
                batchNumber = batchNumber,
                manufacturingDate = cols.getOrNull(10)?.trim().orEmpty(),
                expiryDate = expiryDate,
                purchasePrice = purchasePrice,
                retailPrice = retailPrice,
                currentStock = stock
            )

            validList.add(Pair(product, batch))
        }

        return ImportPreviewResult(
            validProducts = validList,
            duplicateCount = duplicates,
            invalidRows = errors,
            totalRows = dataLines.size
        )
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = java.lang.StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    sb.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    fun getSampleTemplateCsv(): String {
        return """
Barcode,SKU,Product Name,Generic Name,Brand,Category,Strength,Pack Size,Manufacturer,Batch,Manufacturing Date,Expiry Date,Purchase Price,Retail Price,Stock,Minimum Stock,Supplier
89640001,SKU-001,Panadol 500mg,Paracetamol,Panadol,Tablets,500mg,20x10,GSK,PAN-2025,2025-01-10,2027-12-31,22.5,30.0,50,15,Ali Medico
89640002,SKU-002,Augmentin 625mg,Co-Amoxiclav,Augmentin,Tablets,625mg,1x14,GSK,AUG-2025,2025-02-01,2028-02-01,260.0,320.0,30,10,Care Pharma
89640003,SKU-003,Brufen 400mg,Ibuprofen,Brufen,Tablets,400mg,25x10,Abbott,BRU-2025,2025-03-01,2027-06-30,35.0,45.0,40,20,Lahore Wholesalers
        """.trimIndent()
    }
}
