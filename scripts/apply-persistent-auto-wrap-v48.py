#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
MODELS = ROOT / "app/src/main/java/eu/blackserv/clientssh/model/Models.kt"
TERMINAL = ROOT / "app/src/main/java/eu/blackserv/clientssh/ui/screens/TerminalScreen.kt"
PREFERENCES = ROOT / "app/src/main/java/eu/blackserv/clientssh/terminal/TerminalWrapPreferences.kt"
PREFERENCES_TEST = ROOT / "app/src/test/java/eu/blackserv/clientssh/terminal/TerminalWrapPreferencesTest.kt"
GRADLE = ROOT / "app/build.gradle.kts"


def ensure_import(source: str, imported: str) -> str:
    line = f"import {imported}\n"
    if line in source:
        return source
    first_import = source.find("import ")
    if first_import < 0:
        raise RuntimeError("Kotlin source has no import section")
    return source[:first_import] + line + source[first_import:]


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"Expected one {label}, found {count}")
    return source.replace(old, new, 1)


if PREFERENCES.exists():
    print("Persistent terminal wrap already applied; nothing to do.")
    sys.exit(0)

models = MODELS.read_text(encoding="utf-8")
models = replace_once(models, 'WRAP("Zawijaj")', 'WRAP("AUTO ZAWIJANIE")', "WRAP label")
models = replace_once(models, 'NO_WRAP("Bez zawijania")', 'NO_WRAP("BEZ ZAWIJANIA")', "NO_WRAP label")
MODELS.write_text(models, encoding="utf-8")

terminal = TERMINAL.read_text(encoding="utf-8")
state_pattern = re.compile(
    r"var\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s+by\s+remember(?:Saveable)?\s*\{\s*"
    r"mutableStateOf\(\s*TextWrapMode\.(?:WRAP|NO_WRAP)\s*\)\s*\}",
    re.DOTALL,
)
match = state_pattern.search(terminal)
if match is None:
    raise RuntimeError("Could not find the existing transient TextWrapMode state")
mode_name = match.group("name")
replacement = f'''val terminalWrapContext = LocalContext.current
    var {mode_name} by remember {{
        mutableStateOf(TerminalWrapPreferences.load(terminalWrapContext))
    }}
    LaunchedEffect({mode_name}) {{
        TerminalWrapPreferences.save(terminalWrapContext, {mode_name})
    }}'''
terminal = terminal[: match.start()] + replacement + terminal[match.end() :]
terminal = ensure_import(terminal, "androidx.compose.runtime.LaunchedEffect")
terminal = ensure_import(terminal, "androidx.compose.ui.platform.LocalContext")
terminal = ensure_import(terminal, "eu.blackserv.clientssh.terminal.TerminalWrapPreferences")
if terminal.count("TerminalWrapPreferences.load") != 1:
    raise RuntimeError("Persistent wrap load hook was not inserted exactly once")
if terminal.count("TerminalWrapPreferences.save") != 1:
    raise RuntimeError("Persistent wrap save hook was not inserted exactly once")
TERMINAL.write_text(terminal, encoding="utf-8")

PREFERENCES.parent.mkdir(parents=True, exist_ok=True)
PREFERENCES.write_text(
    '''package eu.blackserv.clientssh.terminal

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
''',
    encoding="utf-8",
)

PREFERENCES_TEST.parent.mkdir(parents=True, exist_ok=True)
PREFERENCES_TEST.write_text(
    '''package eu.blackserv.clientssh.terminal

import eu.blackserv.clientssh.model.TextWrapMode
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalWrapPreferencesTest {
    @Test
    fun `missing or invalid preference defaults to automatic wrapping`() {
        assertEquals(TextWrapMode.WRAP, TerminalWrapPreferences.decode(null))
        assertEquals(TextWrapMode.WRAP, TerminalWrapPreferences.decode("invalid"))
    }

    @Test
    fun `both wrap modes round trip through private preference value`() {
        TextWrapMode.entries.forEach { mode ->
            assertEquals(mode, TerminalWrapPreferences.decode(TerminalWrapPreferences.encode(mode)))
        }
    }
}
''',
    encoding="utf-8",
)

gradle = GRADLE.read_text(encoding="utf-8")
gradle = replace_once(gradle, "versionCode = 47", "versionCode = 48", "versionCode 47")
GRADLE.write_text(gradle, encoding="utf-8")

print("Persistent AUTO ZAWIJANIE applied successfully for v48.")
