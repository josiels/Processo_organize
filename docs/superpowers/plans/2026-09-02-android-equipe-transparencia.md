# Plano 2C — Equipe e transparência — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adicionar três telas independentes ao app Organize_Processo — gerenciar equipe/contas (admin), Fila de Distribuição (todos), configurações pessoais de notificação (todos) — sem tocar nenhuma tela já existente.

**Architecture:** Continua MVVM + Repository. Duas frentes novas de dado: (1) escrita via Edge Function `criar-conta` (não Postgrest direto — criação de conta exige a service role key, que nunca fica no app) usando o módulo `functions-kt` do supabase-kt, ainda não integrado ao projeto; (2) leitura ao vivo, sem cache Room, das views `fila_distribuicao`/`fila_distribuicao_por_tipo` (já existem no backend, mesclado hoje) — reforça a decisão já tomada em 2A §2.2 de que nem tudo precisa de cache local. Preferências de notificação usam o padrão já estabelecido (`PerfilRepository` + Postgrest `update`).

**Tech Stack:** Kotlin, Jetpack Compose, supabase-kt 3.5.0 (postgrest-kt, auth-kt, realtime-kt já presentes; functions-kt novo nesta etapa), kotlinx.serialization, JUnit4.

**Spec:** `docs/superpowers/specs/2026-09-01-android-equipe-transparencia-design.md`

## Global Constraints

- Trabalhar no worktree `C:\Users\03557061485\AndroidStudioProjects\Organize_Processo\.claude\worktrees\plano2c-equipe-transparencia` (branch `plano2c-equipe-transparencia`, criada a partir da `master` já mesclada com os Planos 1/2A/2B).
- `JAVA_HOME` para todo comando gradle: `C:\Program Files\Android\Android Studio\jbr`.
- Verificação de compilação por task: `./gradlew.bat :app:compileDebugKotlin`.
- **As views `fila_distribuicao`/`fila_distribuicao_por_tipo` do backend JÁ EXISTEM** — `supabase/migrations/20260901094236_criar_views_fila_distribuicao.sql`, já commitado, `security_invoker = true` em ambas (confirmado por leitura direta do arquivo — sem isso a view vazaria dados entre organizações, ver o comentário na spec seção 2). **Nenhuma migration nova é necessária nesta etapa.** Pendência que fica para a Task 9 (verificação final): confirmar que a migration já foi de fato aplicada no projeto Supabase real (via `node scripts/run_sql.mjs`, que exige `supabase/.env.local` com `SUPABASE_PROJECT_REF`/`SUPABASE_ACCESS_TOKEN` — esse arquivo não existe neste worktree; se quem for rodar a Task 9 tiver essas credenciais, use-as para uma consulta de leitura simples, ex. `select viewname from pg_views where viewname like 'fila_distribuicao%';`, antes de testar a tela em dispositivo).
- Criação de organização (super_admin) e desativação de conta **não** ganham tela nesta etapa (spec §3 e §7, decisão já tomada) — não inventar isso.
- `MENSAGEM_ERRO_GENERICA`/`MENSAGEM_SESSAO_AUSENTE`/`mensagemDeErro(e: Exception)` já existem em `app/src/main/java/com/josiel/organizeprocesso/ui/common/MensagemErro.kt` (do Plano 2B) — todo novo caminho de escrita desta etapa reutiliza esse helper, não reinventa formatação de erro. Padrão de try/catch: sempre `catch (e: Exception) { if (e is CancellationException) throw e; ... }` (import `kotlinx.coroutines.CancellationException`), nunca engolir cancelamento.
- Ícones: este projeto só depende de `androidx.compose.material.icons.core` (conjunto limitado, não o pacote estendido). Só usar ícones já comprovadamente em uso em algum lugar do código atual — `Icons.Filled.Person` (`ComponentsShowcase.kt`, `IconCircle.kt`) e `Icons.AutoMirrored.Filled.List` (`AppBottomBar.kt`, `MaisScreen.kt`) já são seguros; não inventar nomes novos de ícone sem confirmar que já aparecem em algum arquivo `.kt` existente.
- Cards admin-only em `MaisScreen` são **escondidos** para quem não é admin (não desabilitados com "Em breve" — bug já corrigido no Plano 2B, seção final de revisão).
- Nunca usar `DropdownField` dentro de `AlertDialog`/`Dialog` (regra do projeto, ver Plano 2B §5) — não se aplica diretamente aqui (a tela de Criar Conta é tela cheia, não diálogo), mas o formulário usa `RadioButton` para o papel de qualquer forma, por decisão explícita da spec (§3), não por essa regra.
- Fila de Distribuição **não é cacheada no Room** (spec §4, reforça 2A §2.2) — é a única tela do app com esse padrão; não criar Entity/Dao para ela.

---

## Task 1: Adicionar a dependência `functions-kt` e instalar o plugin `Functions`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/remote/SupabaseSessionManager.kt`

**Interfaces:**
- Produces: `SupabaseSessionManager.client.functions` disponível para chamadas de Edge Function — usado pela Task 7.

- [ ] **Step 1: Adicionar a entrada no catálogo de versões**

Em `gradle/libs.versions.toml`, na seção `[libraries]`, logo após `supabase-realtime-kt`:

```toml
supabase-functions-kt = { group = "io.github.jan-tennert.supabase", name = "functions-kt" }
```

(Sem `version.ref` — todos os módulos supabase-kt neste projeto vêm do `platform(libs.supabase.bom)` já declarado, mesmo padrão de `postgrest-kt`/`auth-kt`/`storage-kt`/`realtime-kt`.)

- [ ] **Step 2: Adicionar a dependência**

Em `app/build.gradle.kts`, no bloco `dependencies`, logo após `implementation(libs.supabase.realtime.kt)`:

```kotlin
    implementation(libs.supabase.functions.kt)
```

- [ ] **Step 3: Instalar o plugin no client**

Em `SupabaseSessionManager.kt`, adicione o import `io.github.jan.supabase.functions.Functions` e `install(Functions)` no builder do `createSupabaseClient`:

```kotlin
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Functions)
        }
    }
```

- [ ] **Step 4: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/josiel/organizeprocesso/data/remote/SupabaseSessionManager.kt
git commit -m "chore: add functions-kt dependency for Edge Function calls"
```

---

## Task 2: `FilaDistribuicaoItem` + `ordenarFilaDistribuicao` — função pura, com testes (TDD)

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/domain/model/FilaDistribuicaoItem.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/domain/usecase/OrdenarFilaDistribuicao.kt`
- Test: `app/src/test/java/com/josiel/organizeprocesso/domain/usecase/OrdenarFilaDistribuicaoTest.kt`

**Interfaces:**
- Produces: `data class FilaDistribuicaoItem(perfilId: String, nome: String, ultimoRecebimentoEm: Instant?, criadoEm: Instant, totalDesignacoes: Long)` e `fun ordenarFilaDistribuicao(itens: List<FilaDistribuicaoItem>): List<FilaDistribuicaoItem>` — usados pela Task 3 (`FilaDistribuicaoRepository`).

- [ ] **Step 1: Criar o modelo de domínio**

```kotlin
package com.josiel.organizeprocesso.domain.model

import java.time.Instant

/** Uma linha da Fila de Distribuição (spec do Plano 2C, seção 4) — já resolvida para exibição. */
data class FilaDistribuicaoItem(
    val perfilId: String,
    val nome: String,
    val ultimoRecebimentoEm: Instant?,
    val criadoEm: Instant,
    val totalDesignacoes: Long
)
```

- [ ] **Step 2: Escrever o teste (falhando)**

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.FilaDistribuicaoItem
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class OrdenarFilaDistribuicaoTest {
    private fun item(nome: String, ultimoRecebimentoEm: Instant?, criadoEm: Instant) = FilaDistribuicaoItem(
        perfilId = nome,
        nome = nome,
        ultimoRecebimentoEm = ultimoRecebimentoEm,
        criadoEm = criadoEm,
        totalDesignacoes = 0
    )

    @Test
    fun `quem nunca recebeu vem antes de quem ja recebeu`() {
        val jaRecebeu = item("Ana", ultimoRecebimentoEm = Instant.parse("2026-09-01T00:00:00Z"), criadoEm = Instant.parse("2026-01-01T00:00:00Z"))
        val nuncaRecebeu = item("Bruno", ultimoRecebimentoEm = null, criadoEm = Instant.parse("2026-06-01T00:00:00Z"))

        val ordenado = ordenarFilaDistribuicao(listOf(jaRecebeu, nuncaRecebeu))

        assertEquals(listOf("Bruno", "Ana"), ordenado.map { it.nome })
    }

    @Test
    fun `entre quem nunca recebeu, ordena por data de criacao da conta`() {
        val maisNovo = item("Carla", ultimoRecebimentoEm = null, criadoEm = Instant.parse("2026-08-01T00:00:00Z"))
        val maisAntigo = item("Diego", ultimoRecebimentoEm = null, criadoEm = Instant.parse("2026-01-01T00:00:00Z"))

        val ordenado = ordenarFilaDistribuicao(listOf(maisNovo, maisAntigo))

        assertEquals(listOf("Diego", "Carla"), ordenado.map { it.nome })
    }

    @Test
    fun `entre quem ja recebeu, ordena por ultimo recebimento ascendente`() {
        val recebeuRecente = item("Elis", ultimoRecebimentoEm = Instant.parse("2026-09-01T00:00:00Z"), criadoEm = Instant.parse("2026-01-01T00:00:00Z"))
        val recebeuAntigo = item("Fabio", ultimoRecebimentoEm = Instant.parse("2026-02-01T00:00:00Z"), criadoEm = Instant.parse("2026-01-01T00:00:00Z"))

        val ordenado = ordenarFilaDistribuicao(listOf(recebeuRecente, recebeuAntigo))

        assertEquals(listOf("Fabio", "Elis"), ordenado.map { it.nome })
    }

    @Test
    fun `caso combinado com todos os grupos`() {
        val nuncaRecebeuAntigo = item("Nunca-Antigo", ultimoRecebimentoEm = null, criadoEm = Instant.parse("2026-01-01T00:00:00Z"))
        val nuncaRecebeuNovo = item("Nunca-Novo", ultimoRecebimentoEm = null, criadoEm = Instant.parse("2026-06-01T00:00:00Z"))
        val recebeuAntigo = item("Recebeu-Antigo", ultimoRecebimentoEm = Instant.parse("2026-03-01T00:00:00Z"), criadoEm = Instant.parse("2026-01-01T00:00:00Z"))
        val recebeuNovo = item("Recebeu-Novo", ultimoRecebimentoEm = Instant.parse("2026-08-01T00:00:00Z"), criadoEm = Instant.parse("2026-01-01T00:00:00Z"))

        val ordenado = ordenarFilaDistribuicao(listOf(recebeuNovo, nuncaRecebeuNovo, recebeuAntigo, nuncaRecebeuAntigo))

        assertEquals(
            listOf("Nunca-Antigo", "Nunca-Novo", "Recebeu-Antigo", "Recebeu-Novo"),
            ordenado.map { it.nome }
        )
    }
}
```

- [ ] **Step 3: Rodar o teste, confirmar que falha**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.OrdenarFilaDistribuicaoTest"`
Expected: FAIL (função `ordenarFilaDistribuicao` não existe ainda).

- [ ] **Step 4: Implementar a função**

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.FilaDistribuicaoItem

/**
 * Ordena a Fila de Distribuição: quem está há mais tempo sem receber
 * processo aparece primeiro. Quem nunca recebeu nada (`ultimoRecebimentoEm
 * == null`) vem sempre antes de quem já recebeu, ordenado entre si pela
 * data de criação da conta (spec do Plano 2C, seção 4).
 */
fun ordenarFilaDistribuicao(itens: List<FilaDistribuicaoItem>): List<FilaDistribuicaoItem> =
    itens.sortedWith(
        compareBy(
            { it.ultimoRecebimentoEm != null },
            { it.ultimoRecebimentoEm ?: it.criadoEm }
        )
    )
```

- [ ] **Step 5: Rodar o teste, confirmar que passa**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.OrdenarFilaDistribuicaoTest"`
Expected: PASS (4 testes).

- [ ] **Step 6: Verificar que o projeto inteiro compila e os testes passam**

Run: `./gradlew.bat :app:compileDebugKotlin`
Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` nos dois.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/domain/model/FilaDistribuicaoItem.kt \
        app/src/main/java/com/josiel/organizeprocesso/domain/usecase/OrdenarFilaDistribuicao.kt \
        app/src/test/java/com/josiel/organizeprocesso/domain/usecase/OrdenarFilaDistribuicaoTest.kt
git commit -m "feat: add FilaDistribuicaoItem and ordenarFilaDistribuicao usecase"
```

---

## Task 3: Camada de dados da Fila de Distribuição (sem Room — consulta ao vivo)

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/FilaDistribuicaoDto.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/FilaDistribuicaoPorTipoDto.kt`
- Test: `app/src/test/java/com/josiel/organizeprocesso/data/remote/dto/FilaDistribuicaoDtoMappingTest.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/repository/FilaDistribuicaoRepository.kt`

**Interfaces:**
- Consumes: `ordenarFilaDistribuicao` (Task 2).
- Produces: `FilaDistribuicaoRepository(client).listar(): List<FilaDistribuicaoItem>` e `.listarPorTipo(perfilId: String): List<FilaDistribuicaoPorTipoDto>` — usados pela Task 4.

`public.fila_distribuicao` tem estas 6 colunas (`supabase/migrations/20260901094236_criar_views_fila_distribuicao.sql`): `perfil_id, organizacao_id, nome, ultimo_recebimento_em, criado_em, total_designacoes`. `public.fila_distribuicao_por_tipo` tem estas 5: `perfil_id, organizacao_id, tipo_processo_id, tipo_processo_nome, total`.

- [ ] **Step 1: Escrever o teste de mapeamento do DTO principal (falhando)**

```kotlin
package com.josiel.organizeprocesso.data.remote.dto

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilaDistribuicaoDtoMappingTest {
    @Test
    fun `mapeia dto para item de dominio preservando todos os campos`() {
        val dto = FilaDistribuicaoDto(
            perfilId = "p1",
            organizacaoId = "org1",
            nome = "Ana",
            ultimoRecebimentoEm = "2026-09-01T10:00:00Z",
            criadoEm = "2026-01-01T00:00:00Z",
            totalDesignacoes = 3
        )

        val item = dto.paraItem()

        assertEquals("p1", item.perfilId)
        assertEquals("Ana", item.nome)
        assertEquals(Instant.parse("2026-09-01T10:00:00Z"), item.ultimoRecebimentoEm)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), item.criadoEm)
        assertEquals(3L, item.totalDesignacoes)
    }

    @Test
    fun `ultimo recebimento nulo mapeia para null`() {
        val dto = FilaDistribuicaoDto(
            perfilId = "p2",
            organizacaoId = "org1",
            nome = "Bruno",
            ultimoRecebimentoEm = null,
            criadoEm = "2026-01-01T00:00:00Z",
            totalDesignacoes = 0
        )

        assertNull(dto.paraItem().ultimoRecebimentoEm)
    }
}
```

- [ ] **Step 2: Rodar o teste, confirmar que falha**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.data.remote.dto.FilaDistribuicaoDtoMappingTest"`
Expected: FAIL (`FilaDistribuicaoDto` não existe).

- [ ] **Step 3: Criar os dois DTOs**

```kotlin
package com.josiel.organizeprocesso.data.remote.dto

import com.josiel.organizeprocesso.domain.model.FilaDistribuicaoItem
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Espelha a view `public.fila_distribuicao` — não é cacheada no Room, só consultada ao vivo. */
@Serializable
data class FilaDistribuicaoDto(
    @SerialName("perfil_id") val perfilId: String,
    @SerialName("organizacao_id") val organizacaoId: String?,
    val nome: String,
    @SerialName("ultimo_recebimento_em") val ultimoRecebimentoEm: String?,
    @SerialName("criado_em") val criadoEm: String,
    @SerialName("total_designacoes") val totalDesignacoes: Long
)

fun FilaDistribuicaoDto.paraItem(): FilaDistribuicaoItem = FilaDistribuicaoItem(
    perfilId = perfilId,
    nome = nome,
    ultimoRecebimentoEm = ultimoRecebimentoEm?.let(Instant::parse),
    criadoEm = Instant.parse(criadoEm),
    totalDesignacoes = totalDesignacoes
)
```

```kotlin
package com.josiel.organizeprocesso.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Espelha a view `public.fila_distribuicao_por_tipo` — detalhe por tipo de processo de uma pessoa. */
@Serializable
data class FilaDistribuicaoPorTipoDto(
    @SerialName("perfil_id") val perfilId: String,
    @SerialName("organizacao_id") val organizacaoId: String?,
    @SerialName("tipo_processo_id") val tipoProcessoId: String,
    @SerialName("tipo_processo_nome") val tipoProcessoNome: String,
    val total: Long
)
```

- [ ] **Step 4: Rodar o teste, confirmar que passa**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.data.remote.dto.FilaDistribuicaoDtoMappingTest"`
Expected: PASS (2 testes).

- [ ] **Step 5: Criar o repositório**

```kotlin
package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.remote.dto.FilaDistribuicaoDto
import com.josiel.organizeprocesso.data.remote.dto.FilaDistribuicaoPorTipoDto
import com.josiel.organizeprocesso.data.remote.dto.paraItem
import com.josiel.organizeprocesso.domain.model.FilaDistribuicaoItem
import com.josiel.organizeprocesso.domain.usecase.ordenarFilaDistribuicao
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

/**
 * Não é cacheada no Room (spec do Plano 2C, seção 4, reforça 2A seção 2.2)
 * — consulta de rede ao vivo toda vez que a tela abre.
 */
class FilaDistribuicaoRepository(private val client: SupabaseClient) {
    suspend fun listar(): List<FilaDistribuicaoItem> {
        val dtos = client.postgrest["fila_distribuicao"].select().decodeList<FilaDistribuicaoDto>()
        return ordenarFilaDistribuicao(dtos.map { it.paraItem() })
    }

    suspend fun listarPorTipo(perfilId: String): List<FilaDistribuicaoPorTipoDto> =
        client.postgrest["fila_distribuicao_por_tipo"].select {
            filter { eq("perfil_id", perfilId) }
        }.decodeList()
}
```

- [ ] **Step 6: Verificar que o projeto compila e os testes passam**

Run: `./gradlew.bat :app:compileDebugKotlin`
Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` nos dois.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/FilaDistribuicaoDto.kt \
        app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/FilaDistribuicaoPorTipoDto.kt \
        app/src/test/java/com/josiel/organizeprocesso/data/remote/dto/FilaDistribuicaoDtoMappingTest.kt \
        app/src/main/java/com/josiel/organizeprocesso/data/repository/FilaDistribuicaoRepository.kt
git commit -m "feat: add FilaDistribuicaoRepository (live query, no Room cache)"
```

---

## Task 4: Telas da Fila de Distribuição (lista + detalhe)

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/ui/filadistribuicao/FilaDistribuicaoViewModel.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/ui/filadistribuicao/FilaDistribuicaoScreen.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/ui/filadistribuicao/FilaDistribuicaoDetalheViewModel.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/ui/filadistribuicao/FilaDistribuicaoDetalheScreen.kt`

**Interfaces:**
- Consumes: `FilaDistribuicaoRepository` (Task 3), `mensagemDeErro` (já existe).
- Produces: `FilaDistribuicaoScreen(onPessoaClick: (perfilId: String, nome: String) -> Unit, onBackClick: () -> Unit)` e `FilaDistribuicaoDetalheScreen(perfilId: String, nomePessoa: String, onBackClick: () -> Unit)` — usadas pela Task 8.

Sem tests unitários nesta task (Compose/AndroidViewModel, mesma convenção já estabelecida no projeto).

- [ ] **Step 1: Criar `FilaDistribuicaoViewModel`**

```kotlin
package com.josiel.organizeprocesso.ui.filadistribuicao

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FilaDistribuicaoRepository
import com.josiel.organizeprocesso.domain.model.FilaDistribuicaoItem
import com.josiel.organizeprocesso.ui.common.mensagemDeErro
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FilaDistribuicaoUiState(
    val carregando: Boolean = true,
    val itens: List<FilaDistribuicaoItem> = emptyList(),
    val erro: String? = null
)

/** Fila de Distribuição (spec do Plano 2C, seção 4) — consulta ao vivo, sem cache Room. */
class FilaDistribuicaoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FilaDistribuicaoRepository(SupabaseSessionManager.client)

    private val _uiState = MutableStateFlow(FilaDistribuicaoUiState())
    val uiState: StateFlow<FilaDistribuicaoUiState> = _uiState.asStateFlow()

    init {
        carregar()
    }

    fun carregar() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(carregando = true, erro = null)
            try {
                val itens = repository.listar()
                _uiState.value = _uiState.value.copy(carregando = false, itens = itens)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(carregando = false, erro = mensagemDeErro(e))
            }
        }
    }
}
```

- [ ] **Step 2: Criar `FilaDistribuicaoScreen`**

```kotlin
package com.josiel.organizeprocesso.ui.filadistribuicao

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.josiel.organizeprocesso.domain.model.FilaDistribuicaoItem
import com.josiel.organizeprocesso.ui.components.NavigationChevron
import com.josiel.organizeprocesso.ui.components.SideBarCard
import com.josiel.organizeprocesso.ui.components.StatusPill
import com.josiel.organizeprocesso.ui.theme.Indigo600
import com.josiel.organizeprocesso.ui.theme.IndigoPastel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Fila de Distribuição (spec do Plano 2C, seção 4) — transparência de carga de trabalho, visível a todos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilaDistribuicaoScreen(
    onPessoaClick: (perfilId: String, nome: String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: FilaDistribuicaoViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FilaDistribuicaoViewModel(application) }
        }
    )
    val estado by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Fila de Distribuição") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        when {
            estado.carregando -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            estado.erro != null -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(estado.erro!!, color = MaterialTheme.colorScheme.error)
            }
            estado.itens.isEmpty() -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nenhuma pessoa ativa na organização ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(estado.itens, key = { it.perfilId }) { item ->
                    FilaDistribuicaoCard(item = item, onClick = { onPessoaClick(item.perfilId, item.nome) })
                }
            }
        }
    }
}

@Composable
private fun FilaDistribuicaoCard(item: FilaDistribuicaoItem, onClick: () -> Unit) {
    SideBarCard(
        barColor = Indigo600,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.nome, style = MaterialTheme.typography.titleMedium)
            Text(
                if (item.ultimoRecebimentoEm == null) {
                    "Nunca recebeu um processo"
                } else {
                    "Último recebimento: ${formatoData.format(item.ultimoRecebimentoEm.atZone(ZoneId.systemDefault()))}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusPill(
                text = "${item.totalDesignacoes} designações",
                containerColor = IndigoPastel,
                contentColor = Indigo600,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        NavigationChevron()
    }
}
```

- [ ] **Step 3: Criar `FilaDistribuicaoDetalheViewModel`**

```kotlin
package com.josiel.organizeprocesso.ui.filadistribuicao

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.remote.dto.FilaDistribuicaoPorTipoDto
import com.josiel.organizeprocesso.data.repository.FilaDistribuicaoRepository
import com.josiel.organizeprocesso.ui.common.mensagemDeErro
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FilaDistribuicaoDetalheUiState(
    val carregando: Boolean = true,
    val itens: List<FilaDistribuicaoPorTipoDto> = emptyList(),
    val erro: String? = null
)

class FilaDistribuicaoDetalheViewModel(
    application: Application,
    private val perfilId: String
) : AndroidViewModel(application) {
    private val repository = FilaDistribuicaoRepository(SupabaseSessionManager.client)

    private val _uiState = MutableStateFlow(FilaDistribuicaoDetalheUiState())
    val uiState: StateFlow<FilaDistribuicaoDetalheUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val itens = repository.listarPorTipo(perfilId)
                _uiState.value = _uiState.value.copy(carregando = false, itens = itens)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(carregando = false, erro = mensagemDeErro(e))
            }
        }
    }
}
```

- [ ] **Step 4: Criar `FilaDistribuicaoDetalheScreen`**

```kotlin
package com.josiel.organizeprocesso.ui.filadistribuicao

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

/** Detalhe da Fila de Distribuição por tipo de processo (spec do Plano 2C, seção 4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilaDistribuicaoDetalheScreen(
    perfilId: String,
    nomePessoa: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: FilaDistribuicaoDetalheViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FilaDistribuicaoDetalheViewModel(application, perfilId) }
        }
    )
    val estado by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(nomePessoa) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        when {
            estado.carregando -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            estado.erro != null -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(estado.erro!!, color = MaterialTheme.colorScheme.error)
            }
            estado.itens.isEmpty() -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nenhuma designação registrada para esta pessoa ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(estado.itens, key = { it.tipoProcessoId }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.tipoProcessoNome, style = MaterialTheme.typography.bodyLarge)
                        Text("${item.total}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 5: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/ui/filadistribuicao/
git commit -m "feat: add Fila de Distribuicao list and detail screens"
```

---

## Task 5: Configurações de notificação

**Files:**
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/repository/PerfilRepository.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/ui/configuracoes/ConfiguracoesViewModel.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/ui/configuracoes/ConfiguracoesScreen.kt`

**Interfaces:**
- Produces: `PerfilRepository.atualizarPreferenciasNotificacao(perfilId, notificarAvancoFase, notificarPrazo, notificarTempoParado)`. `ConfiguracoesScreen(onBackClick: () -> Unit)` — usada pela Task 8, que também habilita o card "Configurações" em `MaisScreen`.

- [ ] **Step 1: Adicionar o método ao `PerfilRepository`**

`perfis_update_propria_conta` (RLS) permite `update` na própria linha (`id = auth.uid()`); o trigger `perfis_proteger_campos` só bloqueia `organizacao_id`/`papel`/`ativo`/`ultimo_recebimento_em` — os três campos de notificação não são protegidos, então esta escrita é permitida sem conflito (confirmado lendo `supabase/migrations/20260901011724_criar_organizacoes_perfis.sql`).

Em `PerfilRepository.kt`, adicione os imports `kotlinx.serialization.json.buildJsonObject` e `kotlinx.serialization.json.put`, e o método:

```kotlin
    suspend fun atualizarPreferenciasNotificacao(
        perfilId: String,
        notificarAvancoFase: Boolean,
        notificarPrazo: Boolean,
        notificarTempoParado: Boolean
    ) {
        val linha = buildJsonObject {
            put("notificar_avanco_fase", notificarAvancoFase)
            put("notificar_prazo", notificarPrazo)
            put("notificar_tempo_parado", notificarTempoParado)
        }
        client.postgrest["perfis"].update(linha) {
            filter { eq("id", perfilId) }
        }
        sincronizar()
    }
```

- [ ] **Step 2: Criar `ConfiguracoesViewModel`**

```kotlin
package com.josiel.organizeprocesso.ui.configuracoes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import com.josiel.organizeprocesso.ui.common.MENSAGEM_SESSAO_AUSENTE
import com.josiel.organizeprocesso.ui.common.mensagemDeErro
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ConfiguracoesUiState(
    val carregando: Boolean = true,
    val notificarAvancoFase: Boolean = true,
    val notificarPrazo: Boolean = true,
    val notificarTempoParado: Boolean = true,
    val erro: String? = null
)

/** Configurações pessoais de notificação (spec do Plano 2C, seção 5). */
class ConfiguracoesViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val perfilRepository = PerfilRepository(database.perfilDao(), SupabaseSessionManager.client)

    private val _uiState = MutableStateFlow(ConfiguracoesUiState())
    val uiState: StateFlow<ConfiguracoesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val meuId = SupabaseSessionManager.perfilAtual.value?.id
            val perfil = perfilRepository.observarTodos().first().find { it.id == meuId }
            _uiState.value = if (perfil != null) {
                ConfiguracoesUiState(
                    carregando = false,
                    notificarAvancoFase = perfil.notificarAvancoFase,
                    notificarPrazo = perfil.notificarPrazo,
                    notificarTempoParado = perfil.notificarTempoParado
                )
            } else {
                _uiState.value.copy(carregando = false)
            }
        }
    }

    fun atualizarNotificarAvancoFase(valor: Boolean) = salvar(_uiState.value.copy(notificarAvancoFase = valor))
    fun atualizarNotificarPrazo(valor: Boolean) = salvar(_uiState.value.copy(notificarPrazo = valor))
    fun atualizarNotificarTempoParado(valor: Boolean) = salvar(_uiState.value.copy(notificarTempoParado = valor))

    private fun salvar(novoEstado: ConfiguracoesUiState) {
        val perfilId = SupabaseSessionManager.perfilAtual.value?.id
        if (perfilId == null) {
            _uiState.value = novoEstado.copy(erro = MENSAGEM_SESSAO_AUSENTE)
            return
        }
        _uiState.value = novoEstado.copy(erro = null)
        viewModelScope.launch {
            try {
                perfilRepository.atualizarPreferenciasNotificacao(
                    perfilId = perfilId,
                    notificarAvancoFase = novoEstado.notificarAvancoFase,
                    notificarPrazo = novoEstado.notificarPrazo,
                    notificarTempoParado = novoEstado.notificarTempoParado
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(erro = mensagemDeErro(e))
            }
        }
    }
}
```

- [ ] **Step 3: Criar `ConfiguracoesScreen`**

```kotlin
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
```

- [ ] **Step 4: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/data/repository/PerfilRepository.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/configuracoes/
git commit -m "feat: add notification preferences screen"
```

---

## Task 6: Tela "Equipe" (admin, só leitura)

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/ui/equipe/EquipeViewModel.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/ui/equipe/EquipeScreen.kt`

**Interfaces:**
- Consumes: `PerfilRepository` (já existe).
- Produces: `EquipeScreen(onCriarContaClick: () -> Unit, onBackClick: () -> Unit)` — usada pela Task 8.

Só leitura por decisão explícita da spec (§3) — não existe endpoint de desativação de conta ainda, então não há ação nenhuma no card além de visualizar. Sem `.clickable`, sem `NavigationChevron`.

- [ ] **Step 1: Criar `EquipeViewModel`**

```kotlin
package com.josiel.organizeprocesso.ui.equipe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.PerfilEntity
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Gerenciar equipe/contas (spec do Plano 2C, seção 3) — admin-only, só leitura. */
class EquipeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PerfilRepository(
        dao = AppDatabase.getInstance(application).perfilDao(),
        client = SupabaseSessionManager.client
    )

    val perfis: StateFlow<List<PerfilEntity>> = repository.observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
```

- [ ] **Step 2: Criar `EquipeScreen`**

```kotlin
package com.josiel.organizeprocesso.ui.equipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.josiel.organizeprocesso.data.local.PerfilEntity
import com.josiel.organizeprocesso.domain.model.Papel
import com.josiel.organizeprocesso.ui.components.PillButton
import com.josiel.organizeprocesso.ui.components.SideBarCard
import com.josiel.organizeprocesso.ui.components.StatusPill
import com.josiel.organizeprocesso.ui.theme.AmareloAtencao
import com.josiel.organizeprocesso.ui.theme.AmareloAtencaoPastel
import com.josiel.organizeprocesso.ui.theme.Indigo600
import com.josiel.organizeprocesso.ui.theme.IndigoPastel
import com.josiel.organizeprocesso.ui.theme.VerdeOk
import com.josiel.organizeprocesso.ui.theme.VerdeOkPastel
import com.josiel.organizeprocesso.ui.theme.VermelhoCritico
import com.josiel.organizeprocesso.ui.theme.VermelhoCriticoPastel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipeScreen(
    onCriarContaClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EquipeViewModel = viewModel()
) {
    val perfis by viewModel.perfis.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Equipe") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (perfis.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhuma conta cadastrada ainda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(perfis, key = { it.id }) { perfil -> PerfilCard(perfil) }
                }
            }

            PillButton(
                text = "+ Nova conta",
                onClick = onCriarContaClick,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
    }
}

@Composable
private fun PerfilCard(perfil: PerfilEntity) {
    val (corBarra, rotuloPapel) = when (perfil.papel) {
        Papel.ADMIN -> Indigo600 to "Admin"
        Papel.USUARIO -> AmareloAtencao to "Usuário"
        Papel.SUPER_ADMIN -> Indigo600 to "Super admin"
    }
    SideBarCard(barColor = corBarra) {
        Column(modifier = Modifier.weight(1f)) {
            Text(perfil.nome, style = MaterialTheme.typography.titleMedium)
            if (!perfil.cargoSetor.isNullOrBlank()) {
                Text(
                    perfil.cargoSetor,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill(
                    text = rotuloPapel,
                    containerColor = if (perfil.papel == Papel.USUARIO) AmareloAtencaoPastel else IndigoPastel,
                    contentColor = corBarra
                )
                StatusPill(
                    text = if (perfil.ativo) "Ativo" else "Inativo",
                    containerColor = if (perfil.ativo) VerdeOkPastel else VermelhoCriticoPastel,
                    contentColor = if (perfil.ativo) VerdeOk else VermelhoCritico
                )
            }
        }
    }
}
```

- [ ] **Step 3: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/ui/equipe/EquipeViewModel.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/equipe/EquipeScreen.kt
git commit -m "feat: add read-only Equipe screen"
```

---

## Task 7: Tela "Criar Conta" (formulário de tela cheia, chama a Edge Function)

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/ui/equipe/CriarContaViewModel.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/ui/equipe/CriarContaScreen.kt`

**Interfaces:**
- Consumes: `client.functions.invoke(...)` (Task 1), `PerfilRepository.sincronizar()` (já existe).
- Produces: `CriarContaScreen(onBackClick: () -> Unit, onCriado: () -> Unit)` — usada pela Task 8.

A Edge Function `criar-conta` (`supabase/functions/criar-conta/index.ts`, já implantada) exige `Authorization` com o JWT de quem chama — o SDK já anexa isso automaticamente porque o mesmo `client` autenticado é usado. Espera um corpo `{ nome, email, senha, papel }` (`papel` só aceita `"admin"`/`"usuario"`, valida no próprio servidor) e responde `{ perfil_id }` em sucesso, ou `{ error }` com status 400/401/403/500 — inclusive `"Apenas admin pode criar contas"` se quem chama não for admin ativo (a UI só *sugere* isso escondendo a tela de quem não é admin — a validação real é no servidor).

- [ ] **Step 1: Criar `CriarContaViewModel`**

```kotlin
package com.josiel.organizeprocesso.ui.equipe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import com.josiel.organizeprocesso.domain.model.Papel
import com.josiel.organizeprocesso.ui.common.mensagemDeErro
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CriarContaUiState(
    val nome: String = "",
    val email: String = "",
    val senha: String = "",
    val papel: Papel = Papel.USUARIO,
    val salvando: Boolean = false,
    val erro: String? = null
) {
    val valido: Boolean
        get() = nome.isNotBlank() && email.isNotBlank() && senha.length >= 6
}

/** Formulário de criação de conta (spec do Plano 2C, seção 3) — chama a Edge Function `criar-conta`. */
class CriarContaViewModel(application: Application) : AndroidViewModel(application) {
    private val perfilRepository = PerfilRepository(
        dao = AppDatabase.getInstance(application).perfilDao(),
        client = SupabaseSessionManager.client
    )

    private val _uiState = MutableStateFlow(CriarContaUiState())
    val uiState: StateFlow<CriarContaUiState> = _uiState.asStateFlow()

    fun atualizarNome(valor: String) { _uiState.value = _uiState.value.copy(nome = valor) }
    fun atualizarEmail(valor: String) { _uiState.value = _uiState.value.copy(email = valor) }
    fun atualizarSenha(valor: String) { _uiState.value = _uiState.value.copy(senha = valor) }
    fun atualizarPapel(valor: Papel) { _uiState.value = _uiState.value.copy(papel = valor) }

    fun criar(onCriado: () -> Unit) {
        val estado = _uiState.value
        if (!estado.valido || estado.salvando) return
        _uiState.value = estado.copy(salvando = true, erro = null)
        viewModelScope.launch {
            try {
                SupabaseSessionManager.client.functions.invoke(
                    "criar-conta",
                    body = buildJsonObject {
                        put("nome", estado.nome.trim())
                        put("email", estado.email.trim())
                        put("senha", estado.senha)
                        put("papel", estado.papel.name.lowercase())
                    }
                )
                perfilRepository.sincronizar()
                _uiState.value = _uiState.value.copy(salvando = false)
                onCriado()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(salvando = false, erro = mensagemDeErro(e))
            }
        }
    }
}
```

- [ ] **Step 2: Criar `CriarContaScreen`**

```kotlin
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
```

- [ ] **Step 3: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Se `client.functions.invoke(...)` não compilar com essa assinatura exata (a documentação pública do supabase-kt 3.5.0 não deixa 100% claro o nome do parâmetro de corpo), ajuste conforme o erro do compilador indicar — a forma de chamar pode variar ligeiramente entre versões menores do SDK; o import `io.github.jan.supabase.functions.functions` e o nome da função (`"criar-conta"`) são o que precisa bater com o backend.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/ui/equipe/CriarContaViewModel.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/equipe/CriarContaScreen.kt
git commit -m "feat: add Criar Conta screen calling criar-conta Edge Function"
```

---

## Task 8: Ligar rotas, `AppNavHost` e os novos cards de `MaisScreen`

**Files:**
- Modify: `app/src/main/java/com/josiel/organizeprocesso/navigation/Routes.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/navigation/AppNavHost.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/MaisScreen.kt`

- [ ] **Step 1: Adicionar as rotas**

Em `Routes.kt`, depois de `CadastroTiposProcesso`:

```kotlin
@Serializable
object Equipe

@Serializable
object CriarConta

@Serializable
object Configuracoes

@Serializable
object FilaDistribuicao

@Serializable
data class FilaDistribuicaoDetalhe(val perfilId: String, val nome: String)
```

- [ ] **Step 2: Atualizar `MaisScreen`**

Adicione os imports `androidx.compose.material.icons.filled.Person` e `com.josiel.organizeprocesso.ui.theme.*` (se ainda não estiverem todos presentes — o arquivo já importa `Indigo600`/`IndigoPastel`). Troque a assinatura e a lista `opcoes`:

```kotlin
@Composable
fun MaisScreen(
    onCadastroFasesClick: () -> Unit,
    onCadastroTiposProcessoClick: () -> Unit,
    onEquipeClick: () -> Unit,
    onFilaDistribuicaoClick: () -> Unit,
    onConfiguracoesClick: () -> Unit,
    onSairClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val perfil by SupabaseSessionManager.perfilAtual.collectAsState()
    val ehAdmin = perfil?.papel == Papel.ADMIN
    val opcoes = buildList {
        if (ehAdmin) {
            add(OpcaoMais("Cadastro de Fases", Icons.Filled.DateRange, habilitado = true, onClick = onCadastroFasesClick))
            add(OpcaoMais("Cadastro de Tipos de Processo", Icons.AutoMirrored.Filled.List, habilitado = true, onClick = onCadastroTiposProcessoClick))
            add(OpcaoMais("Equipe", Icons.Filled.Person, habilitado = true, onClick = onEquipeClick))
        }
        add(OpcaoMais("Fila de Distribuição", Icons.AutoMirrored.Filled.List, habilitado = true, onClick = onFilaDistribuicaoClick))
        add(OpcaoMais("Status de sincronização", Icons.Filled.Refresh, habilitado = false) {})
        add(OpcaoMais("Configurações", Icons.Filled.Settings, habilitado = true, onClick = onConfiguracoesClick))
        add(OpcaoMais("Sair", Icons.AutoMirrored.Filled.ExitToApp, habilitado = true, onClick = onSairClick))
    }
    ...
```

(O resto da função — `LazyColumn`, `OpcaoMaisCard` — continua igual.)

- [ ] **Step 3: Ligar as rotas em `AppNavHost`**

Adicione os imports das novas telas:

```kotlin
import com.josiel.organizeprocesso.ui.configuracoes.ConfiguracoesScreen
import com.josiel.organizeprocesso.ui.equipe.CriarContaScreen
import com.josiel.organizeprocesso.ui.equipe.EquipeScreen
import com.josiel.organizeprocesso.ui.filadistribuicao.FilaDistribuicaoDetalheScreen
import com.josiel.organizeprocesso.ui.filadistribuicao.FilaDistribuicaoScreen
```

Troque a chamada de `MaisScreen` para passar os três novos parâmetros:

```kotlin
            composable<Mais> {
                MaisScreen(
                    onCadastroFasesClick = { navController.navigate(CadastroFases) },
                    onCadastroTiposProcessoClick = { navController.navigate(CadastroTiposProcesso) },
                    onEquipeClick = { navController.navigate(Equipe) },
                    onFilaDistribuicaoClick = { navController.navigate(FilaDistribuicao) },
                    onConfiguracoesClick = { navController.navigate(Configuracoes) },
                    onSairClick = { /* já existe, sem mudança */ }
                )
            }
```

E adicione as cinco novas rotas, depois de `composable<CadastroTiposProcesso>`:

```kotlin
            composable<Equipe> {
                EquipeScreen(
                    onCriarContaClick = { navController.navigate(CriarConta) },
                    onBackClick = { navController.navigateUp() }
                )
            }

            composable<CriarConta> {
                CriarContaScreen(
                    onBackClick = { navController.navigateUp() },
                    onCriado = { navController.navigateUp() }
                )
            }

            composable<Configuracoes> {
                ConfiguracoesScreen(onBackClick = { navController.navigateUp() })
            }

            composable<FilaDistribuicao> {
                FilaDistribuicaoScreen(
                    onPessoaClick = { perfilId, nome -> navController.navigate(FilaDistribuicaoDetalhe(perfilId, nome)) },
                    onBackClick = { navController.navigateUp() }
                )
            }

            composable<FilaDistribuicaoDetalhe> { entry ->
                val rota = entry.toRoute<FilaDistribuicaoDetalhe>()
                FilaDistribuicaoDetalheScreen(
                    perfilId = rota.perfilId,
                    nomePessoa = rota.nome,
                    onBackClick = { navController.navigateUp() }
                )
            }
```

- [ ] **Step 4: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/navigation/Routes.kt \
        app/src/main/java/com/josiel/organizeprocesso/navigation/AppNavHost.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/MaisScreen.kt
git commit -m "feat: wire Equipe, Fila de Distribuicao and Configuracoes routes"
```

---

## Task 9: Verificação final

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
Expected: `BUILD SUCCESSFUL`, todos os testes das Tasks 2 e 3 passando (mais os 25 já existentes das etapas anteriores — total esperado: 31).

- [ ] **Step 3: Registrar o que fica pendente até haver acesso a um projeto Supabase real e a um emulador/dispositivo**

Não é possível, remotamente, verificar: se as views `fila_distribuicao`/`fila_distribuicao_por_tipo` já foram de fato aplicadas no projeto Supabase cloud real (o arquivo `supabase/.env.local` com as credenciais de gerência não existe neste worktree — se quem for rodar esta task tiver essas credenciais, use `node scripts/run_sql.mjs` com uma consulta de leitura simples antes de testar a tela); criação de conta real via `CriarContaScreen` de ponta a ponta (chamar a Edge Function, confirmar que a conta nova consegue logar); se `client.functions.invoke(...)` compila exatamente como escrito na Task 7 Step 1 (a assinatura pode variar ligeiramente entre versões menores do supabase-kt — o compilador vai apontar se precisar de ajuste); se a Fila de Distribuição reflete designações reais feitas no Plano 2B; se o toggle de notificação persiste entre reaberturas do app (spec §6). Recomenda-se testar com pelo menos duas contas (um `admin`, um `usuario`) no mesmo projeto Supabase — isso fica registrado como pendente, não como bloqueador para fechar este plano.

- [ ] **Step 4: Commit (se necessário)**

Se os Steps 1-2 não exigiram nenhuma mudança de código, não há o que commitar — este task é só o portão de verificação final.
