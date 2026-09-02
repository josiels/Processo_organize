# Tipo de Processo Simples vs. com Etapas Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir que um Tipo de Processo seja marcado como "simples" (uma
única etapa, com fase fixa), simplificando a criação e a conclusão de
processos que não precisam do fluxo completo de múltiplas fases.

**Architecture:** Reaproveita 100% a infraestrutura existente de `Fase` /
`processo_fase_historico` / semáforo — um processo simples ainda tem
exatamente uma linha ativa de histórico, só que numa fase fixa escolhida
uma vez no cadastro do tipo, em vez de escolhida por processo. Só a UX de
criação e conclusão muda; `AvancarFaseScreen`, a aba Timeline, o Dashboard
e as regras de notificação continuam idênticos.

**Tech Stack:** Kotlin, Jetpack Compose, Room (destructive migration),
Supabase Postgrest (postgrest-kt), PostgreSQL (migração + pgTAP).

**Spec:** `docs/superpowers/specs/2026-09-02-android-tipo-processo-simples-design.md`

## Global Constraints

- Nova migração adiciona `tipos_processo.simples boolean not null default false` e `tipos_processo.fase_padrao_id uuid references public.fases (id)`, mais o constraint `tipos_processo_fase_padrao_quando_simples check (not simples or fase_padrao_id is not null)` (spec §2).
- `AppDatabase` usa `fallbackToDestructiveMigration(dropAllTables = true)` — bump de `version = 3` para `version = 4` é suficiente, sem `Migration` manual.
- Nenhuma mudança em `AvancarFaseScreen.kt`, na aba Timeline (`AbaTimeline`), no Dashboard, ou nas 4 regras de notificação do Plano 2D (spec §6-7).
- Qualquer seletor de Fase dentro de um `AlertDialog` usa lista simples + `RadioButton` (nunca `DropdownField`/`ExposedDropdownMenuBox`) — bug já documentado neste projeto de popup aninhado (`ExposedDropdownMenu` baseado em `Popup` dentro de outro `Popup`/`AlertDialog` não renderiza corretamente), ver `FaseDestinoDialog` em `AvancarFaseScreen.kt`.
- Sem `supabase/.env.local` nesta sessão — a migração e o teste pgTAP são escritos e revisados à mão, não aplicados/executados de fato contra um banco real (mesma situação do Plano 2D).
- `app/google-services.json` (placeholder, gitignored) e `local.properties` (`sdk.dir`, gitignored) precisam existir localmente neste worktree para compilar — já recriados nesta sessão.

---

## Task 1: Backend — colunas `simples`/`fase_padrao_id` em `tipos_processo`

**Files:**
- Create: `supabase/migrations/20260902170000_adicionar_tipo_simples.sql`
- Test: `supabase/tests/database/120_tipo_processo_simples.sql`

**Interfaces:**
- Produces: colunas `public.tipos_processo.simples` (boolean, not null, default false) e `public.tipos_processo.fase_padrao_id` (uuid, nullable, FK para `public.fases.id`) — consumidas pela Task 2 (`TipoProcessoDto`, campos `simples`/`fase_padrao_id` no JSON do Postgrest).

- [ ] **Step 1: Escrever a migração**

Crie `supabase/migrations/20260902170000_adicionar_tipo_simples.sql`:

```sql
-- Sub-etapa "tipo de processo simples vs. com etapas" (ver spec
-- docs/superpowers/specs/2026-09-02-android-tipo-processo-simples-design.md,
-- seção 2). Um tipo simples usa sempre a mesma fase única, escolhida no
-- cadastro do tipo em vez de escolhida por processo.
alter table public.tipos_processo
  add column simples boolean not null default false,
  add column fase_padrao_id uuid references public.fases (id);

alter table public.tipos_processo
  add constraint tipos_processo_fase_padrao_quando_simples
  check (not simples or fase_padrao_id is not null);
```

- [ ] **Step 2: Escrever o teste pgTAP**

Crie `supabase/tests/database/120_tipo_processo_simples.sql`:

```sql
begin;
select plan(5);

select has_column('public', 'tipos_processo', 'simples', 'tipos_processo.simples should exist');
select has_column('public', 'tipos_processo', 'fase_padrao_id', 'tipos_processo.fase_padrao_id should exist');

insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values ('00000000-0000-0000-0000-000000000002', 'admin.simples@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values ('10000000-0000-0000-0000-000000000001', 'Organização Simples');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'admin', 'Admin Simples');

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;

insert into public.fases (id, organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico)
values ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Execução', 1, 5, 10);

select throws_ok(
  $$insert into public.tipos_processo (organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico, simples)
    values ('10000000-0000-0000-0000-000000000001', 'Responder fornecedor', 5, 10, true)$$,
  '23514',
  null,
  'tipos_processo simples=true sem fase_padrao_id viola o check constraint'
);

insert into public.tipos_processo (organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico, simples, fase_padrao_id)
values ('10000000-0000-0000-0000-000000000001', 'Responder fornecedor', 5, 10, true, '20000000-0000-0000-0000-000000000001');
select ok(true, 'tipos_processo simples=true com fase_padrao_id válida é aceito');

insert into public.tipos_processo (organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico)
values ('10000000-0000-0000-0000-000000000001', 'Instruir processo', 10, 20);
select results_eq(
  $$select simples from public.tipos_processo where nome = 'Instruir processo'$$,
  array[false],
  'tipos_processo sem simples especificado assume default false'
);

reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
```

- [ ] **Step 3: Revisar manualmente (sem banco real nesta sessão)**

Sem `supabase/.env.local` nesta sessão, `node scripts/run_sql.mjs supabase/migrations/20260902170000_adicionar_tipo_simples.sql` não pode ser executado de fato. Revise à mão: sintaxe do `alter table`/`add constraint`, nome do constraint sem colisão com nenhum outro já existente no projeto (`grep -r "add constraint" supabase/migrations/`), e a expressão `not simples or fase_padrao_id is not null` cobre os 3 casos (simples=false+fase_padrao_id=null → ok; simples=false+fase_padrao_id=algo → ok, sem uso mas não é erro; simples=true+fase_padrao_id=null → viola; simples=true+fase_padrao_id=algo → ok).

- [ ] **Step 4: Commit**

```bash
git add supabase/migrations/20260902170000_adicionar_tipo_simples.sql supabase/tests/database/120_tipo_processo_simples.sql
git commit -m "feat: add simples/fase_padrao_id columns to tipos_processo"
```

---

## Task 2: Room, DTO e Repository — `TipoProcessoEntity`/`TipoProcessoDto`/`TipoProcessoRepository`

**Files:**
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/local/TipoProcessoEntity.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/TipoProcessoDto.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/repository/TipoProcessoRepository.kt`

**Interfaces:**
- Consumes: colunas `simples`/`fase_padrao_id` da Task 1.
- Produces: `TipoProcessoEntity(simples: Boolean, fasePadraoId: String?)` e `TipoProcessoRepository.salvar(id, nome, diasAlertaAtencao, diasAlertaCritico, simples: Boolean, fasePadraoId: String?, organizacaoId)` — consumidos pelas Tasks 3, 4 e 5.

- [ ] **Step 1: Atualizar `TipoProcessoEntity`**

Em `app/src/main/java/com/josiel/organizeprocesso/data/local/TipoProcessoEntity.kt`, substitua o conteúdo por:

```kotlin
package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache local de `public.tipos_processo` (backend) — catálogo cadastrado
 * pelo admin. `simples`/`fasePadraoId`: ver spec
 * docs/superpowers/specs/2026-09-02-android-tipo-processo-simples-design.md.
 */
@Entity(tableName = "tipos_processo")
data class TipoProcessoEntity(
    @PrimaryKey val id: String,
    val organizacaoId: String,
    val nome: String,
    val diasAlertaAtencao: Int,
    val diasAlertaCritico: Int,
    val simples: Boolean,
    val fasePadraoId: String?
)
```

- [ ] **Step 2: Bump da versão do Room**

Em `app/src/main/java/com/josiel/organizeprocesso/data/local/AppDatabase.kt`, altere `version = 3` para `version = 4` (linha 21 do arquivo atual, dentro da anotação `@Database`). Nenhuma outra mudança neste arquivo.

- [ ] **Step 3: Atualizar `TipoProcessoDto`**

Substitua `app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/TipoProcessoDto.kt` por:

```kotlin
package com.josiel.organizeprocesso.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TipoProcessoDto(
    val id: String,
    @SerialName("organizacao_id") val organizacaoId: String,
    val nome: String,
    @SerialName("dias_alerta_atencao") val diasAlertaAtencao: Int,
    @SerialName("dias_alerta_critico") val diasAlertaCritico: Int,
    val simples: Boolean = false,
    @SerialName("fase_padrao_id") val fasePadraoId: String? = null
)
```

- [ ] **Step 4: Atualizar `TipoProcessoRepository`**

Substitua `app/src/main/java/com/josiel/organizeprocesso/data/repository/TipoProcessoRepository.kt` por:

```kotlin
package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.TipoProcessoDao
import com.josiel.organizeprocesso.data.local.TipoProcessoEntity
import com.josiel.organizeprocesso.data.remote.dto.TipoProcessoDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Escreve direto no Postgrest (admin-only no backend); Room é só cache de leitura. */
class TipoProcessoRepository(
    private val dao: TipoProcessoDao,
    private val client: SupabaseClient
) {
    fun observarTodas(): Flow<List<TipoProcessoEntity>> = dao.observarTodas()

    suspend fun sincronizar() {
        val dtos = client.postgrest["tipos_processo"].select().decodeList<TipoProcessoDto>()
        dao.upsertTodos(dtos.map { it.paraEntity() })
    }

    suspend fun salvar(
        id: String?,
        nome: String,
        diasAlertaAtencao: Int,
        diasAlertaCritico: Int,
        organizacaoId: String,
        // Defaults preservam a chamada existente em TipoProcessoCadastroViewModel
        // até a Task 3 atualizá-la para passar os dois explicitamente — sem
        // isso, o build ficaria quebrado entre a Task 2 e a Task 3.
        simples: Boolean = false,
        fasePadraoId: String? = null
    ) {
        val linha = buildJsonObject {
            put("id", id ?: UUID.randomUUID().toString())
            put("organizacao_id", organizacaoId)
            put("nome", nome)
            put("dias_alerta_atencao", diasAlertaAtencao)
            put("dias_alerta_critico", diasAlertaCritico)
            put("simples", simples)
            // Sempre inclui a chave, mesmo quando null: upsert do Postgrest só
            // toca colunas presentes no payload — omitir a chave deixaria uma
            // fase_padrao_id antiga intacta no servidor ao desligar o toggle
            // "simples" na edição de um tipo (mesmo motivo documentado em
            // ProcessoRepository.atualizar() para valor_pesquisa_unit).
            put("fase_padrao_id", fasePadraoId)
        }
        client.postgrest["tipos_processo"].upsert(linha)
        sincronizar()
    }

    suspend fun excluir(tipoProcesso: TipoProcessoEntity) {
        client.postgrest["tipos_processo"].delete {
            filter { eq("id", tipoProcesso.id) }
        }
        dao.delete(tipoProcesso)
    }
}

private fun TipoProcessoDto.paraEntity(): TipoProcessoEntity = TipoProcessoEntity(
    id = id,
    organizacaoId = organizacaoId,
    nome = nome,
    diasAlertaAtencao = diasAlertaAtencao,
    diasAlertaCritico = diasAlertaCritico,
    simples = simples,
    fasePadraoId = fasePadraoId
)
```

- [ ] **Step 5: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Os defaults de `simples`/`fasePadraoId` em `TipoProcessoRepository.salvar(...)` mantêm `TipoProcessoCadastroViewModel.salvar(...)` (chamador existente, ainda não tocado nesta task) compilando sem mudança — a Task 3 vai passar os dois explicitamente.

- [ ] **Step 6: Commit**

Rodar o Step 5 já disparou o KSP do Room, que gera
`app/schemas/com.josiel.organizeprocesso.data.local.AppDatabase/4.json`
(`exportSchema = true`, mesmo padrão dos 3 arquivos de schema já versionados
no repositório) — inclua-o no commit:

```bash
git add app/src/main/java/com/josiel/organizeprocesso/data/local/TipoProcessoEntity.kt \
        app/src/main/java/com/josiel/organizeprocesso/data/local/AppDatabase.kt \
        app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/TipoProcessoDto.kt \
        app/src/main/java/com/josiel/organizeprocesso/data/repository/TipoProcessoRepository.kt \
        "app/schemas/com.josiel.organizeprocesso.data.local.AppDatabase/4.json"
git commit -m "feat: add simples/fasePadraoId to TipoProcesso entity, dto and repository"
```

---

## Task 3: Cadastro de Tipos de Processo — toggle "simples" + seletor de fase única

**Files:**
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/TipoProcessoFormDialog.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/TipoProcessoCadastroViewModel.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/TiposProcessoScreen.kt`

**Interfaces:**
- Consumes: `TipoProcessoEntity.simples`/`fasePadraoId`, `TipoProcessoRepository.salvar(...)` (Task 2); `FaseRepository.observarTodas(): Flow<List<FaseEntity>>` (já existe desde o Plano 2A).
- Produces: nenhuma interface nova consumida por tasks seguintes — esta task fecha o ciclo de cadastro do tipo.

- [ ] **Step 1: Adicionar `fases` ao ViewModel**

Substitua `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/TipoProcessoCadastroViewModel.kt` por:

```kotlin
package com.josiel.organizeprocesso.ui.cadastro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.FaseEntity
import com.josiel.organizeprocesso.data.local.TipoProcessoEntity
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.data.repository.TipoProcessoRepository
import com.josiel.organizeprocesso.ui.common.MENSAGEM_SESSAO_AUSENTE
import com.josiel.organizeprocesso.ui.common.mensagemDeErro
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TipoProcessoCadastroViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TipoProcessoRepository(
        dao = AppDatabase.getInstance(application).tipoProcessoDao(),
        client = SupabaseSessionManager.client
    )
    private val faseRepository = FaseRepository(
        dao = AppDatabase.getInstance(application).faseDao(),
        client = SupabaseSessionManager.client
    )

    val tiposProcesso: StateFlow<List<TipoProcessoEntity>> = repository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Catálogo de Fases da organização, para o seletor de "fase única" de um tipo simples. */
    val fases: StateFlow<List<FaseEntity>> = faseRepository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _erro = MutableStateFlow<String?>(null)

    /** Falha da última escrita (rede ou rejeição do servidor), exibida na tela. */
    val erro: StateFlow<String?> = _erro.asStateFlow()

    fun salvar(
        id: String?,
        nome: String,
        diasAlertaAtencao: Int,
        diasAlertaCritico: Int,
        simples: Boolean,
        fasePadraoId: String?
    ) {
        // Sem perfil carregado o organizacao_id iria vazio numa coluna uuid e o
        // servidor devolveria 400 — nem tenta.
        val organizacaoId = SupabaseSessionManager.perfilAtual.value?.organizacaoId
        if (organizacaoId.isNullOrBlank()) {
            _erro.value = MENSAGEM_SESSAO_AUSENTE
            return
        }
        viewModelScope.launch {
            _erro.value = null
            try {
                repository.salvar(
                    id = id,
                    nome = nome,
                    diasAlertaAtencao = diasAlertaAtencao,
                    diasAlertaCritico = diasAlertaCritico,
                    simples = simples,
                    fasePadraoId = fasePadraoId,
                    organizacaoId = organizacaoId
                )
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                _erro.value = mensagemDeErro(e)
            }
        }
    }

    fun excluir(tipoProcesso: TipoProcessoEntity) {
        viewModelScope.launch {
            _erro.value = null
            try {
                repository.excluir(tipoProcesso)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                _erro.value = mensagemDeErro(e)
            }
        }
    }
}
```

- [ ] **Step 2: Atualizar `TipoProcessoFormDialog`**

Substitua `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/TipoProcessoFormDialog.kt` por:

```kotlin
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

@Composable
fun TipoProcessoFormDialog(
    tipoProcessoInicial: TipoProcessoEntity?,
    fases: List<FaseEntity>,
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
```

- [ ] **Step 3: Atualizar `TiposProcessoScreen`**

Em `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/TiposProcessoScreen.kt`:
adicione `val fases by viewModel.fases.collectAsState()` logo abaixo de
`val erro by viewModel.erro.collectAsState()` (linha 54), e substitua o
bloco `if (mostrarFormulario) { ... }` (linhas 122-137) por:

```kotlin
    if (mostrarFormulario) {
        TipoProcessoFormDialog(
            tipoProcessoInicial = tipoEmEdicao,
            fases = fases,
            onDismiss = { mostrarFormulario = false },
            onSalvar = { nome, diasAtencao, diasCritico, simples, fasePadraoId ->
                viewModel.salvar(tipoEmEdicao?.id, nome, diasAtencao, diasCritico, simples, fasePadraoId)
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
```

- [ ] **Step 4: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/TipoProcessoFormDialog.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/TipoProcessoCadastroViewModel.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/TiposProcessoScreen.kt
git commit -m "feat: add simples/fase-padrao toggle to Tipo de Processo form"
```

---

## Task 4: Criação de processo — auto-preenchimento de fase para tipo simples

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/domain/usecase/DecidirFaseAoTrocarTipo.kt`
- Test: `app/src/test/java/com/josiel/organizeprocesso/domain/usecase/DecidirFaseAoTrocarTipoTest.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoFormViewModel.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoFormScreen.kt`

**Interfaces:**
- Consumes: `TipoProcessoEntity.simples`/`fasePadraoId` (Task 2).
- Produces: `fun decidirFaseAoTrocarTipo(tipoSimples: Boolean, fasePadraoId: String?): String?` — função pura, só usada nesta task (`ProcessoFormViewModel.atualizarTipo`), mas testável isoladamente sem Android.

- [ ] **Step 1: Escrever o teste (falhando)**

Crie `app/src/test/java/com/josiel/organizeprocesso/domain/usecase/DecidirFaseAoTrocarTipoTest.kt`:

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecidirFaseAoTrocarTipoTest {
    @Test
    fun `tipo simples usa a fase padrao do tipo`() {
        assertEquals(
            "fase-1",
            decidirFaseAoTrocarTipo(tipoSimples = true, fasePadraoId = "fase-1")
        )
    }

    @Test
    fun `tipo com etapas exige escolha manual (retorna null)`() {
        assertNull(decidirFaseAoTrocarTipo(tipoSimples = false, fasePadraoId = null))
    }

    @Test
    fun `tipo com etapas ignora uma fase padrao presente`() {
        assertNull(decidirFaseAoTrocarTipo(tipoSimples = false, fasePadraoId = "fase-1"))
    }
}
```

- [ ] **Step 2: Rodar o teste, confirmar que falha**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.DecidirFaseAoTrocarTipoTest"`
Expected: FAIL (função não existe ainda).

- [ ] **Step 3: Implementar**

Crie `app/src/main/java/com/josiel/organizeprocesso/domain/usecase/DecidirFaseAoTrocarTipo.kt`:

```kotlin
package com.josiel.organizeprocesso.domain.usecase

/**
 * Decide a fase a usar no formulário de criação/edição de processo quando o
 * usuário troca de Tipo de Processo (spec de tipo-processo-simples, seção
 * 4) — tipo simples sempre usa sua fase única (mesmo que já houvesse uma
 * fase selecionada manualmente antes da troca); tipo com etapas volta a
 * exigir escolha manual, por isso retorna null mesmo que uma fase-padrão
 * esteja presente (ela pertence a outro tipo, não a este).
 */
fun decidirFaseAoTrocarTipo(tipoSimples: Boolean, fasePadraoId: String?): String? =
    if (tipoSimples) fasePadraoId else null
```

- [ ] **Step 4: Rodar o teste, confirmar que passa**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.DecidirFaseAoTrocarTipoTest"`
Expected: PASS (3 testes).

- [ ] **Step 5: Usar a função em `ProcessoFormViewModel.atualizarTipo`**

Em `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoFormViewModel.kt`,
adicione o import `com.josiel.organizeprocesso.domain.usecase.decidirFaseAoTrocarTipo`
e substitua (linhas 153-155 do arquivo atual):

```kotlin
    fun atualizarTipo(tipoProcessoId: String) {
        _uiState.value = _uiState.value.copy(tipoProcessoId = tipoProcessoId)
    }
```

por:

```kotlin
    fun atualizarTipo(tipoProcessoId: String) {
        val tipoSelecionado = tiposProcesso.value.find { it.id == tipoProcessoId }
        val faseSelecionadaId = decidirFaseAoTrocarTipo(
            tipoSimples = tipoSelecionado?.simples == true,
            fasePadraoId = tipoSelecionado?.fasePadraoId
        )
        _uiState.value = _uiState.value.copy(tipoProcessoId = tipoProcessoId, faseSelecionadaId = faseSelecionadaId)
    }
```

- [ ] **Step 6: Esconder o dropdown "Fase inicial" para tipo simples**

Em `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoFormScreen.kt`,
localize o bloco (linhas 161-176 do arquivo atual):

```kotlin
            DropdownField(
                label = "Fase inicial",
                opcoes = fases,
                selecionado = fases.find { it.id == estado.faseSelecionadaId },
                rotulo = { it.nome },
                onSelecionado = { viewModel.atualizarFase(it.id) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !estado.somenteLeitura
            )
            if (fases.isEmpty()) {
                Text(
                    "Cadastre ao menos uma fase em Mais > Cadastro de Fases antes de criar um processo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            DropdownField(
                label = "Tipo de processo",
```

Substitua pelo mesmo bloco envolvido numa checagem de tipo simples (repare
que este bloco fica ANTES do dropdown de "Tipo de processo" no arquivo —
por isso precisa localizar `tipoSelecionado` a partir de `estado.tipoProcessoId`,
já disponível neste ponto do Composable):

```kotlin
            val tipoSelecionado = tiposProcesso.find { it.id == estado.tipoProcessoId }
            if (tipoSelecionado?.simples != true) {
                DropdownField(
                    label = "Fase inicial",
                    opcoes = fases,
                    selecionado = fases.find { it.id == estado.faseSelecionadaId },
                    rotulo = { it.nome },
                    onSelecionado = { viewModel.atualizarFase(it.id) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !estado.somenteLeitura
                )
                if (fases.isEmpty()) {
                    Text(
                        "Cadastre ao menos uma fase em Mais > Cadastro de Fases antes de criar um processo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            DropdownField(
                label = "Tipo de processo",
```

- [ ] **Step 7: Verificar que o projeto inteiro compila e os testes passam**

Run: `./gradlew.bat :app:compileDebugKotlin`
Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` nos dois.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/domain/usecase/DecidirFaseAoTrocarTipo.kt \
        app/src/test/java/com/josiel/organizeprocesso/domain/usecase/DecidirFaseAoTrocarTipoTest.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoFormViewModel.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoFormScreen.kt
git commit -m "feat: auto-fill fase padrao and hide fase dropdown for tipo simples"
```

---

## Task 5: Detalhe do processo — botão "Concluir processo" para tipo simples

**Files:**
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoDetalheViewModel.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoDetalheScreen.kt`

**Interfaces:**
- Consumes: `TipoProcessoEntity.simples` (Task 2); `ProcessoRepository.atualizar(processo, itensAtuais, itensRemovidos)` (já existe desde o Plano 1/2A).
- Produces: `ProcessoDetalheUiState.tipoProcessoSimples: Boolean` e `ProcessoDetalheViewModel.concluir()` — só usados dentro desta task.

- [ ] **Step 1: Expor `tipoProcessoSimples` no estado**

Em `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoDetalheViewModel.kt`,
adicione o campo `tipoProcessoSimples: Boolean = false` a `ProcessoDetalheUiState`
(logo abaixo de `val tipoProcessoNome: String = ""`, linha 47 do arquivo atual):

```kotlin
data class ProcessoDetalheUiState(
    val carregando: Boolean = true,
    val processo: ProcessoEntity? = null,
    val faseAtualNome: String = "",
    val tipoProcessoNome: String = "",
    val tipoProcessoSimples: Boolean = false,
    val itens: List<ItemEntity> = emptyList(),
```

No `combine` que monta `estadoBase` (dentro do `ProcessoDetalheUiState(...)` retornado,
por volta da linha 136-137 do arquivo atual), adicione o novo campo logo
depois de `tipoProcessoNome`:

```kotlin
            faseAtualNome = faseAtual?.nome ?: "",
            tipoProcessoNome = tipoProcesso?.nome ?: "",
            tipoProcessoSimples = tipoProcesso?.simples == true,
```

- [ ] **Step 2: Adicionar `concluir()` ao ViewModel**

No mesmo arquivo, adicione o import `com.josiel.organizeprocesso.domain.model.StatusGeralProcesso`
e a função `concluir()` logo depois de `fun designar(...)` (depois da linha 183 do arquivo atual, ainda dentro da classe):

```kotlin
    /**
     * Conclui um processo de tipo simples direto, sem passar por
     * AvancarFaseScreen — reaproveita ProcessoRepository.atualizar() (spec de
     * tipo-processo-simples, seção 5). A linha ativa de processo_fase_historico
     * não é fechada: não há "próxima fase" para a qual avançar.
     */
    fun concluir() {
        viewModelScope.launch {
            _erro.value = null
            try {
                val estado = uiState.value
                val processo = estado.processo ?: return@launch
                processoRepository.atualizar(
                    processo = processo.copy(statusGeral = StatusGeralProcesso.CONCLUIDO),
                    itensAtuais = estado.itens,
                    itensRemovidos = emptyList()
                )
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                _erro.value = mensagemDeErro(e)
            }
        }
    }
```

- [ ] **Step 3: Trocar o botão "Avançar fase" por "Concluir processo" quando o tipo é simples**

Em `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoDetalheScreen.kt`,
localize dentro de `ProcessoDetalheScreen` (por volta da linha 87 do arquivo
atual) a linha `val onDesignar: (String?) -> Unit = { ... }` e adicione logo
abaixo:

```kotlin
    val onConcluir: () -> Unit = { viewModel.concluir() }
```

Em seguida, no `when (abaSelecionada)` (linha 128 do arquivo atual), passe o
novo callback:

```kotlin
                0 -> AbaDadosGerais(estado, onAvancarFaseClick, onConcluir, onDesignar)
```

Atualize a assinatura de `AbaDadosGerais` (linhas 138-142 do arquivo atual):

```kotlin
@Composable
private fun AbaDadosGerais(
    estado: ProcessoDetalheUiState,
    onAvancarFaseClick: () -> Unit,
    onConcluir: () -> Unit,
    onDesignar: (String?) -> Unit
) {
```

E localize o bloco do botão (linhas 209-217 do arquivo atual):

```kotlin
        if (estado.podeEditar) {
            PillButton(
                text = "Avançar fase",
                onClick = onAvancarFaseClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
```

Substitua por:

```kotlin
        if (estado.podeEditar) {
            if (estado.tipoProcessoSimples) {
                PillButton(
                    text = "Concluir processo",
                    onClick = onConcluir,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            } else {
                PillButton(
                    text = "Avançar fase",
                    onClick = onAvancarFaseClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
```

- [ ] **Step 4: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoDetalheViewModel.kt \
        app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoDetalheScreen.kt
git commit -m "feat: add Concluir processo shortcut for tipo simples"
```

---

## Task 6: Verificação final

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
Expected: `BUILD SUCCESSFUL`, os 3 testes novos da Task 4 passando, mais os 37 já existentes (34 pré-Plano-2D + 3 do Plano 2D) — 40 no total.

- [ ] **Step 3: Confirmar o schema Room versionado**

```bash
git log --oneline -- "app/schemas/com.josiel.organizeprocesso.data.local.AppDatabase/4.json"
```
Expected: aparece o commit da Task 2 (`feat: add simples/fasePadraoId to TipoProcesso entity, dto and repository`). Se não aparecer nada, o arquivo ficou de fora por engano — `git add` nele e `git commit -m "chore: add Room schema v4"` antes de prosseguir.

- [ ] **Step 4: Registrar o que fica pendente**

Nada disto pode ser verificado nesta sessão, e fica registrado como
pendência, não como bloqueador para fechar este plano:
- A migração da Task 1 nunca foi aplicada contra um banco real
  (`supabase/.env.local` não existe neste worktree) — o teste pgTAP nunca
  rodou de fato, só foi escrito seguindo o padrão dos arquivos já existentes.
- Nenhum teste funcional em emulador/dispositivo real (cadastrar um tipo
  simples, criar um processo desse tipo, confirmar que a tela de criação
  não pede fase, concluir pelo botão novo) foi executado nesta sessão.

- [ ] **Step 5: Commit (se necessário)**

Se os Steps 1-3 não exigiram nenhuma mudança de código além do já commitado
nas tasks anteriores, não há o que commitar.
