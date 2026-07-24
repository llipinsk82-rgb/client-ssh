package eu.blackserv.clientssh.health

class HealthMonitorController(
    private val configRepository: HealthMonitorConfigRepository,
    private val snapshotRepository: HealthCheckRepository,
    private val scheduler: HealthWorkScheduler,
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

    fun removeProfile(profileId: String) {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        scheduler.cancel(profileId)
        configRepository.remove(profileId)
        snapshotRepository.remove(profileId)
    }
}
