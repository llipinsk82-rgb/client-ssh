package eu.blackserv.clientssh.ui.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

private const val ESC = '\u001B'
private const val BEL = '\u0007'

fun String.toPlainTerminalText(): String = buildString(length) {
    walkTerminalText(
        onText = { append(it) },
        onSgr = {},
    )
}

fun String.toTerminalAnnotatedString(defaultColor: Color): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var currentColor = defaultColor
    var bold = false

    walkTerminalText(
        onText = { text ->
            builder.pushStyle(SpanStyle(color = currentColor))
            builder.append(text)
            builder.pop()
        },
        onSgr = { codes ->
            if (codes.isEmpty()) {
                currentColor = defaultColor
                bold = false
            }
            codes.forEach { code ->
                when (code) {
                    0 -> {
                        currentColor = defaultColor
                        bold = false
                    }
                    1 -> bold = true
                    22 -> bold = false
                    30 -> currentColor = if (bold) Color(0xFF6E7681) else Color(0xFF484F58)
                    31 -> currentColor = if (bold) Color(0xFFFF7B72) else Color(0xFFD73A49)
                    32 -> currentColor = if (bold) Color(0xFF7EE787) else Color(0xFF3FB950)
                    33 -> currentColor = if (bold) Color(0xFFFFD33D) else Color(0xFFD29922)
                    34 -> currentColor = if (bold) Color(0xFF79C0FF) else Color(0xFF58A6FF)
                    35 -> currentColor = if (bold) Color(0xFFD2A8FF) else Color(0xFFBC8CFF)
                    36 -> currentColor = if (bold) Color(0xFFA5F3FC) else Color(0xFF56D4DD)
                    37 -> currentColor = if (bold) Color(0xFFFFFFFF) else defaultColor
                    39 -> currentColor = defaultColor
                    in 90..97 -> currentColor = brightAnsiColor(code, defaultColor)
                }
            }
        },
    )

    return builder.toAnnotatedString()
}

private fun brightAnsiColor(code: Int, defaultColor: Color): Color = when (code) {
    90 -> Color(0xFF8B949E)
    91 -> Color(0xFFFF7B72)
    92 -> Color(0xFF7EE787)
    93 -> Color(0xFFFFD33D)
    94 -> Color(0xFF79C0FF)
    95 -> Color(0xFFD2A8FF)
    96 -> Color(0xFFA5F3FC)
    97 -> Color(0xFFFFFFFF)
    else -> defaultColor
}

private fun String.walkTerminalText(
    onText: (String) -> Unit,
    onSgr: (List<Int>) -> Unit,
) {
    var index = 0
    var runStart = 0

    fun flush(until: Int) {
        if (until > runStart) onText(substring(runStart, until))
    }

    while (index < length) {
        if (this[index] != ESC) {
            index++
            continue
        }

        flush(index)
        val next = getOrNull(index + 1)
        when (next) {
            ']' -> {
                index = skipOsc(index)
                runStart = index
            }
            '[' -> {
                val finalIndex = findCsiFinal(index + 2)
                if (finalIndex == -1) {
                    index++
                    runStart = index
                } else {
                    if (this[finalIndex] == 'm') {
                        val params = substring(index + 2, finalIndex)
                        onSgr(params.toSgrCodes())
                    }
                    index = finalIndex + 1
                    runStart = index
                }
            }
            else -> {
                index = (index + 2).coerceAtMost(length)
                runStart = index
            }
        }
    }
    flush(length)
}

private fun String.skipOsc(start: Int): Int {
    var i = start + 2
    while (i < length) {
        val ch = this[i]
        if (ch == BEL) return i + 1
        if (ch == ESC && getOrNull(i + 1) == '\\') return i + 2
        i++
    }
    return length
}

private fun String.findCsiFinal(start: Int): Int {
    var i = start
    while (i < length) {
        if (this[i] in '@'..'~') return i
        i++
    }
    return -1
}

private fun String.toSgrCodes(): List<Int> =
    if (isBlank()) listOf(0)
    else split(';').map { part -> part.toIntOrNull() ?: 0 }
