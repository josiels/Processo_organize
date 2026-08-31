package com.josiel.organizeprocesso.ui.processos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import com.josiel.organizeprocesso.ui.components.FasePill
import com.josiel.organizeprocesso.ui.components.NavigationChevron
import com.josiel.organizeprocesso.ui.components.PillButton
import com.josiel.organizeprocesso.ui.components.SideBarCard
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import com.josiel.organizeprocesso.ui.theme.AmareloAtencao
import com.josiel.organizeprocesso.ui.theme.Indigo600
import com.josiel.organizeprocesso.ui.theme.VerdeOk
import com.josiel.organizeprocesso.ui.theme.VermelhoCritico

private enum class FiltroProcesso(val rotulo: String) {
    TODOS("Todos"),
    EM_ANDAMENTO("Em andamento"),
    CRITICOS("Críticos"),
    CONCLUIDOS("Concluídos")
}

private enum class OrdenacaoProcesso(val rotulo: String) {
    URGENCIA("Por urgência"),
    FASE("Por fase"),
    DATA_ABERTURA("Por data de abertura")
}

/** Lista geral de Processos (REQUISITOS.md, seção 8; DESIGN.md, seção 3; ROADMAP.md, passo 7). */
@Composable
fun ProcessosScreen(
    onProcessoClick: (processoId: String) -> Unit,
    onNovoProcessoClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProcessoListViewModel = viewModel()
) {
    val itens by viewModel.itens.collectAsState()
    var busca by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf(FiltroProcesso.TODOS) }
    var ordenacao by remember { mutableStateOf(OrdenacaoProcesso.URGENCIA) }
    var mostrarMenuOrdenacao by remember { mutableStateOf(false) }

    val itensFiltrados = itens
        .filter { item ->
            busca.isBlank() ||
                item.processo.numero.contains(busca, ignoreCase = true) ||
                item.processo.objeto.contains(busca, ignoreCase = true)
        }
        .filter { item ->
            when (filtro) {
                FiltroProcesso.TODOS -> true
                FiltroProcesso.EM_ANDAMENTO -> item.processo.statusGeral == StatusGeralProcesso.EM_ANDAMENTO
                FiltroProcesso.CRITICOS -> item.statusSemaforo == StatusSemaforo.CRITICO
                FiltroProcesso.CONCLUIDOS -> item.processo.statusGeral == StatusGeralProcesso.CONCLUIDO
            }
        }
        .let { lista ->
            when (ordenacao) {
                OrdenacaoProcesso.URGENCIA -> lista.sortedByDescending { it.diasParado }
                OrdenacaoProcesso.FASE -> lista.sortedBy { it.faseNome }
                OrdenacaoProcesso.DATA_ABERTURA -> lista.sortedByDescending { it.processo.dataAbertura }
            }
        }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                placeholder = { Text("Buscar processo...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { mostrarMenuOrdenacao = true }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Ordenar")
                }
                DropdownMenu(expanded = mostrarMenuOrdenacao, onDismissRequest = { mostrarMenuOrdenacao = false }) {
                    OrdenacaoProcesso.entries.forEach { opcao ->
                        DropdownMenuItem(
                            text = { Text(opcao.rotulo) },
                            onClick = {
                                ordenacao = opcao
                                mostrarMenuOrdenacao = false
                            }
                        )
                    }
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(FiltroProcesso.entries) { opcao ->
                FilterChip(
                    selected = filtro == opcao,
                    onClick = { filtro = opcao },
                    label = { Text(opcao.rotulo) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Indigo600,
                        selectedLabelColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }

        if (itensFiltrados.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (itens.isEmpty()) {
                        "Nenhum processo cadastrado ainda.\nToque em \"+ Novo processo\" para começar."
                    } else {
                        "Nenhum processo encontrado para esse filtro."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(itensFiltrados, key = { it.processo.id }) { item ->
                    ProcessoCard(item = item, onClick = { onProcessoClick(item.processo.id) })
                }
            }
        }

        PillButton(
            text = "+ Novo processo",
            onClick = onNovoProcessoClick,
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
    }
}

@Composable
private fun ProcessoCard(item: ProcessoListItem, onClick: () -> Unit) {
    val corBarra = when (item.statusSemaforo) {
        StatusSemaforo.OK -> VerdeOk
        StatusSemaforo.ATENCAO -> AmareloAtencao
        StatusSemaforo.CRITICO -> VermelhoCritico
    }
    SideBarCard(
        barColor = corBarra,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.processo.numero,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                FasePill(nome = item.faseNome, faseId = item.processo.faseAtualId)
            }
            Text(
                item.processo.objeto,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            if (item.responsavelNome != null) {
                Text(
                    "Responsável: ${item.responsavelNome}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        NavigationChevron()
    }
}
