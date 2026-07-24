package eu.blackserv.clientssh.health

class HealthCheckExecutor(
    private val snapshotRepository: HealthCheckRepository,
    private val probe: HealthProbe,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun execute(
        profileId: String,
        target: HealthTarget,
        offlineFailureThreshold: Int,
    ): HealthCheckTransition {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        val observation = probe.check(target)
        return snapshotRepository.applyObservation(
            profileId = profileId,
            observation = observation,
            now = clock(),
            offlineFailureThreshold = offlineFailureThreshold,
        )
    }
}
