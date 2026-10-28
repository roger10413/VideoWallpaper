package com.ming.videowallpaper

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Ongoing notification showing the e-invoice carrier barcode on the lock screen. Tapping it opens
 * [CarrierActivity] over the lock screen, so the barcode can be scanned without unlocking.
 */
object CarrierNotification {
    private const val CHANNEL_ID = "carrier"
    private const val NOTIFICATION_ID = 1

    /** Posts, refreshes or removes the notification to match the current settings. */
    fun update(context: Context) {
        val settings = WallpaperSettings(context)
        val code = settings.carrierCode
        val manager = NotificationManagerCompat.from(context)
        if (!settings.carrierNotify || !Code39.isValidCarrier(code) || !canPost(context)) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        createChannel(context)

        val openBarcode = PendingIntent.getActivity(
            context, 0,
            Intent(context, CarrierActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Wide bitmap with thick modules; the ImageView scales it down to fit the notification.
        val barcode = Code39.render(code, moduleWidth = 6, height = 160)
        val collapsed = RemoteViews(context.packageName, R.layout.notification_carrier).apply {
            setImageViewBitmap(R.id.barcode, barcode)
        }
        val expanded = RemoteViews(context.packageName, R.layout.notification_carrier_big).apply {
            setImageViewBitmap(R.id.barcode, barcode)
            setTextViewText(R.id.code, code)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_barcode)
            // Title/text are used where custom views aren't shown (e.g. some lock screen styles).
            .setContentTitle("手機條碼 $code")
            .setContentText("點一下放大條碼，不用解鎖")
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(openBarcode)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check above and now; nothing to show.
        }
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun createChannel(context: Context) {
        // Default importance without sound: "silent" (low-importance) notifications are hidden
        // from the lock screen by default, which would defeat the purpose.
        val channel = NotificationChannel(CHANNEL_ID, "載具條碼", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "在鎖定畫面顯示發票載具的手機條碼"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
