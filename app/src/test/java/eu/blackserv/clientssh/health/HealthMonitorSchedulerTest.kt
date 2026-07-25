package eu.blackserv.clientssh.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HealthMonitorSchedulerTest {
    @Test
    fun `work name is stable without exposing profile id`() {
        val first = HealthMonitorScheduler.uniqueWorkName("production@example.com:22")
        val second = HealthMonitorScheduler.uniqueWorkName("production@example.com:22")

        assertEquals(first, second)
        assertFalse(first.contains("production"))
        assertFalse(first.contains("example.com"))
    }

    @Test
    fun `immediate work name is stable and separate from periodic work`() {
        val profileId = "production@example.com:22"
        val immediate = HealthMonitorScheduler.immediateWorkName(profileId)

        assertEquals(immediate, HealthMonitorScheduler.immediateWorkName(profileId))
        assertNotEquals(HealthMonitorScheduler.uniqueWorkName(profileId), immediate)
        assertFalse(immediate.contains("production"))
        assertFalse(immediate.contains("example.com"))
    }

    @Test
    fun `different profiles receive different work names and tags`() {
        assertNotEquals(
            HealthMonitorScheduler.uniqueWorkName("profile-a"),
            HealthMonitorScheduler.uniqueWorkName("profile-b"),
        )
        assertNotEquals(
            HealthMonitorScheduler.immediateWorkName("profile-a"),
            HealthMonitorScheduler.immediateWorkName("profile-b"),
        )
        assertNotEquals(
            HealthMonitorScheduler.profileTag("profile-a"),
            HealthMonitorScheduler.profileTag("profile-b"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank profile id is rejected`() {
        HealthMonitorScheduler.uniqueWorkName(" ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank profile id is rejected for immediate work`() {
        HealthMonitorScheduler.immediateWorkName(" ")
    }
}
