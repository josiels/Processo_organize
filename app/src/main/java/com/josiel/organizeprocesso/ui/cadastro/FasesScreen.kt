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
import com.josiel.organizeprocesso.data.local.FaseEntity
import com.josiel.organizeprocesso.ui.components.NavigationChevron
import com.josiel.organizeprocesso.ui.components.PillButton
import com.josiel.organizeprocesso.ui.components.SideBarCard
import com.josiel.organizeprocesso.ui.components.StatusPill
import com.josiel.organizeprocesso.ui.theme.AmareloAtencao
import com.josiel.organizeprocesso.ui.theme.AmareloAtencaoPastel
import com.josiel.organizeprocesso.ui.theme.Indigo600
import com.josiel.organizeprocesso.ui.theme.IndigoPastel
import com.josiel.organizeprocesso.ui.theme.VermelhoCritico
import com.josiel.organizeprocesso.ui.theme.VermelhoCriticoPastel

/** Cadastro de Fases (REQUISITOS.md, seção 3; DESIGN.md, seção 7) — catálogo reutilizável. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FasesScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FaseCadastroViewModel = viewModel()
) {
    val fases by viewModel.fases.collectAsState()
    var faseEmEdicao by remember { mutableStateOf<FaseEntity?>(null) }
    var mostrarFormulario by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Fases") },
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
            if (fases.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhuma fase cadastrada ainda.\nToque em \"+ Nova fase\" para começar.",
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
                    items(fases, key = { it.id }) { fase ->
                        FaseCard(
                            fase = fase,
                            onClick = {
                                faseEmEdicao = fase
                                mostrarFormulario = true
                            }
                        )
                    }
                }
            }

            PillButton(
                text = "+ Nova fase",
                onClick = {
                    faseEmEdicao = null
                    mostrarFormulario = true
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }

    if (mostrarFormulario) {
        FaseFormDialog(
            faseInicial = faseEmEdicao,
            onDismiss = { mostrarFormulario = false },
            onSalvar = { nome, ordem, descricao, diasAtencao, diasCritico ->
                viewModel.salvar(
                    id = faseEmEdicao?.id,
                    nome = nome,
                    ordem = ordem,
                    descricao = descricao,
                    diasAlertaAtencao = diasAtencao,
                    diasAlertaCritico = diasCritico,
                    padrao = faseEmEdicao?.padrao ?: false
                )
                mostrarFormulario = false
            },
            onExcluir = faseEmEdicao?.let { fase ->
                {
                    viewModel.excluir(fase)
                    mostrarFormulario = false
                }
            }
        )
    }
}

@Composable
private fun FaseCard(fase: FaseEntity, onClick: () -> Unit) {
    SideBarCard(
        barColor = Indigo600,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(fase.nome, style = MaterialTheme.typography.titleMedium)
            if (!fase.descricao.isNullOrBlank()) {
                Text(
                    fase.descricao,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill(text = "Ordem ${fase.ordem}", containerColor = IndigoPastel, contentColor = Indigo600)
                StatusPill(
                    text = "${fase.diasAlertaAtencao}d atenção",
                    containerColor = AmareloAtencaoPastel,
                    contentColor = AmareloAtencao
                )
                StatusPill(
                    text = "${fase.diasAlertaCritico}d crítico",
                    containerColor = VermelhoCriticoPastel,
                    contentColor = VermelhoCritico
                )
            }
        }
        NavigationChevron()
    }
}
