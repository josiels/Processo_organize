# Agenda Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substituir o placeholder `AgendaScreen.kt` por um calendário
mensal funcional: grade de dias com indicador colorido nos dias com
prazo, navegação entre meses, e lista de processos com prazo no dia
selecionado.

**Architecture:** Calendário construído do zero (nenhuma biblioteca
externa), mesmo padrão de UI 100% hand-rolled do resto do app.
`java.time.YearMonth`/`LocalDate` cobre a matemática de dias/semanas. Duas
funções puras novas (`gerarGradeCalendario`, `calcularUrgenciaPrazo`)
testadas isoladamente; um `AgendaViewModel` novo reaproveita os
repositórios já existentes (`ProcessoRepository`, `FaseRepository`,
`ProcessoFaseHistoricoDao.observarTodosAtivos()`, este último já usado
desde o Plano 2B) via `combine`+`stateIn`, mesmo padrão de
`InicioViewModel`/`ProcessoListViewModel`.

**Tech Stack:** Kotlin, Jetpack Compose, Room.

**Spec:** `docs/superpowers/specs/2026-09-02-android-agenda-design.md`

## Global Constraints

- Nenhuma biblioteca de calendário nova — grade construída à mão (spec §2).
- Semana começa no domingo, hardcoded, sem depender de `Locale`/`WeekFields` (spec §4).
- Cor do indicador de um dia é sempre `calcularUrgenciaPrazo(dia, hoje)` — vencido/hoje = CRÍTICO, até 5 dias = ATENÇÃO, mais adiante = OK. Reaproveita o enum `StatusSemaforo` já existente, não cria vocabulário de cor novo (spec §5).
- Só processos `status_geral = EM_ANDAMENTO` com `prazoLimite` não nulo na fase ativa entram na Agenda (spec §6).
- Dia selecionado por padrão é hoje; navegar de mês é estado local da tela, não toca o Room (spec §7).
- Zero mudança em `AvancarFaseScreen`, aba Timeline, Dashboard, ou regras de notificação (spec §10).

---

## Task 1: Funções puras — grade do calendário + urgência do prazo (TDD)

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/domain/usecase/GerarGradeCalendario.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/domain/usecase/CalcularUrgenciaPrazo.kt`
- Test: `app/src/test/java/com/josiel/organizeprocesso/domain/usecase/GerarGradeCalendarioTest.kt`
- Test: `app/src/test/java/com/josiel/organizeprocesso/domain/usecase/CalcularUrgenciaPrazoTest.kt`

**Interfaces:**
- Produces: `fun gerarGradeCalendario(mes: YearMonth): List<LocalDate?>`; `fun calcularUrgenciaPrazo(prazo: LocalDate, hoje: LocalDate): StatusSemaforo` — ambas consumidas pela Task 3 (`AgendaScreen`).

- [ ] **Step 1: Escrever o teste de `gerarGradeCalendario` (falhando)**

Crie `app/src/test/java/com/josiel/organizeprocesso/domain/usecase/GerarGradeCalendarioTest.kt`:

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GerarGradeCalendarioTest {
    @Test
    fun `mes que comeca no domingo nao tem celulas vazias`() {
        // Fevereiro de 2026 começa num domingo e tem exatamente 4 semanas (28 dias).
        val grade = gerarGradeCalendario(YearMonth.of(2026, 2))
        assertEquals(28, grade.size)
        assertEquals(LocalDate.of(2026, 2, 1), grade.first())
        assertEquals(LocalDate.of(2026, 2, 28), grade.last())
        assertEquals(0, grade.count { it == null })
    }

    @Test
    fun `mes que comeca no meio da semana tem celulas vazias no inicio e no fim`() {
        // Abril de 2026 começa numa quarta-feira (deslocamento 3) e tem 30 dias.
        val grade = gerarGradeCalendario(YearMonth.of(2026, 4))
        assertEquals(35, grade.size) // 5 semanas completas
        assertNull(grade[0])
        assertNull(grade[1])
        assertNull(grade[2])
        assertEquals(LocalDate.of(2026, 4, 1), grade[3])
        assertEquals(LocalDate.of(2026, 4, 30), grade[32])
        assertNull(grade[33])
        assertNull(grade[34])
    }

    @Test
    fun `tamanho da grade e sempre multiplo de 7`() {
        for (mes in 1..12) {
            val grade = gerarGradeCalendario(YearMonth.of(2026, mes))
            assertEquals(0, grade.size % 7)
        }
    }

    @Test
    fun `todos os dias do mes aparecem na grade, em ordem`() {
        val mes = YearMonth.of(2026, 4)
        val grade = gerarGradeCalendario(mes)
        val diasNaoNulos = grade.filterNotNull()
        assertEquals((1..30).map { mes.atDay(it) }, diasNaoNulos)
    }
}
```

- [ ] **Step 2: Rodar o teste, confirmar que falha**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.GerarGradeCalendarioTest"`
Expected: FAIL (função não existe ainda).

- [ ] **Step 3: Implementar `gerarGradeCalendario`**

Crie `app/src/main/java/com/josiel/organizeprocesso/domain/usecase/GerarGradeCalendario.kt`:

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import java.time.LocalDate
import java.time.YearMonth

/**
 * Gera a grade do calendário para [mes]: uma lista de células (múltiplo de
 * 7, uma ou mais semanas completas), com `null` nas posições fora do mês
 * (antes do dia 1 ou depois do último dia) — semana começando no domingo
 * (spec da Agenda, seção 4).
 */
fun gerarGradeCalendario(mes: YearMonth): List<LocalDate?> {
    val primeiroDia = mes.atDay(1)
    // DayOfWeek.value: MONDAY=1..SUNDAY=7 — domingo precisa virar
    // deslocamento 0, daí o módulo por 7.
    val deslocamento = primeiroDia.dayOfWeek.value % 7
    val dias: List<LocalDate?> = (1..mes.lengthOfMonth()).map { mes.atDay(it) }
    val celulasIniciais: List<LocalDate?> = List(deslocamento) { null }
    val celulas = celulasIniciais + dias
    val restante = (7 - celulas.size % 7) % 7
    val celulasFinais: List<LocalDate?> = List(restante) { null }
    return celulas + celulasFinais
}
```

- [ ] **Step 4: Rodar o teste, confirmar que passa**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.GerarGradeCalendarioTest"`
Expected: PASS (4 testes).

- [ ] **Step 5: Escrever o teste de `calcularUrgenciaPrazo` (falhando)**

Crie `app/src/test/java/com/josiel/organizeprocesso/domain/usecase/CalcularUrgenciaPrazoTest.kt`:

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CalcularUrgenciaPrazoTest {
    private val hoje = LocalDate.of(2026, 9, 2)

    @Test
    fun `prazo vencido e critico`() {
        assertEquals(StatusSemaforo.CRITICO, calcularUrgenciaPrazo(hoje.minusDays(1), hoje))
    }

    @Test
    fun `prazo hoje e critico`() {
        assertEquals(StatusSemaforo.CRITICO, calcularUrgenciaPrazo(hoje, hoje))
    }

    @Test
    fun `prazo em 5 dias e atencao (limite exato)`() {
        assertEquals(StatusSemaforo.ATENCAO, calcularUrgenciaPrazo(hoje.plusDays(5), hoje))
    }

    @Test
    fun `prazo em 1 dia e atencao`() {
        assertEquals(StatusSemaforo.ATENCAO, calcularUrgenciaPrazo(hoje.plusDays(1), hoje))
    }

    @Test
    fun `prazo em 6 dias e ok`() {
        assertEquals(StatusSemaforo.OK, calcularUrgenciaPrazo(hoje.plusDays(6), hoje))
    }

    @Test
    fun `prazo bem no futuro e ok`() {
        assertEquals(StatusSemaforo.OK, calcularUrgenciaPrazo(hoje.plusDays(30), hoje))
    }
}
```

- [ ] **Step 6: Rodar o teste, confirmar que falha**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.CalcularUrgenciaPrazoTest"`
Expected: FAIL (função não existe ainda).

- [ ] **Step 7: Implementar `calcularUrgenciaPrazo`**

Crie `app/src/main/java/com/josiel/organizeprocesso/domain/usecase/CalcularUrgenciaPrazo.kt`:

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Urgência de um [prazo] em relação a [hoje] — vencido ou vence hoje é
 * CRÍTICO, dentro dos próximos 5 dias é ATENÇÃO, mais adiante é OK (spec
 * da Agenda, seção 5). Eixo diferente do semáforo de "tempo parado na
 * fase" (CalcularSemaforo.kt) — este mede proximidade de um prazo, não
 * duração numa fase.
 */
fun calcularUrgenciaPrazo(prazo: LocalDate, hoje: LocalDate): StatusSemaforo {
    val dias = ChronoUnit.DAYS.between(hoje, prazo)
    return when {
        dias <= 0 -> StatusSemaforo.CRITICO
        dias <= 5 -> StatusSemaforo.ATENCAO
        else -> StatusSemaforo.OK
    }
}
```

- [ ] **Step 8: Rodar o teste, confirmar que passa**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.CalcularUrgenciaPrazoTest"`
Expected: PASS (6 testes).

- [ ] **Step 9: Verificar que o projeto inteiro compila e os testes passam**

Run: `./gradlew.bat :app:compileDebugKotlin`
Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` nos dois.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/domain/usecase/GerarGradeCalendario.kt \
        app/src/main/java/com/josiel/organizeprocesso/domain/usecase/CalcularUrgenciaPrazo.kt \
        app/src/test/java/com/josiel/organizeprocesso/domain/usecase/GerarGradeCalendarioTest.kt \
        app/src/test/java/com/josiel/organizeprocesso/domain/usecase/CalcularUrgenciaPrazoTest.kt
git commit -m "feat: add pure calendar grid and prazo urgency usecases for the Agenda"
```

---

## Task 2: `PrazoAgendaItem` + `AgendaViewModel`

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/domain/model/PrazoAgendaItem.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/ui/agenda/AgendaViewModel.kt`

**Interfaces:**
- Consumes: `ProcessoRepository.observarTodos()`, `FaseRepository.observarTodas()`, `ProcessoFaseHistoricoDao.observarTodosAtivos()` (todos já existem).
- Produces: `data class PrazoAgendaItem(processoId, numero, objeto, faseNome, prazoLimite: LocalDate)`; `AgendaViewModel.prazos: StateFlow<List<PrazoAgendaItem>>` — consumidos pela Task 3 (`AgendaScreen`).

- [ ] **Step 1: Criar o modelo `PrazoAgendaItem`**

Crie `app/src/main/java/com/josiel/organizeprocesso/domain/model/PrazoAgendaItem.kt`:

```kotlin
package com.josiel.organizeprocesso.domain.model

import java.time.LocalDate

/**
 * Um processo com prazo na fase corrente, para a grade da Agenda (spec da
 * Agenda, seção 6).
 */
data class PrazoAgendaItem(
    val processoId: String,
    val numero: String,
    val objeto: String,
    val faseNome: String,
    val prazoLimite: LocalDate
)
```

- [ ] **Step 2: Criar `AgendaViewModel`**

Crie `app/src/main/java/com/josiel/organizeprocesso/ui/agenda/AgendaViewModel.kt`:

```kotlin
package com.josiel.organizeprocesso.ui.agenda

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.data.repository.ProcessoRepository
import com.josiel.organizeprocesso.domain.model.PrazoAgendaItem
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** ViewModel da Agenda (spec da Agenda, seção 6). */
class AgendaViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val processoRepository = ProcessoRepository(database, SupabaseSessionManager.client)
    private val faseRepository = FaseRepository(database.faseDao(), SupabaseSessionManager.client)
    private val historicoDao = database.processoFaseHistoricoDao()

    val prazos: StateFlow<List<PrazoAgendaItem>> = combine(
        processoRepository.observarTodos(),
        historicoDao.observarTodosAtivos(),
        faseRepository.observarTodas()
    ) { processos, historicosAtivos, fases ->
        val faseMap = fases.associateBy { it.id }
        val historicoPorProcesso = historicosAtivos.associateBy { it.processoId }

        processos.mapNotNull { processo ->
            if (processo.statusGeral != StatusGeralProcesso.EM_ANDAMENTO) return@mapNotNull null
            val historico = historicoPorProcesso[processo.id] ?: return@mapNotNull null
            val prazoLimite = historico.prazoLimite ?: return@mapNotNull null
            PrazoAgendaItem(
                processoId = processo.id,
                numero = processo.numero,
                objeto = processo.objeto,
                faseNome = faseMap[historico.faseId]?.nome ?: "—",
                prazoLimite = prazoLimite
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
```

- [ ] **Step 3: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/domain/model/PrazoAgendaItem.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/agenda/AgendaViewModel.kt
git commit -m "feat: add PrazoAgendaItem model and AgendaViewModel"
```

---

## Task 3: `AgendaScreen` + navegação

**Files:**
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/agenda/AgendaScreen.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/navigation/AppNavHost.kt`

**Interfaces:**
- Consumes: `gerarGradeCalendario`, `calcularUrgenciaPrazo` (Task 1); `AgendaViewModel.prazos`, `PrazoAgendaItem` (Task 2).
- Produces: `AgendaScreen(onProcessoClick: (String) -> Unit)` — consumido só por `AppNavHost.kt` nesta mesma task.

- [ ] **Step 1: Reescrever `AgendaScreen`**

Substitua `app/src/main/java/com/josiel/organizeprocesso/ui/agenda/AgendaScreen.kt` (hoje um placeholder) por:

```kotlin
package com.josiel.organizeprocesso.ui.agenda

import androidx.compose.foundation.background
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
            IconButton(onClick = { mesAtual = mesAtual.minusMonths(1) }) {
                Text("‹", style = MaterialTheme.typography.headlineSmall)
            }
            Text(nomeDoMes(mesAtual), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { mesAtual = mesAtual.plusMonths(1) }) {
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
                        Text(item.numero, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${item.objeto} — ${item.faseNome}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun nomeDoMes(mes: YearMonth): String = "${nomesDosMeses[mes.monthValue - 1]} de ${mes.year}"

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
                Text(
                    dia.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selecionado || dia == hoje) Indigo600 else MaterialTheme.colorScheme.onSurface
                )
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
```

- [ ] **Step 2: Wire a navegação em `AppNavHost.kt`**

Em `app/src/main/java/com/josiel/organizeprocesso/navigation/AppNavHost.kt`, localize:

```kotlin
            composable<Agenda> { AgendaScreen() }
```

Substitua por:

```kotlin
            composable<Agenda> {
                AgendaScreen(onProcessoClick = { processoId -> navController.navigate(ProcessoDetalhe(processoId)) })
            }
```

(`ProcessoDetalhe` já está em uso mais abaixo no mesmo arquivo, dentro de `composable<Processos> { ... }` e `composable<Inicio> { ... }` — nenhum import novo necessário.)

- [ ] **Step 3: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/ui/agenda/AgendaScreen.kt \
        app/src/main/java/com/josiel/organizeprocesso/navigation/AppNavHost.kt
git commit -m "feat: implement Agenda calendar screen and wire navigation"
```

---

## Task 4: Verificação final

**Files:** nenhum criado — só verificação.

- [ ] **Step 1: Build completo (limpo)**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin --rerun-tasks
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Suíte de testes unitários completa**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`, os 10 testes novos da Task 1 passando (4 + 6), mais os 52 já existentes — 62 no total.

- [ ] **Step 3: Registrar o que fica pendente**

Nenhum teste funcional em emulador/dispositivo real foi executado nesta
sessão (confirmar visualmente que os indicadores de cor batem com prazos
reais, testar navegação entre meses, testar seleção de dia e clique num
item da lista). Fica registrado como pendência, não como bloqueador para
fechar este plano.

- [ ] **Step 4: Commit (se necessário)**

Se os Steps 1-2 não exigiram nenhuma mudança de código, não há o que commitar.
