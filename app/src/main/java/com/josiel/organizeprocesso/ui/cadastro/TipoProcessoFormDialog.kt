package com.josiel.organizeprocesso.ui.cadastro

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.josiel.organizeprocesso.data.local.FaseEntity
import com.josiel.organizeprocesso.data.local.TipoProcessoEntity
import com.josiel.organizeprocesso.ui.components.AppToggle
import com.josiel.organizeprocesso.ui.theme.AmareloAtencao

@Composable
fun TipoProcessoFormDialog(
    tipoProcessoInicial: TipoProcessoEntity?,
    fases: List<FaseEntity>,
    processosEmAndamentoDoTipo: Int = 0,
    onDismiss: () -> Unit,
    onSalvar: (nome: String, diasAtencao: Int, diasCritico: Int, simples: Boolean, fasePadraoId: String?) -> Unit,
    onExcluir: (() -> Unit)?
) {
    var nome by remember { mutableStateOf(tipoProcessoInicial?.nome.orEmpty()) }
    var diasAtencao by remember { mutableStateOf((tipoProcessoInicial?.diasAlertaAtencao ?: 5).toString()) }
    var diasCritico by remember { mutableStateOf((tipoProcessoInicial?.diasAlertaCritico ?: 10).toString()) }
    var simples by remember { mutableStateOf(tipoProcessoInicial?.simples ?: false) }
    var fasePadraoId by remember { mutableStateOf(tipoProcessoInicial?.fasePadraoId) }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Processo simples (uma única etapa)?")
                    AppToggle(
                        checked = simples,
                        onCheckedChange = { ligado ->
                            simples = ligado
                            // Desligar o toggle limpa a fase escolhida — evita
                            // salvar uma fase-padrão obsoleta "escondida" atrás
                            // de um tipo que voltou a ser com etapas.
                            if (!ligado) fasePadraoId = null
                        }
                    )
                }
                if (simples && tipoProcessoInicial?.simples != true && processosEmAndamentoDoTipo > 0) {
                    Text(
                        "$processosEmAndamentoDoTipo processo(s) em andamento deste tipo vão perder o botão " +
                            "\"Avançar fase\" e passar a mostrar \"Concluir processo\" diretamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmareloAtencao
                    )
                }
                if (simples) {
                    Text(
                        "Fase única deste tipo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (fases.isEmpty()) {
                        Text(
                            "Cadastre ao menos uma fase em Mais > Cadastro de Fases antes de marcar um tipo como simples.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    // Lista simples em vez de DropdownField: um ExposedDropdownMenu
                    // (baseado em Popup) dentro de um AlertDialog (outra janela) não
                    // posiciona/renderiza corretamente — mesmo bug conhecido de
                    // FaseDestinoDialog (AvancarFaseScreen.kt).
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    ) {
                        fases.forEach { fase ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = fasePadraoId == fase.id,
                                        onClick = { fasePadraoId = fase.id }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = fasePadraoId == fase.id, onClick = { fasePadraoId = fase.id })
                                Text(fase.nome)
                            }
                        }
                    }
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
                enabled = nome.isNotBlank() && (!simples || fasePadraoId != null),
                onClick = {
                    onSalvar(nome.trim(), diasAtencao.toIntOrNull() ?: 5, diasCritico.toIntOrNull() ?: 10, simples, fasePadraoId)
                }
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
