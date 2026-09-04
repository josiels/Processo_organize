package com.josiel.organizeprocesso.ui.processos

import android.app.Application
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.josiel.organizeprocesso.data.local.DiligenciaEntity
import com.josiel.organizeprocesso.data.local.FaseEntity
import com.josiel.organizeprocesso.data.local.PerfilEntity
import com.josiel.organizeprocesso.ui.components.AppToggle
import com.josiel.organizeprocesso.ui.components.DateField
import com.josiel.organizeprocesso.ui.components.DropdownField
import com.josiel.organizeprocesso.ui.components.PillButton
import com.josiel.organizeprocesso.ui.components.SemaforoPill
import com.josiel.organizeprocesso.ui.theme.Orange800
import com.josiel.organizeprocesso.ui.theme.OrangePastel
import com.josiel.organizeprocesso.ui.theme.VerdeOk
import com.josiel.organizeprocesso.ui.theme.VerdeOkPastel
import java.time.format.DateTimeFormatter

/** Tela de ação central: avançar/retroceder fase (REQUISITOS.md, seção 9; DESIGN.md, seção 5; ROADMAP.md, passo 9). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvancarFaseScreen(
    processoId: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: AvancarFaseViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AvancarFaseViewModel(application, processoId) }
        }
    )
    val estado by viewModel.uiState.collectAsState()
    val diligencias by viewModel.diligencias.collectAsState()
    var dialogoFase by remember { mutableStateOf<TipoMudancaFase?>(null) }
    var mostrarDialogoDiligencia by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Avançar fase") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    FilledIconButton(
                        enabled = estado.historicoAtual != null && estado.podeEditar && !estado.salvandoEntrada,
                        onClick = { viewModel.salvarEntradaAtual() }
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Salvar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        if (estado.carregando || estado.historicoAtual == null) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                // Sem o histórico da fase corrente a tela inteira fica em
                // espera; se o sync inicial falhou, mostrar o erro aqui evita
                // um spinner infinito sem explicação.
                val erro = estado.erro
                if (erro != null) {
                    Text(erro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                } else {
                    CircularProgressIndicator()
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(estado.faseAtual?.nome ?: "—", style = MaterialTheme.typography.headlineSmall)
            SemaforoPill(status = estado.statusSemaforo)
            if (!estado.podeEditar) {
                Text(
                    "Somente leitura — você não pode editar esta fase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            estado.erro?.let { erro ->
                Text(erro, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBackClick, modifier = Modifier.weight(1f)) {
                    Text("Ver anexos/links da fase")
                }
                TextButton(onClick = onBackClick, modifier = Modifier.weight(1f)) {
                    Text("Ver histórico de fases")
                }
            }

            Column {
                OutlinedTextField(
                    value = estado.observacao,
                    onValueChange = viewModel::atualizarObservacao,
                    enabled = estado.podeEditar,
                    label = { Text("Observação") },
                    placeholder = { Text("Adicionar uma observação sobre esta fase...") },
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )
                Text(
                    "${estado.observacao.length}/$LIMITE_CARACTERES_OBSERVACAO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    textAlign = TextAlign.End
                )
            }

            DropdownField(
                label = "Executor desta passagem",
                opcoes = estado.perfis.filter { it.ativo },
                selecionado = estado.perfis.find { it.id == estado.executorId },
                rotulo = PerfilEntity::nome,
                onSelecionado = { viewModel.atualizarExecutor(it.id) },
                enabled = estado.podeEditar,
                modifier = Modifier.fillMaxWidth()
            )

            DateField(
                label = "Prazo limite (opcional)",
                data = estado.prazoLimite,
                onDataSelecionada = viewModel::atualizarPrazoLimite,
                onLimpar = { viewModel.atualizarPrazoLimite(null) },
                enabled = estado.podeEditar,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Notificar sobre prazo desta fase?")
                AppToggle(
                    checked = estado.notificarPrazo,
                    onCheckedChange = viewModel::atualizarNotificarPrazo,
                    enabled = estado.podeEditar
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Diligências", style = MaterialTheme.typography.titleMedium)
            if (diligencias.isEmpty()) {
                Text(
                    "Nenhuma diligência registrada nesta fase ainda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                diligencias.forEach { diligencia ->
                    val autorNome = estado.perfis.find { it.id == diligencia.autorId }?.nome ?: "—"
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(diligencia.conteudo, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "$autorNome · ${formatoDataHora.format(diligencia.criadoEm.atZone(java.time.ZoneId.systemDefault()))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (estado.podeEditar) {
                PillButton(
                    text = "Registrar diligência",
                    onClick = { mostrarDialogoDiligencia = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.wrapContentSize()
                )
            }

            if (estado.podeEditar) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    PillButton(
                        text = "Avançar fase",
                        onClick = { dialogoFase = TipoMudancaFase.AVANCAR },
                        containerColor = VerdeOkPastel,
                        contentColor = VerdeOk,
                        modifier = Modifier.weight(1f)
                    )
                    PillButton(
                        text = "Retornar fase",
                        onClick = { dialogoFase = TipoMudancaFase.RETORNAR },
                        containerColor = OrangePastel,
                        contentColor = Orange800,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    dialogoFase?.let { tipo ->
        FaseDestinoDialog(
            tipo = tipo,
            fases = estado.fases.filter { it.id != estado.faseAtual?.id },
            salvando = estado.salvando,
            onDismiss = { dialogoFase = null },
            onConfirmar = { faseDestino, motivo ->
                viewModel.mudarFase(faseDestino.id, motivo) {
                    dialogoFase = null
                    onBackClick()
                }
            }
        )
    }

    if (mostrarDialogoDiligencia) {
        RegistrarDiligenciaDialog(
            registrando = estado.registrandoDiligencia,
            onDismiss = { mostrarDialogoDiligencia = false },
            onConfirmar = { conteudo ->
                viewModel.registrarDiligencia(conteudo)
                mostrarDialogoDiligencia = false
            }
        )
    }
}

private enum class TipoMudancaFase { AVANCAR, RETORNAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FaseDestinoDialog(
    tipo: TipoMudancaFase,
    fases: List<FaseEntity>,
    salvando: Boolean,
    onDismiss: () -> Unit,
    onConfirmar: (fase: FaseEntity, motivo: String?) -> Unit
) {
    var faseSelecionada by remember { mutableStateOf<FaseEntity?>(null) }
    var motivo by remember { mutableStateOf("") }
    val ehRetorno = tipo == TipoMudancaFase.RETORNAR

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ehRetorno) "Retornar fase" else "Selecionar fase de destino") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fase", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (fases.isEmpty()) {
                    Text(
                        "Cadastre outra fase em Mais > Cadastro de Fases para poder mover este processo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                // Lista simples em vez de DropdownField: um ExposedDropdownMenu
                // (baseado em Popup) dentro de um AlertDialog (outra janela) não
                // posiciona/renderiza corretamente — bug conhecido de popup
                // aninhado em outro popup/diálogo.
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
                                    selected = faseSelecionada?.id == fase.id,
                                    onClick = { faseSelecionada = fase }
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = faseSelecionada?.id == fase.id, onClick = { faseSelecionada = fase })
                            Text(fase.nome)
                        }
                    }
                }
                if (ehRetorno) {
                    OutlinedTextField(
                        value = motivo,
                        onValueChange = { motivo = it },
                        label = { Text("Motivo do retorno") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = faseSelecionada != null && (!ehRetorno || motivo.isNotBlank()) && !salvando,
                onClick = {
                    faseSelecionada?.let { onConfirmar(it, if (ehRetorno) motivo else null) }
                }
            ) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private val formatoDataHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

@Composable
private fun RegistrarDiligenciaDialog(
    registrando: Boolean,
    onDismiss: () -> Unit,
    onConfirmar: (String) -> Unit
) {
    var texto by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar diligência") },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                label = { Text("O que aconteceu?") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = texto.isNotBlank() && !registrando,
                onClick = { onConfirmar(texto.trim()) }
            ) { Text("Registrar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
