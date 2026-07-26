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
            val telemetryRepository = SshTelemetryRepository(
                SharedPreferencesHealthCheckStorage(
                    context = applicationContext,
                    valueKey = SharedPreferencesHealthCheckStorage.SSH_TELEMETRY_HISTORY_VALUE_KEY,
                ),
            )
            val run = HealthMonitorRunExecutor(
                healthExecutor = HealthCheckExecutor(
                    snapshotRepository = snapshotRepository,
                    probe = TcpHealthProbe(),
                    historyRepository = historyRepository,
                ),
                telemetryCollector = SshTelemetryCollector(
                    JschSshTelemetryTransport(applicationContext),
                ),
                telemetryRepository = telemetryRepository,
            ).execute(
                profile = profile,
                config = config,
            )

            if (run.transition.notifyStatusChange) {
                HealthStatusNotifier(applicationContext).notifyStatusChange(
                    profileId = profileId,
                    displayName = profile.name.ifBlank { profile.host },
                    snapshot = run.transition.snapshot,
                )
            }

            finish(
                outcome = HealthCheckRunOutcome.SUCCESS,
                detail = run.diagnosticLabel(),
                result = Result.success(),
            )
        }.getOrElse { error ->
            val decision = healthWorkerFailureDecision(runAttemptCount)
            finish(
                outcome = decision.outcome,
                detail = error.message.orEmpty().ifBlank { error::class.simpleName.orEmpty() },
                result = if (decision.shouldRetry) Result.retry() else Result.failure(),
            )
        }
    }

    companion object {
        const val KEY_PROFILE_ID = "profile_id"
        internal const val MAX_RETRY_ATTEMPTS = 3
    }
}

internal data class HealthWorkerFailureDecision(
    val shouldRetry: Boolean,
    val outcome: HealthCheckRunOutcome,
)

internal fun healthWorkerFailureDecision(runAttemptCount: Int): HealthWorkerFailureDecision {
    require(runAttemptCount >= 0) { "runAttemptCount must not be negative" }
    val shouldRetry = runAttemptCount < HealthCheckWorker.MAX_RETRY_ATTEMPTS
    return HealthWorkerFailureDecision(
        shouldRetry = shouldRetry,
        outcome = if (shouldRetry) HealthCheckRunOutcome.RETRY else HealthCheckRunOutcome.FAILED,
    )
}
