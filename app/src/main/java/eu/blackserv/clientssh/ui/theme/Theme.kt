package eu.blackserv.clientssh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF148CFF),
    secondary = Color(0xFF52B0FF),
    background = Color(0xFF050A11),
    surface = Color(0xFF0B1420),
    surfaceVariant = Color(0xFF132235),
    onPrimary = Color.White,
    onBackground = Color(0xFFE9F2FF),
    onSurface = Color(0xFFE9F2FF),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0068C9),
    secondary = Color(0xFF005FAF),
    background = Color(0xFFF5F8FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFE6EEF8),
)

@Composable
fun ClientSshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
