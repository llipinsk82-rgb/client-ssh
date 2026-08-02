package eu.blackserv.clientssh.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PremiumPanel(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    selected: Boolean = false,
    cornerRadius: Dp = 18.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalPremiumSkin.current
    val shape = RoundedCornerShape(cornerRadius)
    val border = when {
        selected -> tokens.accentBright
        strong -> tokens.border
        else -> tokens.borderMuted
    }
    val top = if (strong) tokens.panelStrong else tokens.panelTop
    val bottom = tokens.panelBottom

    Box(
        modifier = modifier
            .shadow(
                elevation = if (selected) 14.dp else 7.dp,
                shape = shape,
                ambientColor = border.copy(alpha = if (selected) 0.42f else 0.18f),
                spotColor = border.copy(alpha = if (selected) 0.42f else 0.18f),
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(
                BorderStroke(
                    width = if (selected) 1.4.dp else 1.dp,
                    color = border.copy(alpha = if (selected) 0.95f else 0.72f),
                ),
                shape,
            )
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun PremiumActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    secondary: Boolean = false,
    enabled: Boolean = true,
) {
    val tokens = LocalPremiumSkin.current
    val shape = RoundedCornerShape(13.dp)
    val accent = if (secondary) tokens.secondary else tokens.accentBright
    val fill = if (secondary) {
        Brush.verticalGradient(
            listOf(tokens.secondary.copy(alpha = 0.13f), tokens.panelBottom),
        )
    } else {
        Brush.verticalGradient(
            listOf(tokens.accent.copy(alpha = 0.38f), tokens.accent.copy(alpha = 0.13f)),
        )
    }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 46.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, accent.copy(alpha = if (enabled) 0.92f else 0.35f), shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent.copy(alpha = if (enabled) 1f else 0.45f),
            )
        }
        Text(
            text = text,
            modifier = if (icon == null) Modifier else Modifier.padding(start = 8.dp),
            color = accent.copy(alpha = if (enabled) 1f else 0.45f),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun PremiumIconSurface(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalPremiumSkin.current.accentBright,
) {
    val tokens = LocalPremiumSkin.current
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = tokens.panelStrong,
        border = BorderStroke(1.dp, tokens.border.copy(alpha = 0.82f)),
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
            Icon(icon, contentDescription = contentDescription, tint = tint)
        }
    }
}
