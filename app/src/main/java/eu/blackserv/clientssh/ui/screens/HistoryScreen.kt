package eu.blackserv.clientssh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.ui.theme.LocalPremiumSkin
import eu.blackserv.clientssh.ui.theme.PremiumPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val tokens = LocalPremiumSkin.current
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Historia", fontWeight = FontWeight.Bold)
                        Text(
                            "Sesje, zdarzenia i szybkie ponowne połączenia",
                            color = tokens.accentBright,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tokens.night.copy(alpha = 0.88f),
                    titleContentColor = tokens.text,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            PremiumPanel(
                modifier = Modifier.fillMaxWidth(),
                strong = true,
                contentPadding = PaddingValues(26.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = tokens.accentBright,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        "Historia jest gotowa na dane",
                        color = tokens.text,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Udane i nieudane połączenia będą prezentowane tutaj w bezpiecznej osi czasu — bez haseł, passphrase i kluczy prywatnych.",
                        color = tokens.muted,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
