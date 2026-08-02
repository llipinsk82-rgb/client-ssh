package eu.blackserv.clientssh.terminal

import android.content.Context
import eu.blackserv.clientssh.model.TextWrapMode

internal object TerminalWrapPreferences {
    private const val PREFERENCES_NAME = "terminal_preferences"
    private const val KEY_TEXT_WRAP_MODE = "text_wrap_mode"

    fun load(context: Context): TextWrapMode {
        val stored = context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TEXT_WRAP_MODE, null)
        return decode(stored)
    }

    fun save(context: Context, mode: TextWrapMode) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TEXT_WRAP_MODE, encode(mode))
            .apply()
    }

    internal fun decode(value: String?): TextWrapMode =
        value
            ?.let { stored -> runCatching { TextWrapMode.valueOf(stored) }.getOrNull() }
            ?: TextWrapMode.WRAP

    internal fun encode(mode: TextWrapMode): String = mode.name
}
