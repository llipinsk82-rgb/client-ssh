package eu.blackserv.clientssh.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.BuildConfig
import eu.blackserv.clientssh.update.GitHubUpdateManager
import eu.blackserv.clientssh.update.ReleaseInfo
import eu.blackserv.clientssh.update.UpdateCheckResult
import java.io.File

private sealed interface UpdateUiState {
    data object Checking : UpdateUiState
    data object Current : UpdateUiState
    data class Available(val release: ReleaseInfo) : UpdateUiState
    data class Downloading(val release: ReleaseInfo) : UpdateUiState
    data class Ready(val release: ReleaseInfo, val apk: File) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

@Composable
fun UpdateDialog(context: Context, onDismiss: () -> Unit) {
    val manager = remember(context) { GitHubUpdateManager(context.applicationContext) }
    var state by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Checking) }

    fun check() {
        state = UpdateUiState.Checking
        manager.check { result ->
            state = when (result) {
                is UpdateCheckResult.Available -> UpdateUiState.Available(result.release)
                UpdateCheckResult.Current -> UpdateUiState.Current
                is UpdateCheckResult.Error -> UpdateUiState.Error(result.message)
            }
        }
    }

    LaunchedEffect(Unit) { check() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aktualizacje") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Zainstalowana wersja: ${BuildConfig.VERSION_NAME}")
                Spacer(Modifier.height(12.dp))
                when (val current = state) {
                    UpdateUiState.Checking -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Sprawdzanie GitHub Releases…")
                    }
                    UpdateUiState.Current -> Text("Masz najnowszą wersję aplikacji.")
                    is UpdateUiState.Available -> {
                        Text("Dostępna wersja: ${current.release.version}")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            current.release.notes,
                            modifier = Modifier.height(220.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                    is UpdateUiState.Downloading -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Pobieranie ${current.release.apkName}…")
                    }
                    is UpdateUiState.Ready -> {
                        Text("APK zostało pobrane. Naciśnij Instaluj.")
                        Spacer(Modifier.height(8.dp))
                        Text("Przy pierwszej aktualizacji Android może poprosić o zgodę na instalowanie z tej aplikacji.")
                    }
                    is UpdateUiState.Error -> Text(current.message)
                }
            }
        },
        confirmButton = {
            when (val current = state) {
                is UpdateUiState.Available -> Button(onClick = {
                    state = UpdateUiState.Downloading(current.release)
                    manager.download(current.release) { result ->
                        state = result.fold(
                            onSuccess = { UpdateUiState.Ready(current.release, it) },
                            onFailure = { UpdateUiState.Error(it.message ?: "Błąd pobierania") },
                        )
                    }
                }) { Text("Pobierz") }
                is UpdateUiState.Ready -> Button(onClick = {
                    manager.install(current.apk)
                }) { Text("Instaluj") }
                is UpdateUiState.Error, UpdateUiState.Current -> Button(onClick = { check() }) {
                    Text("Sprawdź ponownie")
                }
                else -> Unit
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}
