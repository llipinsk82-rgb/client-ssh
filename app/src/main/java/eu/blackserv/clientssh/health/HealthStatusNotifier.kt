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
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_terminal)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(profileId), notification)
            true
        }.getOrDefault(false)
    }

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

        internal fun notificationId(profileId: String): Int =
            0x48000000 or (profileId.hashCode() and 0x00ffffff)
    }
}
