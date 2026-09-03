package com.josiel.organizeprocesso.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import com.josiel.organizeprocesso.domain.usecase.calcularUrgenciaPrazo
import com.josiel.organizeprocesso.domain.usecase.gerarGradeCalendario
import com.josiel.organizeprocesso.ui.theme.AmareloAtencao
import com.josiel.organizeprocesso.ui.theme.Indigo600
import com.josiel.organizeprocesso.ui.theme.VerdeOk
import com.josiel.organizeprocesso.ui.theme.VermelhoCritico
import java.time.LocalDate
import java.time.YearMonth

private val nomesDosMeses = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
)

private val rotulosDiasDaSemana = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")

/** Agenda: calendário de prazos (spec da Agenda). */
@Composable
fun AgendaScreen(
    onProcessoClick: (processoId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AgendaViewModel = viewModel()
) {
    val prazos by viewModel.prazos.collectAsState()
    val hoje = remember { LocalDate.now() }
    var mesAtual by remember { mutableStateOf(YearMonth.from(hoje)) }
    var diaSelecionado by remember { mutableStateOf(hoje) }

    val prazosPorDia = remember(prazos) { prazos.groupBy { it.prazoLimite } }
    val grade = remember(mesAtual) { gerarGradeCalendario(mesAtual) }
    val itensDoDiaSelecionado = prazosPorDia[diaSelecionado].orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                // Reseleciona o dia 1 do mês exibido — sem isso, a lista abaixo
                // continuava mostrando os prazos do dia selecionado no mês
                // anterior, sem nenhuma indicação disso na tela (achado da
                // revisão final da Agenda).
                val novoMes = mesAtual.minusMonths(1)
                mesAtual = novoMes
                diaSelecionado = novoMes.atDay(1)
            }) {
                Text("‹", style = MaterialTheme.typography.headlineSmall)
            }
            Text(nomeDoMes(mesAtual), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = {
                val novoMes = mesAtual.plusMonths(1)
                mesAtual = novoMes
                diaSelecionado = novoMes.atDay(1)
            }) {
                Text("›", style = MaterialTheme.typography.headlineSmall)
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            rotulosDiasDaSemana.forEach { rotulo ->
                Text(
                    rotulo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        grade.chunked(7).forEach { semana ->
            Row(modifier = Modifier.fillMaxWidth()) {
                semana.forEach { dia ->
                    Box(modifier = Modifier.weight(1f)) {
                        DiaCelula(
                            dia = dia,
                            hoje = hoje,
                            selecionado = dia == diaSelecionado,
                            urgencia = dia?.let { d ->
                                if (prazosPorDia.containsKey(d)) calcularUrgenciaPrazo(d, hoje) else null
                            },
                            onClick = { dia?.let { diaSelecionado = it } }
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            "Prazos de ${nomeCompletoDoDia(diaSelecionado)}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (itensDoDiaSelecionado.isEmpty()) {
            Text(
                "Nenhum prazo neste dia.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itensDoDiaSelecionado.forEach { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProcessoClick(item.processoId) }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            item.numero,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${item.objeto} — ${item.faseNome}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun nomeDoMes(mes: YearMonth): String = "${nomesDosMeses[mes.monthValue - 1]} de ${mes.year}"

private fun nomeCompletoDoDia(dia: LocalDate): String =
    "${dia.dayOfMonth} de ${nomesDosMeses[dia.monthValue - 1].lowercase()} de ${dia.year}"

@Composable
private fun DiaCelula(
    dia: LocalDate?,
    hoje: LocalDate,
    selecionado: Boolean,
    urgencia: StatusSemaforo?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clickable(enabled = dia != null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (dia != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Selecionado ganha círculo de fundo preenchido; hoje (quando
                // não é o selecionado) ganha só uma borda — antes os dois
                // compartilhavam a mesma cor de texto sem mais nenhuma
                // distinção visual (achado da revisão final da Agenda).
                val corTexto = when {
                    selecionado -> MaterialTheme.colorScheme.onPrimary
                    dia == hoje -> Indigo600
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .then(
                            when {
                                selecionado -> Modifier.background(Indigo600, CircleShape)
                                dia == hoje -> Modifier.border(1.5.dp, Indigo600, CircleShape)
                                else -> Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        dia.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = corTexto
                    )
                }
                if (urgencia != null) {
                    val cor = when (urgencia) {
                        StatusSemaforo.OK -> VerdeOk
                        StatusSemaforo.ATENCAO -> AmareloAtencao
                        StatusSemaforo.CRITICO -> VermelhoCritico
                    }
                    Box(modifier = Modifier.size(6.dp).background(cor, CircleShape))
                } else {
                    Box(modifier = Modifier.size(6.dp))
                }
            }
        }
    }
}
