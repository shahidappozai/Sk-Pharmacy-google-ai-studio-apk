package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.model.PharmacySettings
import com.example.data.model.ProductWithStock
import com.example.data.model.Sale
import com.example.data.model.SaleItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfGenerator {

    fun generateInvoicePdf(
        context: Context,
        sale: Sale,
        items: List<SaleItem>,
        settings: PharmacySettings,
        isThermal: Boolean = false
    ): File {
        val pageHeight = if (isThermal) (400 + (items.size * 35)).coerceAtLeast(600) else 842
        val pageWidth = if (isThermal) 300 else 595

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint()
        val titlePaint = Paint().apply {
            color = Color.rgb(0, 77, 64)
            textSize = if (isThermal) 16f else 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = if (isThermal) 9f else 11f
            textAlign = Paint.Align.CENTER
        }
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = if (isThermal) 9f else 11f
        }
        val boldPaint = Paint().apply {
            color = Color.BLACK
            textSize = if (isThermal) 9f else 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rightPaint = Paint().apply {
            color = Color.BLACK
            textSize = if (isThermal) 9f else 11f
            textAlign = Paint.Align.RIGHT
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        var y = if (isThermal) 30f else 45f
        val centerX = pageWidth / 2f

        // Draw Logo if configured
        if (settings.logoUri.isNotBlank()) {
            try {
                val logoFile = File(settings.logoUri)
                if (logoFile.exists()) {
                    val logoBmp = BitmapFactory.decodeFile(logoFile.absolutePath)
                    if (logoBmp != null) {
                        val logoSize = if (isThermal) 28f else 40f
                        val destRect = RectF(centerX - (logoSize / 2f), y - 10f, centerX + (logoSize / 2f), y - 10f + logoSize)
                        canvas.drawBitmap(logoBmp, null, destRect, null)
                        y += logoSize + 4f
                    }
                }
            } catch (_: Exception) {}
        }

        // Header
        canvas.drawText(settings.pharmacyName, centerX, y, titlePaint)
        y += if (isThermal) 14f else 20f
        canvas.drawText(settings.address, centerX, y, subPaint)
        y += if (isThermal) 12f else 16f
        canvas.drawText("Phone: ${settings.phone} | Lic #: ${settings.licenseNumber}", centerX, y, subPaint)
        y += if (isThermal) 12f else 16f
        if (settings.ntnNumber.isNotEmpty()) {
            canvas.drawText("NTN: ${settings.ntnNumber}", centerX, y, subPaint)
            y += if (isThermal) 12f else 16f
        }

        canvas.drawLine(15f, y, pageWidth - 15f, y, linePaint)
        y += if (isThermal) 15f else 22f

        // Invoice Meta
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(sale.saleDate))

        canvas.drawText("Invoice #: ${sale.invoiceNumber}", 15f, y, boldPaint)
        canvas.drawText(dateStr, pageWidth - 15f, y, rightPaint)
        y += if (isThermal) 14f else 18f

        canvas.drawText("Cashier: ${sale.cashierName}", 15f, y, textPaint)
        canvas.drawText("Customer: ${sale.customerName}", pageWidth - 15f, y, rightPaint)
        y += if (isThermal) 14f else 18f

        canvas.drawLine(15f, y, pageWidth - 15f, y, linePaint)
        y += if (isThermal) 14f else 18f

        // Table Header
        canvas.drawText("Item / Batch", 15f, y, boldPaint)
        canvas.drawText("Qty", if (isThermal) 170f else 340f, y, boldPaint)
        canvas.drawText("Price", if (isThermal) 215f else 420f, y, boldPaint)
        canvas.drawText("Total", pageWidth - 15f, y, Paint(boldPaint).apply { textAlign = Paint.Align.RIGHT })
        y += if (isThermal) 12f else 15f
        canvas.drawLine(15f, y, pageWidth - 15f, y, linePaint)
        y += if (isThermal) 14f else 18f

        // Items
        for (item in items) {
            val itemName = if (item.productName.length > (if (isThermal) 22 else 40))
                item.productName.take(if (isThermal) 20 else 38) + ".."
            else item.productName

            canvas.drawText(itemName, 15f, y, boldPaint)
            canvas.drawText("${item.quantity}", if (isThermal) 170f else 340f, y, textPaint)
            canvas.drawText("%.1f".format(item.unitPrice), if (isThermal) 215f else 420f, y, textPaint)
            canvas.drawText("%.1f".format(item.subtotal), pageWidth - 15f, y, rightPaint)
            y += if (isThermal) 10f else 14f

            // Batch & Expiry subline
            val subline = "B:${item.batchNumber} Exp:${item.expiryDate}"
            canvas.drawText(subline, 20f, y, Paint(subPaint).apply { textAlign = Paint.Align.LEFT })
            y += if (isThermal) 14f else 18f
        }

        canvas.drawLine(15f, y, pageWidth - 15f, y, linePaint)
        y += if (isThermal) 15f else 22f

        // Totals
        fun drawRow(label: String, value: String, isBold: Boolean = false) {
            val p = if (isBold) boldPaint else textPaint
            val rp = if (isBold) Paint(boldPaint).apply { textAlign = Paint.Align.RIGHT } else rightPaint
            canvas.drawText(label, if (isThermal) 120f else 320f, y, p)
            canvas.drawText(value, pageWidth - 15f, y, rp)
            y += if (isThermal) 14f else 18f
        }

        drawRow("Subtotal:", "${settings.currency} %.2f".format(sale.subtotal))
        if (sale.discountAmount > 0) {
            drawRow("Discount:", "-${settings.currency} %.2f".format(sale.discountAmount))
        }
        if (sale.taxAmount > 0) {
            drawRow("Tax:", "${settings.currency} %.2f".format(sale.taxAmount))
        }
        drawRow("Grand Total:", "${settings.currency} %.2f".format(sale.grandTotal), true)
        drawRow("Amount Paid (${sale.paymentMethod}):", "${settings.currency} %.2f".format(sale.amountPaid))
        if (sale.changeGiven > 0) {
            drawRow("Change Returned:", "${settings.currency} %.2f".format(sale.changeGiven))
        }
        if (sale.remainingBalance > 0) {
            drawRow("Balance Due:", "${settings.currency} %.2f".format(sale.remainingBalance), true)
        }

        y += if (isThermal) 10f else 20f
        canvas.drawLine(15f, y, pageWidth - 15f, y, linePaint)
        y += if (isThermal) 16f else 22f

        // Footer note
        canvas.drawText(settings.invoiceFooter, centerX, y, subPaint)

        pdfDocument.finishPage(page)

        val dir = File(context.cacheDir, "invoices")
        dir.mkdirs()
        val file = File(dir, "${sale.invoiceNumber}.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return file
    }

    fun generateReorderReportPdf(
        context: Context,
        products: List<ProductWithStock>,
        settings: PharmacySettings
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(0, 77, 64)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val boldPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        var y = 40f
        canvas.drawText("${settings.pharmacyName} — Low Stock Reorder List", 20f, y, titlePaint)
        y += 20f
        val sdf = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault())
        canvas.drawText("Generated: ${sdf.format(Date())} | Total Reorder Items: ${products.size}", 20f, y, textPaint)
        y += 20f
        canvas.drawLine(20f, y, 575f, y, linePaint)
        y += 18f

        // Table Header
        canvas.drawText("Medicine Name", 20f, y, boldPaint)
        canvas.drawText("Category", 180f, y, boldPaint)
        canvas.drawText("Current", 280f, y, boldPaint)
        canvas.drawText("Min Level", 350f, y, boldPaint)
        canvas.drawText("Supplier", 430f, y, boldPaint)
        y += 12f
        canvas.drawLine(20f, y, 575f, y, linePaint)
        y += 18f

        for (p in products) {
            if (y > 800) break
            canvas.drawText(p.name.take(24), 20f, y, textPaint)
            canvas.drawText(p.category.take(14), 180f, y, textPaint)
            canvas.drawText("${p.totalStock}", 280f, y, boldPaint)
            canvas.drawText("${p.minStockLevel}", 350f, y, textPaint)
            canvas.drawText(p.supplierName.ifEmpty { "Default" }.take(18), 430f, y, textPaint)
            y += 18f
        }

        pdfDocument.finishPage(page)
        val file = File(context.cacheDir, "Reorder_Report.pdf")
        FileOutputStream(file).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return file
    }
}
