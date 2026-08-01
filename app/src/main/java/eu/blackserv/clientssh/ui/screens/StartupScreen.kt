package eu.blackserv.clientssh.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

private val SPLASH_ASSETS = (0..15).map { index ->
    "client_ssh_splash_4k_${index.toString().padStart(2, '0')}.b64"
}

/**
 * Oficjalny Sapphire splash jest prawdziwym assetem 2160 x 3840 WebP.
 * Nie jest odtwarzany z prostych figur Compose, dzięki czemu zachowuje dokładnie
 * zatwierdzony premium look zamiast wcześniejszego płaskiego efektu.
 */
@Composable
fun StartupScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )
    val splash = remember(context.applicationContext) {
        runCatching {
            val encoded = buildString {
                SPLASH_ASSETS.forEach { assetName ->
                    append(
                        context.assets.open(assetName)
                            .bufferedReader(Charsets.US_ASCII)
                            .use { it.readText() },
                    )
                }
            }
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

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
        delay(2_350)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF00050D)),
        contentAlignment = Alignment.Center,
    ) {
        if (splash != null) {
            Image(
                bitmap = splash,
                contentDescription = "Client SSH",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
            )
        } else {
            CircularProgressIndicator(color = Color(0xFF28D7FF))
        }
    }
}
