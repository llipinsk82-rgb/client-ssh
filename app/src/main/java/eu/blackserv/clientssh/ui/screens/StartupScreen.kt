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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import eu.blackserv.clientssh.model.AppSkin
import eu.blackserv.clientssh.ui.theme.ClientSshTheme
import eu.blackserv.clientssh.update.GitHubUpdateManager
import eu.blackserv.clientssh.update.ReleaseInfo
import eu.blackserv.clientssh.update.UpdateCheckResult
import kotlinx.coroutines.delay

private val SPLASH_ASSETS = (0..2).map { index ->
    "client_ssh_splash_hd_${index.toString().padStart(2, '0')}.b64"
}

/**
 * Oficjalny Sapphire splash używa zatwierdzonej grafiki rastrowej przygotowanej
 * z mastera 4K. Przy każdym zwykłym uruchomieniu równolegle wykonywany jest
 * krótki check OTA; dialog pojawia się tylko wtedy, gdy GitHub Releases zawiera
 * nowszą, podpisaną wersję aplikacji.
 */
@Composable
fun StartupScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val updateManager = remember(appContext) { GitHubUpdateManager(appContext) }
    var splashElapsed by remember { mutableStateOf(false) }
    var updateCheckFinished by remember { mutableStateOf(false) }
    var updateRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var navigationCompleted by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )
    val splash = remember(appContext) {
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

        updateManager.check { result ->
            if (result is UpdateCheckResult.Available) {
                updateRelease = result.release
            }
            updateCheckFinished = true
        }

        delay(2_350)
        splashElapsed = true

        // Nie blokuj uruchomienia aplikacji przez wolne lub niedostępne GitHub API.
        delay(1_650)
        if (!updateCheckFinished) updateCheckFinished = true
    }

    LaunchedEffect(splashElapsed, updateCheckFinished, updateRelease) {
        if (!navigationCompleted && splashElapsed && updateCheckFinished) {
            val release = updateRelease
            if (release != null) {
                showUpdateDialog = true
            } else {
                navigationCompleted = true
                onFinished()
            }
        }
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

        val release = updateRelease
        if (showUpdateDialog && release != null) {
            ClientSshTheme(skin = AppSkin.SAPPHIRE, darkTheme = true) {
                UpdateDialog(
                    context = context,
                    initialRelease = release,
                    onDismiss = {
                        showUpdateDialog = false
                        navigationCompleted = true
                        onFinished()
                    },
                )
            }
        }
    }
}
