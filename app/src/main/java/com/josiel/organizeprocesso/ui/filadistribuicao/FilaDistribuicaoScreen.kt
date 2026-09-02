package com.josiel.organizeprocesso.ui.filadistribuicao

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.josiel.organizeprocesso.domain.model.FilaDistribuicaoItem
import com.josiel.organizeprocesso.ui.components.NavigationChevron
import com.josiel.organizeprocesso.ui.components.SideBarCard
import com.josiel.organizeprocesso.ui.components.StatusPill
import com.josiel.organizeprocesso.ui.theme.Indigo600
import com.josiel.organizeprocesso.ui.theme.IndigoPastel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Fila de Distribuição (spec do Plano 2C, seção 4) — transparência de carga de trabalho, visível a todos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilaDistribuicaoScreen(
    onPessoaClick: (perfilId: String, nome: String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: FilaDistribuicaoViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FilaDistribuicaoViewModel(application) }
        }
    )
    val estado by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Fila de Distribuição") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        when {
            estado.carregando -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            estado.erro != null -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(estado.erro!!, color = MaterialTheme.colorScheme.error)
            }
            estado.itens.isEmpty() -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nenhuma pessoa ativa na organização ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(estado.itens, key = { it.perfilId }) { item ->
                    FilaDistribuicaoCard(item = item, onClick = { onPessoaClick(item.perfilId, item.nome) })
                }
            }
        }
    }
}

@Composable
private fun FilaDistribuicaoCard(item: FilaDistribuicaoItem, onClick: () -> Unit) {
    SideBarCard(
        barColor = Indigo600,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.nome, style = MaterialTheme.typography.titleMedium)
            Text(
                if (item.ultimoRecebimentoEm == null) {
                    "Nunca recebeu um processo"
                } else {
                    "Último recebimento: ${formatoData.format(item.ultimoRecebimentoEm.atZone(ZoneId.systemDefault()))}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusPill(
                text = "${item.totalDesignacoes} designações",
                containerColor = IndigoPastel,
                contentColor = Indigo600,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        NavigationChevron()
    }
}
