# Plano 2B — Fluxos de processo adaptados aos papéis — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reescrever as telas de Processo existentes (form, lista, detalhe, avançar fase) para operar sobre o modelo multiusuário (Perfil/TipoProcesso/designação), adicionar cadastro de Tipo de Processo e diligências, e fechar uma lacuna de sincronização inicial descoberta durante o planejamento.

**Architecture:** Continua MVVM + Repository (Room como cache de leitura, Postgrest/RPC como escrita — ver spec do pivô, seção 10). Toda decisão de UI sobre o que mostrar/esconder por papel é um espelho da RLS do backend (fonte da verdade), nunca uma regra nova — ver spec 2B, seção 2. Duas novas funções puras (`calcularSemaforo`, `PermissoesProcesso`) centralizam lógica hoje duplicada ou implícita, testáveis sem Android/Room.

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.8.4, supabase-kt 3.5.0 (postgrest-kt, auth-kt, realtime-kt), kotlinx.serialization, JUnit4 (testes puros, sem Robolectric/mockk).

**Spec:** `docs/superpowers/specs/2026-09-01-android-fluxos-processo-papeis-design.md`

## Global Constraints

- Trabalhar SEMPRE no worktree `C:\Users\03557061485\AndroidStudioProjects\Organize_Processo\.claude\worktrees\backend-supabase-multiusuario` (branch `plano2-android-fundacao`) — não na `master`.
- `JAVA_HOME` para todo comando gradle: `C:\Program Files\Android\Android Studio\jbr`.
- Verificação de compilação por task: `./gradlew.bat :app:compileDebugKotlin` (incremental — o build limpo forçado fica só para a Task 11, mesma disciplina do Plano 2A).
- RLS é a fonte da verdade (spec 2B, seção 2) — a UI só espelha para melhor experiência; nunca inventar regra que a RLS não tenha.
- Nunca usar `DropdownField` dentro de `AlertDialog`/`Dialog` — usar lista inline de `RadioButton` (spec 2B, seção 5; bug conhecido do projeto, já documentado em `FaseDestinoDialog`).
- Toda `buildJsonObject` que grava no Postgrest inclui a chave mesmo quando o valor é `null` — omitir a chave deixa um valor antigo intacto no servidor em vez de limpá-lo (bug já corrigido em `ProcessoRepository`/`FaseRepository`/`ItemRepository`).
- `avancar_fase(p_processo_id uuid, p_fase_destino_id uuid, p_executor_id uuid default null, p_observacao_inicial text default '', p_prazo_limite date default null, p_notificar_prazo boolean default false, p_motivo_retorno text default null) returns uuid` e `designar_processo(p_processo_id uuid, p_novo_responsavel_id uuid) returns void` são as assinaturas EXATAS já implantadas no backend (`supabase/migrations/20260901100340_corrigir_assinatura_avancar_fase.sql` e `20260901051442_endurecer_designacao_ativo_realtime.sql`) — não inventar parâmetros. `avancar_fase` não aceita uma data de entrada customizada (usa sempre `current_date` no servidor).
- Chamada de RPC no supabase-kt 3.5.0: `client.postgrest.rpc(function: String, parameters: JsonObject, request: RpcRequestBuilder.() -> Unit = {}): PostgrestResult` (mesmo import `io.github.jan.supabase.postgrest.postgrest` já usado nos repositories existentes).
- Diligência é *append-only* na UI (spec 2B, seção 3.2) — nunca oferecer editar/excluir uma diligência.
- Remoção de `Pessoa` (spec 2B, seção 4.6) já está 100% completa desde o Plano 2A — confirmado por busca no código-fonte (`grep -r Pessoa app/src/main/java` só retorna comentários obsoletos, sem entidade/tela/repositório). Nenhuma task deste plano precisa tocar nisso.

---

## Task 1: Fechar a lacuna de sincronização inicial + Realtime

Descoberta durante o planejamento: nenhum repositório chama `sincronizar()` no login (só `ProcessoRepository.criar()/atualizar()` chamam internamente, como efeito colateral de uma escrita) e `RealtimeSyncManager.iniciar()` nunca é chamado em lugar nenhum do app — a spec do Plano 2A (seção 2.2) descreve os dois mecanismos ("refresh explícito" + "Realtime após login"), mas a implementação nunca os ligou. Sem isso, toda tela deste plano ficaria mostrando listas vazias após um login limpo, mesmo com dados reais no servidor. Corrigido aqui, antes de qualquer tela nova depender de dados existirem no Room.

**Files:**
- Modify: `app/src/main/java/com/josiel/organizeprocesso/navigation/AppNavHost.kt`

**Interfaces:**
- Consumes: `PerfilRepository.sincronizar()`, `TipoProcessoRepository.sincronizar()`, `FaseRepository.sincronizar()`, `ProcessoRepository.sincronizar()` (já existem), `RealtimeSyncManager.iniciar(client, escopo, repositorios)` e `RepositoriosSincronizaveis` (já existem, sem mudança de assinatura).
- Produces: nada consumido por tasks futuras — é só a ligação que faz o Room deixar de ficar vazio após o login.

- [ ] **Step 1: Adicionar a sincronização inicial + Realtime ao gate de autenticação**

Em `AppNavHost.kt`, adicione os imports que faltam e, dentro de `AppNavHost()`, construa os repositórios uma vez e dispare a sincronização/realtime quando a sessão vira autenticada pela primeira vez:

```kotlin
import com.josiel.organizeprocesso.data.remote.RepositoriosSincronizaveis
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import com.josiel.organizeprocesso.data.repository.ProcessoRepository
import com.josiel.organizeprocesso.data.repository.TipoProcessoRepository
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
```

Dentro de `fun AppNavHost()`, logo após `val coroutineScope = rememberCoroutineScope()`:

```kotlin
    val database = remember { AppDatabase.getInstance(context) }
    val perfilRepository = remember { PerfilRepository(database.perfilDao(), SupabaseSessionManager.client) }
    val tipoProcessoRepository = remember { TipoProcessoRepository(database.tipoProcessoDao(), SupabaseSessionManager.client) }
    val faseRepository = remember { FaseRepository(database.faseDao(), SupabaseSessionManager.client) }
    val processoRepository = remember { ProcessoRepository(database, SupabaseSessionManager.client) }
    var sincronizacaoIniciada by remember { mutableStateOf(false) }
```

E troque o `LaunchedEffect(sessionStatus)` existente para também disparar a sincronização (mantendo a navegação que já existia):

```kotlin
    LaunchedEffect(sessionStatus) {
        val autenticado = sessionStatus is SessionStatus.Authenticated
        val emLogin = currentDestination?.hierarchy?.any { it.hasRoute(Login::class) } == true
        if (autenticado && emLogin) {
            navController.navigate(Inicio) { popUpTo(Login) { inclusive = true } }
        } else if (!autenticado && !emLogin) {
            navController.navigate(Login) { popUpTo(0) { inclusive = true } }
        }

        if (autenticado && !sincronizacaoIniciada) {
            sincronizacaoIniciada = true
            perfilRepository.sincronizar()
            tipoProcessoRepository.sincronizar()
            faseRepository.sincronizar()
            processoRepository.sincronizar()
            RealtimeSyncManager.iniciar(
                client = SupabaseSessionManager.client,
                escopo = coroutineScope,
                repositorios = RepositoriosSincronizaveis(
                    perfis = perfilRepository::sincronizar,
                    tiposProcesso = tipoProcessoRepository::sincronizar,
                    fases = faseRepository::sincronizar,
                    processos = processoRepository::sincronizar
                )
            )
        } else if (!autenticado) {
            sincronizacaoIniciada = false
        }
    }
```

`context` já existe (`val context = LocalContext.current`, linha 48 do arquivo atual) — `AppDatabase.getInstance(Context)` já lida com pegar o `applicationContext` internamente, então não precisa de cast.

- [ ] **Step 2: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/navigation/AppNavHost.kt
git commit -m "fix: wire initial sync and RealtimeSyncManager on login"
```

---

## Task 2: `calcularSemaforo` — função pura, elimina duplicação

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/domain/usecase/CalcularSemaforo.kt`
- Test: `app/src/test/java/com/josiel/organizeprocesso/domain/usecase/CalcularSemaforoTest.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoListViewModel.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/AvancarFaseViewModel.kt`

**Interfaces:**
- Produces: `fun calcularSemaforo(diasDecorridos: Long, diasAlertaAtencao: Int, diasAlertaCritico: Int): StatusSemaforo` — usado por Tasks 8, 9 e 10 para os dois semáforos independentes (dias parado na fase, dias desde designado).

- [ ] **Step 1: Escrever o teste (falhando)**

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import org.junit.Assert.assertEquals
import org.junit.Test

class CalcularSemaforoTest {
    @Test
    fun `abaixo do limite de atencao fica OK`() {
        assertEquals(StatusSemaforo.OK, calcularSemaforo(2, diasAlertaAtencao = 5, diasAlertaCritico = 10))
    }

    @Test
    fun `exatamente no limite de atencao fica ATENCAO`() {
        assertEquals(StatusSemaforo.ATENCAO, calcularSemaforo(5, diasAlertaAtencao = 5, diasAlertaCritico = 10))
    }

    @Test
    fun `entre atencao e critico fica ATENCAO`() {
        assertEquals(StatusSemaforo.ATENCAO, calcularSemaforo(7, diasAlertaAtencao = 5, diasAlertaCritico = 10))
    }

    @Test
    fun `exatamente no limite critico fica CRITICO`() {
        assertEquals(StatusSemaforo.CRITICO, calcularSemaforo(10, diasAlertaAtencao = 5, diasAlertaCritico = 10))
    }

    @Test
    fun `acima do limite critico fica CRITICO`() {
        assertEquals(StatusSemaforo.CRITICO, calcularSemaforo(30, diasAlertaAtencao = 5, diasAlertaCritico = 10))
    }

    @Test
    fun `zero dias decorridos fica OK`() {
        assertEquals(StatusSemaforo.OK, calcularSemaforo(0, diasAlertaAtencao = 5, diasAlertaCritico = 10))
    }
}
```

- [ ] **Step 2: Rodar o teste, confirmar que falha**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.CalcularSemaforoTest"`
Expected: FAIL (função `calcularSemaforo` não existe ainda).

- [ ] **Step 3: Implementar a função**

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.StatusSemaforo

/**
 * Semáforo de tempo decorrido comparado aos limites de alerta — mesma lógica
 * usada tanto para "dias parado na fase" (limites da Fase) quanto para "dias
 * desde designado" (limites do TipoProcesso). Spec do Plano 2B, seção 4.2.
 */
fun calcularSemaforo(diasDecorridos: Long, diasAlertaAtencao: Int, diasAlertaCritico: Int): StatusSemaforo = when {
    diasDecorridos >= diasAlertaCritico -> StatusSemaforo.CRITICO
    diasDecorridos >= diasAlertaAtencao -> StatusSemaforo.ATENCAO
    else -> StatusSemaforo.OK
}
```

- [ ] **Step 4: Rodar o teste, confirmar que passa**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.CalcularSemaforoTest"`
Expected: PASS (6 testes).

- [ ] **Step 5: Usar a função em `ProcessoListViewModel`**

Em `ProcessoListViewModel.kt`, adicione `import com.josiel.organizeprocesso.domain.usecase.calcularSemaforo` e troque:

```kotlin
            val statusSemaforo = when {
                fase == null -> StatusSemaforo.OK
                diasParado >= fase.diasAlertaCritico -> StatusSemaforo.CRITICO
                diasParado >= fase.diasAlertaAtencao -> StatusSemaforo.ATENCAO
                else -> StatusSemaforo.OK
            }
```

por:

```kotlin
            val statusSemaforo = fase?.let { calcularSemaforo(diasParado, it.diasAlertaAtencao, it.diasAlertaCritico) }
                ?: StatusSemaforo.OK
```

- [ ] **Step 6: Usar a função em `AvancarFaseViewModel`**

Em `AvancarFaseViewModel.kt`, adicione o mesmo import e troque:

```kotlin
                val semaforo = when {
                    faseAtual == null -> StatusSemaforo.OK
                    diasParado >= faseAtual.diasAlertaCritico -> StatusSemaforo.CRITICO
                    diasParado >= faseAtual.diasAlertaAtencao -> StatusSemaforo.ATENCAO
                    else -> StatusSemaforo.OK
                }
```

por:

```kotlin
                val semaforo = faseAtual?.let { calcularSemaforo(diasParado, it.diasAlertaAtencao, it.diasAlertaCritico) }
                    ?: StatusSemaforo.OK
```

- [ ] **Step 7: Verificar que o projeto inteiro compila e os testes passam**

Run: `./gradlew.bat :app:compileDebugKotlin`
Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` nos dois.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/domain/usecase/CalcularSemaforo.kt \
        app/src/test/java/com/josiel/organizeprocesso/domain/usecase/CalcularSemaforoTest.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoListViewModel.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/processos/AvancarFaseViewModel.kt
git commit -m "refactor: extract calcularSemaforo, remove duplicated inline logic"
```

---

## Task 3: `PermissoesProcesso` — regras de UI por papel (função pura)

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/domain/usecase/PermissoesProcesso.kt`
- Test: `app/src/test/java/com/josiel/organizeprocesso/domain/usecase/PermissoesProcessoTest.kt`

**Interfaces:**
- Produces:
  - `fun podeCriarProcesso(papel: Papel): Boolean`
  - `fun podeEditarProcesso(papel: Papel, responsavelId: String?, usuarioId: String): Boolean`
  - `enum class AcaoDesignacao { DESIGNAR, ASSUMIR, DEVOLVER, NENHUMA }`
  - `fun acaoDesignacaoDisponivel(papel: Papel, responsavelId: String?, usuarioId: String): AcaoDesignacao`
  - Usadas por Tasks 7 (criar/editar), 8 (mostrar botão "+ Novo processo"), 9 (ação contextual + editar), 10 (editar entrada de fase).

- [ ] **Step 1: Escrever os testes (falhando)**

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.Papel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissoesProcessoTest {
    @Test
    fun `so admin pode criar processo`() {
        assertTrue(podeCriarProcesso(Papel.ADMIN))
        assertFalse(podeCriarProcesso(Papel.USUARIO))
        assertFalse(podeCriarProcesso(Papel.SUPER_ADMIN))
    }

    @Test
    fun `admin pode editar qualquer processo`() {
        assertTrue(podeEditarProcesso(Papel.ADMIN, responsavelId = "outro", usuarioId = "eu"))
    }

    @Test
    fun `usuario pode editar processo orfao`() {
        assertTrue(podeEditarProcesso(Papel.USUARIO, responsavelId = null, usuarioId = "eu"))
    }

    @Test
    fun `usuario pode editar o proprio processo`() {
        assertTrue(podeEditarProcesso(Papel.USUARIO, responsavelId = "eu", usuarioId = "eu"))
    }

    @Test
    fun `usuario nao pode editar processo de outra pessoa`() {
        assertFalse(podeEditarProcesso(Papel.USUARIO, responsavelId = "outro", usuarioId = "eu"))
    }

    @Test
    fun `admin sempre pode designar`() {
        assertEquals(AcaoDesignacao.DESIGNAR, acaoDesignacaoDisponivel(Papel.ADMIN, responsavelId = null, usuarioId = "eu"))
        assertEquals(AcaoDesignacao.DESIGNAR, acaoDesignacaoDisponivel(Papel.ADMIN, responsavelId = "outro", usuarioId = "eu"))
        assertEquals(AcaoDesignacao.DESIGNAR, acaoDesignacaoDisponivel(Papel.ADMIN, responsavelId = "eu", usuarioId = "eu"))
    }

    @Test
    fun `usuario em processo orfao pode assumir`() {
        assertEquals(AcaoDesignacao.ASSUMIR, acaoDesignacaoDisponivel(Papel.USUARIO, responsavelId = null, usuarioId = "eu"))
    }

    @Test
    fun `usuario dono pode devolver`() {
        assertEquals(AcaoDesignacao.DEVOLVER, acaoDesignacaoDisponivel(Papel.USUARIO, responsavelId = "eu", usuarioId = "eu"))
    }

    @Test
    fun `usuario em processo de outra pessoa nao tem acao`() {
        assertEquals(AcaoDesignacao.NENHUMA, acaoDesignacaoDisponivel(Papel.USUARIO, responsavelId = "outro", usuarioId = "eu"))
    }
}
```

- [ ] **Step 2: Rodar os testes, confirmar que falham**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.PermissoesProcessoTest"`
Expected: FAIL (arquivo não existe ainda).

- [ ] **Step 3: Implementar**

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.Papel

/**
 * Regras de UI para decidir quais ações mostrar — espelham exatamente a RLS
 * do backend, nunca a substituem (spec do Plano 2B, seção 2; a RLS é a
 * barreira real, isto só evita que o usuário tente uma ação que o servidor
 * vai rejeitar). `Papel.SUPER_ADMIN` cai nos ramos negativos em todas as
 * funções: não participa do dia a dia de nenhuma organização (spec do
 * pivô, seção 2) e a RLS do backend também só reconhece `= 'admin'`.
 */
fun podeCriarProcesso(papel: Papel): Boolean = papel == Papel.ADMIN

/** Espelha a política `processos_update`: admin, dono atual, ou qualquer um se o processo está órfão. */
fun podeEditarProcesso(papel: Papel, responsavelId: String?, usuarioId: String): Boolean =
    papel == Papel.ADMIN || responsavelId == null || responsavelId == usuarioId

/** Ação de designação disponível no Detalhe do Processo (spec do Plano 2B, seção 4.3). */
enum class AcaoDesignacao { DESIGNAR, ASSUMIR, DEVOLVER, NENHUMA }

fun acaoDesignacaoDisponivel(papel: Papel, responsavelId: String?, usuarioId: String): AcaoDesignacao = when {
    papel == Papel.ADMIN -> AcaoDesignacao.DESIGNAR
    responsavelId == null -> AcaoDesignacao.ASSUMIR
    responsavelId == usuarioId -> AcaoDesignacao.DEVOLVER
    else -> AcaoDesignacao.NENHUMA
}
```

- [ ] **Step 4: Rodar os testes, confirmar que passam**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.PermissoesProcessoTest"`
Expected: PASS (9 testes).

- [ ] **Step 5: Verificar que o projeto inteiro compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/domain/usecase/PermissoesProcesso.kt \
        app/src/test/java/com/josiel/organizeprocesso/domain/usecase/PermissoesProcessoTest.kt
git commit -m "feat: add PermissoesProcesso usecase mirroring backend RLS rules"
```

---

## Task 4: Camada de dados de Diligência (nova — o spec 2B assumia que já existia de 2A, mas não existe)

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/local/DiligenciaEntity.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/local/DiligenciaDao.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/DiligenciaDto.kt`
- Test: `app/src/test/java/com/josiel/organizeprocesso/data/remote/dto/DiligenciaDtoMappingTest.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/repository/DiligenciaRepository.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/local/AppDatabase.kt`

**Interfaces:**
- Produces: `DiligenciaRepository(dao, client)` com `observarPorHistorico(historicoId): Flow<List<DiligenciaEntity>>`, `suspend fun sincronizar(historicoId: String)`, `suspend fun registrar(historicoId: String, conteudo: String)` — usado pela Task 10.

- [ ] **Step 1: Criar a entidade Room**

```kotlin
package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Nova no pivô multiusuário (spec do pivô, seção 3) — mini-histórico de
 * eventos dentro de uma passagem de fase. Append-only: sem UPDATE/DELETE na
 * UI (spec do Plano 2B, seção 3.2, Decisão).
 */
@Entity(
    tableName = "diligencias",
    foreignKeys = [
        ForeignKey(
            entity = ProcessoFaseHistoricoEntity::class,
            parentColumns = ["id"],
            childColumns = ["processoFaseHistoricoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("processoFaseHistoricoId")]
)
data class DiligenciaEntity(
    @PrimaryKey val id: String,
    val processoFaseHistoricoId: String,
    val autorId: String,
    val conteudo: String,
    val criadoEm: Instant
)
```

- [ ] **Step 2: Criar o DAO**

```kotlin
package com.josiel.organizeprocesso.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DiligenciaDao {
    @Upsert
    suspend fun upsertTodas(diligencias: List<DiligenciaEntity>)

    @Query("SELECT * FROM diligencias WHERE processoFaseHistoricoId = :historicoId ORDER BY criadoEm DESC")
    fun observarPorHistorico(historicoId: String): Flow<List<DiligenciaEntity>>
}
```

- [ ] **Step 3: Escrever o teste de mapeamento do DTO (falhando)**

```kotlin
package com.josiel.organizeprocesso.data.remote.dto

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DiligenciaDtoMappingTest {
    @Test
    fun `mapeia dto para entity preservando todos os campos`() {
        val dto = DiligenciaDto(
            id = "d1",
            processoFaseHistoricoId = "h1",
            autorId = "perfil1",
            conteudo = "Enviado ofício ao fornecedor X",
            criadoEm = "2026-09-01T10:00:00Z"
        )

        val entity = dto.paraEntity()

        assertEquals("d1", entity.id)
        assertEquals("h1", entity.processoFaseHistoricoId)
        assertEquals("perfil1", entity.autorId)
        assertEquals("Enviado ofício ao fornecedor X", entity.conteudo)
        assertEquals(Instant.parse("2026-09-01T10:00:00Z"), entity.criadoEm)
    }
}
```

- [ ] **Step 4: Rodar o teste, confirmar que falha**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.data.remote.dto.DiligenciaDtoMappingTest"`
Expected: FAIL (`DiligenciaDto` não existe).

- [ ] **Step 5: Criar o DTO e a função de mapeamento**

`public.diligencias` tem exatamente estas 5 colunas (`supabase/migrations/20260901015252_criar_diligencias_observacoes.sql`): `id, processo_fase_historico_id, autor_id, conteudo, criado_em`.

```kotlin
package com.josiel.organizeprocesso.data.remote.dto

import com.josiel.organizeprocesso.data.local.DiligenciaEntity
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiligenciaDto(
    val id: String,
    @SerialName("processo_fase_historico_id") val processoFaseHistoricoId: String,
    @SerialName("autor_id") val autorId: String,
    val conteudo: String,
    @SerialName("criado_em") val criadoEm: String
)

fun DiligenciaDto.paraEntity(): DiligenciaEntity = DiligenciaEntity(
    id = id,
    processoFaseHistoricoId = processoFaseHistoricoId,
    autorId = autorId,
    conteudo = conteudo,
    criadoEm = Instant.parse(criadoEm)
)
```

- [ ] **Step 6: Rodar o teste, confirmar que passa**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.data.remote.dto.DiligenciaDtoMappingTest"`
Expected: PASS.

- [ ] **Step 7: Criar o repositório**

`autor_id` NUNCA é enviado pelo cliente no insert: a coluna tem `default auth.uid()` no servidor (`supabase/migrations/20260901051442_endurecer_designacao_ativo_realtime.sql`, Finding C) e a RLS exige `autor_id = auth.uid()` no WITH CHECK.

```kotlin
package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.DiligenciaDao
import com.josiel.organizeprocesso.data.local.DiligenciaEntity
import com.josiel.organizeprocesso.data.remote.dto.DiligenciaDto
import com.josiel.organizeprocesso.data.remote.dto.paraEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Append-only (spec do Plano 2B, seção 3.2) — não existe atualizar()/excluir(). */
class DiligenciaRepository(
    private val dao: DiligenciaDao,
    private val client: SupabaseClient
) {
    fun observarPorHistorico(historicoId: String): Flow<List<DiligenciaEntity>> =
        dao.observarPorHistorico(historicoId)

    suspend fun sincronizar(historicoId: String) {
        val dtos = client.postgrest["diligencias"].select {
            filter { eq("processo_fase_historico_id", historicoId) }
        }.decodeList<DiligenciaDto>()
        dao.upsertTodas(dtos.map { it.paraEntity() })
    }

    suspend fun registrar(historicoId: String, conteudo: String) {
        val linha = buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("processo_fase_historico_id", historicoId)
            put("conteudo", conteudo)
        }
        client.postgrest["diligencias"].insert(linha)
        sincronizar(historicoId)
    }
}
```

- [ ] **Step 8: Registrar em `AppDatabase` (bump de versão)**

Em `AppDatabase.kt`, adicione `DiligenciaEntity::class` à lista de `entities`, mude `version = 2` para `version = 3`, e adicione o DAO abstrato:

```kotlin
@Database(
    entities = [
        PerfilEntity::class,
        TipoProcessoEntity::class,
        FaseEntity::class,
        ProcessoEntity::class,
        ProcessoFaseHistoricoEntity::class,
        ObservacaoVersaoEntity::class,
        ItemEntity::class,
        AnexoLinkEntity::class,
        DiligenciaEntity::class
    ],
    version = 3,
    exportSchema = true
)
```

E dentro da classe:

```kotlin
    abstract fun diligenciaDao(): DiligenciaDao
```

Não precisa de `Migration` manual: `AppDatabase` já usa `.fallbackToDestructiveMigration(dropAllTables = true)` — Room é cache descartável (spec do pivô, seção 10), um bump de versão simplesmente recria o banco local vazio, que a sincronização (Task 1) repopula.

- [ ] **Step 9: Verificar que o projeto compila e os testes passam**

Run: `./gradlew.bat :app:compileDebugKotlin`
Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` nos dois.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/data/local/DiligenciaEntity.kt \
        app/src/main/java/com/josiel/organizeprocesso/data/local/DiligenciaDao.kt \
        app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/DiligenciaDto.kt \
        app/src/test/java/com/josiel/organizeprocesso/data/remote/dto/DiligenciaDtoMappingTest.kt \
        app/src/main/java/com/josiel/organizeprocesso/data/repository/DiligenciaRepository.kt \
        app/src/main/java/com/josiel/organizeprocesso/data/local/AppDatabase.kt
git commit -m "feat: add Diligencia data layer (entity, dao, dto, repository)"
```

---

## Task 5: Ligar `ProcessoRepository`/`HistoricoFaseRepository` às RPCs do backend

**Files:**
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/repository/ProcessoRepository.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/repository/HistoricoFaseRepository.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/ProcessoFaseHistoricoDto.kt`
- Test: `app/src/test/java/com/josiel/organizeprocesso/data/remote/dto/ProcessoFaseHistoricoDtoMappingTest.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/AvancarFaseViewModel.kt` (só o mínimo para continuar compilando — o resto é Task 10)

**Interfaces:**
- Consumes: `client.postgrest.rpc("avancar_fase", JsonObject)`, `client.postgrest.rpc("designar_processo", JsonObject)`.
- Produces: `ProcessoRepository.designar(processoId: String, novoResponsavelId: String?)` (usado pela Task 9). `HistoricoFaseRepository.salvarEntradaAtual(...)` (mesma assinatura de antes) agora grava no Postgrest. `HistoricoFaseRepository.mudarFase(processo: ProcessoEntity, faseDestinoId: String, executorId: String?, prazoLimite: LocalDate?, motivoRetorno: String?, notificarPrazo: Boolean)` — assinatura NOVA (perdeu `historicoAtual` e `dataEntrada`; o parâmetro que antes se chamava `responsavelId` passa a ser o executor da RPC) — usada pela Task 10.

`processo_fase_historico` nunca teve um DTO/sincronização real (só existia via a antiga transação 100% local) — esta task cria o primeiro.

- [ ] **Step 1: Escrever o teste de mapeamento do novo DTO (falhando)**

`public.processo_fase_historico` tem estas 11 colunas (`supabase/migrations/20260901013950_criar_itens_historico.sql`): `id, processo_id, fase_id, responsavel_id, data_entrada, data_saida, prazo_limite, observacoes, motivo_retorno, notificar_prazo, criado_em`.

```kotlin
package com.josiel.organizeprocesso.data.remote.dto

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcessoFaseHistoricoDtoMappingTest {
    @Test
    fun `mapeia dto para entity preservando todos os campos`() {
        val dto = ProcessoFaseHistoricoDto(
            id = "h1",
            processoId = "p1",
            faseId = "f1",
            responsavelId = "perfil1",
            dataEntrada = "2026-09-01",
            dataSaida = null,
            prazoLimite = "2026-09-15",
            observacoes = "Em análise",
            motivoRetorno = null,
            notificarPrazo = true,
            criadoEm = "2026-09-01T10:00:00Z"
        )

        val entity = dto.paraEntity()

        assertEquals("h1", entity.id)
        assertEquals("p1", entity.processoId)
        assertEquals("perfil1", entity.responsavelId)
        assertEquals(LocalDate.of(2026, 9, 1), entity.dataEntrada)
        assertNull(entity.dataSaida)
        assertEquals(LocalDate.of(2026, 9, 15), entity.prazoLimite)
        assertEquals(true, entity.notificarPrazo)
    }
}
```

- [ ] **Step 2: Rodar o teste, confirmar que falha**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.data.remote.dto.ProcessoFaseHistoricoDtoMappingTest"`
Expected: FAIL.

- [ ] **Step 3: Criar o DTO**

```kotlin
package com.josiel.organizeprocesso.data.remote.dto

import com.josiel.organizeprocesso.data.local.ProcessoFaseHistoricoEntity
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProcessoFaseHistoricoDto(
    val id: String,
    @SerialName("processo_id") val processoId: String,
    @SerialName("fase_id") val faseId: String,
    @SerialName("responsavel_id") val responsavelId: String?,
    @SerialName("data_entrada") val dataEntrada: String,
    @SerialName("data_saida") val dataSaida: String?,
    @SerialName("prazo_limite") val prazoLimite: String?,
    val observacoes: String,
    @SerialName("motivo_retorno") val motivoRetorno: String?,
    @SerialName("notificar_prazo") val notificarPrazo: Boolean,
    @SerialName("criado_em") val criadoEm: String
)

fun ProcessoFaseHistoricoDto.paraEntity(): ProcessoFaseHistoricoEntity = ProcessoFaseHistoricoEntity(
    id = id,
    processoId = processoId,
    faseId = faseId,
    responsavelId = responsavelId,
    dataEntrada = LocalDate.parse(dataEntrada),
    dataSaida = dataSaida?.let(LocalDate::parse),
    prazoLimite = prazoLimite?.let(LocalDate::parse),
    observacoes = observacoes,
    motivoRetorno = motivoRetorno,
    notificarPrazo = notificarPrazo
)
```

- [ ] **Step 4: Rodar o teste, confirmar que passa**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.data.remote.dto.ProcessoFaseHistoricoDtoMappingTest"`
Expected: PASS.

- [ ] **Step 5: Adicionar `designar()` ao `ProcessoRepository`**

Em `ProcessoRepository.kt`, dentro da classe (depois de `atualizar`):

```kotlin
    /** Chama a RPC `designar_processo` — admin designa a qualquer um, ou o próprio usuário autoatribui/devolve um órfão (RLS do backend valida). */
    suspend fun designar(processoId: String, novoResponsavelId: String?) {
        client.postgrest.rpc(
            "designar_processo",
            buildJsonObject {
                put("p_processo_id", processoId)
                put("p_novo_responsavel_id", novoResponsavelId)
            }
        )
        sincronizar()
    }
```

- [ ] **Step 6: Reescrever `HistoricoFaseRepository`**

```kotlin
package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.ObservacaoVersaoEntity
import com.josiel.organizeprocesso.data.local.ProcessoEntity
import com.josiel.organizeprocesso.data.local.ProcessoFaseHistoricoEntity
import com.josiel.organizeprocesso.data.remote.dto.ProcessoFaseHistoricoDto
import com.josiel.organizeprocesso.data.remote.dto.paraEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Orquestra o histórico de fases de um processo — escreve direto no
 * Postgrest/RPC (spec do pivô, seção 10); Room é cache de leitura.
 */
class HistoricoFaseRepository(
    private val database: AppDatabase,
    private val client: SupabaseClient
) {
    private val processoDao = database.processoDao()
    private val historicoDao = database.processoFaseHistoricoDao()
    private val observacaoVersaoDao = database.observacaoVersaoDao()

    fun observarAtivoPorProcesso(processoId: String): Flow<ProcessoFaseHistoricoEntity?> =
        historicoDao.observarAtivoPorProcesso(processoId)

    fun observarVersoesObservacao(historicoId: String): Flow<List<ObservacaoVersaoEntity>> =
        observacaoVersaoDao.observarPorHistorico(historicoId)

    private suspend fun sincronizarHistorico(processoId: String) {
        val dtos = client.postgrest["processo_fase_historico"].select {
            filter { eq("processo_id", processoId) }
        }.decodeList<ProcessoFaseHistoricoDto>()
        dtos.forEach { historicoDao.upsert(it.paraEntity()) }
    }

    /** Atualiza responsável/prazo/notificação/observação da entrada corrente, sem trocar de fase. */
    suspend fun salvarEntradaAtual(
        historico: ProcessoFaseHistoricoEntity,
        responsavelId: String?,
        prazoLimite: LocalDate?,
        notificarPrazo: Boolean,
        novaObservacao: String
    ) {
        val linha = buildJsonObject {
            put("responsavel_id", responsavelId)
            put("prazo_limite", prazoLimite?.toString())
            put("notificar_prazo", notificarPrazo)
            put("observacoes", novaObservacao)
        }
        client.postgrest["processo_fase_historico"].update(linha) {
            filter { eq("id", historico.id) }
        }
        if (novaObservacao != historico.observacoes) {
            val linhaVersao = buildJsonObject {
                put("id", UUID.randomUUID().toString())
                put("processo_fase_historico_id", historico.id)
                put("conteudo", novaObservacao)
            }
            client.postgrest["observacao_versoes"].insert(linhaVersao)
        }
        sincronizarHistorico(historico.processoId)
    }

    /**
     * Encerra a entrada corrente e abre uma nova para [faseDestinoId] via a
     * RPC `avancar_fase()` (transação atômica no servidor) — nunca
     * automático, sempre por escolha explícita do usuário. [executorId] é
     * quem executa esta passagem específica (pode divergir do responsável
     * do processo). [motivoRetorno] só é preenchido quando é um retrocesso.
     * A RPC sempre usa a data atual do servidor — não aceita data customizada.
     */
    suspend fun mudarFase(
        processo: ProcessoEntity,
        faseDestinoId: String,
        executorId: String?,
        prazoLimite: LocalDate?,
        motivoRetorno: String?,
        notificarPrazo: Boolean
    ) {
        client.postgrest.rpc(
            "avancar_fase",
            buildJsonObject {
                put("p_processo_id", processo.id)
                put("p_fase_destino_id", faseDestinoId)
                put("p_executor_id", executorId)
                put("p_observacao_inicial", "")
                put("p_prazo_limite", prazoLimite?.toString())
                put("p_notificar_prazo", notificarPrazo)
                put("p_motivo_retorno", motivoRetorno)
            }
        )
        processoDao.upsert(processo.copy(faseAtualId = faseDestinoId))
        sincronizarHistorico(processo.id)
    }
}
```

- [ ] **Step 7: Ajustar o único ponto de construção/chamada em `AvancarFaseViewModel` para continuar compilando**

Em `AvancarFaseViewModel.kt`, troque:

```kotlin
    private val historicoRepository = HistoricoFaseRepository(database)
```

por:

```kotlin
    private val historicoRepository = HistoricoFaseRepository(database, SupabaseSessionManager.client)
```

E troque a chamada dentro de `fun mudarFase(...)` (a assinatura completa do método da ViewModel e o resto da tela são reescritos na Task 10 — aqui é só o mínimo para o projeto compilar):

```kotlin
    fun mudarFase(faseDestinoId: String, dataEntrada: LocalDate, motivoRetorno: String?, onConcluido: () -> Unit) {
        val estado = _uiState.value
        val processo = estado.processo ?: return
        viewModelScope.launch {
            historicoRepository.mudarFase(
                processo = processo,
                faseDestinoId = faseDestinoId,
                executorId = estado.responsavelId,
                prazoLimite = estado.prazoLimite,
                motivoRetorno = motivoRetorno,
                notificarPrazo = estado.notificarPrazo
            )
            onConcluido()
        }
    }
```

(O parâmetro `dataEntrada` fica sem uso aqui de propósito — a Task 10 remove ele de toda a cadeia, incluindo a tela; deixar por enquanto evita reescrever `AvancarFaseScreen` duas vezes.)

- [ ] **Step 8: Verificar que o projeto compila e os testes passam**

Run: `./gradlew.bat :app:compileDebugKotlin`
Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` nos dois. Um warning de parâmetro `dataEntrada` não usado é esperado e aceitável neste ponto intermediário.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/data/repository/ProcessoRepository.kt \
        app/src/main/java/com/josiel/organizeprocesso/data/repository/HistoricoFaseRepository.kt \
        app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/ProcessoFaseHistoricoDto.kt \
        app/src/test/java/com/josiel/organizeprocesso/data/remote/dto/ProcessoFaseHistoricoDtoMappingTest.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/processos/AvancarFaseViewModel.kt
git commit -m "feat: wire ProcessoRepository.designar() and rewrite HistoricoFaseRepository to use Postgrest/RPC"
```

---

## Task 6: Cadastro de Tipo de Processo + gate de admin em "Mais"

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/TipoProcessoCadastroViewModel.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/TipoProcessoFormDialog.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/TiposProcessoScreen.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/MaisScreen.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/navigation/Routes.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/navigation/AppNavHost.kt`

**Interfaces:**
- Consumes: `TipoProcessoRepository` (já existe desde 2A, sem mudança).

Espelha exatamente o padrão de `FaseCadastroViewModel`/`FasesScreen`/`FaseFormDialog` (spec 2B, seção 3.1). O "ganha checagem de papel admin" da spec (seções 3.1 e 4.5) é satisfeito escondendo os dois cards em `MaisScreen` para quem não é admin — a RLS (`fases_admin_escreve`/`tipos_processo_admin_escreve`) é a barreira real; não há necessidade de duplicar a checagem dentro dos ViewModels.

- [ ] **Step 1: Criar `TipoProcessoCadastroViewModel`**

```kotlin
package com.josiel.organizeprocesso.ui.cadastro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.TipoProcessoEntity
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.TipoProcessoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TipoProcessoCadastroViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TipoProcessoRepository(
        dao = AppDatabase.getInstance(application).tipoProcessoDao(),
        client = SupabaseSessionManager.client
    )

    val tiposProcesso: StateFlow<List<TipoProcessoEntity>> = repository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun salvar(id: String?, nome: String, diasAlertaAtencao: Int, diasAlertaCritico: Int) {
        viewModelScope.launch {
            repository.salvar(
                id = id,
                nome = nome,
                diasAlertaAtencao = diasAlertaAtencao,
                diasAlertaCritico = diasAlertaCritico,
                organizacaoId = SupabaseSessionManager.perfilAtual?.organizacaoId.orEmpty()
            )
        }
    }

    fun excluir(tipoProcesso: TipoProcessoEntity) {
        viewModelScope.launch { repository.excluir(tipoProcesso) }
    }
}
```

- [ ] **Step 2: Criar `TipoProcessoFormDialog`**

```kotlin
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
```

- [ ] **Step 3: Criar `TiposProcessoScreen`**

```kotlin
package com.josiel.organizeprocesso.ui.cadastro

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.josiel.organizeprocesso.data.local.TipoProcessoEntity
import com.josiel.organizeprocesso.ui.components.NavigationChevron
import com.josiel.organizeprocesso.ui.components.PillButton
import com.josiel.organizeprocesso.ui.components.SideBarCard
import com.josiel.organizeprocesso.ui.components.StatusPill
import com.josiel.organizeprocesso.ui.theme.AmareloAtencao
import com.josiel.organizeprocesso.ui.theme.AmareloAtencaoPastel
import com.josiel.organizeprocesso.ui.theme.Indigo600
import com.josiel.organizeprocesso.ui.theme.VermelhoCritico
import com.josiel.organizeprocesso.ui.theme.VermelhoCriticoPastel

/** Cadastro de Tipo de Processo (spec do Plano 2B, seção 3.1) — catálogo reutilizável, admin-only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiposProcessoScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TipoProcessoCadastroViewModel = viewModel()
) {
    val tiposProcesso by viewModel.tiposProcesso.collectAsState()
    var tipoEmEdicao by remember { mutableStateOf<TipoProcessoEntity?>(null) }
    var mostrarFormulario by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Tipos de processo") },
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
            if (tiposProcesso.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhum tipo de processo cadastrado ainda.\nToque em \"+ Novo tipo\" para começar.",
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
                    items(tiposProcesso, key = { it.id }) { tipo ->
                        TipoProcessoCard(
                            tipoProcesso = tipo,
                            onClick = {
                                tipoEmEdicao = tipo
                                mostrarFormulario = true
                            }
                        )
                    }
                }
            }

            PillButton(
                text = "+ Novo tipo",
                onClick = {
                    tipoEmEdicao = null
                    mostrarFormulario = true
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
    }

    if (mostrarFormulario) {
        TipoProcessoFormDialog(
            tipoProcessoInicial = tipoEmEdicao,
            onDismiss = { mostrarFormulario = false },
            onSalvar = { nome, diasAtencao, diasCritico ->
                viewModel.salvar(tipoEmEdicao?.id, nome, diasAtencao, diasCritico)
                mostrarFormulario = false
            },
            onExcluir = tipoEmEdicao?.let { tipo ->
                {
                    viewModel.excluir(tipo)
                    mostrarFormulario = false
                }
            }
        )
    }
}

@Composable
private fun TipoProcessoCard(tipoProcesso: TipoProcessoEntity, onClick: () -> Unit) {
    SideBarCard(
        barColor = Indigo600,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tipoProcesso.nome, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill(
                    text = "${tipoProcesso.diasAlertaAtencao}d atenção",
                    containerColor = AmareloAtencaoPastel,
                    contentColor = AmareloAtencao
                )
                StatusPill(
                    text = "${tipoProcesso.diasAlertaCritico}d crítico",
                    containerColor = VermelhoCriticoPastel,
                    contentColor = VermelhoCritico
                )
            }
        }
        NavigationChevron()
    }
}
```

- [ ] **Step 4: Gatear `MaisScreen` a admin e adicionar o card de Tipo de Processo**

Em `MaisScreen.kt`, adicione os imports `com.josiel.organizeprocesso.data.remote.SupabaseSessionManager`, `com.josiel.organizeprocesso.domain.model.Papel`, e `androidx.compose.material.icons.automirrored.filled.List`. Troque a assinatura e o corpo:

```kotlin
@Composable
fun MaisScreen(
    onCadastroFasesClick: () -> Unit,
    onCadastroTiposProcessoClick: () -> Unit,
    onSairClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ehAdmin = SupabaseSessionManager.perfilAtual?.papel == Papel.ADMIN
    val opcoes = listOf(
        OpcaoMais("Cadastro de Fases", Icons.Filled.DateRange, habilitado = ehAdmin, onClick = onCadastroFasesClick),
        OpcaoMais("Cadastro de Tipos de Processo", Icons.AutoMirrored.Filled.List, habilitado = ehAdmin, onClick = onCadastroTiposProcessoClick),
        OpcaoMais("Status de sincronização", Icons.Filled.Refresh, habilitado = false) {},
        OpcaoMais("Configurações", Icons.Filled.Settings, habilitado = false) {},
        OpcaoMais("Sair", Icons.AutoMirrored.Filled.ExitToApp, habilitado = true, onClick = onSairClick)
    )
    ...
```

(o resto da função continua igual — só a lista `opcoes` muda).

- [ ] **Step 5: Adicionar a rota**

Em `Routes.kt`, adicione:

```kotlin
@Serializable
object CadastroTiposProcesso
```

- [ ] **Step 6: Ligar a rota e o novo parâmetro em `AppNavHost`**

Em `AppNavHost.kt`, adicione o import `com.josiel.organizeprocesso.ui.cadastro.TiposProcessoScreen`. Troque a chamada de `MaisScreen`:

```kotlin
            composable<Mais> {
                MaisScreen(
                    onCadastroFasesClick = { navController.navigate(CadastroFases) },
                    onCadastroTiposProcessoClick = { navController.navigate(CadastroTiposProcesso) },
                    onSairClick = { ... já existe, sem mudança ... }
                )
            }
```

E adicione a rota depois de `composable<CadastroFases>`:

```kotlin
            composable<CadastroTiposProcesso> {
                TiposProcessoScreen(onBackClick = { navController.navigateUp() })
            }
```

- [ ] **Step 7: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/TipoProcessoCadastroViewModel.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/TipoProcessoFormDialog.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/TiposProcessoScreen.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/MaisScreen.kt \
        app/src/main/java/com/josiel/organizeprocesso/navigation/Routes.kt \
        app/src/main/java/com/josiel/organizeprocesso/navigation/AppNavHost.kt
git commit -m "feat: add Tipo de Processo cadastro screen, gate admin-only cards in Mais"
```

---

## Task 7: `ProcessoFormViewModel`/`ProcessoFormScreen` — dropdown de tipo, criação admin-only, edição somente-leitura

**Files:**
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoFormViewModel.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoFormScreen.kt`

**Interfaces:**
- Consumes: `TipoProcessoRepository` (existente), `podeCriarProcesso`/`podeEditarProcesso` (Task 3).

- [ ] **Step 1: Atualizar `ProcessoFormUiState` e o ViewModel**

Em `ProcessoFormViewModel.kt`, troque o campo `tipo: String = ""` por `tipoProcessoId: String? = null` e adicione `somenteLeitura: Boolean = false` em `ProcessoFormUiState`; ajuste `valido`:

```kotlin
data class ProcessoFormUiState(
    val carregando: Boolean = true,
    val numero: String = "",
    val objeto: String = "",
    val descricao: String = "",
    val orgaoDemandante: String = "",
    val tipoProcessoId: String? = null,
    val dataAbertura: LocalDate = LocalDate.now(),
    val faseSelecionadaId: String? = null,
    val statusGeral: StatusGeralProcesso = StatusGeralProcesso.EM_ANDAMENTO,
    val itens: List<ItemEntity> = emptyList(),
    val fasesPercorridasNomes: Set<String> = emptySet(),
    val somenteLeitura: Boolean = false
) {
    val valorEstimadoTotal: Double
        get() = itens.sumOf { it.quantidade * it.valorEstimadoUnit }

    val valido: Boolean
        get() = numero.isNotBlank() && objeto.isNotBlank() && faseSelecionadaId != null && tipoProcessoId != null
}
```

Adicione os imports `com.josiel.organizeprocesso.data.local.TipoProcessoEntity`, `com.josiel.organizeprocesso.data.repository.TipoProcessoRepository`, `com.josiel.organizeprocesso.domain.usecase.podeCriarProcesso`, `com.josiel.organizeprocesso.domain.usecase.podeEditarProcesso`. Dentro da classe, junto dos outros repositórios:

```kotlin
    private val tipoProcessoRepository = TipoProcessoRepository(database.tipoProcessoDao(), SupabaseSessionManager.client)

    val tiposProcesso: StateFlow<List<TipoProcessoEntity>> = tipoProcessoRepository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val sessao = SupabaseSessionManager.perfilAtual
```

No `init`, branch de edição, troque `tipo = processo.tipoProcessoId` por `tipoProcessoId = processo.tipoProcessoId` e adicione o cálculo de `somenteLeitura`:

```kotlin
                if (processo != null) {
                    processoOriginal = processo
                    val somenteLeitura = sessao == null ||
                        !podeEditarProcesso(sessao.papel, processo.responsavelId, sessao.id)
                    _uiState.value = ProcessoFormUiState(
                        carregando = false,
                        numero = processo.numero,
                        objeto = processo.objeto,
                        descricao = processo.descricao,
                        orgaoDemandante = processo.orgaoDemandante,
                        tipoProcessoId = processo.tipoProcessoId,
                        dataAbertura = processo.dataAbertura,
                        faseSelecionadaId = processo.faseAtualId,
                        statusGeral = processo.statusGeral,
                        itens = itens,
                        fasesPercorridasNomes = fasesPercorridasNomes,
                        somenteLeitura = somenteLeitura
                    )
                } else {
                    _uiState.value = _uiState.value.copy(carregando = false)
                }
```

No branch de criação (`else` do `if (processoId != null)`), calcule também:

```kotlin
        } else {
            val podeCriar = sessao?.let { podeCriarProcesso(it.papel) } ?: false
            _uiState.value = _uiState.value.copy(carregando = false, somenteLeitura = !podeCriar)
        }
```

Troque `fun atualizarTipo(valor: String)` por:

```kotlin
    fun atualizarTipo(tipoProcessoId: String) {
        _uiState.value = _uiState.value.copy(tipoProcessoId = tipoProcessoId)
    }
```

E em `salvar()`, adicione a guarda no topo e troque `tipoProcessoId = estado.tipo` por `tipoProcessoId = estado.tipoProcessoId.orEmpty()` nas duas chamadas (`atualizar`/`criar`):

```kotlin
    fun salvar(onSalvo: (String) -> Unit) {
        val estado = _uiState.value
        if (estado.somenteLeitura) return
        val faseId = estado.faseSelecionadaId ?: return
        if (!estado.valido) return
        ...
```

- [ ] **Step 2: Atualizar `ProcessoFormScreen`**

Adicione o import `com.josiel.organizeprocesso.data.local.TipoProcessoEntity`. Colete `val tiposProcesso by viewModel.tiposProcesso.collectAsState()` junto de `val fases by ...`.

Troque o campo de texto livre "Tipo" por um dropdown, logo depois do `DropdownField` de "Fase inicial":

```kotlin
            DropdownField(
                label = "Tipo de processo",
                opcoes = tiposProcesso,
                selecionado = tiposProcesso.find { it.id == estado.tipoProcessoId },
                rotulo = { it.nome },
                onSelecionado = { viewModel.atualizarTipo(it.id) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !estado.somenteLeitura
            )
            if (tiposProcesso.isEmpty()) {
                Text(
                    "Cadastre ao menos um tipo de processo em Mais > Cadastro de Tipos de Processo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
```

(Remova o antigo `OutlinedTextField` de `estado.tipo` / `viewModel::atualizarTipo` de texto livre.)

`DropdownField` ainda não tem parâmetro `enabled` — adicione-o em `app/src/main/java/com/josiel/organizeprocesso/ui/components/DropdownField.kt`, com `default = true`, e passe para o `ExposedDropdownMenuBox`/`OutlinedTextField` internos:

```kotlin
@Composable
fun <T> DropdownField(
    label: String,
    opcoes: List<T>,
    selecionado: T?,
    rotulo: (T) -> String,
    onSelecionado: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expandido by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expandido && enabled,
        onExpandedChange = { if (enabled) expandido = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selecionado?.let(rotulo).orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido && enabled) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expandido && enabled,
            onDismissRequest = { expandido = false }
        ) {
            opcoes.forEach { opcao ->
                DropdownMenuItem(
                    text = { Text(rotulo(opcao)) },
                    onClick = {
                        onSelecionado(opcao)
                        expandido = false
                    }
                )
            }
        }
    }
}
```

Aplique `enabled = !estado.somenteLeitura` também no `DropdownField` de "Fase inicial" e no de "Status geral", e em cada `OutlinedTextField` do formulário (numero, objeto, descricao, orgaoDemandante) e no `DateField` de "Data de abertura". Gatear o botão "+ Adicionar item" e o `onClick` de cada `ItemRow` com `enabled = !estado.somenteLeitura` (o `PillButton`/`Row.clickable` já aceitam desabilitar via não chamar `onClick` — para o `PillButton` "+ Adicionar item", envolva com `if (!estado.somenteLeitura) { PillButton(...) }`; para `ItemRow`, mude `onClick` para `if (estado.somenteLeitura) {} else { itemEmEdicao = item; mostrarFormularioItem = true }`).

No `topBar`, gate o ícone de salvar:

```kotlin
                actions = {
                    IconButton(
                        enabled = estado.valido && !estado.somenteLeitura,
                        onClick = { viewModel.salvar(onSalvo) }
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Salvar")
                    }
                },
```

E logo abaixo do `TopAppBar`, dentro do `Column` de conteúdo, no topo (antes do campo "Número"), adicione o aviso quando somente leitura:

```kotlin
            if (estado.somenteLeitura) {
                Text(
                    "Somente leitura — você não pode editar este processo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
```

- [ ] **Step 3: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoFormViewModel.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoFormScreen.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/components/DropdownField.kt
git commit -m "feat: tipo de processo dropdown, admin-only create, read-only edit mode"
```

---

## Task 8: `ProcessoListViewModel`/`ProcessosScreen` — designação real, segundo semáforo, criar admin-only

**Files:**
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoListViewModel.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessosScreen.kt`

**Interfaces:**
- Consumes: `calcularSemaforo` (Task 2), `podeCriarProcesso` (Task 3), `TipoProcessoRepository` (existente).
- Produces: `ProcessoListItem` ganha `statusSemaforoDesignacao: StatusSemaforo` e `diasDesdeDesignado: Long?`; `responsavelNome` passa a ser não-nulo (`"Não designado"` no lugar de `null`).

O código atual resolve `responsavelNome` a partir de `historico.responsavelId` (o EXECUTOR da passagem de fase — um conceito diferente, ver `ProcessoFaseHistoricoEntity`, doc-comment) em vez de `processo.responsavelId` (a designação do processo, o que a spec 2B seção 4.2 pede). Esta task corrige isso.

- [ ] **Step 1: Reescrever o `combine` do ViewModel**

```kotlin
package com.josiel.organizeprocesso.ui.processos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.ProcessoEntity
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import com.josiel.organizeprocesso.data.repository.ProcessoRepository
import com.josiel.organizeprocesso.data.repository.TipoProcessoRepository
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import com.josiel.organizeprocesso.domain.usecase.calcularSemaforo
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProcessoListItem(
    val processo: ProcessoEntity,
    val faseNome: String,
    val responsavelNome: String,
    val statusSemaforo: StatusSemaforo,
    val diasParado: Long,
    val statusSemaforoDesignacao: StatusSemaforo,
    val diasDesdeDesignado: Long?
)

/** ViewModel da lista de Processos (REQUISITOS.md, seção 8; spec do Plano 2B, seção 4.2). */
class ProcessoListViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val processoRepository = ProcessoRepository(database, SupabaseSessionManager.client)
    private val faseRepository = FaseRepository(database.faseDao(), SupabaseSessionManager.client)
    private val perfilRepository = PerfilRepository(database.perfilDao(), SupabaseSessionManager.client)
    private val tipoProcessoRepository = TipoProcessoRepository(database.tipoProcessoDao(), SupabaseSessionManager.client)
    private val historicoDao = database.processoFaseHistoricoDao()

    val itens: StateFlow<List<ProcessoListItem>> = combine(
        processoRepository.observarTodos(),
        faseRepository.observarTodas(),
        perfilRepository.observarTodos(),
        historicoDao.observarTodosAtivos(),
        tipoProcessoRepository.observarTodas()
    ) { processos, fases, perfis, historicosAtivos, tiposProcesso ->
        val faseMap = fases.associateBy { it.id }
        val perfilMap = perfis.associateBy { it.id }
        val tipoProcessoMap = tiposProcesso.associateBy { it.id }
        val historicoPorProcesso = historicosAtivos.associateBy { it.processoId }
        val hoje = LocalDate.now()

        processos.map { processo ->
            val historico = historicoPorProcesso[processo.id]
            val fase = faseMap[processo.faseAtualId]
            val diasParado = historico?.let { ChronoUnit.DAYS.between(it.dataEntrada, hoje) } ?: 0L
            val statusSemaforo = fase?.let { calcularSemaforo(diasParado, it.diasAlertaAtencao, it.diasAlertaCritico) }
                ?: StatusSemaforo.OK

            val tipoProcesso = tipoProcessoMap[processo.tipoProcessoId]
            val diasDesdeDesignado = processo.designadoEm?.let {
                ChronoUnit.DAYS.between(it.atZone(ZoneId.systemDefault()).toLocalDate(), hoje)
            }
            val statusSemaforoDesignacao = if (diasDesdeDesignado != null && tipoProcesso != null) {
                calcularSemaforo(diasDesdeDesignado, tipoProcesso.diasAlertaAtencao, tipoProcesso.diasAlertaCritico)
            } else {
                StatusSemaforo.OK
            }

            ProcessoListItem(
                processo = processo,
                faseNome = fase?.nome ?: "—",
                responsavelNome = processo.responsavelId?.let { perfilMap[it]?.nome } ?: "Não designado",
                statusSemaforo = statusSemaforo,
                diasParado = diasParado,
                statusSemaforoDesignacao = statusSemaforoDesignacao,
                diasDesdeDesignado = diasDesdeDesignado
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
```

- [ ] **Step 2: Atualizar `ProcessosScreen`**

Adicione os imports `com.josiel.organizeprocesso.data.remote.SupabaseSessionManager` e `com.josiel.organizeprocesso.domain.usecase.podeCriarProcesso`. No topo de `fun ProcessosScreen(...)`:

```kotlin
    val podeCriar = SupabaseSessionManager.perfilAtual?.papel?.let(::podeCriarProcesso) ?: false
```

Troque o `PillButton` final ("+ Novo processo") para só aparecer quando `podeCriar`:

```kotlin
        if (podeCriar) {
            PillButton(
                text = "+ Novo processo",
                onClick = onNovoProcessoClick,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
```

Em `ProcessoCard`, troque o bloco condicional de `responsavelNome` (que hoje só aparece se não-nulo) para sempre aparecer, e adicione os dois semáforos lado a lado logo abaixo:

```kotlin
@Composable
private fun ProcessoCard(item: ProcessoListItem, onClick: () -> Unit) {
    val corBarra = when (item.statusSemaforo) {
        StatusSemaforo.OK -> VerdeOk
        StatusSemaforo.ATENCAO -> AmareloAtencao
        StatusSemaforo.CRITICO -> VermelhoCritico
    }
    SideBarCard(
        barColor = corBarra,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.processo.numero,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                FasePill(nome = item.faseNome, faseId = item.processo.faseAtualId)
            }
            Text(
                item.processo.objeto,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            Text(
                "Responsável: ${item.responsavelNome}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                Column {
                    Text("Fase", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SemaforoPill(status = item.statusSemaforo)
                }
                Column {
                    Text("Designação", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SemaforoPill(status = item.statusSemaforoDesignacao)
                }
            }
        }
        NavigationChevron()
    }
}
```

Adicione o import `com.josiel.organizeprocesso.ui.components.SemaforoPill`.

- [ ] **Step 3: Verificar que o projeto compila e os testes passam**

Run: `./gradlew.bat :app:compileDebugKotlin`
Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` nos dois.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoListViewModel.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessosScreen.kt
git commit -m "fix: resolve responsavel from processo designation, add designation semaforo, gate create button"
```

---

## Task 9: `ProcessoDetalheViewModel`/`ProcessoDetalheScreen` — designação, segundo semáforo, ação contextual

**Files:**
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoDetalheViewModel.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoDetalheScreen.kt`

**Interfaces:**
- Consumes: `calcularSemaforo` (Task 2), `podeEditarProcesso`/`acaoDesignacaoDisponivel`/`AcaoDesignacao` (Task 3), `ProcessoRepository.designar()` (Task 5).

- [ ] **Step 1: Reescrever `ProcessoDetalheViewModel`**

```kotlin
package com.josiel.organizeprocesso.ui.processos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.ItemEntity
import com.josiel.organizeprocesso.data.local.PerfilEntity
import com.josiel.organizeprocesso.data.local.ProcessoEntity
import com.josiel.organizeprocesso.data.local.ProcessoFaseHistoricoEntity
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import com.josiel.organizeprocesso.data.repository.ProcessoRepository
import com.josiel.organizeprocesso.data.repository.TipoProcessoRepository
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import com.josiel.organizeprocesso.domain.usecase.AcaoDesignacao
import com.josiel.organizeprocesso.domain.usecase.acaoDesignacaoDisponivel
import com.josiel.organizeprocesso.domain.usecase.calcularSemaforo
import com.josiel.organizeprocesso.domain.usecase.podeEditarProcesso
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoricoItemUi(
    val historico: ProcessoFaseHistoricoEntity,
    val faseNome: String,
    val responsavelNome: String?
)

data class ProcessoDetalheUiState(
    val carregando: Boolean = true,
    val processo: ProcessoEntity? = null,
    val faseAtualNome: String = "",
    val tipoProcessoNome: String = "",
    val itens: List<ItemEntity> = emptyList(),
    val historico: List<HistoricoItemUi> = emptyList(),
    val designadoParaNome: String? = null,
    val designadoPorNome: String? = null,
    val statusSemaforoDesignacao: StatusSemaforo = StatusSemaforo.OK,
    val diasDesdeDesignado: Long? = null,
    val acaoDesignacao: AcaoDesignacao = AcaoDesignacao.NENHUMA,
    val podeEditar: Boolean = false,
    val perfisAtivos: List<PerfilEntity> = emptyList()
)

/** ViewModel de Detalhe do Processo (spec do Plano 2B, seção 4.3). */
class ProcessoDetalheViewModel(
    application: Application,
    private val processoId: String
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val processoRepository = ProcessoRepository(database, SupabaseSessionManager.client)
    private val faseRepository = FaseRepository(database.faseDao(), SupabaseSessionManager.client)
    private val perfilRepository = PerfilRepository(database.perfilDao(), SupabaseSessionManager.client)
    private val tipoProcessoRepository = TipoProcessoRepository(database.tipoProcessoDao(), SupabaseSessionManager.client)
    private val historicoDao = database.processoFaseHistoricoDao()

    val uiState: StateFlow<ProcessoDetalheUiState> = combine(
        processoRepository.observarPorId(processoId),
        processoRepository.observarItens(processoId),
        historicoDao.observarPorProcesso(processoId),
        faseRepository.observarTodas(),
        combine(perfilRepository.observarTodos(), tipoProcessoRepository.observarTodas()) { perfis, tiposProcesso ->
            perfis to tiposProcesso
        }
    ) { processo, itens, historico, fases, perfisETipos ->
        val (perfis, tiposProcesso) = perfisETipos
        val faseMap = fases.associateBy { it.id }
        val perfilMap = perfis.associateBy { it.id }
        val tipoProcessoMap = tiposProcesso.associateBy { it.id }
        val sessao = SupabaseSessionManager.perfilAtual

        val tipoProcesso = processo?.let { tipoProcessoMap[it.tipoProcessoId] }
        val diasDesdeDesignado = processo?.designadoEm?.let {
            ChronoDaysBetween(it.atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now())
        }
        val statusSemaforoDesignacao = if (diasDesdeDesignado != null && tipoProcesso != null) {
            calcularSemaforo(diasDesdeDesignado, tipoProcesso.diasAlertaAtencao, tipoProcesso.diasAlertaCritico)
        } else {
            StatusSemaforo.OK
        }

        ProcessoDetalheUiState(
            carregando = false,
            processo = processo,
            faseAtualNome = processo?.let { faseMap[it.faseAtualId]?.nome } ?: "",
            tipoProcessoNome = tipoProcesso?.nome ?: "",
            itens = itens,
            historico = historico
                .sortedByDescending { it.dataEntrada }
                .map { entrada ->
                    HistoricoItemUi(
                        historico = entrada,
                        faseNome = faseMap[entrada.faseId]?.nome ?: "—",
                        responsavelNome = entrada.responsavelId?.let { perfilMap[it]?.nome }
                    )
                },
            designadoParaNome = processo?.responsavelId?.let { perfilMap[it]?.nome },
            designadoPorNome = processo?.designadoPor?.let { perfilMap[it]?.nome },
            statusSemaforoDesignacao = statusSemaforoDesignacao,
            diasDesdeDesignado = diasDesdeDesignado,
            acaoDesignacao = if (processo != null && sessao != null) {
                acaoDesignacaoDisponivel(sessao.papel, processo.responsavelId, sessao.id)
            } else {
                AcaoDesignacao.NENHUMA
            },
            podeEditar = processo != null && sessao != null &&
                podeEditarProcesso(sessao.papel, processo.responsavelId, sessao.id),
            perfisAtivos = perfis.filter { it.ativo }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProcessoDetalheUiState())

    fun designar(novoResponsavelId: String?) {
        viewModelScope.launch { processoRepository.designar(processoId, novoResponsavelId) }
    }
}

private fun ChronoDaysBetween(inicio: LocalDate, fim: LocalDate): Long =
    java.time.temporal.ChronoUnit.DAYS.between(inicio, fim)
```

- [ ] **Step 2: Adicionar a ação contextual e o `DesignarDialog` em `ProcessoDetalheScreen`**

Em `AbaDadosGerais`, adicione (depois de `CampoDado("Data de abertura", ...)`, antes do botão "Avançar fase"):

```kotlin
        if (estado.tipoProcessoNome.isNotBlank()) CampoDado("Tipo de processo", estado.tipoProcessoNome)
        CampoDado("Designado a", estado.designadoParaNome ?: "Ninguém (órfão)")
        if (estado.designadoPorNome != null) CampoDado("Designado por", estado.designadoPorNome)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Designação", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SemaforoPill(status = estado.statusSemaforoDesignacao)
            }
        }
```

E troque o final da função (o `PillButton` de "Avançar fase") para incluir o botão contextual e gatear "Avançar fase" por `podeEditar`:

```kotlin
        var mostrarDialogoDesignar by remember { mutableStateOf(false) }

        when (estado.acaoDesignacao) {
            com.josiel.organizeprocesso.domain.usecase.AcaoDesignacao.DESIGNAR -> PillButton(
                text = "Designar",
                onClick = { mostrarDialogoDesignar = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            com.josiel.organizeprocesso.domain.usecase.AcaoDesignacao.ASSUMIR -> PillButton(
                text = "Assumir processo",
                onClick = onDesignar,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            com.josiel.organizeprocesso.domain.usecase.AcaoDesignacao.DEVOLVER -> PillButton(
                text = "Devolver processo",
                onClick = { onDesignar(null) },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            com.josiel.organizeprocesso.domain.usecase.AcaoDesignacao.NENHUMA -> {}
        }

        if (estado.podeEditar) {
            PillButton(
                text = "Avançar fase",
                onClick = onAvancarFaseClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        if (mostrarDialogoDesignar) {
            DesignarDialog(
                perfis = estado.perfisAtivos,
                designacaoAtualId = estado.processo?.responsavelId,
                onDismiss = { mostrarDialogoDesignar = false },
                onConfirmar = { escolhaId ->
                    onDesignar(escolhaId)
                    mostrarDialogoDesignar = false
                }
            )
        }
```

Isso exige mudar a assinatura de `AbaDadosGerais` e de `ProcessoDetalheScreen` para receber `onDesignar: (String?) -> Unit`:

```kotlin
@Composable
fun ProcessoDetalheScreen(
    processoId: String,
    onAvancarFaseClick: () -> Unit,
    onEditarClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ...
    val onDesignar: (String?) -> Unit = { novoResponsavelId -> viewModel.designar(novoResponsavelId) }
    ...
            when (abaSelecionada) {
                0 -> AbaDadosGerais(estado, onAvancarFaseClick, onDesignar)
                ...

@Composable
private fun AbaDadosGerais(estado: ProcessoDetalheUiState, onAvancarFaseClick: () -> Unit, onDesignar: (String?) -> Unit) {
```

(Para o caso `ASSUMIR`, `onClick = onDesignar` precisa ser `onClick = { onDesignar(SupabaseSessionManager.perfilAtual?.id) }` — ajuste ao transcrever. Adicione o import `com.josiel.organizeprocesso.data.remote.SupabaseSessionManager`.)

Adicione, no fim do arquivo, `DesignarDialog` (segue o mesmo padrão inline de `RadioButton` de `FaseDestinoDialog`, spec seção 5 — nunca `DropdownField` dentro de `AlertDialog`):

```kotlin
@Composable
private fun DesignarDialog(
    perfis: List<com.josiel.organizeprocesso.data.local.PerfilEntity>,
    designacaoAtualId: String?,
    onDismiss: () -> Unit,
    onConfirmar: (novoResponsavelId: String?) -> Unit
) {
    var escolhaId by remember { mutableStateOf(designacaoAtualId) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Designar processo") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color.Transparent)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .androidx.compose.foundation.selection.selectable(
                            selected = escolhaId == null,
                            onClick = { escolhaId = null }
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(selected = escolhaId == null, onClick = { escolhaId = null })
                    Text("Ninguém (tornar órfão)")
                }
                perfis.forEach { perfil ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .androidx.compose.foundation.selection.selectable(
                                selected = escolhaId == perfil.id,
                                onClick = { escolhaId = perfil.id }
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(selected = escolhaId == perfil.id, onClick = { escolhaId = perfil.id })
                        Text(perfil.nome)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirmar(escolhaId) }) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
```

(Os caminhos totalmente qualificados acima evitam colidir com o `import androidx.compose.material3.Text`/`TextButton`/`Column`/`Row` já existentes no arquivo — ao transcrever, prefira adicionar os imports que faltam — `androidx.compose.foundation.selection.selectable`, `androidx.compose.material3.AlertDialog`, `androidx.compose.material3.RadioButton`, `com.josiel.organizeprocesso.data.local.PerfilEntity` — no topo do arquivo e remover a qualificação completa, mantendo o arquivo legível como o resto do projeto.)

- [ ] **Step 3: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoDetalheViewModel.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoDetalheScreen.kt
git commit -m "feat: designation info, second semaforo, contextual designacao action on Processo detail"
```

---

## Task 10: `AvancarFaseViewModel`/`AvancarFaseScreen` — RPC, renomeação de executor, diligências

**Files:**
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/AvancarFaseViewModel.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/AvancarFaseScreen.kt`

**Interfaces:**
- Consumes: `HistoricoFaseRepository.mudarFase(...)` (Task 5, sem `dataEntrada`), `DiligenciaRepository` (Task 4), `podeEditarProcesso` (Task 3).

- [ ] **Step 1: Reescrever `AvancarFaseViewModel`**

```kotlin
package com.josiel.organizeprocesso.ui.processos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.DiligenciaEntity
import com.josiel.organizeprocesso.data.local.FaseEntity
import com.josiel.organizeprocesso.data.local.PerfilEntity
import com.josiel.organizeprocesso.data.local.ProcessoEntity
import com.josiel.organizeprocesso.data.local.ProcessoFaseHistoricoEntity
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.DiligenciaRepository
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.data.repository.HistoricoFaseRepository
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import com.josiel.organizeprocesso.data.repository.ProcessoRepository
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import com.josiel.organizeprocesso.domain.usecase.calcularSemaforo
import com.josiel.organizeprocesso.domain.usecase.podeEditarProcesso
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val LIMITE_CARACTERES_OBSERVACAO = 500

data class AvancarFaseUiState(
    val carregando: Boolean = true,
    val processo: ProcessoEntity? = null,
    val faseAtual: FaseEntity? = null,
    val historicoAtual: ProcessoFaseHistoricoEntity? = null,
    val statusSemaforo: StatusSemaforo = StatusSemaforo.OK,
    val fases: List<FaseEntity> = emptyList(),
    val perfis: List<PerfilEntity> = emptyList(),
    val observacao: String = "",
    val executorId: String? = null,
    val prazoLimite: LocalDate? = null,
    val notificarPrazo: Boolean = false,
    val podeEditar: Boolean = false
)

/** ViewModel da tela Avançar/Retroceder Fase (spec do Plano 2B, seção 4.4). */
class AvancarFaseViewModel(
    application: Application,
    private val processoId: String
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val processoRepository = ProcessoRepository(database, SupabaseSessionManager.client)
    private val faseRepository = FaseRepository(database.faseDao(), SupabaseSessionManager.client)
    private val perfilRepository = PerfilRepository(database.perfilDao(), SupabaseSessionManager.client)
    private val historicoRepository = HistoricoFaseRepository(database, SupabaseSessionManager.client)
    private val diligenciaRepository = DiligenciaRepository(database.diligenciaDao(), SupabaseSessionManager.client)

    private val _uiState = MutableStateFlow(AvancarFaseUiState())
    val uiState: StateFlow<AvancarFaseUiState> = _uiState.asStateFlow()

    val diligencias: StateFlow<List<DiligenciaEntity>> = historicoRepository.observarAtivoPorProcesso(processoId)
        .flatMapLatest { historico -> historico?.let { diligenciaRepository.observarPorHistorico(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var sementeAplicada = false
    private var historicoSincronizado: String? = null

    init {
        viewModelScope.launch {
            combine(
                processoRepository.observarPorId(processoId),
                historicoRepository.observarAtivoPorProcesso(processoId),
                faseRepository.observarTodas(),
                perfilRepository.observarTodos()
            ) { processo, historico, fases, perfis ->
                val faseAtual = processo?.let { p -> fases.find { it.id == p.faseAtualId } }
                val diasParado = historico?.let { ChronoUnit.DAYS.between(it.dataEntrada, LocalDate.now()) } ?: 0L
                val semaforo = faseAtual?.let { calcularSemaforo(diasParado, it.diasAlertaAtencao, it.diasAlertaCritico) }
                    ?: StatusSemaforo.OK
                val sessao = SupabaseSessionManager.perfilAtual
                val podeEditar = processo != null && sessao != null &&
                    podeEditarProcesso(sessao.papel, processo.responsavelId, sessao.id)
                Sextupla(processo, faseAtual, historico, semaforo, fases to perfis, podeEditar)
            }.collect { (processo, faseAtual, historico, semaforo, fasesPerfis, podeEditar) ->
                val estadoAtual = _uiState.value
                _uiState.value = estadoAtual.copy(
                    carregando = false,
                    processo = processo,
                    faseAtual = faseAtual,
                    historicoAtual = historico,
                    statusSemaforo = semaforo,
                    fases = fasesPerfis.first,
                    perfis = fasesPerfis.second,
                    observacao = if (!sementeAplicada && historico != null) historico.observacoes else estadoAtual.observacao,
                    executorId = if (!sementeAplicada && historico != null) historico.responsavelId else estadoAtual.executorId,
                    prazoLimite = if (!sementeAplicada && historico != null) historico.prazoLimite else estadoAtual.prazoLimite,
                    notificarPrazo = if (!sementeAplicada && historico != null) historico.notificarPrazo else estadoAtual.notificarPrazo,
                    podeEditar = podeEditar
                )
                if (historico != null) {
                    sementeAplicada = true
                    if (historicoSincronizado != historico.id) {
                        historicoSincronizado = historico.id
                        diligenciaRepository.sincronizar(historico.id)
                    }
                }
            }
        }
    }

    fun atualizarObservacao(valor: String) {
        if (valor.length <= LIMITE_CARACTERES_OBSERVACAO) {
            _uiState.value = _uiState.value.copy(observacao = valor)
        }
    }

    fun atualizarExecutor(perfilId: String?) {
        _uiState.value = _uiState.value.copy(executorId = perfilId)
    }

    fun atualizarPrazoLimite(data: LocalDate?) {
        _uiState.value = _uiState.value.copy(prazoLimite = data)
    }

    fun atualizarNotificarPrazo(valor: Boolean) {
        _uiState.value = _uiState.value.copy(notificarPrazo = valor)
    }

    fun salvarEntradaAtual() {
        val estado = _uiState.value
        if (!estado.podeEditar) return
        val historico = estado.historicoAtual ?: return
        viewModelScope.launch {
            historicoRepository.salvarEntradaAtual(
                historico = historico,
                responsavelId = estado.executorId,
                prazoLimite = estado.prazoLimite,
                notificarPrazo = estado.notificarPrazo,
                novaObservacao = estado.observacao
            )
        }
    }

    fun mudarFase(faseDestinoId: String, motivoRetorno: String?, onConcluido: () -> Unit) {
        val estado = _uiState.value
        if (!estado.podeEditar) return
        val processo = estado.processo ?: return
        viewModelScope.launch {
            historicoRepository.mudarFase(
                processo = processo,
                faseDestinoId = faseDestinoId,
                executorId = estado.executorId,
                prazoLimite = estado.prazoLimite,
                motivoRetorno = motivoRetorno,
                notificarPrazo = estado.notificarPrazo
            )
            onConcluido()
        }
    }

    fun registrarDiligencia(conteudo: String) {
        if (conteudo.isBlank()) return
        val historicoId = _uiState.value.historicoAtual?.id ?: return
        viewModelScope.launch { diligenciaRepository.registrar(historicoId, conteudo) }
    }
}

private data class Sextupla<A, B, C, D, E, F>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F)
```

- [ ] **Step 2: Reescrever `AvancarFaseScreen`**

Substitua as referências a `estado.pessoas`/`estado.responsavelId`/`viewModel::atualizarResponsavel` por `estado.perfis`/`estado.executorId`/`viewModel::atualizarExecutor`; gateie os campos editáveis e os botões de ação por `estado.podeEditar`; remova o `DateField` de "Data de entrada" do `FaseDestinoDialog` (a RPC não aceita mais); adicione a seção de diligências. Adicione os imports `com.josiel.organizeprocesso.data.local.DiligenciaEntity` e `java.time.format.DateTimeFormatter`.

Troque o `topBar` para gatear o ícone de salvar:

```kotlin
                actions = {
                    IconButton(
                        enabled = estado.historicoAtual != null && estado.podeEditar,
                        onClick = { viewModel.salvarEntradaAtual() }
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Salvar")
                    }
                },
```

No corpo, depois do `SemaforoPill` de status, adicione (se `!estado.podeEditar`) o aviso somente-leitura:

```kotlin
            if (!estado.podeEditar) {
                Text(
                    "Somente leitura — você não pode editar esta fase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
```

Aplique `enabled = estado.podeEditar` no `OutlinedTextField` de observação, no `DropdownField` de "Responsável" (trocando label para "Executor desta passagem" e `estado.pessoas`/`estado.responsavelId`/`viewModel::atualizarResponsavel` por `estado.perfis`/`estado.executorId`/`viewModel::atualizarExecutor`), no `DateField` de prazo limite, no `AppToggle` de notificar prazo, e nos dois `PillButton` "Avançar fase"/"Retornar fase" (envolva com `if (estado.podeEditar) { ... }`).

No `FaseDestinoDialog`, remova o parâmetro/estado `dataEntrada` e o `DateField` correspondente; a assinatura de `onConfirmar` passa a ser `(fase: FaseEntity, motivo: String?) -> Unit`; ajuste a chamada em `AvancarFaseScreen`:

```kotlin
    dialogoFase?.let { tipo ->
        FaseDestinoDialog(
            tipo = tipo,
            fases = estado.fases.filter { it.id != estado.faseAtual?.id },
            onDismiss = { dialogoFase = null },
            onConfirmar = { faseDestino, motivo ->
                viewModel.mudarFase(faseDestino.id, motivo) {
                    dialogoFase = null
                    onBackClick()
                }
            }
        )
    }
```

E dentro de `FaseDestinoDialog`, remova `var dataEntrada by remember { ... }` e o bloco `DateField(label = "Data de entrada", ...)`, e troque `onConfirmar(fase: FaseEntity, dataEntrada: LocalDate, motivo: String?) -> Unit` no parâmetro da função e na chamada final (`onConfirmar(it, dataEntrada, ...)` vira `onConfirmar(it, ...)`) por:

```kotlin
private fun FaseDestinoDialog(
    tipo: TipoMudancaFase,
    fases: List<FaseEntity>,
    onDismiss: () -> Unit,
    onConfirmar: (fase: FaseEntity, motivo: String?) -> Unit
) {
    var faseSelecionada by remember { mutableStateOf<FaseEntity?>(null) }
    var motivo by remember { mutableStateOf("") }
    val ehRetorno = tipo == TipoMudancaFase.RETORNAR
    ...
        confirmButton = {
            TextButton(
                enabled = faseSelecionada != null && (!ehRetorno || motivo.isNotBlank()),
                onClick = {
                    faseSelecionada?.let { onConfirmar(it, if (ehRetorno) motivo else null) }
                }
            ) { Text("Confirmar") }
        },
```

Por fim, adicione a seção de diligências no `Column` principal, depois do `Row` de "Notificar sobre prazo desta fase?" e antes da `Row` dos botões Avançar/Retornar:

```kotlin
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
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(diligencia.conteudo, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            formatoDataHora.format(diligencia.criadoEm.atZone(java.time.ZoneId.systemDefault())),
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
```

Adicione, no topo da função `AvancarFaseScreen`, junto dos outros `remember`:

```kotlin
    val diligencias by viewModel.diligencias.collectAsState()
    var mostrarDialogoDiligencia by remember { mutableStateOf(false) }
```

E, depois do bloco `dialogoFase?.let { ... }`, adicione:

```kotlin
    if (mostrarDialogoDiligencia) {
        RegistrarDiligenciaDialog(
            onDismiss = { mostrarDialogoDiligencia = false },
            onConfirmar = { conteudo ->
                viewModel.registrarDiligencia(conteudo)
                mostrarDialogoDiligencia = false
            }
        )
    }
```

E, no fim do arquivo, a nova função privada:

```kotlin
private val formatoDataHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

@Composable
private fun RegistrarDiligenciaDialog(
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
                enabled = texto.isNotBlank(),
                onClick = { onConfirmar(texto.trim()) }
            ) { Text("Registrar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
```

Adicione o import `androidx.compose.material3.HorizontalDivider`.

- [ ] **Step 3: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/ui/processos/AvancarFaseViewModel.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/processos/AvancarFaseScreen.kt
git commit -m "feat: rename executor, wire avancar_fase RPC end-to-end, add diligencias UI"
```

---

## Task 11: Verificação final

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
Expected: `BUILD SUCCESSFUL`, todos os testes das Tasks 2, 3, 4 e 5 passando (mais os testes já existentes das Tasks 3 e 5 do Plano 2A).

- [ ] **Step 3: Registrar o que fica pendente até haver acesso a um emulador/dispositivo**

Não é possível, remotamente, verificar: sincronização inicial + Realtime realmente populando o Room após um login real (Task 1); as três ações de designação (Designar, Assumir, Devolver) contra o Supabase real; `avancar_fase` via RPC de ponta a ponta (incluindo o corte do parâmetro de data customizada); diligências sendo persistidas e aparecendo em tempo quase-real; o modo somente-leitura do formulário de Processo e da tela Avançar Fase na perspectiva de um `usuario` sobre o processo de outra pessoa; os cadastros de Tipo de Processo escondidos para um `usuario`. Recomenda-se testar com pelo menos duas contas (um `admin`, um `usuario`) no mesmo projeto Supabase, exercitando as duas perspectivas de papel (spec do Plano 2B, seção 7) — isso fica registrado como pendente, não como bloqueador para fechar este plano.

- [ ] **Step 4: Commit (se necessário)**

Se os Steps 1-2 não exigiram nenhuma mudança de código, não há o que commitar — este task é só o portão de verificação final.
