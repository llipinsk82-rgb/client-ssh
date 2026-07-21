package eu.blackserv.clientssh.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import eu.blackserv.clientssh.MainActivity
import eu.blackserv.clientssh.R

class TerminalSessionService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val profileName = intent?.getStringExtra(EXTRA_PROFILE_NAME).orEmpty().ifBlank { "Terminal" }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Aktywna sesja: $profileName")
            .setContentText("Sesja pozostaje aktywna po zablokowaniu telefonu")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Aktywne sesje terminala",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Utrzymuje połączenie SSH lub Telnet podczas blokady ekranu"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_PROFILE_NAME = "profile_name"
        private const val CHANNEL_ID = "terminal_session"
        private const val NOTIFICATION_ID = 1001
    }
}
