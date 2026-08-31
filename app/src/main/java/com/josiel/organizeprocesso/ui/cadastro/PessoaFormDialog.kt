package com.josiel.organizeprocesso.ui.cadastro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.josiel.organizeprocesso.data.local.PessoaEntity
import com.josiel.organizeprocesso.ui.components.AppToggle

/** Formulário de criação/edição de Pessoa (DESIGN.md, seção 7). */
@Composable
fun PessoaFormDialog(
    pessoaInicial: PessoaEntity?,
    onDismiss: () -> Unit,
    onSalvar: (nome: String, cargoSetor: String?, ativo: Boolean) -> Unit,
    onExcluir: (() -> Unit)?
) {
    var nome by remember { mutableStateOf(pessoaInicial?.nome.orEmpty()) }
    var cargoSetor by remember { mutableStateOf(pessoaInicial?.cargoSetor.orEmpty()) }
    var ativo by remember { mutableStateOf(pessoaInicial?.ativo ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pessoaInicial == null) "Nova pessoa" else "Editar pessoa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cargoSetor,
                    onValueChange = { cargoSetor = it },
                    label = { Text("Cargo/setor (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ativo")
                    AppToggle(checked = ativo, onCheckedChange = { ativo = it })
                }
                if (onExcluir != null) {
                    TextButton(onClick = onExcluir) {
                        Text("Excluir pessoa", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nome.isNotBlank(),
                onClick = { onSalvar(nome.trim(), cargoSetor.trim().ifBlank { null }, ativo) }
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
