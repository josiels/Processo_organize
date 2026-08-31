package com.josiel.organizeprocesso.ui.processos

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
import com.josiel.organizeprocesso.data.local.ItemEntity

/**
 * Formulário de criação/edição de Item do processo (REQUISITOS.md, seção 4).
 * `valorPesquisaUnit` fica visível mas desabilitado (trava visual, não só
 * sugestão — REQUISITOS.md, seção 4) enquanto [valorPesquisaLiberado] for
 * falso; ver `domain/usecase/RegrasBloqueioCampos.kt`.
 */
@Composable
fun ItemFormDialog(
    itemInicial: ItemEntity?,
    valorPesquisaLiberado: Boolean,
    onDismiss: () -> Unit,
    onSalvar: (descricao: String, quantidade: Double, unidade: String, valorEstimadoUnit: Double, valorPesquisaUnit: Double?) -> Unit,
    onExcluir: (() -> Unit)?
) {
    var descricao by remember { mutableStateOf(itemInicial?.descricao.orEmpty()) }
    var quantidade by remember { mutableStateOf((itemInicial?.quantidade ?: 1.0).toString()) }
    var unidade by remember { mutableStateOf(itemInicial?.unidade.orEmpty()) }
    var valorEstimadoUnit by remember { mutableStateOf((itemInicial?.valorEstimadoUnit ?: 0.0).toString()) }
    var valorPesquisaUnit by remember { mutableStateOf(itemInicial?.valorPesquisaUnit?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (itemInicial == null) "Novo item" else "Editar item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = descricao,
                    onValueChange = { descricao = it },
                    label = { Text("Descrição") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantidade,
                        onValueChange = { quantidade = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Quantidade") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unidade,
                        onValueChange = { unidade = it },
                        label = { Text("Unidade") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = valorEstimadoUnit,
                    onValueChange = { valorEstimadoUnit = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Valor estimado unitário") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = valorPesquisaUnit,
                    onValueChange = { valorPesquisaUnit = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Valor de pesquisa unitário") },
                    enabled = valorPesquisaLiberado,
                    supportingText = {
                        if (!valorPesquisaLiberado) {
                            Text("Disponível a partir da fase de Pesquisa de Preços")
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (onExcluir != null) {
                    TextButton(onClick = onExcluir) {
                        Text("Excluir item", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = descricao.isNotBlank() && unidade.isNotBlank(),
                onClick = {
                    onSalvar(
                        descricao.trim(),
                        quantidade.toDoubleOrNull() ?: 0.0,
                        unidade.trim(),
                        valorEstimadoUnit.toDoubleOrNull() ?: 0.0,
                        if (valorPesquisaLiberado) valorPesquisaUnit.toDoubleOrNull() else itemInicial?.valorPesquisaUnit
                    )
                }
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
