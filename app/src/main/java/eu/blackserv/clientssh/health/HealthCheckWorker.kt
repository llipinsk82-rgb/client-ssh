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
        val diagnostics = HealthCheckDiagnosticsRepository(
            SharedPreferencesHealthCheckStorage(
                context = applicationContext,
                valueKey = SharedPreferencesHealthCheckStorage.DIAGNOSTICS_VALUE_KEY,
            ),
        )
        val startedAt = System.currentTimeMillis()
        diagnostics.markStarted(profileId, startedAt)

        fun finish(outcome: HealthCheckRunOutcome, detail: String, result: Result): Result {
            diagnostics.markFinished(
                profileId = profileId,
                startedAt = startedAt,
                finishedAt = System.currentTimeMillis(),
                outcome = outcome,
                detail = detail,
            )
            return result
        }

        return runCatching {
            val configStorage = SharedPreferencesHealthCheckStorage(
                context = applicationContext,
                valueKey = SharedPreferencesHealthCheckStorage.CONFIG_VALUE_KEY,
            )
            val config = HealthMonitorConfigRepository(configStorage).get(profileId)
                ?: return finish(HealthCheckRunOutcome.SKIPPED, "Brak konfiguracji", Result.success())
            if (!config.enabled) {
                return finish(HealthCheckRunOutcome.SKIPPED, "Monitoring wyłączony", Result.success())
            }

            val profile = LocalAppStore(applicationContext)
                .loadProfiles()
                .firstOrNull { it.id == profileId }
                ?: return finish(HealthCheckRunOutcome.SKIPPED, "Profil nie istnieje", Result.success())

            val snapshotRepository = HealthCheckRepository(
                SharedPreferencesHealthCheckStorage(applicationContext),
            )
            val historyRepository = HealthCheckHistoryRepository(
                SharedPreferencesHealthCheckStorage(
                    context = applicationContext,
                    valueKey = SharedPreferencesHealthCheckStorage.HISTORY_VALUE_KEY,
                ),
            )
            val transition = HealthCheckExecutor(
                snapshotRepository = snapshotRepository,
                probe = TcpHealthProbe(),
                historyRepository = historyRepository,
            ).execute(
                profileId = profileId,
                target = HealthTarget(
                    host = profile.host,
                    port = profile.port,
                    timeoutMs = config.timeoutMs,
                ),
                offlineFailureThreshold = config.offlineFailureThreshold,
            )

            if (transition.notifyStatusChange) {
                HealthStatusNotifier(applicationContext).notifyStatusChange(
                    profileId = profileId,
                    displayName = profile.name.ifBlank { profile.host },
                    snapshot = transition.snapshot,
                )
            }

            finish(
                outcome = HealthCheckRunOutcome.SUCCESS,
                detail = transition.snapshot.status.name,
                result = Result.success(),
            )
        }.getOrElse { error ->
            finish(
                outcome = HealthCheckRunOutcome.RETRY,
                detail = error.message.orEmpty().ifBlank { error::class.simpleName.orEmpty() },
                result = Result.retry(),
            )
        }
    }

    companion object {
        const val KEY_PROFILE_ID = "profile_id"
    }
}
