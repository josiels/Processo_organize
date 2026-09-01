# Plano 2A — Fundação de dados e autenticação (Android) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reescrever a camada de dados do app Android Organize_Processo para consumir o backend Supabase multiusuário (Plano 1, já mesclado, mais o incremento `avancar_fase()`/`fila_distribuicao`) — cliente Supabase, sessão/autenticação, schema Room virando cache, e a remoção de Pessoa como cadastro livre.

**Architecture:** `SupabaseSessionManager` (singleton, seguindo o padrão manual de `AppDatabase.getInstance` já usado no projeto — não há DI framework) concentra o `SupabaseClient` e o estado de sessão/perfil logado. Cada repositório grava direto no Postgrest (escrita síncrona, exige rede) e mantém o Room como cache de leitura (as telas continuam observando `Flow`s do Room, sem saber que os dados vêm de rede). `RealtimeSyncManager` mantém o cache atualizado via assinaturas `postgres_changes`. Login é a nova porta de entrada do app.

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.8.4, `supabase-kt` 3.5.0 BOM (Postgrest + Auth + Realtime), Kotlin Coroutines/Flow, `kotlinx-serialization-json`.

**Spec:** `docs/superpowers/specs/2026-09-01-android-fundacao-dados-auth-design.md`

## Global Constraints

- **Verificação headless confirmada antes deste plano ser escrito:** `./gradlew.bat :app:compileDebugKotlin` e `./gradlew.bat :app:testDebugUnitTest` rodam com sucesso nesta máquina sem emulador, usando o JDK do Android Studio (`$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` no PowerShell) e um `local.properties` com `sdk.dir` apontando para `%LOCALAPPDATA%\Android\Sdk`. Todo passo de verificação deste plano usa esses dois comandos — nenhuma etapa depende de emulador ou dispositivo físico.
- **A validação do schema Room acontece automaticamente na compilação** (o processador de anotações KSP do Room falha o build se uma entidade/DAO estiver mal formada) — isso substitui a necessidade de um teste instrumentado (`androidTest`) para a migração de schema, já que a estratégia de migração é destrutiva (`fallbackToDestructiveMigration()`), não incremental.
- Nenhum código deste plano roda contra o Supabase real de forma automatizada nos testes (diferente do Plano 1, que testava contra o projeto na nuvem via `run_sql.mjs`) — os testes unitários deste plano cobrem lógica pura (mapeamento, cálculo, tradução de erro), não chamadas de rede reais. Verificação de ponta a ponta (login real, sincronização real) fica para quando houver acesso físico a um emulador/dispositivo — cada task marca isso explicitamente onde se aplica.
- Segredos (`SUPABASE_URL`, `SUPABASE_ANON_KEY` — a anon key, nunca a service role key) entram via `local.properties` (gitignored) → `BuildConfig` fields. Nunca hardcoded no código-fonte.
- Sem framework de DI (nem Hilt, nem Koin) — todo singleton segue o padrão manual já estabelecido no projeto (`object` ou `companion object` com inicialização lazy/double-checked-locking), e toda `ViewModel` continua sendo `AndroidViewModel` instanciando suas próprias dependências no corpo, exatamente como hoje.
- Toda entidade Room remove os campos `synced`/`deviceOrigin` (vestígios da fila de sincronização offline-first que este pivô elimina).
- IDs continuam `String` (UUID como texto) em todas as entidades Room, consistente com o padrão já usado.
- **Escopo explicitamente fora deste plano (ruling, refinamento em relação à spec):** `HistoricoFaseRepository` (o `mudarFase(...)` que hoje faz a transição de fase) e `DiligenciaRepository` **não** são tocados aqui — ambos estão fortemente acoplados à reescrita do `AvancarFaseScreen`/`AvancarFaseViewModel`, que é explicitamente escopo do Plano 2B, e dependem da assinatura de `avancar_fase()` no backend (que só acabou de estabilizar). A spec de 2A (seção 3) os menciona genericamente na lista de repositórios "no mesmo formato" — esta é uma decisão de sequenciamento, não uma contradição: fazem mais sentido reescritos junto com a tela que os usa, no Plano 2B.
- Nomes de arquivo/pacote em português onde já é a convenção do projeto (`FaseRepository`, `ProcessoEntity`, etc.); nomes de biblioteca/API externa (Supabase, Room) mantêm o inglês próprio delas.

---

## Task 1: Dependências e configuração de segredos

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `local.properties` (não versionado — instruções de conteúdo, não um diff)
- Modify: `.gitignore` (confirmar, não deve precisar de mudança — `local.properties` já está listado)

**Interfaces:**
- Produces: `BuildConfig.SUPABASE_URL` e `BuildConfig.SUPABASE_ANON_KEY` (strings, geradas em tempo de build) — todo código dos próximos tasks que cria um `SupabaseClient` usa essas duas constantes, nunca lê `local.properties` diretamente em código Kotlin.

- [ ] **Step 1: Adicionar o módulo `realtime-kt` e as dependências de teste**

Em `gradle/libs.versions.toml`, adicione ao final do bloco `[versions]` (depois de `kotlinxCoroutinesAndroid = "1.11.0"`):

```toml
kotlinxCoroutinesTest = "1.11.0"
mockk = "1.14.6"
```

No bloco `[libraries]`, adicione depois da linha `ktor-client-android = { group = "io.ktor", name = "ktor-client-android", version.ref = "ktor" }`:

```toml
supabase-realtime-kt = { group = "io.github.jan-tennert.supabase", name = "realtime-kt" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
```

Em `app/build.gradle.kts`, na seção `// Supabase (backend de sincronização)`, adicione a linha do realtime logo após `implementation(libs.supabase.storage.kt)`:

```kotlin
implementation(libs.supabase.realtime.kt)
```

Na seção de test dependencies, depois de `testImplementation(libs.junit)`, adicione:

```kotlin
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.mockk)
```

- [ ] **Step 2: Habilitar `BuildConfig` e ler segredos de `local.properties`**

Em `app/build.gradle.kts`, adicione no topo do arquivo, antes do bloco `plugins { ... }`:

```kotlin
import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
```

Dentro do bloco `android { ... }`, no `defaultConfig { ... }` (depois de `testInstrumentationRunner = "..."`), adicione:

```kotlin
buildConfigField("String", "SUPABASE_URL", "\"${localProperties.getProperty("SUPABASE_URL", "")}\"")
buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProperties.getProperty("SUPABASE_ANON_KEY", "")}\"")
```

E no bloco `buildFeatures { compose = true }`, adicione `buildConfig = true`:

```kotlin
buildFeatures {
    compose = true
    buildConfig = true
}
```

- [ ] **Step 3: Preencher `local.properties` com os valores reais**

Adicione (ou confirme, se já existirem) estas duas linhas em `local.properties` (arquivo já gitignored, nunca commitado):

```properties
SUPABASE_URL=https://isjhxusoxeuxfoxooppe.supabase.co
SUPABASE_ANON_KEY=<o valor de SUPABASE_ANON_KEY em supabase/.env.local>
```

O valor real da anon key está em `supabase/.env.local` (também gitignored) — copie de lá, não deste plano (este documento é commitado, nunca cole a chave real aqui).

- [ ] **Step 4: Verificar que o projeto compila com os novos campos**

Run (PowerShell, a partir da raiz do projeto):
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`. Se falhar por `local.properties` sem `sdk.dir`, adicione `sdk.dir=<caminho do Android SDK>` (ex.: `C:\\Users\\<usuário>\\AppData\\Local\\Android\\Sdk`) no mesmo arquivo.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "chore: add realtime-kt, test deps, and Supabase BuildConfig fields"
```

(Não faça `git add local.properties` — confirme com `git status` que ele não aparece, já está gitignored.)

---

## Task 2: Schema Room v2 — remoção de Pessoa, novas entidades, campos de designação

**Files:**
- Delete: `app/src/main/java/com/josiel/organizeprocesso/data/local/PessoaEntity.kt`
- Delete: `app/src/main/java/com/josiel/organizeprocesso/data/local/PessoaDao.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/domain/model/Papel.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/local/PerfilEntity.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/local/PerfilDao.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/local/TipoProcessoEntity.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/local/TipoProcessoDao.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/local/FaseEntity.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/local/ProcessoEntity.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/local/ItemEntity.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/local/ProcessoFaseHistoricoEntity.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/local/ObservacaoVersaoEntity.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/local/Converters.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/local/AppDatabase.kt`

**Interfaces:**
- Produces: `PerfilEntity(id, organizacaoId, papel: Papel, nome, cargoSetor, ativo, notificarAvancoFase, notificarPrazo, notificarTempoParado, ultimoRecebimentoEm: Instant?, criadoEm: Instant)`; `PerfilDao` com `observarTodos(): Flow<List<PerfilEntity>>`, `observarPorId(id): Flow<PerfilEntity?>`, `upsert`, `upsertTodos(lista: List<PerfilEntity>)`, `limparTudo()`. `TipoProcessoEntity(id, organizacaoId, nome, diasAlertaAtencao, diasAlertaCritico)`; `TipoProcessoDao` com o mesmo formato de `FaseDao` (upsert, delete, observarPorId, observarTodas) mais `upsertTodos`/`limparTudo`. `Papel` enum (`SUPER_ADMIN, ADMIN, USUARIO`) em `domain/model/`. `AppDatabase` na versão 2.
- Consumes: nada de tasks anteriores (task independente de Task 1 — pode ser feito em paralelo se não fosse a regra de nunca dispachar implementadores em paralelo).

- [ ] **Step 1: Criar o enum `Papel`**

Create `app/src/main/java/com/josiel/organizeprocesso/domain/model/Papel.kt`:

```kotlin
package com.josiel.organizeprocesso.domain.model

/** Espelha o enum `papel_usuario` do backend (Postgres) — ver spec do pivô, seção 2. */
enum class Papel {
    SUPER_ADMIN,
    ADMIN,
    USUARIO
}
```

- [ ] **Step 2: Remover Pessoa**

Delete `app/src/main/java/com/josiel/organizeprocesso/data/local/PessoaEntity.kt` e `app/src/main/java/com/josiel/organizeprocesso/data/local/PessoaDao.kt`.

- [ ] **Step 3: Criar `PerfilEntity` e `PerfilDao`**

Create `app/src/main/java/com/josiel/organizeprocesso/data/local/PerfilEntity.kt`:

```kotlin
package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.josiel.organizeprocesso.domain.model.Papel
import java.time.Instant

/**
 * Cache local de `public.perfis` (backend) — só leitura no app; criação é
 * exclusivamente via Edge Function `criar-conta`/`criar-organizacao`, nunca
 * um formulário CRUD direto (ver spec do Plano 2A, seção 2.3).
 */
@Entity(tableName = "perfis")
data class PerfilEntity(
    @PrimaryKey val id: String,
    val organizacaoId: String?,
    val papel: Papel,
    val nome: String,
    val cargoSetor: String?,
    val ativo: Boolean,
    val notificarAvancoFase: Boolean,
    val notificarPrazo: Boolean,
    val notificarTempoParado: Boolean,
    val ultimoRecebimentoEm: Instant?,
    val criadoEm: Instant
)
```

Create `app/src/main/java/com/josiel/organizeprocesso/data/local/PerfilDao.kt`:

```kotlin
package com.josiel.organizeprocesso.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PerfilDao {
    @Upsert
    suspend fun upsert(perfil: PerfilEntity)

    @Upsert
    suspend fun upsertTodos(perfis: List<PerfilEntity>)

    @Query("DELETE FROM perfis")
    suspend fun limparTudo()

    @Query("SELECT * FROM perfis WHERE id = :id")
    fun observarPorId(id: String): Flow<PerfilEntity?>

    @Query("SELECT * FROM perfis ORDER BY nome")
    fun observarTodos(): Flow<List<PerfilEntity>>
}
```

- [ ] **Step 4: Criar `TipoProcessoEntity` e `TipoProcessoDao`**

Create `app/src/main/java/com/josiel/organizeprocesso/data/local/TipoProcessoEntity.kt`:

```kotlin
package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cache local de `public.tipos_processo` (backend) — catálogo cadastrado pelo admin. */
@Entity(tableName = "tipos_processo")
data class TipoProcessoEntity(
    @PrimaryKey val id: String,
    val organizacaoId: String,
    val nome: String,
    val diasAlertaAtencao: Int,
    val diasAlertaCritico: Int
)
```

Create `app/src/main/java/com/josiel/organizeprocesso/data/local/TipoProcessoDao.kt`:

```kotlin
package com.josiel.organizeprocesso.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TipoProcessoDao {
    @Upsert
    suspend fun upsert(tipoProcesso: TipoProcessoEntity)

    @Upsert
    suspend fun upsertTodos(tiposProcesso: List<TipoProcessoEntity>)

    @Delete
    suspend fun delete(tipoProcesso: TipoProcessoEntity)

    @Query("DELETE FROM tipos_processo")
    suspend fun limparTudo()

    @Query("SELECT * FROM tipos_processo WHERE id = :id")
    fun observarPorId(id: String): Flow<TipoProcessoEntity?>

    @Query("SELECT * FROM tipos_processo ORDER BY nome")
    fun observarTodas(): Flow<List<TipoProcessoEntity>>
}
```

- [ ] **Step 5: Atualizar `FaseEntity`**

Replace the full contents of `app/src/main/java/com/josiel/organizeprocesso/data/local/FaseEntity.kt`:

```kotlin
package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ARQUITETURA.md, seção 4 — Fase (catálogo reutilizável, cadastro manual).
 * `campos_habilitados` não existe aqui de propósito: a liberação de campos
 * por fase é lógica fixa no código (ver ARQUITETURA.md, regra de negócio 1).
 * `organizacaoId` chegou com o pivô multiusuário — cada organização tem seu
 * próprio catálogo de fases.
 */
@Entity(
    tableName = "fases",
    indices = [Index("organizacaoId")]
)
data class FaseEntity(
    @PrimaryKey val id: String,
    val organizacaoId: String,
    val nome: String,
    val ordem: Int,
    val descricao: String?,
    val diasAlertaAtencao: Int,
    val diasAlertaCritico: Int
)
```

(`padrao`, `updatedAt`, `synced`, `deviceOrigin` removidos — `padrao` não existe na tabela `fases` do backend; os três últimos eram vestígios do esquema de sincronização offline-first.)

- [ ] **Step 6: Atualizar `ProcessoEntity`**

Replace the full contents of `app/src/main/java/com/josiel/organizeprocesso/data/local/ProcessoEntity.kt`:

```kotlin
package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import java.time.Instant
import java.time.LocalDate

/**
 * ARQUITETURA.md, seção 4 — Processo (dados-mãe). `fase_atual_id` não faz
 * CASCADE: excluir uma Fase em uso não deve arrancar processos junto.
 * Campos de designação (`responsavelId`/`designadoEm`/`designadoPor`) são
 * só leitura no app — a única forma sancionada de mudá-los é a RPC
 * `designar_processo()` no backend (Plano 1, Task 9); nenhum código deste
 * plano escreve nesses três campos diretamente.
 */
@Entity(
    tableName = "processos",
    foreignKeys = [
        ForeignKey(
            entity = FaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["faseAtualId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index("faseAtualId"), Index("organizacaoId"), Index("responsavelId")]
)
data class ProcessoEntity(
    @PrimaryKey val id: String,
    val organizacaoId: String,
    val numero: String,
    val objeto: String,
    val descricao: String,
    val orgaoDemandante: String,
    val tipoProcessoId: String,
    val valorEstimadoTotal: Double,
    val dataAbertura: LocalDate,
    val faseAtualId: String,
    val statusGeral: StatusGeralProcesso,
    val responsavelId: String?,
    val designadoEm: Instant?,
    val designadoPor: String?,
    val criadoEm: Instant,
    val atualizadoEm: Instant
)
```

(`tipo: String` virou `tipoProcessoId: String`; `createdAt`/`updatedAt` renomeados para `criadoEm`/`atualizadoEm` para bater com o nome das colunas do backend; `synced`/`deviceOrigin` removidos.)

- [ ] **Step 7: Atualizar `ItemEntity`**

In `app/src/main/java/com/josiel/organizeprocesso/data/local/ItemEntity.kt`, remove the `updatedAt`, `synced`, and `deviceOrigin` fields from the data class (keep everything else, including the `ForeignKey`/`Index` block unchanged):

```kotlin
data class ItemEntity(
    @PrimaryKey val id: String,
    val processoId: String,
    val descricao: String,
    val quantidade: Double,
    val unidade: String,
    val valorEstimadoUnit: Double,
    val valorPesquisaUnit: Double?
)
```

Remove the now-unused `import java.time.Instant` from the top of the file.

- [ ] **Step 8: Atualizar `ProcessoFaseHistoricoEntity`**

Replace the full contents of `app/src/main/java/com/josiel/organizeprocesso/data/local/ProcessoFaseHistoricoEntity.kt`:

```kotlin
package com.josiel.organizeprocesso.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * ARQUITETURA.md, seção 4 — ProcessoFaseHistorico (núcleo do histórico de
 * fases). `dataSaida == null` marca a fase corrente/ativa do processo.
 * `responsavelId` aqui é "quem executou esta passagem específica pela fase"
 * — um conceito DIFERENTE de `ProcessoEntity.responsavelId` (a designação
 * corrente do processo inteiro). Ver spec do Plano 2A, seção 2.3.
 */
@Entity(
    tableName = "processo_fase_historico",
    foreignKeys = [
        ForeignKey(
            entity = ProcessoEntity::class,
            parentColumns = ["id"],
            childColumns = ["processoId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["faseId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = PerfilEntity::class,
            parentColumns = ["id"],
            childColumns = ["responsavelId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("processoId"), Index("faseId"), Index("responsavelId")]
)
data class ProcessoFaseHistoricoEntity(
    @PrimaryKey val id: String,
    val processoId: String,
    val faseId: String,
    val responsavelId: String?,
    val dataEntrada: LocalDate,
    val dataSaida: LocalDate?,
    val prazoLimite: LocalDate?,
    val observacoes: String,
    val motivoRetorno: String?,
    val notificarPrazo: Boolean
)
```

(FK trocada de `PessoaEntity` para `PerfilEntity`; `updatedAt`/`synced`/`deviceOrigin` removidos.)

- [ ] **Step 9: Atualizar `ObservacaoVersaoEntity`**

In `app/src/main/java/com/josiel/organizeprocesso/data/local/ObservacaoVersaoEntity.kt`, remove the `deviceOrigin` and `synced` fields:

```kotlin
data class ObservacaoVersaoEntity(
    @PrimaryKey val id: String,
    val processoFaseHistoricoId: String,
    val conteudo: String,
    val criadoEm: Instant
)
```

- [ ] **Step 10: Adicionar o conversor de `Papel` em `Converters.kt`**

In `app/src/main/java/com/josiel/organizeprocesso/data/local/Converters.kt`, add the import `com.josiel.organizeprocesso.domain.model.Papel` and these two methods inside the `Converters` class (after the `toTipoAnexo` method):

```kotlin
    @TypeConverter
    fun fromPapel(value: Papel?): String? = value?.name

    @TypeConverter
    fun toPapel(value: String?): Papel? = value?.let(Papel::valueOf)
```

- [ ] **Step 11: Atualizar `AppDatabase`**

Replace the full contents of `app/src/main/java/com/josiel/organizeprocesso/data/local/AppDatabase.kt`:

```kotlin
package com.josiel.organizeprocesso.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PerfilEntity::class,
        TipoProcessoEntity::class,
        FaseEntity::class,
        ProcessoEntity::class,
        ProcessoFaseHistoricoEntity::class,
        ObservacaoVersaoEntity::class,
        ItemEntity::class,
        AnexoLinkEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun perfilDao(): PerfilDao
    abstract fun tipoProcessoDao(): TipoProcessoDao
    abstract fun faseDao(): FaseDao
    abstract fun processoDao(): ProcessoDao
    abstract fun processoFaseHistoricoDao(): ProcessoFaseHistoricoDao
    abstract fun observacaoVersaoDao(): ObservacaoVersaoDao
    abstract fun itemDao(): ItemDao
    abstract fun anexoLinkDao(): AnexoLinkDao

    companion object {
        @Volatile
        private var instancia: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "organize_processo.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instancia = it }
            }
    }
}
```

(`PessoaEntity`/`pessoaDao()` removidos; `PerfilEntity`/`TipoProcessoEntity` adicionados; versão 2; `fallbackToDestructiveMigration(dropAllTables = true)` — não há usuários em produção com dados locais a preservar, o app inteiro passa a exigir login e sincronização do zero.)

- [ ] **Step 12: Verificar que o schema compila**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`. Isto é o teste — o processador KSP do Room falha o build se alguma entidade/FK/índice estiver mal formada (chave estrangeira apontando para uma entidade que não existe mais, tipo sem conversor, etc.). Se falhar, o erro do KSP aponta exatamente a entidade/campo problemático.

Este step vai falhar até que os passos seguintes (repositórios/ViewModels que ainda referenciam `PessoaEntity`/`PessoaRepository`/o campo `tipo` antigo) também sejam ajustados — é esperado que o build só fique verde depois da Task 8 remover as últimas referências a Pessoa. Rode este comando de novo ao final de cada task seguinte para acompanhar o progresso; não é um bloqueador desta task especificamente, é o critério de "está tudo consistente" do plano inteiro.

- [ ] **Step 13: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/domain/model/Papel.kt
git add app/src/main/java/com/josiel/organizeprocesso/data/local
git commit -m "feat: Room schema v2 (perfis, tipos_processo, designação, remove Pessoa)"
```

---

## Task 3: `SupabaseSessionManager`

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/remote/SupabaseSessionManager.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/PerfilDto.kt`
- Create: `app/src/test/java/com/josiel/organizeprocesso/data/remote/PapelMappingTest.kt`

**Interfaces:**
- Consumes: `Papel` enum (Task 2).
- Produces: `SupabaseSessionManager` object com `client: SupabaseClient` (lazy), `sessionStatus: StateFlow<SessionStatus>`, `perfilAtual: PerfilSessao?` (var, só leitura de fora), `suspend fun login(email: String, senha: String)`, `suspend fun logout()`. `PerfilSessao(id: String, organizacaoId: String?, papel: Papel)` — todo código dos próximos tasks que precisa saber "quem está logado, com que papel, em que organização" lê `SupabaseSessionManager.perfilAtual`. `fun papelDoTexto(valor: String): Papel` — função pura de mapeamento, usada por este arquivo e testável isoladamente.

- [ ] **Step 1: Criar o DTO de perfil**

Create `app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/PerfilDto.kt`:

```kotlin
package com.josiel.organizeprocesso.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Espelha as colunas de `public.perfis` que o app lê via Postgrest. */
@Serializable
data class PerfilDto(
    val id: String,
    @SerialName("organizacao_id") val organizacaoId: String?,
    val papel: String,
    val nome: String,
    @SerialName("cargo_setor") val cargoSetor: String?,
    val ativo: Boolean,
    @SerialName("notificar_avanco_fase") val notificarAvancoFase: Boolean,
    @SerialName("notificar_prazo") val notificarPrazo: Boolean,
    @SerialName("notificar_tempo_parado") val notificarTempoParado: Boolean,
    @SerialName("ultimo_recebimento_em") val ultimoRecebimentoEm: String?,
    @SerialName("criado_em") val criadoEm: String
)
```

- [ ] **Step 2: Escrever o teste do mapeamento de papel (falhando)**

Create `app/src/test/java/com/josiel/organizeprocesso/data/remote/PapelMappingTest.kt`:

```kotlin
package com.josiel.organizeprocesso.data.remote

import com.josiel.organizeprocesso.domain.model.Papel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PapelMappingTest {
    @Test
    fun `mapeia super_admin`() {
        assertEquals(Papel.SUPER_ADMIN, papelDoTexto("super_admin"))
    }

    @Test
    fun `mapeia admin`() {
        assertEquals(Papel.ADMIN, papelDoTexto("admin"))
    }

    @Test
    fun `mapeia usuario`() {
        assertEquals(Papel.USUARIO, papelDoTexto("usuario"))
    }

    @Test
    fun `valor desconhecido lanca excecao`() {
        assertThrows(IllegalArgumentException::class.java) {
            papelDoTexto("papel-que-nao-existe")
        }
    }
}
```

- [ ] **Step 2b: Rodar o teste, confirmar que falha**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.data.remote.PapelMappingTest"
```
Expected: FAIL — `papelDoTexto` ainda não existe (erro de compilação, "unresolved reference").

- [ ] **Step 3: Criar `SupabaseSessionManager`**

Create `app/src/main/java/com/josiel/organizeprocesso/data/remote/SupabaseSessionManager.kt`:

```kotlin
package com.josiel.organizeprocesso.data.remote

import com.josiel.organizeprocesso.BuildConfig
import com.josiel.organizeprocesso.data.remote.dto.PerfilDto
import com.josiel.organizeprocesso.domain.model.Papel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.flow.StateFlow

/** Espelha uma linha de `public.perfis`, já convertida para os tipos do domínio do app. */
data class PerfilSessao(
    val id: String,
    val organizacaoId: String?,
    val papel: Papel
)

/**
 * Singleton manual (sem DI framework, mesmo padrão de `AppDatabase.getInstance`)
 * que concentra o cliente Supabase e o estado de quem está logado. Ver spec
 * do Plano 2A, seção 2.1.
 */
object SupabaseSessionManager {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }

    val sessionStatus: StateFlow<SessionStatus>
        get() = client.auth.sessionStatus

    var perfilAtual: PerfilSessao? = null
        private set

    suspend fun login(email: String, senha: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = senha
        }
        carregarPerfilAtual()
    }

    suspend fun logout() {
        client.auth.signOut()
        perfilAtual = null
    }

    private suspend fun carregarPerfilAtual() {
        val userId = client.auth.currentUserOrNull()?.id ?: return
        val dto = client.postgrest["perfis"]
            .select(columns = Columns.ALL) {
                filter { eq("id", userId) }
            }
            .decodeSingle<PerfilDto>()
        perfilAtual = PerfilSessao(
            id = dto.id,
            organizacaoId = dto.organizacaoId,
            papel = papelDoTexto(dto.papel)
        )
    }
}

/** Mapeia o texto do enum `papel_usuario` do backend para o enum de domínio do app. */
fun papelDoTexto(valor: String): Papel = when (valor) {
    "super_admin" -> Papel.SUPER_ADMIN
    "admin" -> Papel.ADMIN
    "usuario" -> Papel.USUARIO
    else -> throw IllegalArgumentException("Papel desconhecido: $valor")
}
```

**Nota para quem implementa:** a API exata do `supabase-kt` 3.5.0 (nomes de `install`, assinatura de `signInWith`/`select`/`filter`, etc.) foi escrita com base no conhecimento geral da biblioteca, não verificada contra a documentação ao vivo — é esperado que o Step 4 abaixo (compilar) pegue qualquer nome de método/parâmetro que tenha mudado de versão. Se `compileDebugKotlin` falhar num destes pontos, ajuste a chamada para a API real da versão instalada (navegável pela IDE, ou inspecionando os fontes da lib no cache do Gradle em `~/.gradle/caches/modules-2/files-2.1/io.github.jan-tennert.supabase/`), preservando o mesmo comportamento — não remova a funcionalidade para fazer compilar.

- [ ] **Step 4: Rodar o teste, confirmar que passa**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.data.remote.PapelMappingTest"
```
Expected: `BUILD SUCCESSFUL`, 4/4 testes passando.

- [ ] **Step 5: Verificar que o projeto inteiro ainda compila**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/data/remote app/src/test/java/com/josiel/organizeprocesso/data/remote
git commit -m "feat: SupabaseSessionManager (client, session state, current perfil)"
```

---

## Task 4: `PerfilRepository` e `TipoProcessoRepository`

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/repository/PerfilRepository.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/repository/TipoProcessoRepository.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/TipoProcessoDto.kt`

**Interfaces:**
- Consumes: `PerfilEntity`/`PerfilDao`, `TipoProcessoEntity`/`TipoProcessoDao` (Task 2); `SupabaseSessionManager.client` (Task 3).
- Produces: `PerfilRepository(dao: PerfilDao, client: SupabaseClient)` com `observarTodos(): Flow<List<PerfilEntity>>`, `suspend fun sincronizar()`. `TipoProcessoRepository(dao: TipoProcessoDao, client: SupabaseClient)` com `observarTodas(): Flow<List<TipoProcessoEntity>>`, `suspend fun sincronizar()`, `suspend fun salvar(id: String?, nome: String, diasAlertaAtencao: Int, diasAlertaCritico: Int, organizacaoId: String)`, `suspend fun excluir(tipoProcesso: TipoProcessoEntity)`.

- [ ] **Step 1: Criar o DTO de tipo de processo**

Create `app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/TipoProcessoDto.kt`:

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
    @SerialName("dias_alerta_critico") val diasAlertaCritico: Int
)
```

- [ ] **Step 2: Criar `PerfilRepository` (só leitura)**

Create `app/src/main/java/com/josiel/organizeprocesso/data/repository/PerfilRepository.kt`:

```kotlin
package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.PerfilDao
import com.josiel.organizeprocesso.data.local.PerfilEntity
import com.josiel.organizeprocesso.data.remote.dto.PerfilDto
import com.josiel.organizeprocesso.data.remote.papelDoTexto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Só leitura — criação de perfil é exclusivamente via Edge Function
 * (`criar-conta`/`criar-organizacao`, ver Plano 2C), nunca por este
 * repositório. Espelha `public.perfis` da organização do usuário logado
 * (a RLS do backend já restringe o `select` à própria organização).
 */
class PerfilRepository(
    private val dao: PerfilDao,
    private val client: SupabaseClient
) {
    fun observarTodos(): Flow<List<PerfilEntity>> = dao.observarTodos()

    suspend fun sincronizar() {
        val dtos = client.postgrest["perfis"].select().decodeList<PerfilDto>()
        dao.upsertTodos(dtos.map { it.paraEntity() })
    }
}

private fun PerfilDto.paraEntity(): PerfilEntity = PerfilEntity(
    id = id,
    organizacaoId = organizacaoId,
    papel = papelDoTexto(papel),
    nome = nome,
    cargoSetor = cargoSetor,
    ativo = ativo,
    notificarAvancoFase = notificarAvancoFase,
    notificarPrazo = notificarPrazo,
    notificarTempoParado = notificarTempoParado,
    ultimoRecebimentoEm = ultimoRecebimentoEm?.let(Instant::parse),
    criadoEm = Instant.parse(criadoEm)
)
```

- [ ] **Step 3: Criar `TipoProcessoRepository`**

Create `app/src/main/java/com/josiel/organizeprocesso/data/repository/TipoProcessoRepository.kt`:

```kotlin
package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.TipoProcessoDao
import com.josiel.organizeprocesso.data.local.TipoProcessoEntity
import com.josiel.organizeprocesso.data.remote.dto.TipoProcessoDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
        organizacaoId: String
    ) {
        val linha = buildJsonObject {
            put("id", id ?: UUID.randomUUID().toString())
            put("organizacao_id", organizacaoId)
            put("nome", nome)
            put("dias_alerta_atencao", diasAlertaAtencao)
            put("dias_alerta_critico", diasAlertaCritico)
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
    diasAlertaCritico = diasAlertaCritico
)
```

Mesma nota da Task 3 sobre a API exata do `supabase-kt` — ajuste conforme o `compileDebugKotlin` do Step 4 indicar, preservando o comportamento (upsert grava a linha inteira, delete filtra por id, select traz todas as linhas visíveis pela RLS).

- [ ] **Step 4: Verificar que compila**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/data/repository/PerfilRepository.kt
git add app/src/main/java/com/josiel/organizeprocesso/data/repository/TipoProcessoRepository.kt
git add app/src/main/java/com/josiel/organizeprocesso/data/remote/dto
git commit -m "feat: PerfilRepository and TipoProcessoRepository"
```

---

## Task 5: Reescrever `ProcessoRepository`

**Files:**
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/repository/ProcessoRepository.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/ProcessoDto.kt`
- Create: `app/src/test/java/com/josiel/organizeprocesso/data/repository/ProcessoDtoMappingTest.kt`

**Interfaces:**
- Consumes: `ProcessoEntity` v2 (Task 2), `SupabaseSessionManager.client` (Task 3).
- Produces: `ProcessoRepository(database: AppDatabase, client: SupabaseClient)` — mesmo formato de leitura de antes (`observarTodos()`, `observarPorId(id)`, `observarItens(processoId)`), mas `criar(...)`/`atualizar(...)` agora escrevem no Postgrest primeiro e só atualizam o Room depois de confirmação do servidor; adiciona `suspend fun sincronizar()`.

- [ ] **Step 1: Criar o DTO de processo e o teste de mapeamento (falhando)**

Create `app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/ProcessoDto.kt`:

```kotlin
package com.josiel.organizeprocesso.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProcessoDto(
    val id: String,
    @SerialName("organizacao_id") val organizacaoId: String,
    val numero: String,
    val objeto: String,
    val descricao: String,
    @SerialName("orgao_demandante") val orgaoDemandante: String,
    @SerialName("tipo_processo_id") val tipoProcessoId: String,
    @SerialName("valor_estimado_total") val valorEstimadoTotal: Double,
    @SerialName("data_abertura") val dataAbertura: String,
    @SerialName("fase_atual_id") val faseAtualId: String,
    @SerialName("status_geral") val statusGeral: String,
    @SerialName("responsavel_id") val responsavelId: String?,
    @SerialName("designado_em") val designadoEm: String?,
    @SerialName("designado_por") val designadoPor: String?,
    @SerialName("criado_em") val criadoEm: String,
    @SerialName("atualizado_em") val atualizadoEm: String
)
```

Create `app/src/test/java/com/josiel/organizeprocesso/data/repository/ProcessoDtoMappingTest.kt`:

```kotlin
package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.remote.dto.ProcessoDto
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessoDtoMappingTest {
    @Test
    fun `mapeia dto para entity preservando todos os campos`() {
        val dto = ProcessoDto(
            id = "p1",
            organizacaoId = "org1",
            numero = "001/2026",
            objeto = "Aquisição de equipamentos",
            descricao = "",
            orgaoDemandante = "",
            tipoProcessoId = "tipo1",
            valorEstimadoTotal = 1000.0,
            dataAbertura = "2026-09-01",
            faseAtualId = "fase1",
            statusGeral = "em_andamento",
            responsavelId = null,
            designadoEm = null,
            designadoPor = null,
            criadoEm = "2026-09-01T10:00:00Z",
            atualizadoEm = "2026-09-01T10:00:00Z"
        )

        val entity = dto.paraEntity()

        assertEquals("p1", entity.id)
        assertEquals("org1", entity.organizacaoId)
        assertEquals("tipo1", entity.tipoProcessoId)
        assertEquals(StatusGeralProcesso.EM_ANDAMENTO, entity.statusGeral)
        assertEquals(LocalDate.of(2026, 9, 1), entity.dataAbertura)
        assertEquals(null, entity.responsavelId)
    }
}
```

- [ ] **Step 2: Rodar o teste, confirmar que falha**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.data.repository.ProcessoDtoMappingTest"
```
Expected: FAIL — `paraEntity()` ainda não existe.

- [ ] **Step 3: Reescrever `ProcessoRepository`**

Replace the full contents of `app/src/main/java/com/josiel/organizeprocesso/data/repository/ProcessoRepository.kt`:

```kotlin
package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.local.ItemEntity
import com.josiel.organizeprocesso.data.local.ProcessoEntity
import com.josiel.organizeprocesso.data.remote.dto.ProcessoDto
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Escreve direto no Postgrest (RLS: só admin cria; admin/dono/órfão edita).
 * Room é cache de leitura, atualizado após a confirmação do servidor — não
 * há mais fila de sincronização local (spec do pivô, seção 10).
 */
class ProcessoRepository(
    private val database: AppDatabase,
    private val client: SupabaseClient
) {
    private val processoDao = database.processoDao()
    private val itemDao = database.itemDao()

    fun observarTodos(): Flow<List<ProcessoEntity>> = processoDao.observarTodos()
    fun observarPorId(id: String): Flow<ProcessoEntity?> = processoDao.observarPorId(id)
    fun observarItens(processoId: String): Flow<List<ItemEntity>> = itemDao.observarPorProcesso(processoId)

    suspend fun sincronizar() {
        val dtos = client.postgrest["processos"].select().decodeList<ProcessoDto>()
        dtos.forEach { processoDao.upsert(it.paraEntity()) }
    }

    suspend fun criar(
        organizacaoId: String,
        numero: String,
        objeto: String,
        descricao: String,
        orgaoDemandante: String,
        tipoProcessoId: String,
        dataAbertura: LocalDate,
        faseInicialId: String,
        statusGeral: StatusGeralProcesso,
        itens: List<ItemEntity>
    ): String {
        val processoId = UUID.randomUUID().toString()
        val valorTotal = itens.sumOf { it.quantidade * it.valorEstimadoUnit }

        val linhaProcesso = buildJsonObject {
            put("id", processoId)
            put("organizacao_id", organizacaoId)
            put("numero", numero)
            put("objeto", objeto)
            put("descricao", descricao)
            put("orgao_demandante", orgaoDemandante)
            put("tipo_processo_id", tipoProcessoId)
            put("valor_estimado_total", valorTotal)
            put("data_abertura", dataAbertura.toString())
            put("fase_atual_id", faseInicialId)
            put("status_geral", statusGeral.name.lowercase())
        }
        client.postgrest["processos"].insert(linhaProcesso)

        if (itens.isNotEmpty()) {
            val linhasItens = itens.map { item ->
                buildJsonObject {
                    put("id", UUID.randomUUID().toString())
                    put("processo_id", processoId)
                    put("descricao", item.descricao)
                    put("quantidade", item.quantidade)
                    put("unidade", item.unidade)
                    put("valor_estimado_unit", item.valorEstimadoUnit)
                    item.valorPesquisaUnit?.let { put("valor_pesquisa_unit", it) }
                }
            }
            client.postgrest["itens"].insert(linhasItens)
        }

        sincronizar()
        return processoId
    }

    suspend fun atualizar(
        processo: ProcessoEntity,
        itensAtuais: List<ItemEntity>,
        itensRemovidos: List<ItemEntity>
    ) {
        val valorTotal = itensAtuais.sumOf { it.quantidade * it.valorEstimadoUnit }

        val linhaProcesso = buildJsonObject {
            put("numero", processo.numero)
            put("objeto", processo.objeto)
            put("descricao", processo.descricao)
            put("orgao_demandante", processo.orgaoDemandante)
            put("tipo_processo_id", processo.tipoProcessoId)
            put("valor_estimado_total", valorTotal)
            put("status_geral", processo.statusGeral.name.lowercase())
        }
        client.postgrest["processos"].update(linhaProcesso) {
            filter { eq("id", processo.id) }
        }

        itensAtuais.forEach { item ->
            val linhaItem = buildJsonObject {
                put("id", item.id)
                put("processo_id", processo.id)
                put("descricao", item.descricao)
                put("quantidade", item.quantidade)
                put("unidade", item.unidade)
                put("valor_estimado_unit", item.valorEstimadoUnit)
                item.valorPesquisaUnit?.let { put("valor_pesquisa_unit", it) }
            }
            client.postgrest["itens"].upsert(linhaItem)
        }
        itensRemovidos.forEach { item ->
            client.postgrest["itens"].delete {
                filter { eq("id", item.id) }
            }
        }

        sincronizar()
        val itensAtualizados = client.postgrest["itens"].select {
            filter { eq("processo_id", processo.id) }
        }.decodeList<com.josiel.organizeprocesso.data.remote.dto.ItemDto>()
        itensAtualizados.forEach { itemDao.upsert(it.paraEntity(processo.id)) }
        itensRemovidos.forEach { itemDao.delete(it) }
    }
}

fun ProcessoDto.paraEntity(): ProcessoEntity = ProcessoEntity(
    id = id,
    organizacaoId = organizacaoId,
    numero = numero,
    objeto = objeto,
    descricao = descricao,
    orgaoDemandante = orgaoDemandante,
    tipoProcessoId = tipoProcessoId,
    valorEstimadoTotal = valorEstimadoTotal,
    dataAbertura = LocalDate.parse(dataAbertura),
    faseAtualId = faseAtualId,
    statusGeral = StatusGeralProcesso.valueOf(statusGeral.uppercase()),
    responsavelId = responsavelId,
    designadoEm = designadoEm?.let(Instant::parse),
    designadoPor = designadoPor,
    criadoEm = Instant.parse(criadoEm),
    atualizadoEm = Instant.parse(atualizadoEm)
)
```

Este código referencia `com.josiel.organizeprocesso.data.remote.dto.ItemDto`, criado no próximo step.

- [ ] **Step 4: Criar `ItemDto`**

Create `app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/ItemDto.kt`:

```kotlin
package com.josiel.organizeprocesso.data.remote.dto

import com.josiel.organizeprocesso.data.local.ItemEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItemDto(
    val id: String,
    @SerialName("processo_id") val processoId: String,
    val descricao: String,
    val quantidade: Double,
    val unidade: String,
    @SerialName("valor_estimado_unit") val valorEstimadoUnit: Double,
    @SerialName("valor_pesquisa_unit") val valorPesquisaUnit: Double?
)

fun ItemDto.paraEntity(processoIdFallback: String): ItemEntity = ItemEntity(
    id = id,
    processoId = processoId.ifBlank { processoIdFallback },
    descricao = descricao,
    quantidade = quantidade,
    unidade = unidade,
    valorEstimadoUnit = valorEstimadoUnit,
    valorPesquisaUnit = valorPesquisaUnit
)
```

- [ ] **Step 5: Rodar o teste, confirmar que passa**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.data.repository.ProcessoDtoMappingTest"
```
Expected: `BUILD SUCCESSFUL`, 1/1 teste passando.

- [ ] **Step 6: Verificar que o projeto compila**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

Note: `ProcessoListViewModel`/`ProcessoFormViewModel`/`ProcessoDetalheViewModel` ainda referenciam a assinatura antiga de `ProcessoRepository` (que recebia `deviceId: String`, não `client: SupabaseClient`) e o campo `processo.tipo` que não existe mais — o build só fica totalmente verde depois da Task 8 (que ajusta esses ViewModels o suficiente para compilar, sem reescrever seu comportamento de UI — isso é Plano 2B). Se `compileDebugKotlin` falhar aqui apontando esses ViewModels, isso é esperado nesta altura do plano; confirme que o erro é exatamente nesses arquivos (e não em `ProcessoRepository.kt`/`ProcessoDto.kt` que você acabou de escrever) antes de seguir.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/data/repository/ProcessoRepository.kt
git add app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/ProcessoDto.kt
git add app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/ItemDto.kt
git add app/src/test/java/com/josiel/organizeprocesso/data/repository/ProcessoDtoMappingTest.kt
git commit -m "feat: rewrite ProcessoRepository to write through Postgrest"
```

---

## Task 6: Reescrever `FaseRepository` e `ItemRepository`

**Files:**
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/repository/FaseRepository.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/repository/ItemRepository.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/FaseDto.kt`

**Interfaces:**
- Consumes: `FaseEntity`/`ItemEntity` v2 (Task 2), `ItemDto`/`paraEntity` (Task 5).
- Produces: `FaseRepository(dao: FaseDao, client: SupabaseClient)` com `observarTodas()`, `suspend fun sincronizar()`, `suspend fun salvar(id: String?, organizacaoId: String, nome: String, ordem: Int, descricao: String?, diasAlertaAtencao: Int, diasAlertaCritico: Int)`, `suspend fun excluir(fase: FaseEntity)`. `ItemRepository(dao: ItemDao, client: SupabaseClient)` com `observarPorProcesso(processoId)`, `suspend fun sincronizar(processoId: String)`.

- [ ] **Step 1: Criar `FaseDto`**

Create `app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/FaseDto.kt`:

```kotlin
package com.josiel.organizeprocesso.data.remote.dto

import com.josiel.organizeprocesso.data.local.FaseEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FaseDto(
    val id: String,
    @SerialName("organizacao_id") val organizacaoId: String,
    val nome: String,
    val ordem: Int,
    val descricao: String?,
    @SerialName("dias_alerta_atencao") val diasAlertaAtencao: Int,
    @SerialName("dias_alerta_critico") val diasAlertaCritico: Int
)

fun FaseDto.paraEntity(): FaseEntity = FaseEntity(
    id = id,
    organizacaoId = organizacaoId,
    nome = nome,
    ordem = ordem,
    descricao = descricao,
    diasAlertaAtencao = diasAlertaAtencao,
    diasAlertaCritico = diasAlertaCritico
)
```

- [ ] **Step 2: Reescrever `FaseRepository`**

Replace the full contents of `app/src/main/java/com/josiel/organizeprocesso/data/repository/FaseRepository.kt`:

```kotlin
package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.FaseDao
import com.josiel.organizeprocesso.data.local.FaseEntity
import com.josiel.organizeprocesso.data.remote.dto.FaseDto
import com.josiel.organizeprocesso.data.remote.dto.paraEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Escreve direto no Postgrest (admin-only no backend); Room é cache de leitura. */
class FaseRepository(
    private val dao: FaseDao,
    private val client: SupabaseClient
) {
    fun observarTodas(): Flow<List<FaseEntity>> = dao.observarTodas()

    suspend fun sincronizar() {
        val dtos = client.postgrest["fases"].select().decodeList<FaseDto>()
        dao.upsertTodos(dtos.map { it.paraEntity() })
    }

    suspend fun salvar(
        id: String?,
        organizacaoId: String,
        nome: String,
        ordem: Int,
        descricao: String?,
        diasAlertaAtencao: Int,
        diasAlertaCritico: Int
    ) {
        val linha = buildJsonObject {
            put("id", id ?: UUID.randomUUID().toString())
            put("organizacao_id", organizacaoId)
            put("nome", nome)
            put("ordem", ordem)
            descricao?.takeIf { it.isNotBlank() }?.let { put("descricao", it) }
            put("dias_alerta_atencao", diasAlertaAtencao)
            put("dias_alerta_critico", diasAlertaCritico)
        }
        client.postgrest["fases"].upsert(linha)
        sincronizar()
    }

    suspend fun excluir(fase: FaseEntity) {
        client.postgrest["fases"].delete {
            filter { eq("id", fase.id) }
        }
        dao.delete(fase)
    }
}
```

`FaseDao` precisa do método `upsertTodos` (mesmo formato de `PerfilDao`/`TipoProcessoDao` da Task 2/4) — adicione em `app/src/main/java/com/josiel/organizeprocesso/data/local/FaseDao.kt`, depois do método `upsert` existente:

```kotlin
    @Upsert
    suspend fun upsertTodos(fases: List<FaseEntity>)
```

- [ ] **Step 3: Reescrever `ItemRepository`**

Replace the full contents of `app/src/main/java/com/josiel/organizeprocesso/data/repository/ItemRepository.kt`:

```kotlin
package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.ItemDao
import com.josiel.organizeprocesso.data.local.ItemEntity
import com.josiel.organizeprocesso.data.remote.dto.ItemDto
import com.josiel.organizeprocesso.data.remote.dto.paraEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Escreve direto no Postgrest; Room é cache de leitura por processo. */
class ItemRepository(
    private val dao: ItemDao,
    private val client: SupabaseClient
) {
    fun observarPorProcesso(processoId: String): Flow<List<ItemEntity>> = dao.observarPorProcesso(processoId)

    suspend fun sincronizar(processoId: String) {
        val dtos = client.postgrest["itens"].select {
            filter { eq("processo_id", processoId) }
        }.decodeList<ItemDto>()
        dtos.forEach { dao.upsert(it.paraEntity(processoId)) }
    }

    suspend fun salvar(item: ItemEntity) {
        val linha = buildJsonObject {
            put("id", item.id)
            put("processo_id", item.processoId)
            put("descricao", item.descricao)
            put("quantidade", item.quantidade)
            put("unidade", item.unidade)
            put("valor_estimado_unit", item.valorEstimadoUnit)
            item.valorPesquisaUnit?.let { put("valor_pesquisa_unit", it) }
        }
        client.postgrest["itens"].upsert(linha)
        sincronizar(item.processoId)
    }

    suspend fun excluir(item: ItemEntity) {
        client.postgrest["itens"].delete {
            filter { eq("id", item.id) }
        }
        dao.delete(item)
    }
}
```

- [ ] **Step 4: Verificar que compila**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL` para os arquivos desta task especificamente — `FaseCadastroViewModel` (que usa `FaseRepository`) ainda referencia a assinatura antiga (`FaseDao, deviceId: String` em vez de `FaseDao, SupabaseClient`) e só é ajustado na Task 8; mesma situação já registrada na Task 5 para os ViewModels de Processo.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/data/repository/FaseRepository.kt
git add app/src/main/java/com/josiel/organizeprocesso/data/repository/ItemRepository.kt
git add app/src/main/java/com/josiel/organizeprocesso/data/local/FaseDao.kt
git add app/src/main/java/com/josiel/organizeprocesso/data/remote/dto/FaseDto.kt
git commit -m "feat: rewrite FaseRepository and ItemRepository to write through Postgrest"
```

---

## Task 7: `RealtimeSyncManager`

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/remote/RealtimeSyncManager.kt`

**Interfaces:**
- Consumes: `SupabaseSessionManager.client` (Task 3); todos os repositórios com `sincronizar()` (Tasks 4-6).
- Produces: `RealtimeSyncManager` object com `fun iniciar(escopo: CoroutineScope, repositorios: RepositoriosSincronizaveis)` (assina os canais Realtime das 8 tabelas de negócio e chama `sincronizar()` do repositório correspondente a cada evento) e `suspend fun encerrar()` (desconecta todos os canais — chamado no logout).

- [ ] **Step 1: Criar o gerenciador de sincronização em tempo real**

Create `app/src/main/java/com/josiel/organizeprocesso/data/remote/RealtimeSyncManager.kt`:

```kotlin
package com.josiel.organizeprocesso.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.PostgrestChannelBuilder
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Uma função `sincronizar()` por tabela de negócio — chamada sempre que
 * chega um evento `postgres_changes` para essa tabela (não tenta aplicar o
 * payload do evento diretamente no Room; simplesmente refaz a leitura
 * completa da tabela via Postgrest, mais simples e robusto que reconciliar
 * insert/update/delete evento a evento).
 */
data class RepositoriosSincronizaveis(
    val perfis: suspend () -> Unit,
    val tiposProcesso: suspend () -> Unit,
    val fases: suspend () -> Unit,
    val processos: suspend () -> Unit
)

private val TABELAS_MONITORADAS = listOf(
    "perfis", "tipos_processo", "fases", "processos",
    "itens", "processo_fase_historico", "diligencias", "observacao_versoes"
)

object RealtimeSyncManager {
    private val canais = mutableListOf<RealtimeChannel>()

    fun iniciar(client: SupabaseClient, escopo: CoroutineScope, repositorios: RepositoriosSincronizaveis) {
        TABELAS_MONITORADAS.forEach { tabela ->
            val canal = client.channel("cache-sync-$tabela")
            val callback: suspend () -> Unit = when (tabela) {
                "perfis" -> repositorios.perfis
                "tipos_processo" -> repositorios.tiposProcesso
                "fases" -> repositorios.fases
                else -> repositorios.processos
            }
            escopo.launch {
                canal.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = tabela
                }.collect { callback() }
            }
            escopo.launch { canal.subscribe() }
            canais.add(canal)
        }
    }

    suspend fun encerrar() {
        canais.forEach { it.unsubscribe() }
        canais.clear()
    }
}
```

**Nota:** os eventos das tabelas `itens`, `processo_fase_historico`, `diligencias`, `observacao_versoes` disparam a mesma função `repositorios.processos` como uma simplificação desta task (refaz a sincronização geral de processos, que é barata) — o Plano 2B pode refinar isso com callbacks dedicados quando `DiligenciaRepository`/`HistoricoFaseRepository` existirem de verdade. Mesma nota de API do `supabase-kt` das tasks anteriores se aplica aqui (`channel`, `postgresChangeFlow`, `subscribe`/`unsubscribe` são os nomes esperados da API de Realtime da biblioteca — ajuste conforme o compilador indicar).

- [ ] **Step 2: Verificar que compila**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin
```
Expected: erros restantes (se houver) devem estar apenas nos ViewModels/telas ainda não ajustados (Task 8), não em `RealtimeSyncManager.kt`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/data/remote/RealtimeSyncManager.kt
git commit -m "feat: RealtimeSyncManager subscribes to org-scoped tables"
```

---

## Task 8: Tela de login, gate de navegação, logout, remoção da UI de Pessoa

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/ui/auth/LoginScreen.kt`
- Create: `app/src/main/java/com/josiel/organizeprocesso/ui/auth/LoginViewModel.kt`
- Delete: `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/PessoaCadastroViewModel.kt`
- Delete: `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/PessoasScreen.kt`
- Delete: `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/PessoaFormDialog.kt`
- Delete: `app/src/main/java/com/josiel/organizeprocesso/data/util/DeviceId.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/navigation/Routes.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/navigation/AppNavHost.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/MaisScreen.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoListViewModel.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoFormViewModel.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoDetalheViewModel.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/AvancarFaseViewModel.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/FaseCadastroViewModel.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/data/repository/HistoricoFaseRepository.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/FasesScreen.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoDetalheScreen.kt`

**Ruling adicionada durante a execução deste plano (Task 6 fez uma verificação de build limpo — `--rerun-tasks` — e descobriu que a compilação incremental do Gradle não reportava 4 arquivos que já estavam quebrados desde a Task 2, mascarados pelo cache de compilação incremental do Kotlin):** os três arquivos acima entram na lista desta task pelo mesmo motivo dos outros — referenciam campos/entidades removidos na Task 2 (`ProcessoFaseHistoricoEntity`/`ObservacaoVersaoEntity` com `updatedAt`/`synced`/`deviceOrigin`; `FaseEntity.padrao`; `ProcessoEntity.tipo`) e precisam do mesmo tratamento "só o suficiente para compilar" descrito abaixo — não uma reescrita de `HistoricoFaseRepository`/`AvancarFaseScreen` de verdade, que continua sendo escopo do Plano 2B.

**Interfaces:**
- Consumes: `SupabaseSessionManager` (Task 3), `RealtimeSyncManager` (Task 7), os repositórios reescritos (Tasks 4-6).
- Produces: rota `Login` como novo `startDestination`; `AppNavHost` observando `SupabaseSessionManager.sessionStatus` para decidir entre `Login` e `Inicio`.

**Nota sobre o tamanho desta task:** os quatro ViewModels de Processo e o `FaseCadastroViewModel` são ajustados aqui **só o suficiente para compilar** contra as novas assinaturas de repositório (`SupabaseClient` em vez de `deviceId: String`, `PerfilRepository` em vez de `PessoaRepository`, `tipoProcessoId` em vez de `tipo`) — não é uma reescrita de comportamento/UI (isso é Plano 2B, que já tem sua própria spec). O critério de pronto desta task é "o app compila e abre no login", não "os fluxos de processo já refletem os novos papéis".

- [ ] **Step 1: Criar `LoginViewModel`**

Create `app/src/main/java/com/josiel/organizeprocesso/ui/auth/LoginViewModel.kt`:

```kotlin
package com.josiel.organizeprocesso.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LoginUiState(
    val carregando: Boolean = false,
    val erro: String? = null
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(email: String, senha: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState(carregando = true)
            try {
                SupabaseSessionManager.login(email, senha)
                _uiState.value = LoginUiState(carregando = false)
            } catch (e: Exception) {
                _uiState.value = LoginUiState(carregando = false, erro = "Não foi possível entrar. Verifique e-mail e senha.")
            }
        }
    }
}
```

- [ ] **Step 2: Criar `LoginScreen`**

Create `app/src/main/java/com/josiel/organizeprocesso/ui/auth/LoginScreen.kt`:

```kotlin
package com.josiel.organizeprocesso.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** Porta de entrada do app (spec do pivô, seção 9) — login por e-mail/senha via Supabase Auth. */
@Composable
fun LoginScreen(viewModel: LoginViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(24.dp)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Organize Processo", style = MaterialTheme.typography.headlineMedium)
        Column(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = senha,
                onValueChange = { senha = it },
                label = { Text("Senha") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            uiState.erro?.let { erro ->
                Text(erro, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { viewModel.login(email, senha) },
                enabled = !uiState.carregando && email.isNotBlank() && senha.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.carregando) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                } else {
                    Text("Entrar")
                }
            }
        }
    }
}
```

- [ ] **Step 3: Adicionar a rota de login**

In `app/src/main/java/com/josiel/organizeprocesso/navigation/Routes.kt`, add after the `Mais` object:

```kotlin
@Serializable
object Login
```

Remove the `CadastroPessoas` route (`@Serializable object CadastroPessoas`) — não existe mais tela de cadastro de Pessoas.

- [ ] **Step 4: Adicionar o gate de autenticação em `AppNavHost`**

Replace the full contents of `app/src/main/java/com/josiel/organizeprocesso/navigation/AppNavHost.kt`:

```kotlin
package com.josiel.organizeprocesso.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import io.github.jan.supabase.auth.status.SessionStatus
import com.josiel.organizeprocesso.ui.agenda.AgendaScreen
import com.josiel.organizeprocesso.ui.auth.LoginScreen
import com.josiel.organizeprocesso.ui.cadastro.FasesScreen
import com.josiel.organizeprocesso.ui.cadastro.MaisScreen
import com.josiel.organizeprocesso.ui.inicio.InicioScreen
import com.josiel.organizeprocesso.ui.processos.AvancarFaseScreen
import com.josiel.organizeprocesso.ui.processos.ProcessoDetalheScreen
import com.josiel.organizeprocesso.ui.processos.ProcessoFormScreen
import com.josiel.organizeprocesso.ui.processos.ProcessosScreen

private val abasComBottomBar = listOf(Inicio::class, Processos::class, Agenda::class, Mais::class)

/** Grafo de navegação do app: gate de login, depois bottom nav de 4 abas + rotas empilhadas. */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val mostrarBottomBar = abasComBottomBar.any { rota ->
        currentDestination?.hierarchy?.any { it.hasRoute(rota) } == true
    }
    val sessionStatus by SupabaseSessionManager.sessionStatus.collectAsState()

    LaunchedEffect(sessionStatus) {
        val autenticado = sessionStatus is SessionStatus.Authenticated
        val emLogin = currentDestination?.hierarchy?.any { it.hasRoute(Login::class) } == true
        if (autenticado && emLogin) {
            navController.navigate(Inicio) { popUpTo(Login) { inclusive = true } }
        } else if (!autenticado && !emLogin) {
            navController.navigate(Login) { popUpTo(0) { inclusive = true } }
        }
    }

    Scaffold(
        bottomBar = { if (mostrarBottomBar) AppBottomBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Login,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<Login> { LoginScreen() }

            composable<Inicio> { InicioScreen() }

            composable<Processos> {
                ProcessosScreen(
                    onProcessoClick = { processoId ->
                        navController.navigate(ProcessoDetalhe(processoId))
                    },
                    onNovoProcessoClick = { navController.navigate(ProcessoForm()) }
                )
            }

            composable<Agenda> { AgendaScreen() }

            composable<Mais> {
                MaisScreen(
                    onCadastroFasesClick = { navController.navigate(CadastroFases) }
                )
            }

            composable<CadastroFases> {
                FasesScreen(onBackClick = { navController.navigateUp() })
            }

            composable<ProcessoDetalhe> { entry ->
                val rota = entry.toRoute<ProcessoDetalhe>()
                ProcessoDetalheScreen(
                    processoId = rota.processoId,
                    onAvancarFaseClick = {
                        navController.navigate(AvancarFase(rota.processoId))
                    },
                    onEditarClick = {
                        navController.navigate(ProcessoForm(rota.processoId))
                    },
                    onBackClick = { navController.navigateUp() }
                )
            }

            composable<AvancarFase> { entry ->
                val rota = entry.toRoute<AvancarFase>()
                AvancarFaseScreen(
                    processoId = rota.processoId,
                    onBackClick = { navController.navigateUp() }
                )
            }

            composable<ProcessoForm> { entry ->
                val rota = entry.toRoute<ProcessoForm>()
                ProcessoFormScreen(
                    processoId = rota.processoId,
                    onBackClick = { navController.navigateUp() },
                    onSalvo = { navController.navigateUp() }
                )
            }
        }
    }
}
```

- [ ] **Step 5: Remover a UI de Pessoa**

Delete `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/PessoaCadastroViewModel.kt`, `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/PessoasScreen.kt`, `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/PessoaFormDialog.kt`.

In `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/MaisScreen.kt`: remove the `onCadastroPessoasClick: () -> Unit` parameter from the `MaisScreen` function signature, remove the `OpcaoMais("Cadastro de Pessoas", ...)` entry from the `opcoes` list, and remove the now-unused `import androidx.compose.material.icons.filled.Person`.

- [ ] **Step 6: Remover `DeviceId.kt`**

Delete `app/src/main/java/com/josiel/organizeprocesso/data/util/DeviceId.kt` (órfão desde que `deviceOrigin` saiu de todas as entidades na Task 2).

- [ ] **Step 7: Ajustar os ViewModels de Processo para compilar contra as novas assinaturas**

In `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoListViewModel.kt`: replace the constructor body (lines instantiating `deviceId`/`pessoaRepository`) with:

```kotlin
    private val database = AppDatabase.getInstance(application)
    private val processoRepository = ProcessoRepository(database, SupabaseSessionManager.client)
    private val faseRepository = FaseRepository(database.faseDao(), SupabaseSessionManager.client)
    private val perfilRepository = PerfilRepository(database.perfilDao(), SupabaseSessionManager.client)
    private val historicoDao = database.processoFaseHistoricoDao()
```

Update the `combine(...)` block to use `perfilRepository.observarTodos()` instead of `pessoaRepository.observarTodas()`, and `pessoaMap[it]?.nome` becomes `perfilMap[it]?.nome` (rename the local `pessoaMap`/`pessoas` variables to `perfilMap`/`perfis` for clarity). Update imports: remove `com.josiel.organizeprocesso.data.repository.PessoaRepository` and `com.josiel.organizeprocesso.data.util.DeviceId`, add `com.josiel.organizeprocesso.data.remote.SupabaseSessionManager` and `com.josiel.organizeprocesso.data.repository.PerfilRepository`.

In `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoFormViewModel.kt`, `ProcessoDetalheViewModel.kt`, and `AvancarFaseViewModel.kt`: apply the same mechanical substitution pattern — wherever the file currently does `DeviceId.obter(application)` followed by passing `deviceId` into a repository constructor, replace with passing `SupabaseSessionManager.client` instead; wherever it references `PessoaRepository`/`PessoaEntity`/`pessoaDao()`, replace with `PerfilRepository`/`PerfilEntity`/`perfilDao()`; wherever it references `processo.tipo` (the old free-text field), replace with `processo.tipoProcessoId` (the field itself now holds an id, not display text — any UI code that was displaying `processo.tipo` as a string directly will now display a raw id, which is visually wrong but compiles; fixing the actual tipo-de-processo dropdown/display is explicitly Plano 2B's job, not this task's).

Specifically in `ProcessoFormViewModel.kt`, the call site for `processoRepository.criar(...)` needs two additional changes beyond the mechanical pattern above, since Task 5's new `criar(...)` signature added a leading `organizacaoId: String` parameter and renamed `tipo` to `tipoProcessoId`: add `organizacaoId = SupabaseSessionManager.perfilAtual?.organizacaoId.orEmpty()` as the first named argument, and rename the `tipo = ...` argument to `tipoProcessoId = ...` (passing whatever the form currently holds for that field — it will be a raw id string post-Task-2, matching the parameter's new type; the form's own UI for picking a tipo de processo is still Plano 2B's job, not this task's).

- [ ] **Step 8: Ajustar `FaseCadastroViewModel`**

In `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/FaseCadastroViewModel.kt`: replace the `FaseRepository(database.faseDao(), deviceId)` construction with `FaseRepository(database.faseDao(), SupabaseSessionManager.client)`, remove the `DeviceId.obter(application)` line and its import, add the `SupabaseSessionManager` import. If the file calls `faseRepository.salvar(...)` with the old parameter list (no `organizacaoId`), add `organizacaoId = SupabaseSessionManager.perfilAtual?.organizacaoId.orEmpty()` as an argument.

- [ ] **Step 9: Ajustar `HistoricoFaseRepository`, `FasesScreen` e `ProcessoDetalheScreen`**

In `app/src/main/java/com/josiel/organizeprocesso/data/repository/HistoricoFaseRepository.kt`: every `.copy(...)`/constructor call for `ProcessoFaseHistoricoEntity`/`ObservacaoVersaoEntity` still passes the named arguments `updatedAt = ...`, `synced = ...`, `deviceOrigin = ...`, all removed from those entities in Task 2 — remove all three named arguments from every such call site in this file (there are four call sites: two inside `salvarEntradaAtual`, two inside `mudarFase`). Do not otherwise change this file's logic — it still does local-only Room writes for now (its real rewrite to call the `avancar_fase()`/`designar_processo()` backend RPCs is Plano 2B's job, not this task's).

In `app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/FasesScreen.kt`, line ~127: this screen reads `.padrao` from a `FaseEntity` (removed in Task 2) — remove whatever UI element displays/uses this field (likely a badge or checkbox indicating "fase padrão"). If removing it leaves an empty `if`/conditional branch, remove that too. This is a visual regression accepted for this task (per the same "compiles, doesn't need to look right yet" standard already set for the ViewModels in Steps 7-8) — Plano 2B can decide whether "fase padrão" as a concept comes back in some form.

In `app/src/main/java/com/josiel/organizeprocesso/ui/processos/ProcessoDetalheScreen.kt`, line ~145: this screen reads `.tipo` from a `ProcessoEntity` twice (removed in Task 2, replaced by `.tipoProcessoId`) — replace both occurrences with `.tipoProcessoId`. As already noted for the ViewModels in Step 7, this will display a raw id instead of a readable type name until Plano 2B adds the real tipo-de-processo lookup/display — that's expected and acceptable for this task.

- [ ] **Step 10: Verificar que o projeto compila por completo (build limpo)**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin --rerun-tasks
```
Expected: `BUILD SUCCESSFUL`. **Use `--rerun-tasks` here specifically** (not a plain incremental `compileDebugKotlin`) — a Task 6 diligence check found that Gradle/Kotlin's incremental compilation can silently omit reporting errors in files that were not pulled into that particular build's affected-file set, even though those files would fail a real build. A plain incremental compile passing is not trustworthy evidence that the WHOLE project compiles; `--rerun-tasks` forces a full, honest recompilation. If it still fails anywhere, that's a place this plan didn't anticipate — resolve it mechanically following the same substitution patterns already used in this task (never invent a new business rule to "make it compile"; if an error requires a real design decision, stop and report instead of guessing).

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/ui/auth
git add app/src/main/java/com/josiel/organizeprocesso/navigation
git add app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/MaisScreen.kt
git add app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/FasesScreen.kt
git add app/src/main/java/com/josiel/organizeprocesso/ui/processos
git add app/src/main/java/com/josiel/organizeprocesso/data/repository/HistoricoFaseRepository.kt
git add app/src/main/java/com/josiel/organizeprocesso/ui/cadastro/FaseCadastroViewModel.kt
git commit -m "feat: login screen, auth gate, remove Pessoa UI"
```

---

## Task 9: Verificação final

**Files:** nenhum criado — só verificação.

- [ ] **Step 1: Build completo (limpo)**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin --rerun-tasks
```
Expected: `BUILD SUCCESSFUL`. Use `--rerun-tasks` (não incremental) pelo mesmo motivo do Step 10 da Task 8 — só um build forçado do zero é evidência confiável de que o projeto inteiro compila.

- [ ] **Step 2: Suíte de testes unitários completa**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`, todos os testes das Tasks 3 e 5 passando (mais qualquer teste pré-existente do template, que devem continuar passando).

- [ ] **Step 3: Registrar o que fica pendente até haver acesso a um emulador/dispositivo**

Não é possível, remotamente, verificar: login real contra o projeto Supabase (rede funciona neste ambiente, mas a UI/fluxo de Auth completo precisa rodar em um dispositivo Android de verdade, não JVM puro), a sincronização Realtime de fato atualizando a UI, e a aparência visual da nova `LoginScreen`. Isso fica registrado como pendente para quando o usuário tiver acesso físico — não é um bloqueador para fechar este plano, mas deve ser verificado antes do Plano 2B assumir que a fundação funciona de ponta a ponta.

- [ ] **Step 4: Commit (se necessário)**

Se os Steps 1-2 não exigiram nenhuma mudança de código, não há o que commitar — este task é só o portão de verificação final.
