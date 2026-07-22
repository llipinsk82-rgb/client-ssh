package eu.blackserv.clientssh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.model.FavoriteCommand

@Composable
fun FavoritesDialog(
    favorites: List<FavoriteCommand>,
    onDismiss: () -> Unit,
    onInsert: (FavoriteCommand) -> Unit,
    onRun: (FavoriteCommand) -> Unit,
    onSave: (FavoriteCommand) -> Unit,
    onDelete: (FavoriteCommand) -> Unit,
    onMoveUp: (FavoriteCommand) -> Unit,
    onMoveDown: (FavoriteCommand) -> Unit,
) {
    var edited by remember { mutableStateOf<FavoriteCommand?>(null) }
    var creating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ulubione komendy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (favorites.isEmpty()) Text("Nie masz jeszcze ulubionych komend.")
                favorites.forEachIndexed { index, favorite ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(favorite.name)
                            Text(favorite.command)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { onInsert(favorite) }) { Text("Wstaw") }
                                TextButton(onClick = { onRun(favorite) }) { Text("Uruchom") }
                                Spacer(Modifier.weight(1f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    enabled = index > 0,
                                    onClick = { onMoveUp(favorite) },
                                ) { Text("↑ Wyżej") }
                                TextButton(
                                    enabled = index < favorites.lastIndex,
                                    onClick = { onMoveDown(favorite) },
                                ) { Text("↓ Niżej") }
                                Spacer(Modifier.weight(1f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { edited = favorite }) { Text("Edytuj") }
                                TextButton(onClick = { onDelete(favorite) }) { Text("Usuń") }
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Dodaj komendę")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )

    val target = edited ?: if (creating) FavoriteCommand(name = "", command = "") else null
    if (target != null) {
        FavoriteEditorDialog(
            favorite = target,
            onDismiss = {
                edited = null
                creating = false
            },
            onSave = {
                onSave(it)
                edited = null
                creating = false
            },
        )
    }
}

@Composable
private fun FavoriteEditorDialog(
    favorite: FavoriteCommand,
    onDismiss: () -> Unit,
    onSave: (FavoriteCommand) -> Unit,
) {
    var name by remember(favorite.id) { mutableStateOf(favorite.name) }
    var command by remember(favorite.id) { mutableStateOf(favorite.command) }
    var runImmediately by remember(favorite.id) { mutableStateOf(favorite.runImmediately) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (favorite.name.isBlank()) "Nowa komenda" else "Edytuj komendę") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nazwa") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(command, { command = it }, label = { Text("Komenda") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = runImmediately, onCheckedChange = { runImmediately = it })
                    Text("Uruchamiaj od razu")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && command.isNotBlank(),
                onClick = {
                    onSave(favorite.copy(name = name.trim(), command = command.trim(), runImmediately = runImmediately))
                },
            ) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
