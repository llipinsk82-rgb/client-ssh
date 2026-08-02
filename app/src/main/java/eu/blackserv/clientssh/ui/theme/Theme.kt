package eu.blackserv.clientssh.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
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

data class PremiumSkinTokens(
    val skin: AppSkin,
    val night: Color,
    val deep: Color,
    val panelTop: Color,
    val panelBottom: Color,
    val panelStrong: Color,
    val accent: Color,
    val accentBright: Color,
    val secondary: Color,
    val border: Color,
    val borderMuted: Color,
    val text: Color,
    val muted: Color,
    val terminal: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
)

private val SapphireTokens = PremiumSkinTokens(
    skin = AppSkin.SAPPHIRE,
    night = Color(0xFF00050D),
    deep = Color(0xFF03152E),
    panelTop = Color(0xF20A203B),
    panelBottom = Color(0xF2051122),
    panelStrong = Color(0xFF07172A),
    accent = Color(0xFF008DFF),
    accentBright = Color(0xFF28D7FF),
    secondary = Color(0xFFFFB44A),
    border = Color(0xFF147ED0),
    borderMuted = Color(0xFF214D73),
    text = Color(0xFFF4FAFF),
    muted = Color(0xFF9EB6CE),
    terminal = Color(0xFF00070D),
    success = Color(0xFF29E58B),
    warning = Color(0xFFFFB44A),
    danger = Color(0xFFFF5C6F),
)

private val AuroraTokens = PremiumSkinTokens(
    skin = AppSkin.AURORA,
    night = Color(0xFF000807),
    deep = Color(0xFF03251F),
    panelTop = Color(0xF2072D2A),
    panelBottom = Color(0xF2021515),
    panelStrong = Color(0xFF061F1D),
    accent = Color(0xFF00C9C1),
    accentBright = Color(0xFF4CFFE6),
    secondary = Color(0xFFFFC45B),
    border = Color(0xFF0CBAB1),
    borderMuted = Color(0xFF246B67),
    text = Color(0xFFF2FFFC),
    muted = Color(0xFF9AC8C1),
    terminal = Color(0xFF000B0A),
    success = Color(0xFF53F59C),
    warning = Color(0xFFFFC45B),
    danger = Color(0xFFFF6B7E),
)

private val ObsidianTokens = PremiumSkinTokens(
    skin = AppSkin.OBSIDIAN,
    night = Color(0xFF05030A),
    deep = Color(0xFF170A2E),
    panelTop = Color(0xF21D1035),
    panelBottom = Color(0xF2090712),
    panelStrong = Color(0xFF160C27),
    accent = Color(0xFF8D4DFF),
    accentBright = Color(0xFFC683FF),
    secondary = Color(0xFFFFB84D),
    border = Color(0xFF7A3DDA),
    borderMuted = Color(0xFF4A316B),
    text = Color(0xFFFFF8FF),
    muted = Color(0xFFB7A6C9),
    terminal = Color(0xFF07040C),
    success = Color(0xFF55E89B),
    warning = Color(0xFFFFB84D),
    danger = Color(0xFFFF6681),
)

fun premiumTokensFor(skin: AppSkin): PremiumSkinTokens = when (skin.canonical) {
    AppSkin.AURORA -> AuroraTokens
    AppSkin.OBSIDIAN -> ObsidianTokens
    else -> SapphireTokens
}

val LocalAppSkin = staticCompositionLocalOf { AppSkin.SAPPHIRE }
val LocalPremiumSkin = staticCompositionLocalOf { SapphireTokens }

private fun premiumColorScheme(tokens: PremiumSkinTokens) = darkColorScheme(
    primary = tokens.accent,
    onPrimary = Color(0xFF001523),
    primaryContainer = tokens.accent.copy(alpha = 0.22f),
    onPrimaryContainer = tokens.text,
    secondary = tokens.secondary,
    onSecondary = Color(0xFF211300),
    secondaryContainer = tokens.secondary.copy(alpha = 0.16f),
    onSecondaryContainer = tokens.secondary,
    tertiary = tokens.accentBright,
    onTertiary = Color(0xFF00181D),
    tertiaryContainer = tokens.accentBright.copy(alpha = 0.17f),
    onTertiaryContainer = tokens.text,
    background = tokens.night,
    onBackground = tokens.text,
    surface = tokens.panelBottom,
    onSurface = tokens.text,
    surfaceVariant = tokens.panelStrong,
    onSurfaceVariant = tokens.muted,
    surfaceTint = tokens.accent,
    inverseSurface = tokens.text,
    inverseOnSurface = tokens.night,
    inversePrimary = tokens.accent,
    outline = tokens.border,
    outlineVariant = tokens.borderMuted,
    error = tokens.danger,
    onError = Color(0xFF2B0006),
    errorContainer = tokens.danger.copy(alpha = 0.17f),
    onErrorContainer = Color(0xFFFFD9DE),
    scrim = Color.Black.copy(alpha = 0.82f),
)

private val PremiumShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun ClientSshTheme(
    skin: AppSkin = AppSkin.SAPPHIRE,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredDarkTheme = darkTheme
    val canonical = skin.canonical
    val tokens = premiumTokensFor(canonical)

    CompositionLocalProvider(
        LocalAppSkin provides canonical,
        LocalPremiumSkin provides tokens,
    ) {
        MaterialTheme(
            colorScheme = premiumColorScheme(tokens),
            shapes = PremiumShapes,
            content = content,
        )
    }
}

@Composable
fun AppBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalPremiumSkin.current
    val gridStep = with(LocalDensity.current) { 34.dp.toPx() }
    val fineStroke = with(LocalDensity.current) { 0.55.dp.toPx() }
    val brightStroke = with(LocalDensity.current) { 1.dp.toPx() }
    val nodeRadius = with(LocalDensity.current) { 2.2.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(tokens.night)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(tokens.deep, tokens.night, tokens.deep.copy(alpha = 0.82f)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tokens.accent.copy(alpha = 0.26f),
                            tokens.accent.copy(alpha = 0.07f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.50f, size.height * 0.46f),
                        radius = size.maxDimension * 0.74f,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(tokens.accentBright.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.02f, size.height * 0.18f),
                        radius = size.maxDimension * 0.48f,
                    ),
                )

                val horizon = size.height * 0.46f
                var y = horizon
                var row = 0
                while (y <= size.height) {
                    val alpha = 0.045f + (row.coerceAtMost(12) * 0.004f)
                    drawLine(
                        color = tokens.accent.copy(alpha = alpha),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = fineStroke,
                    )
                    y += gridStep * (0.72f + row * 0.035f)
                    row++
                }
                var x = -size.width
                while (x <= size.width * 2f) {
                    drawLine(
                        color = tokens.accentBright.copy(alpha = 0.045f),
                        start = Offset(size.width * 0.5f, horizon),
                        end = Offset(x, size.height),
                        strokeWidth = fineStroke,
                    )
                    x += gridStep
                }

                val traces = listOf(
                    listOf(
                        Offset(size.width * 0.06f, size.height * 0.16f),
                        Offset(size.width * 0.20f, size.height * 0.28f),
                        Offset(size.width * 0.13f, size.height * 0.42f),
                    ),
                    listOf(
                        Offset(size.width * 0.92f, size.height * 0.19f),
                        Offset(size.width * 0.77f, size.height * 0.31f),
                        Offset(size.width * 0.88f, size.height * 0.44f),
                    ),
                    listOf(
                        Offset(size.width * 0.10f, size.height * 0.78f),
                        Offset(size.width * 0.31f, size.height * 0.69f),
                        Offset(size.width * 0.48f, size.height * 0.82f),
                        Offset(size.width * 0.69f, size.height * 0.70f),
                        Offset(size.width * 0.90f, size.height * 0.83f),
                    ),
                )
                traces.forEachIndexed { traceIndex, points ->
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { point -> lineTo(point.x, point.y) }
                    }
                    drawPath(
                        path = path,
                        color = if (traceIndex == 1) {
                            tokens.accentBright.copy(alpha = 0.17f)
                        } else {
                            tokens.accent.copy(alpha = 0.19f)
                        },
                        style = Stroke(width = brightStroke),
                    )
                    points.forEach { point ->
                        drawCircle(tokens.accent.copy(alpha = 0.13f), nodeRadius * 4.2f, point)
                        drawCircle(tokens.accentBright.copy(alpha = 0.90f), nodeRadius, point)
                    }
                }

                repeat(7) { index ->
                    val inset = index * size.minDimension * 0.035f
                    drawArc(
                        color = tokens.accent.copy(alpha = 0.12f - index * 0.012f),
                        startAngle = 204f,
                        sweepAngle = 112f,
                        useCenter = false,
                        topLeft = Offset(-size.width * 0.46f + inset, -size.height * 0.10f + inset),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.92f, size.height * 0.56f),
                        style = Stroke(width = fineStroke),
                    )
                }

                drawLine(
                    color = tokens.accentBright.copy(alpha = 0.62f),
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = brightStroke,
                )
            },
        content = content,
    )
}
