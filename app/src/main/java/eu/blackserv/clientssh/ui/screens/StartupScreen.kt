package eu.blackserv.clientssh.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

private val SplashNight = Color(0xFF010611)
private val SplashDeep = Color(0xFF041126)
private val SplashBlue = Color(0xFF008DFF)
private val SplashCyan = Color(0xFF26D5FF)
private val SplashIce = Color(0xFFEDF8FF)

@Composable
fun StartupScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )

    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        delay(2_300)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SplashNight, SplashDeep, SplashNight),
                ),
            ),
    ) {
        PremiumNetworkField(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(2.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PremiumBsMark()
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Client",
                        color = SplashIce,
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Light,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "SSH",
                        color = SplashCyan,
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .width(220.dp)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, SplashCyan, Color.Transparent),
                            ),
                        ),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "B E Z P I E C Z N E   P O Ł Ą C Z E N I A",
                    color = SplashIce.copy(alpha = 0.88f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "P E Ł N A   K O N T R O L A",
                    color = SplashCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }

            PremiumDeviceScene(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            )

            Text(
                text = "BLACKSERV  •  SECURE COMMAND CENTER",
                color = SplashIce.copy(alpha = 0.48f),
                fontSize = 9.sp,
                letterSpacing = 1.4.sp,
            )
        }
    }
}

@Composable
private fun PremiumBsMark() {
    Box(
        modifier = Modifier
            .size(132.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFDFEFF), Color(0xFFB9C8DA)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(34.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "B",
            color = Color(0xFF07142A),
            fontSize = 88.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "S",
            modifier = Modifier.offset(x = 10.dp, y = 8.dp),
            color = SplashCyan,
            fontSize = 72.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun PremiumNetworkField(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(SplashBlue.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(size.width * 0.08f, size.height * 0.30f),
                radius = size.maxDimension * 0.72f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(SplashCyan.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.92f, size.height * 0.72f),
                radius = size.maxDimension * 0.55f,
            ),
        )

        val step = size.width / 12f
        var x = 0f
        while (x <= size.width) {
            drawLine(
                color = SplashBlue.copy(alpha = 0.055f),
                start = Offset(x, size.height * 0.53f),
                end = Offset(x, size.height),
                strokeWidth = 1f,
            )
            x += step
        }
        var y = size.height * 0.56f
        while (y <= size.height) {
            drawLine(
                color = SplashCyan.copy(alpha = 0.045f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += step
        }

        repeat(8) { index ->
            val inset = index * size.minDimension * 0.035f
            drawArc(
                color = SplashBlue.copy(alpha = 0.15f - index * 0.012f),
                startAngle = 210f,
                sweepAngle = 108f,
                useCenter = false,
                topLeft = Offset(-size.width * 0.36f + inset, -size.height * 0.10f + inset),
                size = Size(size.width * 0.86f, size.height * 0.55f),
                style = Stroke(width = 1.2f),
            )
        }

        val nodes = listOf(
            Offset(size.width * 0.12f, size.height * 0.61f),
            Offset(size.width * 0.28f, size.height * 0.78f),
            Offset(size.width * 0.52f, size.height * 0.67f),
            Offset(size.width * 0.74f, size.height * 0.84f),
            Offset(size.width * 0.90f, size.height * 0.63f),
        )
        nodes.zipWithNext().forEach { (start, end) ->
            drawLine(SplashCyan.copy(alpha = 0.25f), start, end, 1.5f)
        }
        nodes.forEachIndexed { index, node ->
            val color = if (index % 2 == 0) SplashCyan else SplashBlue
            drawCircle(color.copy(alpha = 0.14f), 11f, node)
            drawCircle(color, 2.7f, node)
        }
    }
}

@Composable
private fun PremiumDeviceScene(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val phoneWidth = size.width * 0.48f
            val phoneHeight = size.height * 0.76f
            val phoneLeft = (size.width - phoneWidth) / 2f
            val phoneTop = size.height * 0.10f
            val corner = CornerRadius(phoneWidth * 0.12f, phoneWidth * 0.12f)

            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF183C68), Color(0xFF020813)),
                    start = Offset(phoneLeft, phoneTop),
                    end = Offset(phoneLeft + phoneWidth, phoneTop + phoneHeight),
                ),
                topLeft = Offset(phoneLeft, phoneTop),
                size = Size(phoneWidth, phoneHeight),
                cornerRadius = corner,
            )
            drawRoundRect(
                color = SplashCyan.copy(alpha = 0.70f),
                topLeft = Offset(phoneLeft, phoneTop),
                size = Size(phoneWidth, phoneHeight),
                cornerRadius = corner,
                style = Stroke(width = 2.4f),
            )

            val center = Offset(size.width / 2f, phoneTop + phoneHeight * 0.48f)
            drawCircle(SplashBlue.copy(alpha = 0.18f), phoneWidth * 0.32f, center)
            drawCircle(SplashCyan.copy(alpha = 0.20f), phoneWidth * 0.24f, center)

            val shield = Path().apply {
                moveTo(center.x, center.y - phoneWidth * 0.23f)
                lineTo(center.x + phoneWidth * 0.20f, center.y - phoneWidth * 0.12f)
                lineTo(center.x + phoneWidth * 0.15f, center.y + phoneWidth * 0.15f)
                quadraticBezierTo(center.x, center.y + phoneWidth * 0.29f, center.x, center.y + phoneWidth * 0.29f)
                quadraticBezierTo(center.x - phoneWidth * 0.15f, center.y + phoneWidth * 0.15f, center.x - phoneWidth * 0.20f, center.y - phoneWidth * 0.12f)
                close()
            }
            drawPath(shield, SplashCyan.copy(alpha = 0.15f))
            drawPath(shield, SplashCyan, style = Stroke(width = 4f))

            val lockWidth = phoneWidth * 0.13f
            drawRoundRect(
                color = SplashIce,
                topLeft = Offset(center.x - lockWidth / 2f, center.y),
                size = Size(lockWidth, lockWidth * 0.82f),
                cornerRadius = CornerRadius(8f, 8f),
            )
            drawArc(
                color = SplashIce,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - lockWidth * 0.36f, center.y - lockWidth * 0.48f),
                size = Size(lockWidth * 0.72f, lockWidth * 0.80f),
                style = Stroke(width = 5f),
            )

            fun serviceCard(left: Float, top: Float, terminal: Boolean) {
                val cardW = size.width * 0.22f
                val cardH = size.height * 0.23f
                drawRoundRect(
                    brush = Brush.linearGradient(listOf(Color(0xFF12345D), Color(0xFF071326))),
                    topLeft = Offset(left, top),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(20f, 20f),
                )
                drawRoundRect(
                    color = SplashBlue.copy(alpha = 0.70f),
                    topLeft = Offset(left, top),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(20f, 20f),
                    style = Stroke(width = 2f),
                )
                if (terminal) {
                    drawLine(SplashIce, Offset(left + cardW * 0.25f, top + cardH * 0.36f), Offset(left + cardW * 0.42f, top + cardH * 0.50f), 4f)
                    drawLine(SplashIce, Offset(left + cardW * 0.42f, top + cardH * 0.50f), Offset(left + cardW * 0.25f, top + cardH * 0.64f), 4f)
                    drawLine(SplashCyan, Offset(left + cardW * 0.50f, top + cardH * 0.66f), Offset(left + cardW * 0.72f, top + cardH * 0.66f), 4f)
                } else {
                    repeat(3) { row ->
                        val rowY = top + cardH * (0.28f + row * 0.22f)
                        drawRoundRect(
                            color = SplashIce.copy(alpha = 0.12f),
                            topLeft = Offset(left + cardW * 0.18f, rowY),
                            size = Size(cardW * 0.64f, cardH * 0.13f),
                            cornerRadius = CornerRadius(6f, 6f),
                        )
                        drawCircle(SplashCyan, 3.5f, Offset(left + cardW * 0.70f, rowY + cardH * 0.065f))
                    }
                }
            }

            serviceCard(size.width * 0.02f, size.height * 0.60f, terminal = true)
            serviceCard(size.width * 0.76f, size.height * 0.58f, terminal = false)
        }
    }
}
