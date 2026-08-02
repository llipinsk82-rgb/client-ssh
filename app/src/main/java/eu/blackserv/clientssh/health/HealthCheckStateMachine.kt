package eu.blackserv.clientssh.health

enum class HealthStatus {
    UNKNOWN,
    ONLINE,
    OFFLINE,
}

sealed interface HealthObservation {
    data class Success(val responseTimeMs: Long) : HealthObservation
    data class Failure(val message: String) : HealthObservation
}

data class HealthCheckSnapshot(
    val profileId: String,
    val status: HealthStatus = HealthStatus.UNKNOWN,
    val consecutiveFailures: Int = 0,
    val lastCheckedAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val responseTimeMs: Long? = null,
    val message: String = "",
)

data class HealthCheckTransition(
    val snapshot: HealthCheckSnapshot,
    val notifyStatusChange: Boolean,
)

object HealthCheckStateMachine {
    fun apply(
        profileId: String,
        current: HealthCheckSnapshot?,
        observation: HealthObservation,
        now: Long,
        offlineFailureThreshold: Int,
    ): HealthCheckTransition {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        require(now >= 0L) { "now must not be negative" }
        require(offlineFailureThreshold > 0) { "offlineFailureThreshold must be greater than zero" }
        require(current == null || current.profileId == profileId) {
            "Current snapshot belongs to another profile"
        }

        val previous = current ?: HealthCheckSnapshot(profileId = profileId)
        if (previous.lastCheckedAt != null && now < previous.lastCheckedAt) {
            return HealthCheckTransition(
                snapshot = previous,
                notifyStatusChange = false,
            )
        }

        return when (observation) {
            is HealthObservation.Success -> onSuccess(previous, observation, now)
            is HealthObservation.Failure -> onFailure(
                previous = previous,
                observation = observation,
                now = now,
                offlineFailureThreshold = offlineFailureThreshold,
            )
        }
    }

    private fun onSuccess(
        previous: HealthCheckSnapshot,
        observation: HealthObservation.Success,
        now: Long,
    ): HealthCheckTransition {
        val next = previous.copy(
            status = HealthStatus.ONLINE,
            consecutiveFailures = 0,
            lastCheckedAt = now,
            lastSuccessAt = now,
            responseTimeMs = observation.responseTimeMs.coerceAtLeast(0L),
            message = "Host dostępny",
        )
        return HealthCheckTransition(
            snapshot = next,
            notifyStatusChange = previous.status == HealthStatus.OFFLINE,
        )
    }

    private fun onFailure(
        previous: HealthCheckSnapshot,
        observation: HealthObservation.Failure,
        now: Long,
        offlineFailureThreshold: Int,
    ): HealthCheckTransition {
        val failures = if (previous.consecutiveFailures == Int.MAX_VALUE) {
            Int.MAX_VALUE
        } else {
            previous.consecutiveFailures + 1
        }
        val confirmedOffline = failures >= offlineFailureThreshold
        val nextStatus = if (confirmedOffline) HealthStatus.OFFLINE else previous.status
        val next = previous.copy(
            status = nextStatus,
            consecutiveFailures = failures,
            lastCheckedAt = now,
            responseTimeMs = null,
            message = observation.message,
        )
        return HealthCheckTransition(
            snapshot = next,
            notifyStatusChange = confirmedOffline && previous.status != HealthStatus.OFFLINE,
        )
    }
}
