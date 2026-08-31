package com.josiel.organizeprocesso.ui.processos

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.josiel.organizeprocesso.data.local.ItemEntity
import com.josiel.organizeprocesso.domain.model.rotulo
import com.josiel.organizeprocesso.ui.components.PillButton
import com.josiel.organizeprocesso.ui.components.StatusPill
import com.josiel.organizeprocesso.ui.theme.Indigo600
import com.josiel.organizeprocesso.ui.theme.IndigoPastel
import com.josiel.organizeprocesso.ui.theme.OrangePastel
import com.josiel.organizeprocesso.ui.theme.Orange800
import java.time.format.DateTimeFormatter

private val formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val abas = listOf("Dados gerais", "Itens", "Timeline", "Anexos")

/** Detalhe do Processo em abas (DESIGN.md, seção 4; ROADMAP.md, passo 8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessoDetalheScreen(
    processoId: String,
    onAvancarFaseClick: () -> Unit,
    onEditarClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: ProcessoDetalheViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ProcessoDetalheViewModel(application, processoId) }
        }
    )
    val estado by viewModel.uiState.collectAsState()
    var abaSelecionada by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(estado.processo?.numero ?: "Processo") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = onEditarClick) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        if (estado.carregando || estado.processo == null) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            SecondaryTabRow(selectedTabIndex = abaSelecionada, containerColor = MaterialTheme.colorScheme.background) {
                abas.forEachIndexed { indice, titulo ->
                    Tab(
                        selected = abaSelecionada == indice,
                        onClick = { abaSelecionada = indice },
                        text = { Text(titulo) }
                    )
                }
            }

            when (abaSelecionada) {
                0 -> AbaDadosGerais(estado, onAvancarFaseClick)
                1 -> AbaItens(estado.itens)
                2 -> AbaTimeline(estado.historico)
                else -> AbaAnexos()
            }
        }
    }
}

@Composable
private fun AbaDadosGerais(estado: ProcessoDetalheUiState, onAvancarFaseClick: () -> Unit) {
    val processo = estado.processo ?: return
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(text = estado.faseAtualNome, containerColor = IndigoPastel, contentColor = Indigo600)
            StatusPill(
                text = processo.statusGeral.rotulo(),
                containerColor = OrangePastel,
                contentColor = Orange800
            )
        }
        CampoDado("Objeto", processo.objeto)
        if (processo.descricao.isNotBlank()) CampoDado("Descrição", processo.descricao)
        CampoDado("Órgão demandante", processo.orgaoDemandante)
        if (processo.tipo.isNotBlank()) CampoDado("Tipo", processo.tipo)
        CampoDado("Valor estimado total", "R$ %.2f".format(processo.valorEstimadoTotal))
        CampoDado("Data de abertura", processo.dataAbertura.format(formatoData))

        PillButton(
            text = "Avançar fase",
            onClick = onAvancarFaseClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

@Composable
private fun CampoDado(rotulo: String, valor: String) {
    Column {
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AbaItens(itens: List<ItemEntity>) {
    if (itens.isEmpty()) {
        EstadoVazio("Nenhum item cadastrado para este processo.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(itens, key = { it.id }) { item ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(item.descricao, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${item.quantidade} ${item.unidade} x R$ %.2f = R$ %.2f".format(
                        item.valorEstimadoUnit,
                        item.quantidade * item.valorEstimadoUnit
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.valorPesquisaUnit != null) {
                    Text(
                        "Valor de pesquisa: R$ %.2f".format(item.valorPesquisaUnit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun AbaTimeline(historico: List<HistoricoItemUi>) {
    if (historico.isEmpty()) {
        EstadoVazio("Nenhuma fase registrada ainda.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(historico, key = { it.historico.id }) { entrada ->
            val ehUltimo = entrada == historico.last()
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                color = if (entrada.historico.motivoRetorno != null) Orange800 else Indigo600,
                                shape = CircleShape
                            )
                    )
                    if (!ehUltimo) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outline)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp, bottom = 20.dp)
                        .fillMaxWidth()
                ) {
                    Text(entrada.faseNome, style = MaterialTheme.typography.titleMedium)
                    val periodo = if (entrada.historico.dataSaida != null) {
                        "${entrada.historico.dataEntrada.format(formatoData)} — ${entrada.historico.dataSaida.format(formatoData)}"
                    } else {
                        "${entrada.historico.dataEntrada.format(formatoData)} — atual"
                    }
                    Text(periodo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (entrada.responsavelNome != null) {
                        Text(
                            "Responsável: ${entrada.responsavelNome}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (entrada.historico.motivoRetorno != null) {
                        Text(
                            "Retrocesso: ${entrada.historico.motivoRetorno}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Orange800
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AbaAnexos() {
    EstadoVazio("Nenhum anexo ainda. Anexos e links são adicionados a partir da tela \"Avançar fase\".")
}

@Composable
private fun EstadoVazio(texto: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
