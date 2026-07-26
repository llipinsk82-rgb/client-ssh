package eu.blackserv.clientssh

import android.app.Application
import eu.blackserv.clientssh.ssh.SshKnownHostsStore

class ClientSshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SshKnownHostsStore.prepare(this)
    }
}
