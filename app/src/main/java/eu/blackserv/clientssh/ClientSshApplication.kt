package eu.blackserv.clientssh

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import eu.blackserv.clientssh.backup.ProfileBackupActivity

class ClientSshApplication : Application(), Application.ActivityLifecycleCallbacks {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        secureBackupWindow(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        secureBackupWindow(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        secureBackupWindow(activity)
    }

    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun secureBackupWindow(activity: Activity) {
        if (activity is ProfileBackupActivity) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
