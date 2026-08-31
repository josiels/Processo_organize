package com.josiel.organizeprocesso.ui.cadastro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.josiel.organizeprocesso.data.local.PessoaEntity
import com.josiel.organizeprocesso.ui.components.AppToggle
import com.josiel.organizeprocesso.ui.components.NavigationChevron
import com.josiel.organizeprocesso.ui.components.PillButton
import com.josiel.organizeprocesso.ui.components.SideBarCard
import com.josiel.organizeprocesso.ui.theme.Contorno
import com.josiel.organizeprocesso.ui.theme.VerdeOk

/** Cadastro de Pessoas (REQUISITOS.md, seção 3; DESIGN.md, seção 7) — responsáveis reutilizáveis por fase. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PessoasScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PessoaCadastroViewModel = viewModel()
) {
    val pessoas by viewModel.pessoas.collectAsState()
    var pessoaEmEdicao by remember { mutableStateOf<PessoaEntity?>(null) }
    var mostrarFormulario by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Pessoas") },
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
            if (pessoas.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhuma pessoa cadastrada ainda.\nToque em \"+ Nova pessoa\" para começar.",
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
                    items(pessoas, key = { it.id }) { pessoa ->
                        PessoaCard(
                            pessoa = pessoa,
                            onClick = {
                                pessoaEmEdicao = pessoa
                                mostrarFormulario = true
                            },
                            onAtivoChange = { ativo ->
                                viewModel.salvar(pessoa.id, pessoa.nome, pessoa.cargoSetor, ativo)
                            }
                        )
                    }
                }
            }

            PillButton(
                text = "+ Nova pessoa",
                onClick = {
                    pessoaEmEdicao = null
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
        PessoaFormDialog(
            pessoaInicial = pessoaEmEdicao,
            onDismiss = { mostrarFormulario = false },
            onSalvar = { nome, cargoSetor, ativo ->
                viewModel.salvar(pessoaEmEdicao?.id, nome, cargoSetor, ativo)
                mostrarFormulario = false
            },
            onExcluir = pessoaEmEdicao?.let { pessoa ->
                {
                    viewModel.excluir(pessoa)
                    mostrarFormulario = false
                }
            }
        )
    }
}

@Composable
private fun PessoaCard(
    pessoa: PessoaEntity,
    onClick: () -> Unit,
    onAtivoChange: (Boolean) -> Unit
) {
    SideBarCard(
        barColor = if (pessoa.ativo) VerdeOk else Contorno,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(pessoa.nome, style = MaterialTheme.typography.titleMedium)
            if (!pessoa.cargoSetor.isNullOrBlank()) {
                Text(
                    pessoa.cargoSetor,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        AppToggle(checked = pessoa.ativo, onCheckedChange = onAtivoChange)
        NavigationChevron()
    }
}
