package eu.blackserv.clientssh.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.model.AppSkin

private val BlackServGreen = Color(0xFF62D58A)
private val BlackServAmber = Color(0xFFD9A441)
private val BlackServGraphite = Color(0xFF050A08)
private val BlackServPanel = Color(0xFF101A16)
private val BlackServPanelSoft = Color(0xFF19241F)
private val BlackServText = Color(0xFFE6F0EA)
private val BlackServMuted = Color(0xFFA7B5AD)

val LocalAppSkin = staticCompositionLocalOf { AppSkin.GRAPHITE }

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
    primary = Color(0xFF35FF7A),
    secondary = Color(0xFFFFBE2E),
    tertiary = Color(0xFF24D8FF),
    background = Color(0xFF000302),
    surface = Color(0xE607110D),
    surfaceVariant = Color(0xFF071C13),
    onPrimary = Color(0xFF001B08),
    onSecondary = Color(0xFF211500),
    onBackground = Color(0xFFF0FFF6),
    onSurface = Color(0xFFF0FFF6),
    onSurfaceVariant = Color(0xFFA8C1B3),
    outline = Color(0xFF16844A),
    error = Color(0xFFFF6673),
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

private val GraphiteShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(13.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val NeonShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(7.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(14.dp),
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
    val shapes = if (skin == AppSkin.NEON) NeonShapes else GraphiteShapes

    CompositionLocalProvider(LocalAppSkin provides skin) {
        MaterialTheme(
            colorScheme = colors,
            shapes = shapes,
            content = content,
        )
    }
}

@Composable
fun AppBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val skin = LocalAppSkin.current
    val gridStep = with(LocalDensity.current) { 30.dp.toPx() }
    val fineStroke = with(LocalDensity.current) { 0.55.dp.toPx() }
    val strongStroke = with(LocalDensity.current) { 1.dp.toPx() }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val background = MaterialTheme.colorScheme.background

    val backdropModifier = if (skin == AppSkin.NEON) {
        Modifier.drawBehind {
            drawRect(background)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.5f, 0f),
                    radius = size.maxDimension * 0.72f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(tertiary.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width, size.height * 0.55f),
                    radius = size.maxDimension * 0.58f,
                ),
            )

            var x = 0f
            while (x <= size.width) {
                drawLine(
                    color = tertiary.copy(alpha = 0.045f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = fineStroke,
                )
                x += gridStep
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(
                    color = primary.copy(alpha = 0.04f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = fineStroke,
                )
                y += gridStep
            }
            drawLine(
                color = primary.copy(alpha = 0.55f),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = strongStroke,
            )
        }
    } else {
        Modifier.background(background)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(backdropModifier),
        content = content,
    )
}
