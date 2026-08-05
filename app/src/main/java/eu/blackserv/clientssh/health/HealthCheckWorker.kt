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
        val manualBackgroundTest = inputData.getBoolean(KEY_MANUAL_BACKGROUND_TEST, false)
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

            val notifier = HealthStatusNotifier(applicationContext)
            if (run.transition.notifyStatusChange) {
                notifier.notifyStatusChange(
                    profileId = profileId,
                    displayName = profile.name.ifBlank { profile.host },
                    snapshot = run.transition.snapshot,
                )
            }

            val testNotificationShown = if (manualBackgroundTest) {
                notifier.notifyBackgroundSelfTest(
                    profileId = profileId,
                    displayName = profile.name.ifBlank { profile.host },
                    snapshot = run.transition.snapshot,
                )
            } else {
                null
            }
            val diagnosticDetail = if (manualBackgroundTest) {
                buildString {
                    append(run.diagnosticLabel())
                    append(" • test tła: ")
                    append(
                        if (testNotificationShown == true) {
                            "powiadomienie wysłane"
                        } else {
                            "powiadomienie zablokowane"
                        },
                    )
                }
            } else {
                run.diagnosticLabel()
            }

            finish(
                outcome = HealthCheckRunOutcome.SUCCESS,
                detail = diagnosticDetail,
                result = Result.success(),
            )
        }.getOrElse { error ->
            val decision = healthWorkerFailureDecision(runAttemptCount)
            finish(
                outcome = decision.outcome,
                detail = healthWorkerSafeFailureDetail(error),
                result = if (decision.shouldRetry) Result.retry() else Result.failure(),
            )
        }
    }

    companion object {
        const val KEY_PROFILE_ID = "profile_id"
        const val KEY_MANUAL_BACKGROUND_TEST = "manual_background_test"
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

internal fun healthWorkerSafeFailureDetail(error: Throwable): String {
    val type = error::class.simpleName?.takeIf(String::isNotBlank) ?: "WorkerFailure"
    return "Błąd infrastruktury: $type"
}
