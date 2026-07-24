package eu.blackserv.clientssh.health

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesHealthCheckStorage(
    context: Context,
    preferencesName: String = DEFAULT_PREFERENCES_NAME,
    private val valueKey: String = DEFAULT_VALUE_KEY,
) : HealthCheckStorage {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )

    override fun read(): String? = preferences.getString(valueKey, null)

    override fun write(value: String) {
        check(preferences.edit().putString(valueKey, value).commit()) {
            "Nie udało się zapisać stanu Health Check Monitora"
        }
    }

    companion object {
        const val DEFAULT_PREFERENCES_NAME = "health_check_monitor"
        const val DEFAULT_VALUE_KEY = "snapshots_v1"
    }
}
