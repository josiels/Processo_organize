package com.josiel.organizeprocesso.ui.configuracoes

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.josiel.organizeprocesso.ui.components.AppToggle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracoesScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: ConfiguracoesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ConfiguracoesViewModel(application) }
        }
    )
    val estado by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
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
            modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConfiguracaoToggleRow(
                titulo = "Notificar quando um processo avança de fase",
                checked = estado.notificarAvancoFase,
                onCheckedChange = viewModel::atualizarNotificarAvancoFase
            )
            ConfiguracaoToggleRow(
                titulo = "Notificar sobre prazos se aproximando",
                checked = estado.notificarPrazo,
                onCheckedChange = viewModel::atualizarNotificarPrazo
            )
            ConfiguracaoToggleRow(
                titulo = "Notificar quando um processo fica parado demais",
                checked = estado.notificarTempoParado,
                onCheckedChange = viewModel::atualizarNotificarTempoParado
            )
            if (estado.erro != null) {
                Text(estado.erro!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ConfiguracaoToggleRow(titulo: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        AppToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}
