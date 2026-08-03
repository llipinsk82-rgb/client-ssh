package eu.blackserv.clientssh.sftp

import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile

/**
 * Backward-compatible entry point used by security policy tests and callers
 * that only know the host. New connection code passes the complete profile so
 * it can distinguish modern SSH from the explicitly enabled legacy mode.
 */
internal fun safeSftpErrorMessage(error: Throwable, host: String): String =
    safeSftpErrorMessage(
        error = error,
        profile = HostProfile(
            name = host,
            host = host,
            port = ConnectionProtocol.SSH.defaultPort,
            username = "",
            protocol = ConnectionProtocol.SSH,
            authenticationMethod = AuthenticationMethod.PASSWORD,
        ),
    )
