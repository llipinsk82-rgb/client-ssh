package eu.blackserv.clientssh.health

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

interface HealthWorkScheduler {
    fun schedule(config: HealthMonitorConfig)
    fun cancel(profileId: String)
}

class HealthMonitorScheduler(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context.applicationContext),
) : HealthWorkScheduler {
    override fun schedule(config: HealthMonitorConfig) {
        if (!config.enabled) {
            cancel(config.profileId)
            return
        }

        val request = PeriodicWorkRequest.Builder(
            HealthCheckWorker::class.java,
            config.intervalMinutes,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(
                Data.Builder()
                    .putString(HealthCheckWorker.KEY_PROFILE_ID, config.profileId)
                    .build(),
            )
            .addTag(TAG_ALL_HEALTH_CHECKS)
            .addTag(profileTag(config.profileId))
            .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName(config.profileId),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun cancel(profileId: String) {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        workManager.cancelUniqueWork(uniqueWorkName(profileId))
    }

    fun reconcile(configs: Collection<HealthMonitorConfig>) {
        configs.forEach { config ->
            if (config.enabled) schedule(config) else cancel(config.profileId)
        }
    }

    companion object {
        const val TAG_ALL_HEALTH_CHECKS = "health-check-monitor"

        internal fun uniqueWorkName(profileId: String): String =
            "health-check-${stableId(profileId)}"

        internal fun profileTag(profileId: String): String =
            "health-profile-${stableId(profileId)}"

        private fun stableId(value: String): String {
            require(value.isNotBlank()) { "profileId must not be blank" }
            return MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(StandardCharsets.UTF_8))
                .take(12)
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}
