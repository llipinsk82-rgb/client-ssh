package eu.blackserv.clientssh.health

class HealthCheckExecutor(
    private val snapshotRepository: HealthCheckRepository,
    private val probe: HealthProbe,
    private val historyRepository: HealthCheckHistoryRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun execute(
        profileId: String,
        target: HealthTarget,
        offlineFailureThreshold: Int,
    ): HealthCheckTransition {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        val observation = probe.check(target)
        val now = clock()
        val transition = snapshotRepository.applyObservation(
            profileId = profileId,
            observation = observation,
            now = now,
            offlineFailureThreshold = offlineFailureThreshold,
        )
        historyRepository?.append(
            HealthCheckRecord(
                profileId = profileId,
                checkedAt = now,
                status = transition.snapshot.status,
                responseTimeMs = transition.snapshot.responseTimeMs,
                message = transition.snapshot.message,
            ),
        )
        return transition
    }
}
