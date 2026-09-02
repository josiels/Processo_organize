package com.josiel.organizeprocesso.ui.equipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.josiel.organizeprocesso.domain.model.Papel

/** Formulário de criação de conta (spec do Plano 2C, seção 3) — tela cheia, nunca diálogo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CriarContaScreen(
    onBackClick: () -> Unit,
    onCriado: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CriarContaViewModel = viewModel()
) {
    val estado by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Nova conta") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(
                        enabled = estado.valido && !estado.salvando,
                        onClick = { viewModel.criar(onCriado) }
                    ) {
                        if (estado.salvando) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = "Criar")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = estado.nome,
                onValueChange = viewModel::atualizarNome,
                label = { Text("Nome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = estado.email,
                onValueChange = viewModel::atualizarEmail,
                label = { Text("E-mail") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = estado.senha,
                onValueChange = viewModel::atualizarSenha,
                label = { Text("Senha inicial") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = { Text("Mínimo de 6 caracteres") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Papel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(Papel.ADMIN to "Admin", Papel.USUARIO to "Usuário").forEach { (papel, rotulo) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = estado.papel == papel, onClick = { viewModel.atualizarPapel(papel) }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = estado.papel == papel, onClick = { viewModel.atualizarPapel(papel) })
                    Text(rotulo)
                }
            }

            if (estado.erro != null) {
                Text(estado.erro!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
