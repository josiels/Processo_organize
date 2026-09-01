package com.josiel.organizeprocesso.ui.cadastro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.josiel.organizeprocesso.data.local.TipoProcessoEntity

@Composable
fun TipoProcessoFormDialog(
    tipoProcessoInicial: TipoProcessoEntity?,
    onDismiss: () -> Unit,
    onSalvar: (nome: String, diasAtencao: Int, diasCritico: Int) -> Unit,
    onExcluir: (() -> Unit)?
) {
    var nome by remember { mutableStateOf(tipoProcessoInicial?.nome.orEmpty()) }
    var diasAtencao by remember { mutableStateOf((tipoProcessoInicial?.diasAlertaAtencao ?: 5).toString()) }
    var diasCritico by remember { mutableStateOf((tipoProcessoInicial?.diasAlertaCritico ?: 10).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tipoProcessoInicial == null) "Novo tipo de processo" else "Editar tipo de processo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = diasAtencao,
                        onValueChange = { diasAtencao = it.filter(Char::isDigit) },
                        label = { Text("Dias p/ atenção") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = diasCritico,
                        onValueChange = { diasCritico = it.filter(Char::isDigit) },
                        label = { Text("Dias p/ crítico") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (onExcluir != null) {
                    TextButton(onClick = onExcluir) {
                        Text("Excluir tipo de processo", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nome.isNotBlank(),
                onClick = { onSalvar(nome.trim(), diasAtencao.toIntOrNull() ?: 5, diasCritico.toIntOrNull() ?: 10) }
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
