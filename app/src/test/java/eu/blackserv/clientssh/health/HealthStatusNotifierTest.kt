package eu.blackserv.clientssh.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HealthStatusNotifierTest {
    @Test
    fun `offline notification includes profile and failure`() {
        val content = healthNotificationContent(
            displayName = "Production",
            snapshot = HealthCheckSnapshot(
                profileId = "a",
                status = HealthStatus.OFFLINE,
                message = "SocketTimeoutException: timed out",
            ),
        )

        assertEquals("Production jest niedostępny", content.title)
        assertEquals("SocketTimeoutException: timed out", content.text)
    }

    @Test
    fun `recovery notification includes response time`() {
        val content = healthNotificationContent(
            displayName = "Production",
            snapshot = HealthCheckSnapshot(
                profileId = "a",
                status = HealthStatus.ONLINE,
                responseTimeMs = 18,
            ),
        )

        assertEquals("Production jest ponownie dostępny", content.title)
        assertEquals("Połączenie TCP: 18 ms", content.text)
    }

    @Test
    fun `background test notification clearly confirms worker execution`() {
        val content = healthBackgroundSelfTestContent(
            displayName = "Vu+ Zero",
            snapshot = HealthCheckSnapshot(
                profileId = "vuzero",
                status = HealthStatus.ONLINE,
                responseTimeMs = 79,
            ),
        )

        assertEquals("Test Monitora w tle zakończony", content.title)
        assertEquals(
            "Vu+ Zero: ONLINE • TCP 79 ms. Worker działa po zamknięciu aplikacji.",
            content.text,
        )
    }

    @Test
    fun `notification ids are stable and use separate namespaces`() {
        assertEquals(
            HealthStatusNotifier.notificationId("profile-a"),
            HealthStatusNotifier.notificationId("profile-a"),
        )
        assertNotEquals(
            HealthStatusNotifier.notificationId("profile-a"),
            HealthStatusNotifier.notificationId("profile-b"),
        )
        assertNotEquals(
            HealthStatusNotifier.notificationId("profile-a"),
            HealthStatusNotifier.backgroundSelfTestNotificationId("profile-a"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank notification profile is rejected`() {
        HealthStatusNotifier.notificationId(" ")
    }
}
