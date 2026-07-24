package eu.blackserv.clientssh.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCheckStateMachineTest {
    @Test
    fun firstSuccessMarksOnlineWithoutNotification() {
        val transition = apply(
            current = null,
            observation = HealthObservation.Success(responseTimeMs = 42L),
        )

        assertEquals(HealthStatus.ONLINE, transition.snapshot.status)
        assertEquals(42L, transition.snapshot.responseTimeMs)
        assertEquals(1000L, transition.snapshot.lastSuccessAt)
        assertEquals(0, transition.snapshot.consecutiveFailures)
        assertFalse(transition.notifyStatusChange)
    }

    @Test
    fun transientFailureDoesNotMarkOnlineHostOffline() {
        val online = snapshot(status = HealthStatus.ONLINE)

        val transition = apply(
            current = online,
            observation = HealthObservation.Failure("Timeout"),
            threshold = 3,
        )

        assertEquals(HealthStatus.ONLINE, transition.snapshot.status)
        assertEquals(1, transition.snapshot.consecutiveFailures)
        assertNull(transition.snapshot.responseTimeMs)
        assertFalse(transition.notifyStatusChange)
    }

    @Test
    fun thresholdFailureMarksOfflineAndNotifiesOnce() {
        val afterTwoFailures = snapshot(
            status = HealthStatus.ONLINE,
            consecutiveFailures = 2,
        )

        val offline = apply(
            current = afterTwoFailures,
            observation = HealthObservation.Failure("Connection refused"),
            threshold = 3,
        )
        val stillOffline = apply(
            current = offline.snapshot,
            observation = HealthObservation.Failure("Connection refused"),
            threshold = 3,
        )

        assertEquals(HealthStatus.OFFLINE, offline.snapshot.status)
        assertEquals(3, offline.snapshot.consecutiveFailures)
        assertTrue(offline.notifyStatusChange)
        assertEquals(HealthStatus.OFFLINE, stillOffline.snapshot.status)
        assertEquals(4, stillOffline.snapshot.consecutiveFailures)
        assertFalse(stillOffline.notifyStatusChange)
    }

    @Test
    fun successAfterOfflineRecoversAndNotifiesOnce() {
        val offline = snapshot(
            status = HealthStatus.OFFLINE,
            consecutiveFailures = 5,
            lastSuccessAt = 100L,
        )

        val recovered = apply(
            current = offline,
            observation = HealthObservation.Success(responseTimeMs = 15L),
        )
        val remainsOnline = apply(
            current = recovered.snapshot,
            observation = HealthObservation.Success(responseTimeMs = 12L),
        )

        assertEquals(HealthStatus.ONLINE, recovered.snapshot.status)
        assertEquals(0, recovered.snapshot.consecutiveFailures)
        assertEquals(1000L, recovered.snapshot.lastSuccessAt)
        assertTrue(recovered.notifyStatusChange)
        assertFalse(remainsOnline.notifyStatusChange)
    }

    @Test
    fun staleObservationCannotOverwriteNewerState() {
        val current = snapshot(
            status = HealthStatus.ONLINE,
            consecutiveFailures = 0,
            lastCheckedAt = 2000L,
            lastSuccessAt = 2000L,
        )

        val stale = apply(
            current = current,
            observation = HealthObservation.Failure("Delayed timeout"),
            now = 1000L,
            threshold = 1,
        )

        assertSame(current, stale.snapshot)
        assertFalse(stale.notifyStatusChange)
    }

    @Test
    fun failureCounterSaturatesAtIntegerMaximum() {
        val current = snapshot(
            status = HealthStatus.OFFLINE,
            consecutiveFailures = Int.MAX_VALUE,
        )

        val transition = apply(
            current = current,
            observation = HealthObservation.Failure("Still offline"),
            threshold = 3,
        )

        assertEquals(Int.MAX_VALUE, transition.snapshot.consecutiveFailures)
        assertEquals(HealthStatus.OFFLINE, transition.snapshot.status)
        assertFalse(transition.notifyStatusChange)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSnapshotFromAnotherProfile() {
        HealthCheckStateMachine.apply(
            profileId = "profile-a",
            current = snapshot(profileId = "profile-b"),
            observation = HealthObservation.Success(responseTimeMs = 1L),
            now = 1000L,
            offlineFailureThreshold = 3,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroFailureThreshold() {
        apply(
            current = null,
            observation = HealthObservation.Failure("Timeout"),
            threshold = 0,
        )
    }

    private fun apply(
        current: HealthCheckSnapshot?,
        observation: HealthObservation,
        threshold: Int = 3,
        now: Long = 1000L,
    ): HealthCheckTransition = HealthCheckStateMachine.apply(
        profileId = "profile-a",
        current = current,
        observation = observation,
        now = now,
        offlineFailureThreshold = threshold,
    )

    private fun snapshot(
        profileId: String = "profile-a",
        status: HealthStatus = HealthStatus.UNKNOWN,
        consecutiveFailures: Int = 0,
        lastCheckedAt: Long? = null,
        lastSuccessAt: Long? = null,
    ) = HealthCheckSnapshot(
        profileId = profileId,
        status = status,
        consecutiveFailures = consecutiveFailures,
        lastCheckedAt = lastCheckedAt,
        lastSuccessAt = lastSuccessAt,
        responseTimeMs = 50L,
    )
}
