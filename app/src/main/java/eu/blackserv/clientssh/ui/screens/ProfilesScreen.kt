package eu.blackserv.clientssh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    profiles: List<HostProfile>,
    onAdd: () -> Unit,
    onEdit: (HostProfile) -> Unit,
    onDelete: (HostProfile) -> Unit,
    onConnect: (HostProfile) -> Unit,
    onOpenSftp: (HostProfile) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Client SSH") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Dodaj profil")
            }
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Computer, contentDescription = null)
                    Text("Brak profili", fontWeight = FontWeight.Bold)
                    Text("Dodaj VPS lub tuner Enigma2 przez SSH albo Telnet.")
                    Button(onClick = onAdd, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Dodaj pierwszy profil")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(profile.name, fontWeight = FontWeight.Bold)
                                    Text("${profile.username.ifBlank { "—" }}@${profile.host}:${profile.port}")
                                }
                                AssistChip(onClick = {}, label = { Text(profile.protocol.label) })
                                IconButton(onClick = { onEdit(profile) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edytuj")
                                }
                                IconButton(onClick = { onDelete(profile) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Usuń")
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(onClick = { onConnect(profile) }, modifier = Modifier.weight(1f)) {
                                    Text("Terminal")
                                }
                                if (profile.protocol == ConnectionProtocol.SSH) {
                                    Button(onClick = { onOpenSftp(profile) }) {
                                        Icon(Icons.Default.Folder, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("SFTP")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
