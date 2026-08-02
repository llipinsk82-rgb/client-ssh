package eu.blackserv.clientssh.health

class HealthMonitorController(
    private val configRepository: HealthMonitorConfigRepository,
    private val snapshotRepository: HealthCheckRepository,
    private val scheduler: HealthWorkScheduler,
    private val historyRepository: HealthCheckHistoryRepository? = null,
    private val diagnosticsRepository: HealthCheckDiagnosticsRepository? = null,
) {
    fun save(config: HealthMonitorConfig): HealthMonitorConfig {
        val saved = configRepository.upsert(config)
        if (saved.enabled) scheduler.schedule(saved) else scheduler.cancel(saved.profileId)
        return saved
    }

    fun setEnabled(profileId: String, enabled: Boolean): HealthMonitorConfig {
        val saved = configRepository.setEnabled(profileId, enabled)
        if (saved.enabled) scheduler.schedule(saved) else scheduler.cancel(saved.profileId)
        return saved
    }

    fun testBackgroundWorkerNow(profileId: String) {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        val config = configRepository.get(profileId)
            ?: throw IllegalStateException("Health monitoring is not configured for this profile")
        check(config.enabled) { "Health monitoring must be enabled before testing the background worker" }
        scheduler.runNow(profileId)
    }

    fun removeProfile(profileId: String) {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        scheduler.cancel(profileId)
        configRepository.remove(profileId)
        snapshotRepository.remove(profileId)
        historyRepository?.removeProfile(profileId)
        diagnosticsRepository?.remove(profileId)
    }
}
