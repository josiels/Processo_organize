package com.josiel.organizeprocesso.ui.inicio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.josiel.organizeprocesso.domain.model.ProcessoResumoDashboard
import com.josiel.organizeprocesso.domain.usecase.ProgressoGeral
import com.josiel.organizeprocesso.ui.components.PillButton

/** Dashboard Início (spec do Dashboard Início). */
@Composable
fun InicioScreen(
    onProcessoClick: (processoId: String) -> Unit,
    onVerTodosClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InicioViewModel = viewModel()
) {
    val estado by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(estado.saudacao, style = MaterialTheme.typography.headlineSmall)

        CardProgressoGeral(estado.progresso)

        Text(
            "${estado.processosAtualizadosHoje} processo(s) atualizado(s) hoje",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyRow(
            contentPadding = PaddingValues(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CardDestaque(
                    titulo = "Processos críticos",
                    itens = estado.processosCriticos,
                    vazio = "Nenhum processo crítico no momento.",
                    onProcessoClick = onProcessoClick
                )
            }
            item {
                CardDestaque(
                    titulo = "Próximos prazos",
                    itens = estado.proximosPrazos,
                    vazio = "Nenhum prazo próximo cadastrado.",
                    onProcessoClick = onProcessoClick
                )
            }
        }

        PillButton(
            text = "Ver todos os processos",
            onClick = onVerTodosClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CardProgressoGeral(progresso: ProgressoGeral) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Progresso geral", style = MaterialTheme.typography.titleMedium)
            Text(
                "${progresso.emDia} de ${progresso.total} processos em dia",
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { progresso.percentual / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text("${progresso.percentual}%", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CardDestaque(
    titulo: String,
    itens: List<ProcessoResumoDashboard>,
    vazio: String,
    onProcessoClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.width(280.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            if (itens.isEmpty()) {
                Text(
                    vazio,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                itens.forEach { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProcessoClick(item.id) }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            item.numero,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            item.objeto,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
