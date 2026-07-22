package eu.blackserv.clientssh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.model.AppSkin

private val BlackServGreen = Color(0xFF62D58A)
private val BlackServAmber = Color(0xFFD9A441)
private val BlackServGraphite = Color(0xFF050A08)
private val BlackServPanel = Color(0xFF101A16)
private val BlackServPanelSoft = Color(0xFF19241F)
private val BlackServText = Color(0xFFE6F0EA)
private val BlackServMuted = Color(0xFFA7B5AD)

private val GraphiteDarkColors = darkColorScheme(
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
    error = Color(0xFFFF8D8D),
)

private val NeonDarkColors = darkColorScheme(
    primary = Color(0xFF45FF87),
    secondary = Color(0xFFFFC94A),
    tertiary = Color(0xFF58E8FF),
    background = Color(0xFF010605),
    surface = Color(0xFF07110E),
    surfaceVariant = Color(0xFF0B1B15),
    onPrimary = Color(0xFF001B0A),
    onSecondary = Color(0xFF201600),
    onBackground = Color(0xFFF0FFF6),
    onSurface = Color(0xFFF0FFF6),
    onSurfaceVariant = Color(0xFFA4B9AE),
    outline = Color(0xFF1E7147),
    error = Color(0xFFFF7F87),
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
    skin: AppSkin = AppSkin.GRAPHITE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = when {
        !darkTheme -> LightColors
        skin == AppSkin.NEON -> NeonDarkColors
        else -> GraphiteDarkColors
    }

    MaterialTheme(
        colorScheme = colors,
        shapes = BlackServShapes,
        content = content,
    )
}
