package com.josiel.organizeprocesso.ui.processos

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.josiel.organizeprocesso.data.local.TipoProcessoEntity
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import com.josiel.organizeprocesso.domain.model.rotulo
import com.josiel.organizeprocesso.domain.usecase.RegrasBloqueioCampos
import com.josiel.organizeprocesso.ui.components.DateField
import com.josiel.organizeprocesso.ui.components.DropdownField
import com.josiel.organizeprocesso.ui.components.PillButton

/** Criação/edição de Processo + Itens (REQUISITOS.md, seção 4; ROADMAP.md, passo 6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessoFormScreen(
    processoId: String?,
    onBackClick: () -> Unit,
    onSalvo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: ProcessoFormViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ProcessoFormViewModel(application, processoId) }
        }
    )

    val estado by viewModel.uiState.collectAsState()
    val fases by viewModel.fases.collectAsState()
    val tiposProcesso by viewModel.tiposProcesso.collectAsState()

    var itemEmEdicao by remember { mutableStateOf<ItemEntity?>(null) }
    var mostrarFormularioItem by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.ehEdicao) "Editar processo" else "Novo processo") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(
                        enabled = estado.valido && !estado.somenteLeitura,
                        onClick = { viewModel.salvar(onSalvo) }
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Salvar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        if (estado.carregando) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (estado.somenteLeitura) {
                Text(
                    "Somente leitura — você não pode editar este processo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            estado.erro?.let { erro ->
                Text(erro, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            OutlinedTextField(
                value = estado.numero,
                onValueChange = viewModel::atualizarNumero,
                label = { Text("Número do processo") },
                singleLine = true,
                enabled = !estado.somenteLeitura,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = estado.objeto,
                onValueChange = viewModel::atualizarObjeto,
                label = { Text("Objeto") },
                enabled = !estado.somenteLeitura,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = estado.descricao,
                onValueChange = viewModel::atualizarDescricao,
                label = { Text("Descrição") },
                enabled = !estado.somenteLeitura,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = estado.orgaoDemandante,
                onValueChange = viewModel::atualizarOrgaoDemandante,
                label = { Text("Órgão demandante") },
                singleLine = true,
                enabled = !estado.somenteLeitura,
                modifier = Modifier.fillMaxWidth()
            )

            DateField(
                label = "Data de abertura",
                data = estado.dataAbertura,
                onDataSelecionada = viewModel::atualizarDataAbertura,
                modifier = Modifier.fillMaxWidth(),
                enabled = !estado.somenteLeitura
            )

            DropdownField(
                label = "Fase inicial",
                opcoes = fases,
                selecionado = fases.find { it.id == estado.faseSelecionadaId },
                rotulo = { it.nome },
                onSelecionado = { viewModel.atualizarFase(it.id) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !estado.somenteLeitura
            )
            if (fases.isEmpty()) {
                Text(
                    "Cadastre ao menos uma fase em Mais > Cadastro de Fases antes de criar um processo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            DropdownField(
                label = "Tipo de processo",
                opcoes = tiposProcesso,
                selecionado = tiposProcesso.find { it.id == estado.tipoProcessoId },
                rotulo = { it.nome },
                onSelecionado = { viewModel.atualizarTipo(it.id) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !estado.somenteLeitura
            )
            if (tiposProcesso.isEmpty()) {
                Text(
                    "Cadastre ao menos um tipo de processo em Mais > Cadastro de Tipos de Processo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            DropdownField(
                label = "Status geral",
                opcoes = StatusGeralProcesso.entries,
                selecionado = estado.statusGeral,
                rotulo = { it.rotulo() },
                onSelecionado = viewModel::atualizarStatusGeral,
                modifier = Modifier.fillMaxWidth(),
                enabled = !estado.somenteLeitura
            )

            Text("Itens", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))

            estado.itens.forEach { item ->
                ItemRow(
                    item = item,
                    onClick = {
                        if (estado.somenteLeitura) {
                        } else {
                            itemEmEdicao = item
                            mostrarFormularioItem = true
                        }
                    }
                )
            }

            if (!estado.somenteLeitura) {
                PillButton(
                    text = "+ Adicionar item",
                    onClick = {
                        itemEmEdicao = null
                        mostrarFormularioItem = true
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.wrapContentSize()
                )
            }

            Text(
                "Valor estimado total: R$ %.2f".format(estado.valorEstimadoTotal),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    if (mostrarFormularioItem) {
        val faseSelecionadaNome = fases.find { it.id == estado.faseSelecionadaId }?.nome
        val valorPesquisaLiberado = RegrasBloqueioCampos.valorPesquisaLiberado(
            estado.fasesPercorridasNomes + listOfNotNull(faseSelecionadaNome)
        )
        ItemFormDialog(
            itemInicial = itemEmEdicao,
            valorPesquisaLiberado = valorPesquisaLiberado,
            onDismiss = { mostrarFormularioItem = false },
            onSalvar = { descricao, quantidade, unidade, valorEstimadoUnit, valorPesquisaUnit ->
                viewModel.salvarItem(itemEmEdicao, descricao, quantidade, unidade, valorEstimadoUnit, valorPesquisaUnit)
                mostrarFormularioItem = false
            },
            onExcluir = itemEmEdicao?.let { item ->
                {
                    viewModel.removerItem(item)
                    mostrarFormularioItem = false
                }
            }
        )
    }
}

@Composable
private fun ItemRow(item: ItemEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(item.descricao, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${item.quantidade} ${item.unidade} x R$ %.2f".format(item.valorEstimadoUnit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onClick) { Text("Editar") }
    }
}
