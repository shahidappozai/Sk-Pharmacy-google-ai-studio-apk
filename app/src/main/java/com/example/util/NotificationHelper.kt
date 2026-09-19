package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

object NotificationHelper {
    private const val CHANNEL_EXPIRY = "sk_pharmacy_expiry_alerts"
    private const val CHANNEL_LOW_STOCK = "sk_pharmacy_low_stock"
    private const val CHANNEL_GENERAL = "sk_pharmacy_general"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val expiryChannel = NotificationChannel(
                CHANNEL_EXPIRY,
                "Medicine Expiry Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts for expired or soon-to-expire medicines"
            }

            val lowStockChannel = NotificationChannel(
                CHANNEL_LOW_STOCK,
                "Low Stock & Reorder Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when medicine inventory falls below minimum threshold"
            }

            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL,
                "Pharmacy General & Daily Closing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Daily shift closing reminders and system updates"
            }

            notificationManager.createNotificationChannels(listOf(expiryChannel, lowStockChannel, generalChannel))
        }
    }

    fun showExpiryAlert(context: Context, expiredCount: Int, expiringSoonCount: Int) {
        if (expiredCount == 0 && expiringSoonCount == 0) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "alerts")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            101,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (expiredCount > 0) "⚠️ Pharmacy Alert: $expiredCount Expired Medicine(s)" else "⏰ Expiry Alert: $expiringSoonCount Expiring Soon"
        val message = "Review expiry management in SK Pharmacy to quarantine or discount affected batches."

        val notification = NotificationCompat.Builder(context, CHANNEL_EXPIRY)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(101, notification)
        } catch (_: SecurityException) {
            // Handled gracefully if POST_NOTIFICATIONS runtime permission not granted yet
        }
    }

    fun showLowStockAlert(context: Context, lowStockCount: Int) {
        if (lowStockCount == 0) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "low_stock")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            102,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_LOW_STOCK)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("📦 Low Stock Alert: $lowStockCount Medicines Low")
            .setContentText("Check reorder list and create purchase orders with distributors.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(102, notification)
        } catch (_: SecurityException) {
        }
    }
}
