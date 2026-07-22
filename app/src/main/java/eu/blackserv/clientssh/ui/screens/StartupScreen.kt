package eu.blackserv.clientssh.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import eu.blackserv.clientssh.R
import kotlinx.coroutines.delay

@Composable
fun StartupScreen(
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val startupBitmap = remember(context) {
        runCatching {
            val encoded = context.resources
                .openRawResource(R.raw.client_ssh_startup)
                .bufferedReader()
                .use { it.readText() }
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    LaunchedEffect(Unit) {
        delay(1_650)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF010713)),
        contentAlignment = Alignment.Center,
    ) {
        if (startupBitmap != null) {
            Image(
                bitmap = startupBitmap,
                contentDescription = "Client SSH",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
