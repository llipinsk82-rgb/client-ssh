package eu.blackserv.clientssh.health

import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthMonitorRunExecutorTest {
    @Test
    fun `disabled telemetry leaves TCP execution unchanged`() {
        var transportCalls = 0
        val telemetryRepository = SshTelemetryRepository(MemoryStorage())
        val executor = executor(
            telemetryRepository = telemetryRepository,
            transport = SshTelemetryTransport { _, _, _, _ ->
                transportCalls++
                SshTelemetryExecResult(validPayload(), 0)
            },
        )

        val result = executor.execute(
            profile = profile(),
            config = HealthMonitorConfig(profileId = PROFILE_ID, sshTelemetryEnabled = false),
        )

        assertEquals(HealthStatus.ONLINE, result.transition.snapshot.status)
        assertNull(result.telemetryRecord)
        assertEquals(0, transportCalls)
        assertNull(telemetryRepository.latest(PROFILE_ID))
    }

    @Test
    fun `enabled telemetry stores validated sample`() {
        val telemetryRepository = SshTelemetryRepository(MemoryStorage())
        val executor = executor(
            telemetryRepository = telemetryRepository,
            transport = SshTelemetryTransport { _, _, _, _ -> SshTelemetryExecResult(validPayload(), 0) },
        )

        val result = executor.execute(
            profile = profile(),
            config = HealthMonitorConfig(profileId = PROFILE_ID, sshTelemetryEnabled = true),
        )

        assertEquals(HealthStatus.ONLINE, result.transition.snapshot.status)
        assertEquals(SshTelemetryRecordOutcome.SUCCESS, result.telemetryRecord?.outcome)
        assertEquals(25.0, telemetryRepository.latest(PROFILE_ID)?.sample?.cpuUsagePercent ?: -1.0, 0.001)
        assertTrue(result.diagnosticLabel().contains("SSH_OK"))
    }

    @Test
    fun `telemetry failure does not change TCP online state`() {
        val telemetryRepository = SshTelemetryRepository(MemoryStorage())
        val executor = executor(
            telemetryRepository = telemetryRepository,
            transport = SshTelemetryTransport { _, _, _, _ ->
                throw SshTelemetryTransportException(SshTelemetryFailureKind.HOST_KEY_NOT_TRUSTED)
            },
        )

        val result = executor.execute(
            profile = profile(),
            config = HealthMonitorConfig(profileId = PROFILE_ID, sshTelemetryEnabled = true),
        )

        assertEquals(HealthStatus.ONLINE, result.transition.snapshot.status)
        assertEquals(SshTelemetryRecordOutcome.FAILURE, result.telemetryRecord?.outcome)
        assertEquals(SshTelemetryFailureKind.HOST_KEY_NOT_TRUSTED, result.telemetryRecord?.failureKind)
        assertTrue(result.diagnosticLabel().contains("SSH_HOST_KEY_NOT_TRUSTED"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects profile and config mismatch`() {
        executor(
            telemetryRepository = SshTelemetryRepository(MemoryStorage()),
            transport = SshTelemetryTransport { _, _, _, _ -> SshTelemetryExecResult(validPayload(), 0) },
        ).execute(
            profile = profile(),
            config = HealthMonitorConfig(profileId = "different"),
        )
    }

    private fun executor(
        telemetryRepository: SshTelemetryRepository,
        transport: SshTelemetryTransport,
    ) = HealthMonitorRunExecutor(
        healthExecutor = HealthCheckExecutor(
            snapshotRepository = HealthCheckRepository(MemoryStorage()),
            probe = HealthProbe { HealthObservation.Success(10L) },
            clock = { 100L },
        ),
        telemetryCollector = SshTelemetryCollector(transport),
        telemetryRepository = telemetryRepository,
        clock = { 200L },
    )

    private fun profile() = HostProfile(
        id = PROFILE_ID,
        name = "node",
        host = "example.com",
        port = 22,
        username = "operator",
        protocol = ConnectionProtocol.SSH,
        authenticationMethod = AuthenticationMethod.PASSWORD,
        password = "sample-credential",
    )

    private fun validPayload(): String = """
        ${SshTelemetryParser.FORMAT_VERSION}
        CPU_A=100 0 100 700 0 0 0 0
        CPU_B=125 0 125 850 0 0 0 0
        MEM_TOTAL_KB=8000000
        MEM_AVAILABLE_KB=5000000
        LOAD_1=1.25
        LOAD_5=1.00
        LOAD_15=0.75
        DISK_TOTAL_KB=10000000
        DISK_USED_KB=4000000
        DISK_AVAILABLE_KB=6000000
        DISK_USED_PERCENT=40
        UPTIME_SECONDS=86400
        NET_A_RX_BYTES=100000
        NET_A_TX_BYTES=50000
        NET_B_RX_BYTES=102000
        NET_B_TX_BYTES=51000
        SAMPLE_MS=1000
        PING_STATUS=OK
        PING_MS=12.4
    """.trimIndent() + "\n"

    private class MemoryStorage : HealthCheckStorage {
        private var value: String? = null

        override fun read(): String? = value

        override fun write(value: String) {
            this.value = value
        }
    }

    private companion object {
        const val PROFILE_ID = "profile-a"
    }
}
