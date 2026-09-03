# Dashboard Início Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substituir o placeholder `InicioScreen.kt` por um dashboard
funcional: saudação, progresso geral, resumo de atualizações recentes, e
dois cards de destaque roláveis (processos críticos / próximos prazos).

**Architecture:** Reaproveita 100% os repositórios/DAOs já existentes
(`ProcessoRepository`, `FaseRepository`, `PerfilRepository`,
`ProcessoFaseHistoricoDao`), com uma consulta nova (`observarTodos()` no
DAO de histórico) e um pequeno ajuste em `ProcessoRepository.atualizar()`.
Três funções puras novas em `domain.usecase` fazem as agregações
(progresso, críticos, próximos prazos), testadas isoladamente; um
`InicioViewModel` novo as orquestra a partir dos flows do Room, mesmo
padrão de `combine`+`stateIn` já usado em `ProcessoListViewModel`/
`ProcessoDetalheViewModel`.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Supabase Postgrest.

**Spec:** `docs/superpowers/specs/2026-09-02-android-dashboard-inicio-design.md`

## Global Constraints

- Nenhuma distinção por papel — RLS de `processos` já é org-wide para leitura (spec §2).
- `ui.inicio` não importa tipos de `ui.processos` — `ProcessoResumoDashboard` é um modelo próprio, não reusa `ProcessoListItem` (spec §7).
- Cards "Processos críticos"/"Próximos prazos" navegam direto para `ProcessoDetalhe(processoId)` — sem filtro pré-aplicado na rota `Processos` (spec §7, §10).
- Progresso geral e as duas listas de destaque consideram só `status_geral = EM_ANDAMENTO` (spec §6, §7).
- "Próximos prazos" exclui prazos já vencidos (`prazoLimite < hoje`) (spec §7).
- `ProcessoRepository.atualizar()` passa a sempre gravar `atualizado_em` no payload — sem trigger de banco novo (spec §4).
- Zero mudança em `AvancarFaseScreen`, aba Timeline, ou regras de notificação (spec §10).

---

## Task 1: Fonte de dados — DAO de histórico + ajuste no `ProcessoRepository`

**Files:**
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/local/ProcessoFaseHistoricoDao.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/repository/ProcessoRepository.kt`

**Interfaces:**
- Produces: `ProcessoFaseHistoricoDao.observarTodos(): Flow<List<ProcessoFaseHistoricoEntity>>` — consumido pela Task 3 (`InicioViewModel`, sinal de avanço de fase hoje).

- [ ] **Step 1: Adicionar `observarTodos()` ao DAO**

Em `app/src/main/java/com/josiel/organizeprocesso/data/local/ProcessoFaseHistoricoDao.kt`, adicione (depois de `observarTodosAtivos()`):

```kotlin
    /** Toda a tabela (a RLS já limita à organização) — usado pelo Dashboard para achar avanços de fase de hoje, mesmo em entradas já fechadas. */
    @Query("SELECT * FROM processo_fase_historico")
    fun observarTodos(): Flow<List<ProcessoFaseHistoricoEntity>>
```

- [ ] **Step 2: Gravar `atualizado_em` em toda escrita de `atualizar()`**

Em `app/src/main/java/com/josiel/organizeprocesso/data/repository/ProcessoRepository.kt`, localize dentro de `atualizar(...)`:

```kotlin
        val linhaProcesso = buildJsonObject {
            put("numero", processo.numero)
            put("objeto", processo.objeto)
            put("descricao", processo.descricao)
            put("orgao_demandante", processo.orgaoDemandante)
            put("tipo_processo_id", processo.tipoProcessoId)
            put("valor_estimado_total", valorTotal)
            put("status_geral", processo.statusGeral.name.lowercase())
        }
```

Substitua por:

```kotlin
        val linhaProcesso = buildJsonObject {
            put("numero", processo.numero)
            put("objeto", processo.objeto)
            put("descricao", processo.descricao)
            put("orgao_demandante", processo.orgaoDemandante)
            put("tipo_processo_id", processo.tipoProcessoId)
            put("valor_estimado_total", valorTotal)
            put("status_geral", processo.statusGeral.name.lowercase())
            // Sem isto, uma edição direta de campo ou a conclusão de um
            // processo simples (ProcessoDetalheViewModel.concluir(), que
            // chama este método) ficariam invisíveis para o resumo de
            // "atualizações recentes" do Dashboard — só as RPCs avancar_fase/
            // designar_processo tocavam esta coluna até agora (spec do
            // Dashboard Início, seção 4).
            put("atualizado_em", Instant.now().toString())
        }
```

`Instant` já está importado neste arquivo (usado em `ProcessoDto.paraEntity()`), nenhum import novo necessário.

- [ ] **Step 3: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/data/local/ProcessoFaseHistoricoDao.kt \
        app/src/main/java/com/josiel/organizeprocesso/data/repository/ProcessoRepository.kt
git commit -m "feat: add observarTodos to historico DAO, bump atualizado_em on every atualizar()"
```

---

## Task 2: Agregações do Dashboard — modelo + 3 funções puras (TDD)

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/domain/model/ProcessoResumoDashboard.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/domain/usecase/CalcularProgressoGeral.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/domain/usecase/SelecionarProcessosCriticos.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/domain/usecase/SelecionarProximosPrazos.kt`
- Test: `app/src/test/java/com/josiel/organizeprocesso/domain/usecase/CalcularProgressoGeralTest.kt`
- Test: `app/src/test/java/com/josiel/organizeprocesso/domain/usecase/SelecionarProcessosCriticosTest.kt`
- Test: `app/src/test/java/com/josiel/organizeprocesso/domain/usecase/SelecionarProximosPrazosTest.kt`

**Interfaces:**
- Produces: `data class ProcessoResumoDashboard(id, numero, objeto, statusGeral, statusSemaforo, prazoLimite)`; `data class ProgressoGeral(emDia, total) { val percentual }`; `fun calcularProgressoGeral(processos: List<ProcessoResumoDashboard>): ProgressoGeral`; `fun selecionarProcessosCriticos(processos: List<ProcessoResumoDashboard>, limite: Int = 5): List<ProcessoResumoDashboard>`; `fun selecionarProximosPrazos(processos: List<ProcessoResumoDashboard>, hoje: LocalDate, limite: Int = 5): List<ProcessoResumoDashboard>` — todos consumidos pela Task 3 (`InicioViewModel`).

- [ ] **Step 1: Criar o modelo `ProcessoResumoDashboard`**

Crie `app/src/main/java/com/josiel/organizeprocesso/domain/model/ProcessoResumoDashboard.kt`:

```kotlin
package com.josiel.organizeprocesso.domain.model

import java.time.LocalDate

/**
 * Vista simplificada de um processo para as agregações do Dashboard
 * (spec do Dashboard Início, seção 7) — não reusa `ProcessoListItem`
 * (`ui.processos`) para manter `ui.inicio` desacoplado de outra tela.
 */
data class ProcessoResumoDashboard(
    val id: String,
    val numero: String,
    val objeto: String,
    val statusGeral: StatusGeralProcesso,
    val statusSemaforo: StatusSemaforo,
    val prazoLimite: LocalDate?
)
```

- [ ] **Step 2: Escrever o teste de `calcularProgressoGeral` (falhando)**

Crie `app/src/test/java/com/josiel/organizeprocesso/domain/usecase/CalcularProgressoGeralTest.kt`:

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.ProcessoResumoDashboard
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import org.junit.Assert.assertEquals
import org.junit.Test

class CalcularProgressoGeralTest {
    private fun processo(
        statusGeral: StatusGeralProcesso = StatusGeralProcesso.EM_ANDAMENTO,
        statusSemaforo: StatusSemaforo = StatusSemaforo.OK
    ) = ProcessoResumoDashboard(
        id = "id",
        numero = "001",
        objeto = "Objeto",
        statusGeral = statusGeral,
        statusSemaforo = statusSemaforo,
        prazoLimite = null
    )

    @Test
    fun `lista vazia nao divide por zero`() {
        val resultado = calcularProgressoGeral(emptyList())
        assertEquals(0, resultado.total)
        assertEquals(0, resultado.emDia)
        assertEquals(0, resultado.percentual)
    }

    @Test
    fun `todos em dia da 100 por cento`() {
        val processos = listOf(
            processo(statusSemaforo = StatusSemaforo.OK),
            processo(statusSemaforo = StatusSemaforo.OK)
        )
        val resultado = calcularProgressoGeral(processos)
        assertEquals(2, resultado.total)
        assertEquals(2, resultado.emDia)
        assertEquals(100, resultado.percentual)
    }

    @Test
    fun `processos criticos nao contam como em dia`() {
        val processos = listOf(
            processo(statusSemaforo = StatusSemaforo.OK),
            processo(statusSemaforo = StatusSemaforo.CRITICO)
        )
        val resultado = calcularProgressoGeral(processos)
        assertEquals(2, resultado.total)
        assertEquals(1, resultado.emDia)
        assertEquals(50, resultado.percentual)
    }

    @Test
    fun `processos concluidos saem do denominador`() {
        val processos = listOf(
            processo(statusGeral = StatusGeralProcesso.EM_ANDAMENTO, statusSemaforo = StatusSemaforo.CRITICO),
            processo(statusGeral = StatusGeralProcesso.CONCLUIDO, statusSemaforo = StatusSemaforo.OK)
        )
        val resultado = calcularProgressoGeral(processos)
        assertEquals(1, resultado.total)
        assertEquals(0, resultado.emDia)
    }
}
```

- [ ] **Step 3: Rodar o teste, confirmar que falha**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.CalcularProgressoGeralTest"`
Expected: FAIL (função não existe ainda).

- [ ] **Step 4: Implementar `calcularProgressoGeral`**

Crie `app/src/main/java/com/josiel/organizeprocesso/domain/usecase/CalcularProgressoGeral.kt`:

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.ProcessoResumoDashboard
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import com.josiel.organizeprocesso.domain.model.StatusSemaforo

/** Progresso geral do card do Dashboard (spec do Dashboard Início, seção 6). */
data class ProgressoGeral(val emDia: Int, val total: Int) {
    val percentual: Int get() = if (total == 0) 0 else (emDia * 100) / total
}

/**
 * "X de Y processos em dia" — Y é só processos em andamento (concluídos/
 * cancelados/suspensos saem do cálculo); X é quantos desses têm semáforo
 * de tempo parado na fase OK (spec do Dashboard Início, seção 6).
 */
fun calcularProgressoGeral(processos: List<ProcessoResumoDashboard>): ProgressoGeral {
    val emAndamento = processos.filter { it.statusGeral == StatusGeralProcesso.EM_ANDAMENTO }
    val emDia = emAndamento.count { it.statusSemaforo == StatusSemaforo.OK }
    return ProgressoGeral(emDia = emDia, total = emAndamento.size)
}
```

- [ ] **Step 5: Rodar o teste, confirmar que passa**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.CalcularProgressoGeralTest"`
Expected: PASS (4 testes).

- [ ] **Step 6: Escrever o teste de `selecionarProcessosCriticos` (falhando)**

Crie `app/src/test/java/com/josiel/organizeprocesso/domain/usecase/SelecionarProcessosCriticosTest.kt`:

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.ProcessoResumoDashboard
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import org.junit.Assert.assertEquals
import org.junit.Test

class SelecionarProcessosCriticosTest {
    private fun processo(
        id: String,
        statusGeral: StatusGeralProcesso = StatusGeralProcesso.EM_ANDAMENTO,
        statusSemaforo: StatusSemaforo = StatusSemaforo.CRITICO
    ) = ProcessoResumoDashboard(
        id = id,
        numero = id,
        objeto = "Objeto $id",
        statusGeral = statusGeral,
        statusSemaforo = statusSemaforo,
        prazoLimite = null
    )

    @Test
    fun `so processos criticos em andamento entram`() {
        val processos = listOf(
            processo("1", statusSemaforo = StatusSemaforo.CRITICO),
            processo("2", statusSemaforo = StatusSemaforo.OK),
            processo("3", statusGeral = StatusGeralProcesso.CONCLUIDO, statusSemaforo = StatusSemaforo.CRITICO)
        )
        val resultado = selecionarProcessosCriticos(processos)
        assertEquals(listOf("1"), resultado.map { it.id })
    }

    @Test
    fun `respeita o limite`() {
        val processos = (1..10).map { processo(it.toString()) }
        val resultado = selecionarProcessosCriticos(processos, limite = 5)
        assertEquals(5, resultado.size)
    }

    @Test
    fun `lista vazia retorna lista vazia`() {
        assertEquals(emptyList<ProcessoResumoDashboard>(), selecionarProcessosCriticos(emptyList()))
    }
}
```

- [ ] **Step 7: Rodar o teste, confirmar que falha**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.SelecionarProcessosCriticosTest"`
Expected: FAIL (função não existe ainda).

- [ ] **Step 8: Implementar `selecionarProcessosCriticos`**

Crie `app/src/main/java/com/josiel/organizeprocesso/domain/usecase/SelecionarProcessosCriticos.kt`:

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.ProcessoResumoDashboard
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import com.josiel.organizeprocesso.domain.model.StatusSemaforo

/**
 * Top [limite] processos em andamento com semáforo de tempo parado
 * CRÍTICO, para o card "Processos críticos" do Dashboard (spec do
 * Dashboard Início, seção 7).
 */
fun selecionarProcessosCriticos(
    processos: List<ProcessoResumoDashboard>,
    limite: Int = 5
): List<ProcessoResumoDashboard> =
    processos
        .filter { it.statusGeral == StatusGeralProcesso.EM_ANDAMENTO && it.statusSemaforo == StatusSemaforo.CRITICO }
        .take(limite)
```

- [ ] **Step 9: Rodar o teste, confirmar que passa**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.SelecionarProcessosCriticosTest"`
Expected: PASS (3 testes).

- [ ] **Step 10: Escrever o teste de `selecionarProximosPrazos` (falhando)**

Crie `app/src/test/java/com/josiel/organizeprocesso/domain/usecase/SelecionarProximosPrazosTest.kt`:

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.ProcessoResumoDashboard
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SelecionarProximosPrazosTest {
    private val hoje = LocalDate.of(2026, 9, 2)

    private fun processo(
        id: String,
        prazoLimite: LocalDate?,
        statusGeral: StatusGeralProcesso = StatusGeralProcesso.EM_ANDAMENTO
    ) = ProcessoResumoDashboard(
        id = id,
        numero = id,
        objeto = "Objeto $id",
        statusGeral = statusGeral,
        statusSemaforo = StatusSemaforo.OK,
        prazoLimite = prazoLimite
    )

    @Test
    fun `ordena por prazo mais proximo primeiro`() {
        val processos = listOf(
            processo("1", hoje.plusDays(10)),
            processo("2", hoje.plusDays(2)),
            processo("3", hoje.plusDays(5))
        )
        val resultado = selecionarProximosPrazos(processos, hoje)
        assertEquals(listOf("2", "3", "1"), resultado.map { it.id })
    }

    @Test
    fun `exclui prazos vencidos`() {
        val processos = listOf(
            processo("1", hoje.minusDays(1)),
            processo("2", hoje.plusDays(1))
        )
        val resultado = selecionarProximosPrazos(processos, hoje)
        assertEquals(listOf("2"), resultado.map { it.id })
    }

    @Test
    fun `exclui processos sem prazo definido`() {
        val processos = listOf(processo("1", null), processo("2", hoje.plusDays(1)))
        val resultado = selecionarProximosPrazos(processos, hoje)
        assertEquals(listOf("2"), resultado.map { it.id })
    }

    @Test
    fun `prazo igual a hoje conta como proximo, nao vencido`() {
        val processos = listOf(processo("1", hoje))
        val resultado = selecionarProximosPrazos(processos, hoje)
        assertEquals(listOf("1"), resultado.map { it.id })
    }

    @Test
    fun `respeita o limite`() {
        val processos = (1..10).map { processo(it.toString(), hoje.plusDays(it.toLong())) }
        val resultado = selecionarProximosPrazos(processos, hoje, limite = 5)
        assertEquals(5, resultado.size)
    }
}
```

- [ ] **Step 11: Rodar o teste, confirmar que falha**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.SelecionarProximosPrazosTest"`
Expected: FAIL (função não existe ainda).

- [ ] **Step 12: Implementar `selecionarProximosPrazos`**

Crie `app/src/main/java/com/josiel/organizeprocesso/domain/usecase/SelecionarProximosPrazos.kt`:

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.ProcessoResumoDashboard
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import java.time.LocalDate

/**
 * Top [limite] processos em andamento com `prazoLimite` mais próximo (a
 * partir de [hoje]), excluindo prazos já vencidos — um prazo vencido não
 * é "próximo", já fica coberto pelo sinal de "críticos" se cruzar o
 * limiar de alerta da fase (spec do Dashboard Início, seção 7).
 */
fun selecionarProximosPrazos(
    processos: List<ProcessoResumoDashboard>,
    hoje: LocalDate,
    limite: Int = 5
): List<ProcessoResumoDashboard> =
    processos
        .filter { it.statusGeral == StatusGeralProcesso.EM_ANDAMENTO && it.prazoLimite != null && !it.prazoLimite.isBefore(hoje) }
        .sortedBy { it.prazoLimite }
        .take(limite)
```

- [ ] **Step 13: Rodar o teste, confirmar que passa**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.SelecionarProximosPrazosTest"`
Expected: PASS (5 testes).

- [ ] **Step 14: Verificar que o projeto inteiro compila e os testes passam**

Run: `./gradlew.bat :app:compileDebugKotlin`
Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` nos dois.

- [ ] **Step 15: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/domain/model/ProcessoResumoDashboard.kt \
        app/src/main/java/com/josiel/organizeprocesso/domain/usecase/CalcularProgressoGeral.kt \
        app/src/main/java/com/josiel/organizeprocesso/domain/usecase/SelecionarProcessosCriticos.kt \
        app/src/main/java/com/josiel/organizeprocesso/domain/usecase/SelecionarProximosPrazos.kt \
        app/src/test/java/com/josiel/organizeprocesso/domain/usecase/CalcularProgressoGeralTest.kt \
        app/src/test/java/com/josiel/organizeprocesso/domain/usecase/SelecionarProcessosCriticosTest.kt \
        app/src/test/java/com/josiel/organizeprocesso/domain/usecase/SelecionarProximosPrazosTest.kt
git commit -m "feat: add pure aggregation usecases for the Dashboard"
```

---

## Task 3: `InicioViewModel` + `InicioScreen` + navegação

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/ui/inicio/InicioViewModel.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/inicio/InicioScreen.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/navigation/AppNavHost.kt`

**Interfaces:**
- Consumes: `ProcessoFaseHistoricoDao.observarTodos()` (Task 1); `ProcessoResumoDashboard`, `ProgressoGeral`, `calcularProgressoGeral`, `selecionarProcessosCriticos`, `selecionarProximosPrazos` (Task 2); `calcularSemaforo` (já existe desde o Plano 2B); `PerfilRepository.observarTodos()`, `FaseRepository.observarTodas()`, `ProcessoRepository.observarTodos()` (já existem).
- Produces: `InicioScreen(onProcessoClick: (String) -> Unit, onVerTodosClick: () -> Unit)` — consumido só por `AppNavHost.kt` nesta mesma task.

- [ ] **Step 1: Criar `InicioViewModel`**

Crie `app/src/main/java/com/josiel/organizeprocesso/ui/inicio/InicioViewModel.kt`:

```kotlin
package com.josiel.organizeprocesso.ui.inicio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import com.josiel.organizeprocesso.data.repository.ProcessoRepository
import com.josiel.organizeprocesso.domain.model.ProcessoResumoDashboard
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import com.josiel.organizeprocesso.domain.usecase.ProgressoGeral
import com.josiel.organizeprocesso.domain.usecase.calcularProgressoGeral
import com.josiel.organizeprocesso.domain.usecase.calcularSemaforo
import com.josiel.organizeprocesso.domain.usecase.selecionarProcessosCriticos
import com.josiel.organizeprocesso.domain.usecase.selecionarProximosPrazos
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class InicioUiState(
    val carregando: Boolean = true,
    val saudacao: String = "",
    val progresso: ProgressoGeral = ProgressoGeral(0, 0),
    val processosAtualizadosHoje: Int = 0,
    val processosCriticos: List<ProcessoResumoDashboard> = emptyList(),
    val proximosPrazos: List<ProcessoResumoDashboard> = emptyList()
)

/** ViewModel do Dashboard Início (spec do Dashboard Início, seções 5-8). */
class InicioViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val processoRepository = ProcessoRepository(database, SupabaseSessionManager.client)
    private val faseRepository = FaseRepository(database.faseDao(), SupabaseSessionManager.client)
    private val perfilRepository = PerfilRepository(database.perfilDao(), SupabaseSessionManager.client)
    private val historicoDao = database.processoFaseHistoricoDao()

    val uiState: StateFlow<InicioUiState> = combine(
        processoRepository.observarTodos(),
        faseRepository.observarTodas(),
        historicoDao.observarTodosAtivos(),
        historicoDao.observarTodos(),
        perfilRepository.observarTodos()
    ) { processos, fases, historicosAtivos, historicoCompleto, perfis ->
        val faseMap = fases.associateBy { it.id }
        val historicoAtivoPorProcesso = historicosAtivos.associateBy { it.processoId }
        val hoje = LocalDate.now()

        val resumos = processos.map { processo ->
            val historicoAtivo = historicoAtivoPorProcesso[processo.id]
            val fase = faseMap[processo.faseAtualId]
            val diasParado = historicoAtivo?.let { ChronoUnit.DAYS.between(it.dataEntrada, hoje) } ?: 0L
            val statusSemaforo = fase?.let { calcularSemaforo(diasParado, it.diasAlertaAtencao, it.diasAlertaCritico) }
                ?: StatusSemaforo.OK
            ProcessoResumoDashboard(
                id = processo.id,
                numero = processo.numero,
                objeto = processo.objeto,
                statusGeral = processo.statusGeral,
                statusSemaforo = statusSemaforo,
                prazoLimite = historicoAtivo?.prazoLimite
            )
        }

        val idsComHistoricoHoje = historicoCompleto.filter { it.dataEntrada == hoje }.map { it.processoId }.toSet()
        val idsComAtualizacaoHoje = processos
            .filter { it.atualizadoEm.atZone(ZoneId.systemDefault()).toLocalDate() == hoje }
            .map { it.id }
            .toSet()

        val nomePerfil = perfis.find { it.id == SupabaseSessionManager.perfilAtual.value?.id }?.nome

        InicioUiState(
            carregando = false,
            saudacao = saudacaoPorHorario() + (nomePerfil?.let { ", $it!" } ?: "!"),
            progresso = calcularProgressoGeral(resumos),
            processosAtualizadosHoje = (idsComHistoricoHoje + idsComAtualizacaoHoje).size,
            processosCriticos = selecionarProcessosCriticos(resumos),
            proximosPrazos = selecionarProximosPrazos(resumos, hoje)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InicioUiState())
}

private fun saudacaoPorHorario(): String {
    val hora = LocalTime.now().hour
    return when {
        hora < 12 -> "Bom dia"
        hora < 18 -> "Boa tarde"
        else -> "Boa noite"
    }
}
```

- [ ] **Step 2: Reescrever `InicioScreen`**

Substitua `app/src/main/java/com/josiel/organizeprocesso/ui/inicio/InicioScreen.kt` (hoje um placeholder) por:

```kotlin
package com.josiel.organizeprocesso.ui.inicio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.josiel.organizeprocesso.domain.model.ProcessoResumoDashboard
import com.josiel.organizeprocesso.domain.usecase.ProgressoGeral
import com.josiel.organizeprocesso.ui.components.PillButton

/** Dashboard Início (spec do Dashboard Início). */
@Composable
fun InicioScreen(
    onProcessoClick: (processoId: String) -> Unit,
    onVerTodosClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InicioViewModel = viewModel()
) {
    val estado by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(estado.saudacao, style = MaterialTheme.typography.headlineSmall)

        CardProgressoGeral(estado.progresso)

        Text(
            "${estado.processosAtualizadosHoje} processo(s) atualizado(s) hoje",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyRow(
            contentPadding = PaddingValues(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CardDestaque(
                    titulo = "Processos críticos",
                    itens = estado.processosCriticos,
                    vazio = "Nenhum processo crítico no momento.",
                    onProcessoClick = onProcessoClick
                )
            }
            item {
                CardDestaque(
                    titulo = "Próximos prazos",
                    itens = estado.proximosPrazos,
                    vazio = "Nenhum prazo próximo cadastrado.",
                    onProcessoClick = onProcessoClick
                )
            }
        }

        PillButton(
            text = "Ver todos os processos",
            onClick = onVerTodosClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CardProgressoGeral(progresso: ProgressoGeral) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Progresso geral", style = MaterialTheme.typography.titleMedium)
            Text(
                "${progresso.emDia} de ${progresso.total} processos em dia",
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { progresso.percentual / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text("${progresso.percentual}%", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CardDestaque(
    titulo: String,
    itens: List<ProcessoResumoDashboard>,
    vazio: String,
    onProcessoClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.width(280.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            if (itens.isEmpty()) {
                Text(
                    vazio,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                itens.forEach { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProcessoClick(item.id) }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            item.numero,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            item.objeto,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Wire a navegação em `AppNavHost.kt`**

Em `app/src/main/java/com/josiel/organizeprocesso/navigation/AppNavHost.kt`, localize:

```kotlin
            composable<Inicio> { InicioScreen() }
```

Substitua por:

```kotlin
            composable<Inicio> {
                InicioScreen(
                    onProcessoClick = { processoId -> navController.navigate(ProcessoDetalhe(processoId)) },
                    onVerTodosClick = { navController.navigate(Processos) }
                )
            }
```

(`ProcessoDetalhe` e `Processos` já estão em uso mais abaixo no mesmo arquivo, dentro de `composable<Processos> { ... }` — nenhum import novo necessário.)

- [ ] **Step 4: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/ui/inicio/InicioViewModel.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/inicio/InicioScreen.kt \
        app/src/main/java/com/josiel/organizeprocesso/navigation/AppNavHost.kt
git commit -m "feat: implement Dashboard Início screen and wire navigation"
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
Expected: `BUILD SUCCESSFUL`, os 12 testes novos da Task 2 passando (4 + 3 + 5), mais os 40 já existentes — 52 no total.

- [ ] **Step 3: Registrar o que fica pendente**

Nenhum teste funcional em emulador/dispositivo real foi executado nesta
sessão (confirmar visualmente que os números do Dashboard batem com a
lista de Processos, testar o clique nos itens dos cards, testar com
zero/poucos/muitos processos). Fica registrado como pendência, não como
bloqueador para fechar este plano.

- [ ] **Step 4: Commit (se necessário)**

Se os Steps 1-2 não exigiram nenhuma mudança de código, não há o que commitar.
