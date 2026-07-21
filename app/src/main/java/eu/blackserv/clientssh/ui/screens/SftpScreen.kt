package eu.blackserv.clientssh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.model.HostProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreen(profile: HostProfile, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SFTP • ${profile.name}") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Wstecz") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Text("Menedżer SFTP")
            Text("Lista, wysyłanie, pobieranie, zmiana nazwy i usuwanie plików będą korzystać ze wspólnej sesji SSH.")
        }
    }
}
