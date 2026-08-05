package eu.blackserv.clientssh.health

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import eu.blackserv.clientssh.MainActivity
import eu.blackserv.clientssh.R

internal data class HealthNotificationContent(
    val title: String,
    val text: String,
)

internal fun healthNotificationContent(
    displayName: String,
    snapshot: HealthCheckSnapshot,
): HealthNotificationContent {
    val target = displayName.trim().ifBlank { "Host" }
    return when (snapshot.status) {
        HealthStatus.OFFLINE -> HealthNotificationContent(
            title = "$target jest niedostępny",
            text = snapshot.message.ifBlank { "Health Check Monitor potwierdził brak połączenia." },
        )
        HealthStatus.ONLINE -> HealthNotificationContent(
            title = "$target jest ponownie dostępny",
            text = snapshot.responseTimeMs
                ?.let { "Połączenie TCP: ${it} ms" }
                ?: "Połączenie zostało przywrócone.",
        )
        HealthStatus.UNKNOWN -> HealthNotificationContent(
            title = "$target — status nieznany",
            text = "Brak potwierdzonego wyniku monitoringu.",
        )
    }
}

internal fun healthBackgroundSelfTestContent(
    displayName: String,
    snapshot: HealthCheckSnapshot,
): HealthNotificationContent {
    val target = displayName.trim().ifBlank { "Host" }
    val latency = snapshot.responseTimeMs?.let { " • TCP ${it} ms" }.orEmpty()
    return HealthNotificationContent(
        title = "Test Monitora w tle zakończony",
        text = "$target: ${snapshot.status.name}$latency. Worker działa po zamknięciu aplikacji.",
    )
}

class HealthStatusNotifier(
    private val context: Context,
) {
    fun notifyStatusChange(
        profileId: String,
        displayName: String,
        snapshot: HealthCheckSnapshot,
    ): Boolean {
        if (!notificationsAllowed()) return false
        createChannel()

        val content = healthNotificationContent(displayName, snapshot)
        val openAppIntent = openAppIntent(notificationId(profileId))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        return showNotification(notificationId(profileId), notification)
    }

    fun notifyBackgroundSelfTest(
        profileId: String,
        displayName: String,
        snapshot: HealthCheckSnapshot,
    ): Boolean {
        if (!notificationsAllowed()) return false
        createChannel()

        val notificationId = backgroundSelfTestNotificationId(profileId)
        val content = healthBackgroundSelfTestContent(displayName, snapshot)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setContentIntent(openAppIntent(notificationId))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        return showNotification(notificationId, notification)
    }

    private fun openAppIntent(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun showNotification(id: Int, notification: android.app.Notification): Boolean =
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        }.getOrDefault(false)

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Health Check Monitor",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Zmiany dostępności monitorowanych hostów"
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "health_check_status"

        internal fun notificationId(profileId: String): Int {
            require(profileId.isNotBlank()) { "profileId must not be blank" }
            return 0x48000000 or (profileId.hashCode() and 0x00ffffff)
        }

        internal fun backgroundSelfTestNotificationId(profileId: String): Int {
            require(profileId.isNotBlank()) { "profileId must not be blank" }
            return 0x49000000 or (profileId.hashCode() and 0x00ffffff)
        }
    }
}
