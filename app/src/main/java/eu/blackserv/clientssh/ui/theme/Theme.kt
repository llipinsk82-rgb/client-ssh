package eu.blackserv.clientssh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val BlackServGreen = Color(0xFF62D58A)
private val BlackServAmber = Color(0xFFD9A441)
private val BlackServGraphite = Color(0xFF050A08)
private val BlackServPanel = Color(0xFF101A16)
private val BlackServPanelSoft = Color(0xFF19241F)
private val BlackServText = Color(0xFFE6F0EA)
private val BlackServMuted = Color(0xFFA7B5AD)

private val DarkColors = darkColorScheme(
    primary = BlackServGreen,
    secondary = BlackServAmber,
    tertiary = Color(0xFF7DD6C2),
    background = BlackServGraphite,
    surface = BlackServPanel,
    surfaceVariant = BlackServPanelSoft,
    onPrimary = Color(0xFF06120B),
    onSecondary = Color(0xFF171006),
    onBackground = BlackServText,
    onSurface = BlackServText,
    onSurfaceVariant = BlackServMuted,
    outline = Color(0xFF2F4339),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B7F47),
    secondary = Color(0xFF8A641C),
    tertiary = Color(0xFF197565),
    background = Color(0xFFF3F6F1),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE4EBE4),
    onPrimary = Color.White,
    onBackground = Color(0xFF101713),
    onSurface = Color(0xFF101713),
    onSurfaceVariant = Color(0xFF536159),
    outline = Color(0xFFC7D2CA),
)

private val BlackServShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(13.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun ClientSshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = BlackServShapes,
        content = content,
    )
}
