package eu.blackserv.clientssh.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import eu.blackserv.clientssh.storage.LocalAppStore

class HealthCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val profileId = inputData.getString(KEY_PROFILE_ID)?.takeIf { it.isNotBlank() }
            ?: return Result.failure()

        return runCatching {
            val configStorage = SharedPreferencesHealthCheckStorage(
                context = applicationContext,
                valueKey = SharedPreferencesHealthCheckStorage.CONFIG_VALUE_KEY,
            )
            val config = HealthMonitorConfigRepository(configStorage).get(profileId)
                ?: return Result.success()
            if (!config.enabled) return Result.success()

            val profile = LocalAppStore(applicationContext)
                .loadProfiles()
                .firstOrNull { it.id == profileId }
                ?: return Result.success()

            val observation = TcpHealthProbe().check(
                HealthTarget(
                    host = profile.host,
                    port = profile.port,
                    timeoutMs = config.timeoutMs,
                ),
            )

            val snapshotStorage = SharedPreferencesHealthCheckStorage(applicationContext)
            val transition = HealthCheckRepository(snapshotStorage).applyObservation(
                profileId = profileId,
                observation = observation,
                now = System.currentTimeMillis(),
                offlineFailureThreshold = config.offlineFailureThreshold,
            )

            if (transition.notifyStatusChange) {
                HealthStatusNotifier(applicationContext).notifyStatusChange(
                    profileId = profileId,
                    displayName = profile.name.ifBlank { profile.host },
                    snapshot = transition.snapshot,
                )
            }

            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        const val KEY_PROFILE_ID = "profile_id"
    }
}
