# Plano 2A — Fundação de dados e autenticação do app Android (design)

Data: 2026-09-01

## 0. Como ler este documento

Este é o primeiro de quatro sub-projetos em que o "Plano 2" (camada Android do
pivô multiusuário) foi decomposto — ver seção 8 para o roadmap completo. Esta
spec cobre só a **fundação**: autenticação, o cliente Supabase, e a reescrita
do schema Room + camada de repositório. É o pré-requisito de tudo o mais.

Como esta spec foi escrita sem uma sessão interativa de perguntas (o usuário
pediu para eu avançar sozinho enquanto dormia), toda decisão que normalmente
seria uma pergunta aparece aqui como uma **Decisão (ruling)**, com a
justificativa ao lado. Nada disto é definitivo — é a spec que você revisa e
aprova (ou corrige) antes de eu escrever o plano de implementação. Nenhum
código será escrito até essa aprovação.

## 1. Contexto

O backend (Plano 1, `docs/superpowers/plans/2026-08-31-backend-supabase-multiusuario.md`)
está completo, revisado e mesclado — projeto Supabase real na nuvem,
schema multi-tenant com RLS, duas Edge Functions, função `designar_processo()`.
Este documento parte do design já aprovado em
`docs/superpowers/specs/2026-08-31-multiusuario-gestao-processos-design.md`
(seções 9–13 em especial) e o traduz em decisões técnicas concretas para o
app Android existente.

Levantamento do estado atual do app (código-fonte, não suposição):

- **Sem framework de DI** — nem Hilt, nem Koin. Padrão manual em todo lugar:
  `AppDatabase.getInstance(context)` é um singleton double-checked-locking;
  cada `ViewModel` é um `AndroidViewModel` que instancia seus próprios
  repositories no corpo/`init`.
- **`supabase-kt` já está nas dependências** (`gradle/libs.versions.toml`),
  módulos `postgrest-kt`, `auth-kt`, `storage-kt`, BOM 3.5.0, mais
  `ktor-client-android` 3.5.1 como engine HTTP — mas **nunca foi importado em
  nenhum arquivo `.kt`** (código 100% local/Room hoje, zero chamada de rede).
  Falta o módulo `realtime-kt`.
- **`kotlinx-serialization-json`** já está presente e o plugin de
  serialização já está aplicado — os comentários no `libs.versions.toml`
  já diziam "DTOs Supabase", confirmando que esta escolha de biblioteca já
  era a intenção antes deste pivô.
- **Nenhuma dependência Firebase/FCM**, nenhum `google-services.json` no
  repositório — ver seção 7.
- **Nenhum teste real existe** hoje (só os stubs padrão do Android Studio).
- Schema Room atual: 7 entidades, `version = 1`, todas carregando `synced:
  Boolean` + `deviceOrigin: String` (vestígios da fila de sincronização
  offline-first que este pivô elimina, per spec §10).
- `AppNavHost.kt` não tem nenhum gate de login/sessão — o app abre direto em
  `Inicio`.

## 2. Decisões arquiteturais (rulings)

### 2.1 Cliente Supabase e gerência de sessão

**Decisão:** usar o `supabase-kt` já declarado (Postgrest + Auth), adicionar
o módulo `realtime-kt`, e criar um único objeto singleton
`SupabaseSessionManager` (seguindo o mesmo padrão manual de
`AppDatabase.getInstance`, já que não há DI no projeto) que:
- Expõe a instância única de `SupabaseClient` (criada com `createSupabaseClient`,
  URL/anon key vindos de `BuildConfig` — ver seção 6).
- Expõe `sessionStatus: StateFlow<SessionStatus>` do módulo Auth do SDK
  (que já modela `Authenticated`/`NotAuthenticated`/`RefreshFailure` etc.).
- Usa a persistência de sessão embutida do SDK (backed by
  `SharedPreferences`/`DataStore` conforme a versão do SDK) em vez de
  reinventar armazenamento de token — o SDK já resolve refresh automático.

**Por quê:** evita introduzir uma dependência nova e mantém o padrão de
singleton manual já usado no projeto (`AppDatabase`), em vez de misturar
estilos (não vale a pena adotar Hilt só para este pivô — mudaria a superfície
de risco de forma desproporcional ao benefício).

### 2.2 Papel do Room: cache, nunca fonte de verdade para escrita

Confirma o que a spec §10 já definia, mas explicita o mecanismo:

- **Leitura**: as telas continuam observando `Flow`s do Room, como hoje —
  nenhuma tela precisa saber que os dados vêm de rede.
- **Escrita**: cada método de repositório que hoje grava só no Room passa a
  chamar o Postgrest primeiro; só grava no Room (cache) depois de confirmação
  do servidor. Falha de rede → lança uma exceção tipada
  (`SemConexaoException`/`ErroServidorException`) que a ViewModel traduz na
  mensagem "sem conexão, tente novamente" que a spec pede.
- **Atualização do cache**: dois mecanismos, não um só:
  1. **Refresh explícito**: ao entrar em cada tela principal (lista de
     processos, fila de distribuição, etc.) e via pull-to-refresh, busca a
     tabela relevante inteira (filtrada por RLS automaticamente) e faz
     upsert no Room.
  2. **Realtime**: depois do login, um `RealtimeSyncManager` (outro
     singleton) abre um canal por tabela nas 8 tabelas de negócio —
     `perfis, tipos_processo, fases, processos, itens,
     processo_fase_historico, diligencias, observacao_versoes` — e faz
     upsert/delete no Room a cada evento `postgres_changes`. Fica ativo
     enquanto o app está em primeiro plano; desconecta ao deslogar.

**Decisão — o que NÃO é espelhado no Room:** `organizacoes` (só uma linha
relevante por sessão, não precisa de tabela local) e `designacoes` (log
histórico, cresce sem limite). A spec §10 já lista explicitamente o que é
espelhado — "fases, tipos de processo, processos, itens, histórico,
diligências, perfis para a fila" — e `designações` não está nessa lista.
A tela de Fila de Distribuição (Plano 2C) faz uma consulta de rede ao vivo
com `count` agregado por pessoa em vez de manter uma tabela local só para
isso; é uma tela pequena (uma linha por pessoa da organização) que já
depende de rede para fazer sentido (contagem precisa ser atual).

### 2.3 Schema Room — mapeamento exato

| Entidade atual | Ação |
|---|---|
| `PessoaEntity` / `PessoaDao` / `PessoaRepository` | **Removidas.** Viram `PerfilEntity` (cache de `perfis`) — só leitura no app; criação é exclusivamente via Edge Function `criar-conta`, sem CRUD direto. |
| `FaseEntity` | Mantida, mas **remove `padrao: Boolean`** (não existe em `fases` no backend) e **remove `synced`/`deviceOrigin`**. Adiciona `organizacaoId`. |
| `ProcessoEntity` | Remove `tipo: String` (texto livre) → adiciona `tipoProcessoId: String` (FK). Adiciona `organizacaoId`, `responsavelId: String?`, `designadoEm: Instant?`, `designadoPor: String?`. Remove `synced`/`deviceOrigin`. |
| `ItemEntity` | Sem mudança estrutural além de remover `synced`/`deviceOrigin`. |
| `ProcessoFaseHistoricoEntity` | Sem mudança estrutural além de remover `synced`/`deviceOrigin`. **Nota importante**: `responsavelId` aqui continua existindo e é um conceito *diferente* de `Processo.responsavelId` — este é "quem executou esta passagem específica pela fase" (histórico), aquele é "quem está designado no processo agora" (mutável só via `designar_processo()`). Os nomes colidem; ao implementar, considerar renomear este campo para `executorId` para eliminar a ambiguidade (decisão de nomenclatura, não estrutural — deixo para quem escrever o plano de implementação). |
| `ObservacaoVersaoEntity` | Sem mudança. |
| `AnexoLinkEntity` / `AnexoLinkDao` | **Intocada.** Não existe tabela equivalente no backend (fora de escopo desde a spec original, seção 12) — fica como código morto, não referenciado por nenhuma tela hoje (confirmado: não há `AnexoScreen` em lugar nenhum). Não faz parte de nenhuma sub-etapa do Plano 2. |
| — (nova) | **`PerfilEntity`** (cache de `perfis`): id, organizacaoId, papel, nome, cargoSetor, ativo, notificarAvancoFase, notificarPrazo, notificarTempoParado, ultimoRecebimentoEm. |
| — (nova) | **`TipoProcessoEntity`** (cache de `tipos_processo`): id, organizacaoId, nome, diasAlertaAtencao, diasAlertaCritico. |
| — (nova) | **`DiligenciaEntity`** (cache de `diligencias`): id, processoFaseHistoricoId, autorId, conteudo, criadoEm. |

`Converters.kt` ganha conversores para os novos enums do backend
(`papel_usuario` → um enum Kotlin `Papel { SUPER_ADMIN, ADMIN, USUARIO }`,
mapeado por `.name.lowercase()` no `PostgrestBuilder`/`kotlinx.serialization`,
não à mão) e mantém os já existentes. IDs continuam `String` (UUID como
texto), consistente com o padrão já usado.

Versão do banco sobe para `2`; como não há usuários em produção com dados
locais que precisem sobreviver a esta migração (app single-user, dados eram
descartáveis por design antigo), a estratégia é **destructive migration**
(`fallbackToDestructiveMigration()`), não uma migração incremental — mais
simples e sem risco, dado que o Room deixa de ser fonte de verdade.

### 2.4 A lacuna do "avançar fase": falta uma função atômica no backend

**Achado:** o Plano 1 só criou `designar_processo()` como RPC atômica —
para designar/reatribuir um responsável. A transição de fase
(`processo_fase_historico` fechar entrada atual + abrir nova + atualizar
`processos.fase_atual_id`) não tem equivalente — ficaria a cargo do cliente
Android fazer 2-3 chamadas Postgrest sequenciais sem transação, arriscando
um estado inconsistente se a segunda chamada falhar depois da primeira ter
sucedido (ex.: nova entrada de histórico criada, mas `fase_atual_id` não
atualizado).

**Decisão:** adicionar uma nova migração pequena ao backend já mesclado —
`avancar_fase(p_processo_id uuid, p_fase_destino_id uuid, p_observacao
text, p_prazo_limite date) returns void` — mirando o padrão de
`designar_processo()` (mesmo arquivo de convenções, mesmo `scripts/run_sql.mjs`,
mesmo pgTAP). Não precisa ser `security definer` (o chamador já tem acesso
RLS legítimo às duas tabelas quando tem permissão de avançar a fase) — só
precisa da transação implícita de uma função `plpgsql` para atomicidade.
Esta função entra como um pré-requisito técnico do Plano 2A/2B, não como
parte do Plano 1 (que já foi revisado e fechado) — é um pequeno incremento
novo no mesmo repositório Supabase, usando exatamente a mesma infraestrutura
(`run_sql.mjs`, pgTAP, `node scripts/run_sql.mjs`).

**Pergunta para você confirmar:** concorda com adicionar esta função agora
(antes do Plano 2B, que é quem realmente a usa), ou prefere que o Plano 2B
faça as 2-3 chamadas sequenciais do jeito mais simples primeiro e só
adicione a função atômica depois, se algum problema real aparecer? Minha
recomendação é a primeira opção (função atômica desde já) — é barato agora
e caro depois (uma vez que existam processos reais com histórico
inconsistente, corrigir dados é sempre pior que prevenir).

### 2.5 `DeviceId.kt`

Fica órfão assim que `deviceOrigin` sai de todas as entidades (seu único
consumidor). **Decisão:** remover o arquivo nesta sub-etapa — não tem
relação com `device_tokens`/FCM (que usa o token real do Firebase Cloud
Messaging, uma fonte de dado completamente diferente, tratada no Plano 2D).

### 2.6 Autenticação — telas e fluxo

- Nova tela `LoginScreen` (e-mail/senha) vira o novo `startDestination` do
  `AppNavHost`. Sem tela de cadastro (spec §9: "sem autocadastro").
- Um gate simples no `AppNavHost`: observa
  `SupabaseSessionManager.sessionStatus`; se `Authenticated`, navega para
  `Inicio`; se não, fica em `Login`. Um splash/loading state cobre o
  instante de checagem inicial da sessão persistida.
- Logout: limpa a sessão do SDK, desconecta o `RealtimeSyncManager`, e
  **limpa todas as tabelas do Room** (`clearAllTables()`) — evita que dados
  de uma organização vazem para a tela de outro usuário que logue depois no
  mesmo aparelho.
- Depois do login bem-sucedido: busca o `perfil` do usuário (papel,
  organizacao_id) uma vez e mantém em memória (no próprio
  `SupabaseSessionManager`, como parte do estado de sessão) — todo o resto
  do app (checagem de papel, filtros de UI) lê daí, nunca refaz essa
  consulta.

## 3. Contrato dos repositórios (visão geral, não exaustiva)

Cada repositório ganha o mesmo formato: um método de escrita que fala com o
Postgrest e trata erro, e um método de leitura que continua expondo `Flow`
do Room. Exemplo de assinatura (ilustrativo, o plano de implementação define
os detalhes exatos):

```kotlin
class ProcessoRepository(
    private val db: AppDatabase,
    private val client: SupabaseClient,
) {
    fun observarTodos(): Flow<List<ProcessoEntity>> = db.processoDao().observarTodos()

    suspend fun criar(dados: NovoProcesso): Result<ProcessoEntity> {
        // chama client.postgrest["processos"].insert(...), depois upsert no Room
    }

    suspend fun sincronizar() {
        // busca client.postgrest["processos"].select(), upsert no Room
    }
}
```

`FaseRepository`, `TipoProcessoRepository` (novo), `ItemRepository`,
`HistoricoFaseRepository`, `DiligenciaRepository` (novo), `PerfilRepository`
(novo, read-only) seguem o mesmo formato.

## 4. O que muda nas telas existentes (visão geral — detalhado no Plano 2B)

Não é escopo desta sub-etapa reescrever as telas — só a fundação de dados
que elas vão passar a consumir. Listado aqui só para dar visibilidade do que
vem a seguir: `ProcessoFormViewModel` (dropdown de tipo em vez de texto
livre, checagem de papel para mostrar/esconder o botão criar),
`ProcessoListViewModel`/`ProcessoDetalheViewModel` (resolver responsável via
`PerfilRepository` em vez de `PessoaRepository`), `AvancarFaseViewModel`
(usar `avancar_fase()` RPC), `FaseCadastroViewModel` (checagem de papel
admin), `PessoaCadastroViewModel` e sua tela (**removidos**).

## 5. Testes

Não existe nenhum teste real hoje. Para esta sub-etapa:
- **Testes unitários JVM** para lógica pura sem I/O: mapeamento
  entidade↔DTO, tradução de erro de rede em exceção tipada, lógica de
  `SupabaseSessionManager` (transições de estado).
- **Teste instrumentado** (`androidTest`) para a migração Room `1→2`
  (mesmo sendo destructive, confirma que o schema novo compila e abre sem
  erro).
- **Verificação manual em emulador**: login real contra o projeto Supabase
  já em produção, confirmar que a sessão persiste entre reaberturas do
  app, confirmar que um `sincronizar()` de processos traz dados reais para
  o Room. Isto substitui um teste automatizado de ponta a ponta, que exigiria
  infraestrutura de CI que este projeto não tem — consistente com o padrão
  já usado nas etapas 5-10 (verificação manual em emulador a cada etapa).

## 6. Configuração e segredos

`SUPABASE_URL`/`SUPABASE_ANON_KEY` (a anon key, nunca a service role key)
entram como `BuildConfig` fields, lidos de `local.properties` (já
gitignored no projeto) — nunca hardcoded no código-fonte nem commitados.
Mesma disciplina de segredos já estabelecida no Plano 1
(`supabase/.env.local`).

## 7. Dependência externa ainda pendente

Nenhuma para esta sub-etapa especificamente — o projeto Supabase já existe
e está em produção. A única dependência externa do Plano 2 como um todo é o
projeto Firebase (FCM) para o Plano 2D, já registrada na spec original
(seção 13) e não bloqueia 2A/2B/2C.

## 8. Roadmap completo do Plano 2 (visão geral, não brainstormado em detalhe ainda)

- **2A — Fundação de dados e autenticação** (esta spec).
- **2B — Fluxos de processo adaptados aos papéis**: telas de
  processo/fase/diligência reescritas sobre a fundação de 2A; cadastro de
  Tipo de Processo; remoção definitiva de Pessoa.
- **2C — Equipe e transparência**: tela de gerenciar equipe/contas (admin,
  via Edge Function `criar-conta`), tela de Fila de Distribuição, tela de
  configurações de notificação (usando os cards "Em breve" já existentes em
  `MaisScreen`).
- **2D — Notificações push**: Edge Function agendada (cron) no backend para
  os 4 gatilhos da spec §7 + integração FCM no app. Bloqueado até o usuário
  criar um projeto Firebase e fornecer `google-services.json`.

Cada sub-etapa segue o mesmo ciclo spec → plano → implementação do Plano 1,
com sua própria aprovação antes de virar código.

## 9. Fora de escopo (nesta sub-etapa 2A)

Tudo que está nas telas (2B/2C/2D acima), anexos/links de processo (já fora
de escopo desde a spec original), dashboard "Início"/tela "Agenda"
(explicitamente adiados pela spec original, seção 12).
