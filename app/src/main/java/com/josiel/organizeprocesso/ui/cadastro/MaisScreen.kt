package com.josiel.organizeprocesso.ui.cadastro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.domain.model.Papel
import com.josiel.organizeprocesso.ui.components.IconCircle
import com.josiel.organizeprocesso.ui.components.NavigationChevron
import com.josiel.organizeprocesso.ui.theme.Indigo600
import com.josiel.organizeprocesso.ui.theme.IndigoPastel

private data class OpcaoMais(
    val titulo: String,
    val icone: ImageVector,
    val habilitado: Boolean,
    val onClick: () -> Unit
)

/** Aba "Mais": cadastros de Fases/Pessoas, status de sincronização e configurações (DESIGN.md, seção 7). */
@Composable
fun MaisScreen(
    onCadastroFasesClick: () -> Unit,
    onCadastroTiposProcessoClick: () -> Unit,
    onSairClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Coletado (e não lido como valor solto): num cold start com sessão
    // persistida o perfil chega depois da primeira composição.
    val perfil by SupabaseSessionManager.perfilAtual.collectAsState()
    val ehAdmin = perfil?.papel == Papel.ADMIN
    // Cards admin-only são ESCONDIDOS, não desabilitados: `OpcaoMaisCard`
    // rotula tudo que está desabilitado como "Em breve", o que sugeriria
    // (falsamente) que o cadastro ainda não existe.
    val opcoes = buildList {
        if (ehAdmin) {
            add(OpcaoMais("Cadastro de Fases", Icons.Filled.DateRange, habilitado = true, onClick = onCadastroFasesClick))
            add(OpcaoMais("Cadastro de Tipos de Processo", Icons.AutoMirrored.Filled.List, habilitado = true, onClick = onCadastroTiposProcessoClick))
        }
        add(OpcaoMais("Status de sincronização", Icons.Filled.Refresh, habilitado = false) {})
        add(OpcaoMais("Configurações", Icons.Filled.Settings, habilitado = false) {})
        add(OpcaoMais("Sair", Icons.AutoMirrored.Filled.ExitToApp, habilitado = true, onClick = onSairClick))
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Mais",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        items(opcoes) { opcao -> OpcaoMaisCard(opcao) }
    }
}

@Composable
private fun OpcaoMaisCard(opcao: OpcaoMais) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = opcao.habilitado, onClick = opcao.onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconCircle(backgroundColor = IndigoPastel) {
                Icon(opcao.icone, contentDescription = null, tint = Indigo600)
            }
            Text(
                opcao.titulo,
                style = MaterialTheme.typography.titleMedium,
                color = if (opcao.habilitado) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f)
            )
            if (opcao.habilitado) {
                NavigationChevron()
            } else {
                Text(
                    "Em breve",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
