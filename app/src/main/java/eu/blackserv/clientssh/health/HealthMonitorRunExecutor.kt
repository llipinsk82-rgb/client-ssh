package eu.blackserv.clientssh.health

import eu.blackserv.clientssh.model.HostProfile

data class HealthMonitorRunResult(
    val transition: HealthCheckTransition,
    val telemetryRecord: SshTelemetryRecord?,
) {
    fun diagnosticLabel(): String = buildString {
        append(transition.snapshot.status.name)
        telemetryRecord?.let { record ->
            append(" • SSH_")
            append(
                when (record.outcome) {
                    SshTelemetryRecordOutcome.SUCCESS -> record.sample?.status?.name ?: "INVALID"
                    SshTelemetryRecordOutcome.FAILURE -> record.failureKind?.name ?: "FAILED"
                },
            )
        }
    }
}

class HealthMonitorRunExecutor(
    private val healthExecutor: HealthCheckExecutor,
    private val telemetryCollector: SshTelemetryCollector,
    private val telemetryRepository: SshTelemetryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun execute(
        profile: HostProfile,
        config: HealthMonitorConfig,
    ): HealthMonitorRunResult {
        require(profile.id == config.profileId) { "profile and config must match" }

        val transition = healthExecutor.execute(
            profileId = profile.id,
            target = HealthTarget(
                host = profile.host,
                port = profile.port,
                timeoutMs = config.timeoutMs,
            ),
            offlineFailureThreshold = config.offlineFailureThreshold,
        )

        val telemetryRecord = if (config.sshTelemetryEnabled) {
            val collectedAt = clock()
            val record = when (
                val result = telemetryCollector.collect(
                    profile = profile,
                    pingTarget = config.resolvedPingTarget(),
                )
            ) {
                is SshTelemetryCollectionResult.Success -> SshTelemetryRecord.success(
                    profileId = profile.id,
                    collectedAt = collectedAt,
                    sample = result.sample,
                )

                is SshTelemetryCollectionResult.Failure -> SshTelemetryRecord.failure(
                    profileId = profile.id,
                    collectedAt = collectedAt,
                    kind = result.kind,
                    message = result.message,
                )
            }
            telemetryRepository.append(record)
        } else {
            null
        }

        return HealthMonitorRunResult(
            transition = transition,
            telemetryRecord = telemetryRecord,
        )
    }
}
