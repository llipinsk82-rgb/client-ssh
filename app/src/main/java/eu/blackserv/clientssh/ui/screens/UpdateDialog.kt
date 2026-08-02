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
import eu.blackserv.clientssh.update.InstallLaunchResult
import eu.blackserv.clientssh.update.ReleaseInfo
import eu.blackserv.clientssh.update.UpdateCheckResult
import java.io.File

private sealed interface UpdateUiState {
    data object Checking : UpdateUiState
    data object Current : UpdateUiState
    data class Available(val release: ReleaseInfo) : UpdateUiState
    data class Downloading(val release: ReleaseInfo) : UpdateUiState
    data class Ready(val release: ReleaseInfo, val apk: File) : UpdateUiState
    data class Info(val message: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

@Composable
fun UpdateDialog(
    context: Context,
    onDismiss: () -> Unit,
    initialRelease: ReleaseInfo? = null,
) {
    val manager = remember(context) { GitHubUpdateManager(context.applicationContext) }
    var state by remember(initialRelease) {
        mutableStateOf<UpdateUiState>(
            initialRelease?.let(UpdateUiState::Available) ?: UpdateUiState.Checking,
        )
    }

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

    LaunchedEffect(initialRelease) {
        if (initialRelease == null) check()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aktualizacja Client SSH") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Zainstalowana wersja: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Spacer(Modifier.height(12.dp))
                when (val current = state) {
                    UpdateUiState.Checking -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Sprawdzanie bezpiecznej aktualizacji OTA…")
                    }
                    UpdateUiState.Current -> Text("Masz najnowszą opublikowaną wersję aplikacji.")
                    is UpdateUiState.Available -> ReleaseDetails(current.release)
                    is UpdateUiState.Downloading -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Pobieranie i weryfikowanie ${current.release.apkName}…")
                    }
                    is UpdateUiState.Ready -> {
                        Text("Aktualizacja została pobrana oraz zweryfikowana.")
                        Spacer(Modifier.height(8.dp))
                        Text("Sprawdzono pakiet, wyższy versionCode, SHA-256 i zgodność certyfikatu podpisu.")
                        Spacer(Modifier.height(8.dp))
                        Text("SHA-256: ${current.release.apkSha256}")
                        Spacer(Modifier.height(8.dp))
                        Text("Naciśnij Instaluj. Android pokaże systemowe potwierdzenie aktualizacji.")
                    }
                    is UpdateUiState.Info -> Text(current.message)
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
                            onFailure = { UpdateUiState.Error(it.message ?: "Błąd pobierania aktualizacji") },
                        )
                    }
                }) { Text("Pobierz OTA") }
                is UpdateUiState.Ready -> Button(onClick = {
                    state = when (val result = manager.install(current.apk)) {
                        InstallLaunchResult.Started -> UpdateUiState.Info("Instalator Androida został uruchomiony.")
                        InstallLaunchResult.PermissionRequired -> UpdateUiState.Info(
                            "Android wymaga jednorazowej zgody na instalowanie aktualizacji z Client SSH. " +
                                "Włącz zgodę, wróć do aplikacji i ponownie naciśnij aktualizację.",
                        )
                        is InstallLaunchResult.Error -> UpdateUiState.Error(result.message)
                    }
                }) { Text("Instaluj") }
                is UpdateUiState.Error, UpdateUiState.Current, is UpdateUiState.Info -> Button(onClick = { check() }) {
                    Text("Sprawdź ponownie")
                }
                else -> Unit
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Później") } },
    )
}

@Composable
private fun ReleaseDetails(release: ReleaseInfo) {
    Text("Dostępna wersja: ${release.version}")
    Spacer(Modifier.height(6.dp))
    Text("Oficjalny podpisany plik: ${release.apkName}")
    Spacer(Modifier.height(6.dp))
    Text("SHA-256: ${release.apkSha256}")
    Spacer(Modifier.height(8.dp))
    Text(
        release.notes,
        modifier = Modifier.height(220.dp).verticalScroll(rememberScrollState()),
    )
}
