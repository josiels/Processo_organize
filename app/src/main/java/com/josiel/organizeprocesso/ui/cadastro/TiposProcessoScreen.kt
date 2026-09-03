package com.josiel.organizeprocesso.ui.cadastro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.josiel.organizeprocesso.data.local.TipoProcessoEntity
import com.josiel.organizeprocesso.ui.components.NavigationChevron
import com.josiel.organizeprocesso.ui.components.PillButton
import com.josiel.organizeprocesso.ui.components.SideBarCard
import com.josiel.organizeprocesso.ui.components.StatusPill
import com.josiel.organizeprocesso.ui.theme.AmareloAtencao
import com.josiel.organizeprocesso.ui.theme.AmareloAtencaoPastel
import com.josiel.organizeprocesso.ui.theme.Indigo600
import com.josiel.organizeprocesso.ui.theme.VermelhoCritico
import com.josiel.organizeprocesso.ui.theme.VermelhoCriticoPastel

/** Cadastro de Tipo de Processo (spec do Plano 2B, seção 3.1) — catálogo reutilizável, admin-only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiposProcessoScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TipoProcessoCadastroViewModel = viewModel()
) {
    val tiposProcesso by viewModel.tiposProcesso.collectAsState()
    val erro by viewModel.erro.collectAsState()
    val fases by viewModel.fases.collectAsState()
    val processosEmAndamentoPorTipo by viewModel.processosEmAndamentoPorTipo.collectAsState()
    var tipoEmEdicao by remember { mutableStateOf<TipoProcessoEntity?>(null) }
    var mostrarFormulario by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Tipos de processo") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (tiposProcesso.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhum tipo de processo cadastrado ainda.\nToque em \"+ Novo tipo\" para começar.",
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
                    items(tiposProcesso, key = { it.id }) { tipo ->
                        TipoProcessoCard(
                            tipoProcesso = tipo,
                            onClick = {
                                tipoEmEdicao = tipo
                                mostrarFormulario = true
                            }
                        )
                    }
                }
            }

            erro?.let { mensagem ->
                Text(
                    mensagem,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

            PillButton(
                text = "+ Novo tipo",
                onClick = {
                    tipoEmEdicao = null
                    mostrarFormulario = true
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
    }

    if (mostrarFormulario) {
        TipoProcessoFormDialog(
            tipoProcessoInicial = tipoEmEdicao,
            fases = fases,
            processosEmAndamentoDoTipo = processosEmAndamentoPorTipo[tipoEmEdicao?.id] ?: 0,
            onDismiss = { mostrarFormulario = false },
            onSalvar = { nome, diasAtencao, diasCritico, simples, fasePadraoId ->
                viewModel.salvar(tipoEmEdicao?.id, nome, diasAtencao, diasCritico, simples, fasePadraoId)
                mostrarFormulario = false
            },
            onExcluir = tipoEmEdicao?.let { tipo ->
                {
                    viewModel.excluir(tipo)
                    mostrarFormulario = false
                }
            }
        )
    }
}

@Composable
private fun TipoProcessoCard(tipoProcesso: TipoProcessoEntity, onClick: () -> Unit) {
    SideBarCard(
        barColor = Indigo600,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tipoProcesso.nome, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill(
                    text = "${tipoProcesso.diasAlertaAtencao}d atenção",
                    containerColor = AmareloAtencaoPastel,
                    contentColor = AmareloAtencao
                )
                StatusPill(
                    text = "${tipoProcesso.diasAlertaCritico}d crítico",
                    containerColor = VermelhoCriticoPastel,
                    contentColor = VermelhoCritico
                )
            }
        }
        NavigationChevron()
    }
}
