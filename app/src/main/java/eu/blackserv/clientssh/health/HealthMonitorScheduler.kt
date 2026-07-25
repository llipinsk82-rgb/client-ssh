package eu.blackserv.clientssh.health

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
            .setConstraints(networkConstraints())
            .setInputData(profileInput(config.profileId))
            .addTag(TAG_ALL_HEALTH_CHECKS)
            .addTag(profileTag(config.profileId))
            .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName(config.profileId),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * Runs the same worker used by periodic monitoring as a one-time diagnostic job.
     * The periodic schedule is not replaced or delayed.
     */
    fun runNow(profileId: String) {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        val request = OneTimeWorkRequestBuilder<HealthCheckWorker>()
            .setConstraints(networkConstraints())
            .setInputData(profileInput(profileId))
            .addTag(TAG_ALL_HEALTH_CHECKS)
            .addTag(profileTag(profileId))
            .addTag(TAG_MANUAL_BACKGROUND_TEST)
            .build()

        workManager.enqueueUniqueWork(
            immediateWorkName(profileId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancel(profileId: String) {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        workManager.cancelUniqueWork(uniqueWorkName(profileId))
        workManager.cancelUniqueWork(immediateWorkName(profileId))
    }

    fun reconcile(configs: Collection<HealthMonitorConfig>) {
        configs.forEach { config ->
            if (config.enabled) schedule(config) else cancel(config.profileId)
        }
    }

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private fun profileInput(profileId: String): Data = Data.Builder()
        .putString(HealthCheckWorker.KEY_PROFILE_ID, profileId)
        .build()

    companion object {
        const val TAG_ALL_HEALTH_CHECKS = "health-check-monitor"
        const val TAG_MANUAL_BACKGROUND_TEST = "health-check-manual-background-test"

        internal fun uniqueWorkName(profileId: String): String =
            "health-check-${stableId(profileId)}"

        internal fun immediateWorkName(profileId: String): String =
            "health-check-now-${stableId(profileId)}"

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
