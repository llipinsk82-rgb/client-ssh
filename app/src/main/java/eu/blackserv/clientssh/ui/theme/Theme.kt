package eu.blackserv.clientssh.ui.theme

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.model.AppSkin

private val PremiumNight = Color(0xFF010611)
private val PremiumDeep = Color(0xFF041126)
private val PremiumPanel = Color(0xE6081830)
private val PremiumPanelSoft = Color(0xD9102846)
private val PremiumBlue = Color(0xFF008DFF)
private val PremiumCyan = Color(0xFF26D5FF)
private val PremiumIce = Color(0xFFEAF5FF)
private val PremiumMuted = Color(0xFFA4B9D1)
private val PremiumAmber = Color(0xFFFFB85C)
private val PremiumDanger = Color(0xFFFF7187)

val LocalAppSkin = staticCompositionLocalOf { AppSkin.GRAPHITE }

private val PremiumDarkColors = darkColorScheme(
    primary = PremiumBlue,
    secondary = PremiumAmber,
    tertiary = PremiumCyan,
    background = Color.Transparent,
    surface = PremiumPanel,
    surfaceVariant = PremiumPanelSoft,
    onPrimary = Color(0xFF001526),
    onSecondary = Color(0xFF241504),
    onTertiary = Color(0xFF00161E),
    onBackground = PremiumIce,
    onSurface = PremiumIce,
    onSurfaceVariant = PremiumMuted,
    outline = Color(0xFF24517D),
    outlineVariant = Color(0xFF163656),
    error = PremiumDanger,
    onError = Color(0xFF250007),
)

private val PremiumNeonColors = darkColorScheme(
    primary = PremiumCyan,
    secondary = PremiumAmber,
    tertiary = Color(0xFF3D7CFF),
    background = Color.Transparent,
    surface = Color(0xE606152A),
    surfaceVariant = Color(0xD90B2442),
    onPrimary = Color(0xFF00161E),
    onSecondary = Color(0xFF241504),
    onTertiary = Color.White,
    onBackground = Color(0xFFF1FAFF),
    onSurface = Color(0xFFF1FAFF),
    onSurfaceVariant = Color(0xFFABC5DD),
    outline = Color(0xFF147DB9),
    outlineVariant = Color(0xFF16466D),
    error = PremiumDanger,
    onError = Color(0xFF250007),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0069B8),
    secondary = Color(0xFF8A5B12),
    tertiary = Color(0xFF00677F),
    background = Color(0xFFF3F7FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFE4EDF7),
    onPrimary = Color.White,
    onBackground = Color(0xFF07131F),
    onSurface = Color(0xFF07131F),
    onSurfaceVariant = Color(0xFF4E6275),
    outline = Color(0xFFB8C8D8),
)

private val PremiumShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val PremiumNeonShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun ClientSshTheme(
    skin: AppSkin = AppSkin.GRAPHITE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = when {
        !darkTheme -> LightColors
        skin == AppSkin.NEON -> PremiumNeonColors
        else -> PremiumDarkColors
    }
    val shapes = if (skin == AppSkin.NEON) PremiumNeonShapes else PremiumShapes

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
    val accent = if (skin == AppSkin.NEON) PremiumCyan else PremiumBlue
    val secondaryAccent = if (skin == AppSkin.NEON) Color(0xFF3D7CFF) else PremiumCyan
    val gridStep = with(LocalDensity.current) { 30.dp.toPx() }
    val fineStroke = with(LocalDensity.current) { 0.55.dp.toPx() }
    val curveStroke = with(LocalDensity.current) { 0.9.dp.toPx() }
    val nodeRadius = with(LocalDensity.current) { 2.4.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(PremiumNight)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF061A3B),
                            PremiumDeep,
                            PremiumNight,
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.05f, size.height * 0.22f),
                        radius = size.maxDimension * 0.72f,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryAccent.copy(alpha = 0.13f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.78f),
                        radius = size.maxDimension * 0.60f,
                    ),
                )

                var x = 0f
                while (x <= size.width) {
                    drawLine(
                        color = PremiumCyan.copy(alpha = 0.035f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = fineStroke,
                    )
                    x += gridStep
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(
                        color = PremiumBlue.copy(alpha = 0.032f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = fineStroke,
                    )
                    y += gridStep
                }

                repeat(7) { index ->
                    val shift = index * size.height * 0.034f
                    val leftCurve = Path().apply {
                        moveTo(-size.width * 0.10f, size.height * 0.06f + shift)
                        cubicTo(
                            size.width * 0.18f,
                            size.height * 0.03f + shift,
                            size.width * 0.04f,
                            size.height * 0.38f + shift,
                            size.width * 0.34f,
                            size.height * 0.46f + shift,
                        )
                    }
                    drawPath(
                        path = leftCurve,
                        color = accent.copy(alpha = 0.12f - index * 0.010f),
                        style = Stroke(width = curveStroke),
                    )

                    val rightCurve = Path().apply {
                        moveTo(size.width * 1.08f, size.height * 0.20f + shift)
                        cubicTo(
                            size.width * 0.76f,
                            size.height * 0.18f + shift,
                            size.width * 0.96f,
                            size.height * 0.52f + shift,
                            size.width * 0.62f,
                            size.height * 0.63f + shift,
                        )
                    }
                    drawPath(
                        path = rightCurve,
                        color = secondaryAccent.copy(alpha = 0.10f - index * 0.008f),
                        style = Stroke(width = curveStroke),
                    )
                }

                val nodes = listOf(
                    Offset(size.width * 0.10f, size.height * 0.18f),
                    Offset(size.width * 0.22f, size.height * 0.36f),
                    Offset(size.width * 0.84f, size.height * 0.24f),
                    Offset(size.width * 0.72f, size.height * 0.58f),
                    Offset(size.width * 0.18f, size.height * 0.78f),
                    Offset(size.width * 0.88f, size.height * 0.86f),
                )
                nodes.forEachIndexed { index, node ->
                    val nodeColor = if (index % 2 == 0) accent else secondaryAccent
                    drawCircle(nodeColor.copy(alpha = 0.14f), nodeRadius * 3.8f, node)
                    drawCircle(nodeColor.copy(alpha = 0.85f), nodeRadius, node)
                }

                drawLine(
                    color = accent.copy(alpha = 0.55f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = curveStroke,
                )
            },
        content = content,
    )
}
