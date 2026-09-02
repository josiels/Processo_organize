# Plano 2D — Notificações push — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar os 4 gatilhos de notificação push da spec (processo avançou de fase, prazo se aproximando, dias parado na fase, dias desde designado) — backend (job agendado + Edge Function + FCM) e cliente Android (registro de token, recebimento, deep-link) — com o entendimento explícito de que **o envio real não pode ser testado de ponta a ponta nesta sessão** (sem credenciais Firebase reais); tudo é escrito e verificado por compilação/pgTAP, com um `google-services.json` de placeholder que o usuário troca depois pelo real sem precisar mudar nenhum código.

**Architecture:** Backend: `pg_cron` agenda uma chamada HTTP (via `pg_net`) para a Edge Function `notificar-processos` a cada poucos minutos; a função consulta 4 funções SQL puras (uma por gatilho, cada uma testável via pgTAP sem precisar de FCM), monta a lista de destinatários, busca `device_tokens`, e envia via FCM HTTP v1 API usando a conta de serviço (segredo, nunca commitado). Cliente: `firebase-messaging` + uma `FirebaseMessagingService` própria — `onNewToken` grava em `device_tokens` via Postgrest, `onMessageReceived` monta uma notificação local com deep-link para `ProcessoDetalhe`.

**Tech Stack:** Backend: PostgreSQL (`pg_cron`, `pg_net`, `supabase_vault`), Deno Edge Functions, `npm:google-auth-library` (OAuth2 para FCM HTTP v1), pgTAP. Android: Kotlin, `com.google.firebase:firebase-messaging` (BOM 34.x — sem sufixo `-ktx`, descontinuado desde jul/2025, as APIs Kotlin já estão no módulo principal), plugin `com.google.gms.google-services` 4.4.4.

**Spec:** `docs/superpowers/specs/2026-09-01-android-notificacoes-push-design.md`

## Global Constraints

- Trabalhar no worktree `C:\Users\03557061485\AndroidStudioProjects\Organize_Processo\.claude\worktrees\plano2d-notificacoes-push` (branch `plano2d-notificacoes-push`, criada a partir da `master` já com os Planos 1/2A/2B/2C mesclados, mais o hotfix `e7d8817` que faz `ProcessoRepository.criar()` semear a primeira linha de `processo_fase_historico`).
- `JAVA_HOME` para todo comando gradle: `C:\Program Files\Android\Android Studio\jbr`.
- **Sem credenciais Firebase reais nesta sessão.** Para o Android compilar com o plugin `google-services` aplicado, é preciso um `app/google-services.json` presente no disco — sem ele, o build falha na etapa de sync/build, não só em runtime. Este arquivo:
  - NÃO deve ser commitado (mesmo padrão de `local.properties`, que já guarda `SUPABASE_URL`/`SUPABASE_ANON_KEY` sem ir para o git) — adicionar `app/google-services.json` ao `.gitignore`.
  - Para verificar compilação, criar um arquivo de **placeholder** localmente (não commitado) com a forma exata que o plugin espera, `package_name` = `com.josiel.organizeprocesso` (tem que bater com o `applicationId`), e valores fictícios nos demais campos (`project_id`, `api_key`, `mobilesdk_app_id`) — o plugin só valida a FORMA do JSON no build, não se os valores são reais; a validação real só acontece em runtime contra os servidores do Firebase.
  - Quando o usuário tiver o `google-services.json` de verdade, a troca é: substituir esse arquivo local (mesmo nome, mesmo lugar) e recompilar — **zero mudança de código**, já que o plugin injeta os valores em recursos gerados, nunca hardcoded no Kotlin.
- **A conta de serviço do Firebase (segredo de servidor) nunca é escrita em nenhum arquivo deste plano.** A Edge Function lê de `Deno.env.get('FCM_SERVICE_ACCOUNT_JSON')`, configurado via `supabase secrets set` fora deste plano, pelo usuário, quando ele tiver a credencial — mesmo padrão já usado para `SUPABASE_SERVICE_ROLE_KEY` desde o Plano 1.
- **A chamada do `pg_cron`/`pg_net` para a Edge Function também não pode ter a service role key hardcoded numa migration commitada** — isso vazaria o segredo no histórico do git. Usar o **Supabase Vault** (`supabase_vault`, extensão já disponível em todo projeto Supabase): a migration cria o cron job referenciando `vault.decrypted_secrets` por nome; os dois segredos (URL da função + service role key) são criados manualmente pelo usuário via `select vault.create_secret(...)` **depois** que esta migration rodar — documentado no plano, nunca commitado.
- `perfis` tem um campo `notificar_prazo` (preferência pessoal, spec do pivô) e `processo_fase_historico` **também** tem um campo `notificar_prazo` (toggle por passagem de fase específica, "Notificar sobre prazo desta fase?", já exposto em `AvancarFaseScreen` desde o Plano 2B) — são dois conceitos DIFERENTES com o mesmo nome de coluna em tabelas diferentes. **Decisão:** o gatilho "prazo se aproximando" exige os DOIS truthy (a pessoa quer ser notificada em geral E não desligou o aviso para esta fase específica) — a spec 2D não distingue isso explicitamente (foi escrita sem essa nuance), mas ignorar o toggle por-entrada tornaria o controle que o usuário já tem em `AvancarFaseScreen` inútil.
- O primeiro registro de `processo_fase_historico` de um processo (criado junto com o próprio processo, desde o hotfix `e7d8817`) **não** conta como "avançou de fase" para o gatilho 1 — só uma passagem que teve uma fase ANTERIOR conta. Ver Task 2.
- Toda escrita SQL nova segue a convenção já estabelecida: migration em `supabase/migrations/<timestamp>_<slug>.sql`, aplicada manualmente via `node scripts/run_sql.mjs <arquivo>` contra o projeto cloud (não há push automático — `supabase/.env.local` com `SUPABASE_PROJECT_REF`/`SUPABASE_ACCESS_TOKEN` não existe neste worktree; documentar como pendência de verificação, mesmo padrão já usado nos Planos 2A/2B/2C). Testes pgTAP em `supabase/tests/database/<numero>_<slug>.sql`, mesmo formato de `begin; select plan(N); ...; select * from finish(); rollback;` já usado nos arquivos existentes.
- Edge Functions novas seguem o padrão já estabelecido em `criar-conta`/`criar-organizacao`: `Deno.serve`, CORS via `../_shared/cors.ts`, `try/catch` externo devolvendo `{error}` em JSON.
- Android: nenhum novo caminho de escrita de rede fica sem tratamento de erro — reutilizar `mensagemDeErro()`/`MENSAGEM_ERRO_GENERICA` (`ui/common/MensagemErro.kt`, já existe) e sempre re-lançar `kotlinx.coroutines.CancellationException`, mesmo padrão de todo o Plano 2B/2C.
- `POST_NOTIFICATIONS` já está declarada em `AndroidManifest.xml` — não duplicar.

---

## Task 1: Habilitar `pg_cron`/`pg_net`/`supabase_vault` e preparar o Vault

**Files:**
- Create: `supabase/migrations/<timestamp>_habilitar_pg_cron_pg_net.sql`

**Interfaces:**
- Produces: extensões `pg_cron`, `pg_net`, `supabase_vault` habilitadas — usadas pela Task 4 (agendamento do cron).

- [ ] **Step 1: Criar a migration**

Gere o timestamp com `date +%Y%m%d%H%M%S` (UTC) para manter a convenção de nome já usada nos arquivos existentes em `supabase/migrations/`.

```sql
-- Habilita as extensões necessárias para agendar e disparar a chamada HTTP
-- periódica para a Edge Function de notificações (spec do Plano 2D, seção 3).
-- Mesma convenção já usada para pgtap: `with schema extensions`.
create extension if not exists pg_cron with schema extensions;
create extension if not exists pg_net with schema extensions;
create extension if not exists supabase_vault with schema extensions;
```

- [ ] **Step 2: Documentar o passo manual de Vault (não executar — é para o usuário rodar depois, com as credenciais reais)**

Não crie nenhum arquivo `.sql` com valores reais de segredo. Apenas confirme que a migration acima está correta — a Task 4 vai documentar, no próprio corpo da sua migration, os dois comandos `vault.create_secret(...)` que o usuário precisa rodar manualmente (contra o SQL Editor do projeto Supabase, ou via `node scripts/run_sql.mjs` com um arquivo local não commitado) depois que esta Task 1 e a Task 4 estiverem aplicadas.

- [ ] **Step 3: Commit**

```bash
git add supabase/migrations/<timestamp>_habilitar_pg_cron_pg_net.sql
git commit -m "feat: enable pg_cron, pg_net, supabase_vault extensions"
```

(Sem verificação de compilação Android nesta task — é só SQL, e não há `.env.local` neste worktree para aplicar contra o projeto real; isso fica registrado como pendência na Task 10.)

---

## Task 2: Funções SQL de elegibilidade para notificação (uma por gatilho) + testes pgTAP

**Files:**
- Create: `supabase/migrations/<timestamp>_criar_funcoes_elegibilidade_notificacao.sql`
- Create: `supabase/tests/database/110_elegibilidade_notificacao.sql`

**Interfaces:**
- Produces: 4 funções SQL, cada uma `returns table (...)`, chamadas via RPC pela Task 3 (Edge Function):
  - `public.perfis_a_notificar_avanco_fase()` → `(perfil_id uuid, processo_id uuid, numero text, objeto text)`
  - `public.perfis_a_notificar_prazo()` → `(perfil_id uuid, processo_id uuid, numero text, objeto text, prazo_limite date)`
  - `public.perfis_a_notificar_tempo_parado_fase()` → `(perfil_id uuid, processo_id uuid, numero text, objeto text, dias_parado int)`
  - `public.perfis_a_notificar_tempo_desde_designado()` → `(perfil_id uuid, processo_id uuid, numero text, objeto text, dias_designado int)`

Janela de checagem: **10 minutos**, maior que o intervalo do cron (5 minutos, Task 4) para não perder eventos entre execuções — se o intervalo do cron mudar depois, esta janela precisa ser revisada junto (comentário no código avisa disso).

- [ ] **Step 1: Escrever os testes pgTAP (falhando)**

```sql
begin;
select plan(8);

select has_function('public', 'perfis_a_notificar_avanco_fase', 'perfis_a_notificar_avanco_fase deveria existir');
select has_function('public', 'perfis_a_notificar_prazo', 'perfis_a_notificar_prazo deveria existir');
select has_function('public', 'perfis_a_notificar_tempo_parado_fase', 'perfis_a_notificar_tempo_parado_fase deveria existir');
select has_function('public', 'perfis_a_notificar_tempo_desde_designado', 'perfis_a_notificar_tempo_desde_designado deveria existir');

-- Fixture: uma organização, um admin (quer ser avisado de avanço), um usuário
-- (dono do processo, quer ser avisado de prazo e tempo parado), um segundo
-- usuário com todas as preferências desligadas (nunca deve aparecer em nada).
insert into auth.users (id, email, encrypted_password, email_confirmed_at, instance_id, aud, role)
values
  ('e0000000-0000-0000-0000-000000000001', 'admin.en@teste.com', 'x', now(), '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('e0000000-0000-0000-0000-000000000002', 'user.en@teste.com', 'x', now(), '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('e0000000-0000-0000-0000-000000000003', 'user.mudo.en@teste.com', 'x', now(), '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');
insert into public.organizacoes (id, nome) values ('e1000000-0000-0000-0000-000000000001', 'Organização EN');
insert into public.perfis (id, organizacao_id, papel, nome, notificar_avanco_fase, notificar_prazo, notificar_tempo_parado) values
  ('e0000000-0000-0000-0000-000000000001', 'e1000000-0000-0000-0000-000000000001', 'admin', 'Admin EN', true, true, true),
  ('e0000000-0000-0000-0000-000000000002', 'e1000000-0000-0000-0000-000000000001', 'usuario', 'User EN', true, true, true),
  ('e0000000-0000-0000-0000-000000000003', 'e1000000-0000-0000-0000-000000000001', 'usuario', 'User Mudo EN', false, false, false);
insert into public.fases (id, organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico) values
  ('e2000000-0000-0000-0000-000000000001', 'e1000000-0000-0000-0000-000000000001', 'Fase A EN', 1, 3, 5),
  ('e2000000-0000-0000-0000-000000000002', 'e1000000-0000-0000-0000-000000000001', 'Fase B EN', 2, 3, 5);
insert into public.tipos_processo (id, organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico) values
  ('e3000000-0000-0000-0000-000000000001', 'e1000000-0000-0000-0000-000000000001', 'Tipo EN', 4, 8);

set local role postgres;

-- Processo 1: acabou de ser designado ao User EN há 6 dias (atenção, tipo=4/8) e
-- avançou de fase agora mesmo (Fase A -> Fase B, duas linhas de historico).
insert into public.processos (id, organizacao_id, numero, objeto, tipo_processo_id, data_abertura, fase_atual_id, responsavel_id, designado_em, designado_por) values
  ('e4000000-0000-0000-0000-000000000001', 'e1000000-0000-0000-0000-000000000001', '001/EN', 'Processo EN 1', 'e3000000-0000-0000-0000-000000000001', current_date - 6, 'e2000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000002', now() - interval '6 days', 'e0000000-0000-0000-0000-000000000001');
insert into public.processo_fase_historico (id, processo_id, fase_id, responsavel_id, data_entrada, data_saida, prazo_limite, notificar_prazo, criado_em) values
  ('e5000000-0000-0000-0000-000000000001', 'e4000000-0000-0000-0000-000000000001', 'e2000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000002', current_date - 6, current_date, null, false, now() - interval '6 days');
insert into public.processo_fase_historico (id, processo_id, fase_id, responsavel_id, data_entrada, data_saida, prazo_limite, notificar_prazo, criado_em) values
  ('e5000000-0000-0000-0000-000000000002', 'e4000000-0000-0000-0000-000000000001', 'e2000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000002', current_date, null, current_date + 3, true, now());

-- Processo 2: recém-criado (uma única linha de historico, sem fase anterior) —
-- NUNCA deve aparecer em perfis_a_notificar_avanco_fase, mesmo sendo recente.
insert into public.processos (id, organizacao_id, numero, objeto, tipo_processo_id, data_abertura, fase_atual_id) values
  ('e4000000-0000-0000-0000-000000000002', 'e1000000-0000-0000-0000-000000000001', '002/EN', 'Processo EN 2', 'e3000000-0000-0000-0000-000000000001', current_date, 'e2000000-0000-0000-0000-000000000001');
insert into public.processo_fase_historico (id, processo_id, fase_id, data_entrada, data_saida, notificar_prazo, criado_em) values
  ('e5000000-0000-0000-0000-000000000003', 'e4000000-0000-0000-0000-000000000002', 'e2000000-0000-0000-0000-000000000001', current_date, null, false, now());

reset role;

-- 1) Avanço de fase: só o Processo 1 conta (tem uma linha anterior fechada);
--    o Admin (notificar_avanco_fase=true) aparece, o User Mudo não.
select results_eq(
  $$select perfil_id::text from public.perfis_a_notificar_avanco_fase() order by 1$$,
  $$values ('e0000000-0000-0000-0000-000000000001')$$,
  'so o admin com preferencia ligada deveria ser notificado de avanco de fase, e so pelo Processo 1'
);

-- 2) Prazo: a linha ativa do Processo 1 tem prazo em +3 dias e notificar_prazo=true
--    na entrada; o responsavel (User EN) tem a preferencia pessoal ligada.
select results_eq(
  $$select perfil_id::text from public.perfis_a_notificar_prazo() where processo_id = 'e4000000-0000-0000-0000-000000000001'::uuid$$,
  $$values ('e0000000-0000-0000-0000-000000000002')$$,
  'responsavel com prazo proximo e as duas preferencias ligadas deveria ser notificado'
);

-- 3) Tempo parado na fase: Processo 1 entrou na Fase B (dias_alerta_atencao=3)
--    hoje -> 0 dias parado, nao deveria disparar ainda.
select is_empty(
  $$select 1 from public.perfis_a_notificar_tempo_parado_fase() where processo_id = 'e4000000-0000-0000-0000-000000000001'::uuid$$,
  'processo que acabou de entrar na fase nao deveria disparar tempo parado ainda'
);

-- 4) Tempo desde designado: Processo 1 foi designado ha 6 dias, tipo tem
--    dias_alerta_atencao=4 -> deveria disparar para o responsavel.
select results_eq(
  $$select perfil_id::text from public.perfis_a_notificar_tempo_desde_designado() where processo_id = 'e4000000-0000-0000-0000-000000000001'::uuid$$,
  $$values ('e0000000-0000-0000-0000-000000000002')$$,
  'responsavel de processo designado ha mais dias que o limite do tipo deveria ser notificado'
);

select * from finish();
rollback;
```

- [ ] **Step 2: Rodar o teste, confirmar que falha**

Run: `node scripts/run_sql.mjs supabase/tests/database/110_elegibilidade_notificacao.sql`
Expected: `RESULT: FAIL` (as 4 funções ainda não existem — os dois primeiros `has_function` já falham).

- [ ] **Step 3: Criar a migration com as 4 funções**

```sql
-- Funções puras de elegibilidade para notificação (spec do Plano 2D, seções
-- 1 e 3) — cada uma responde "quem deveria ser notificado agora, e por quê",
-- sem enviar nada. A Edge Function `notificar-processos` (Task 3) chama as
-- quatro via RPC (com a service role key, que já ignora RLS) e faz o envio.
-- Testáveis via pgTAP sem precisar de FCM (spec, seção 5).
--
-- Janela de checagem fixa em 10 minutos — tem que ser MAIOR que o intervalo
-- do cron job (5 minutos, Task 4) para não perder eventos entre execuções.
-- Se o intervalo do cron mudar, revisar esta janela junto.

create or replace function public.perfis_a_notificar_avanco_fase()
returns table (perfil_id uuid, processo_id uuid, numero text, objeto text)
language sql
stable
as $$
  select distinct p.id as perfil_id, pr.id as processo_id, pr.numero, pr.objeto
  from public.processo_fase_historico h
  join public.processos pr on pr.id = h.processo_id
  join public.perfis p on p.organizacao_id = pr.organizacao_id and p.papel = 'admin' and p.ativo and p.notificar_avanco_fase
  where h.data_saida is null
    and h.criado_em >= now() - interval '10 minutes'
    -- Só conta como "avanço" se existir outra linha de histórico do mesmo
    -- processo — a primeira linha (criada junto com o processo desde o
    -- hotfix e7d8817) não é um avanço, é a fase inicial.
    and exists (
      select 1 from public.processo_fase_historico h2
      where h2.processo_id = h.processo_id and h2.id <> h.id
    );
$$;

create or replace function public.perfis_a_notificar_prazo()
returns table (perfil_id uuid, processo_id uuid, numero text, objeto text, prazo_limite date)
language sql
stable
as $$
  select p.id as perfil_id, pr.id as processo_id, pr.numero, pr.objeto, h.prazo_limite
  from public.processo_fase_historico h
  join public.processos pr on pr.id = h.processo_id
  join public.perfis p on p.id = pr.responsavel_id and p.ativo and p.notificar_prazo
  where h.data_saida is null
    and h.notificar_prazo
    and h.prazo_limite is not null
    -- Avisa 5 dias antes até o próprio dia do vencimento (spec original, §7).
    and h.prazo_limite between current_date and current_date + 5;
$$;

create or replace function public.perfis_a_notificar_tempo_parado_fase()
returns table (perfil_id uuid, processo_id uuid, numero text, objeto text, dias_parado int)
language sql
stable
as $$
  select p.id as perfil_id, pr.id as processo_id, pr.numero, pr.objeto,
    (current_date - h.data_entrada)::int as dias_parado
  from public.processo_fase_historico h
  join public.processos pr on pr.id = h.processo_id
  join public.fases f on f.id = h.fase_id
  join public.perfis p on p.id = pr.responsavel_id and p.ativo and p.notificar_tempo_parado
  where h.data_saida is null
    and (current_date - h.data_entrada) >= f.dias_alerta_atencao;
$$;

create or replace function public.perfis_a_notificar_tempo_desde_designado()
returns table (perfil_id uuid, processo_id uuid, numero text, objeto text, dias_designado int)
language sql
stable
as $$
  select p.id as perfil_id, pr.id as processo_id, pr.numero, pr.objeto,
    (current_date - pr.designado_em::date)::int as dias_designado
  from public.processos pr
  join public.tipos_processo tp on tp.id = pr.tipo_processo_id
  join public.perfis p on p.id = pr.responsavel_id and p.ativo and p.notificar_tempo_parado
  where pr.designado_em is not null
    and (current_date - pr.designado_em::date) >= tp.dias_alerta_atencao;
$$;

revoke all on function public.perfis_a_notificar_avanco_fase() from public;
revoke all on function public.perfis_a_notificar_prazo() from public;
revoke all on function public.perfis_a_notificar_tempo_parado_fase() from public;
revoke all on function public.perfis_a_notificar_tempo_desde_designado() from public;
grant execute on function public.perfis_a_notificar_avanco_fase() to service_role;
grant execute on function public.perfis_a_notificar_prazo() to service_role;
grant execute on function public.perfis_a_notificar_tempo_parado_fase() to service_role;
grant execute on function public.perfis_a_notificar_tempo_desde_designado() to service_role;
```

`dias_alerta_atencao`/`dias_alerta_critico` de `fases`/`tipos_processo` são usados aqui como o mesmo limiar já usado pelo semáforo amarelo no app (`calcularSemaforo`, Plano 2B) — dispara a notificação no mesmo momento em que o card fica amarelo, não separadamente.

- [ ] **Step 4: Rodar o teste, confirmar que passa**

Run: `node scripts/run_sql.mjs supabase/tests/database/110_elegibilidade_notificacao.sql`
Expected: `RESULT: PASS`.

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations/<timestamp>_criar_funcoes_elegibilidade_notificacao.sql \
        supabase/tests/database/110_elegibilidade_notificacao.sql
git commit -m "feat: add pure eligibility functions for the 4 notification triggers"
```

---

## Task 3: Edge Function `notificar-processos` (backend, envia via FCM)

**Files:**
- Create: `supabase/functions/notificar-processos/index.ts`

**Interfaces:**
- Consumes: as 4 funções RPC da Task 2, `public.device_tokens` (já existe, Plano 2A), `FCM_SERVICE_ACCOUNT_JSON` (segredo, não configurado nesta sessão).
- Produces: endpoint invocável via `net.http_post` — usado pela Task 4.

Diferente de `criar-conta`/`criar-organizacao`, esta função não é chamada por um usuário do app — é chamada pelo `pg_cron`/`pg_net` (Task 4), passando a própria service role key como `Authorization` (que já passa na verificação de JWT padrão do gateway de Edge Functions, sem precisar desabilitar `verify_jwt`). Por isso não há checagem de papel/`getUser()` aqui — a função já assume privilégio total, igual às outras duas.

- [ ] **Step 1: Criar a função**

```typescript
import { createClient } from 'jsr:@supabase/supabase-js@2';
import { GoogleAuth } from 'npm:google-auth-library@9';
import { corsHeaders } from '../_shared/cors.ts';

interface ElegivelBase {
  perfil_id: string;
  processo_id: string;
  numero: string;
  objeto: string;
}

async function obterAccessTokenFcm(serviceAccountJson: string): Promise<string> {
  const credentials = JSON.parse(serviceAccountJson);
  const auth = new GoogleAuth({
    credentials,
    scopes: ['https://www.googleapis.com/auth/firebase.messaging'],
  });
  const client = await auth.getClient();
  const { token } = await client.getAccessToken();
  if (!token) throw new Error('Não foi possível obter access token do FCM');
  return token;
}

async function enviarParaTokens(
  fcmAccessToken: string,
  projectId: string,
  tokens: string[],
  titulo: string,
  corpo: string,
  processoId: string,
) {
  for (const token of tokens) {
    await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${fcmAccessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        message: {
          token,
          notification: { title: titulo, body: corpo },
          data: { processo_id: processoId },
        },
      }),
    });
    // Falha de envio para um token específico (ex: token expirado) não deveria
    // derrubar o lote inteiro — cada chamada é independente; o FCM responde
    // por token, não há necessidade de checar o corpo da resposta aqui para
    // que o restante dos envios continue (fora de escopo tratar tokens
    // inválidos nesta etapa — ver spec, seção 6, "fora de escopo").
  }
}

Deno.serve(async (_req) => {
  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const fcmServiceAccountJson = Deno.env.get('FCM_SERVICE_ACCOUNT_JSON');

    if (!fcmServiceAccountJson) {
      return new Response(JSON.stringify({ error: 'FCM_SERVICE_ACCOUNT_JSON não configurado — ver Plano 2D, seção 2' }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const adminClient = createClient(supabaseUrl, serviceRoleKey);
    const projectId = JSON.parse(fcmServiceAccountJson).project_id as string;
    const fcmAccessToken = await obterAccessTokenFcm(fcmServiceAccountJson);

    let totalEnviado = 0;

    const gatilhos: Array<{ rpc: string; montarMensagem: (item: ElegivelBase & Record<string, unknown>) => { titulo: string; corpo: string } }> = [
      {
        rpc: 'perfis_a_notificar_avanco_fase',
        montarMensagem: (item) => ({
          titulo: 'Processo avançou de fase',
          corpo: `O processo ${item.numero} (${item.objeto}) avançou de fase.`,
        }),
      },
      {
        rpc: 'perfis_a_notificar_prazo',
        montarMensagem: (item) => ({
          titulo: 'Prazo se aproximando',
          corpo: `O processo ${item.numero} (${item.objeto}) tem prazo em ${item.prazo_limite}.`,
        }),
      },
      {
        rpc: 'perfis_a_notificar_tempo_parado_fase',
        montarMensagem: (item) => ({
          titulo: 'Processo parado há muito tempo',
          corpo: `O processo ${item.numero} (${item.objeto}) está parado nesta fase há ${item.dias_parado} dias.`,
        }),
      },
      {
        rpc: 'perfis_a_notificar_tempo_desde_designado',
        montarMensagem: (item) => ({
          titulo: 'Processo designado há muito tempo',
          corpo: `O processo ${item.numero} (${item.objeto}) está com você há ${item.dias_designado} dias.`,
        }),
      },
    ];

    for (const gatilho of gatilhos) {
      const { data: elegiveis, error } = await adminClient.rpc(gatilho.rpc);
      if (error) {
        console.error(`Erro ao consultar ${gatilho.rpc}:`, error.message);
        continue;
      }
      for (const item of (elegiveis ?? []) as Array<ElegivelBase & Record<string, unknown>>) {
        const { data: tokensRows, error: tokensError } = await adminClient
          .from('device_tokens')
          .select('token_fcm')
          .eq('perfil_id', item.perfil_id);
        if (tokensError || !tokensRows || tokensRows.length === 0) continue;

        const { titulo, corpo } = gatilho.montarMensagem(item);
        const tokens = tokensRows.map((r) => r.token_fcm as string);
        await enviarParaTokens(fcmAccessToken, projectId, tokens, titulo, corpo, item.processo_id);
        totalEnviado += tokens.length;
      }
    }

    return new Response(JSON.stringify({ enviados: totalEnviado }), {
      status: 200,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (err) {
    return new Response(JSON.stringify({ error: String(err) }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
```

- [ ] **Step 2: Verificar que o arquivo é TypeScript válido**

Não há como rodar esta função sem um projeto Supabase local (Docker) ou cloud configurado, e não há `FCM_SERVICE_ACCOUNT_JSON` real disponível nesta sessão de qualquer forma. Verificação possível: `deno check supabase/functions/notificar-processos/index.ts` se o Deno CLI estiver disponível no ambiente (`deno --version`); se não estiver, ao menos confirme visualmente que a sintaxe está correta e que os imports (`jsr:@supabase/supabase-js@2`, `npm:google-auth-library@9`, `../_shared/cors.ts`) seguem exatamente os padrões já usados nas outras duas funções deste projeto. Registrar como pendência de verificação na Task 10 se `deno check` não estiver disponível.

- [ ] **Step 3: Commit**

```bash
git add supabase/functions/notificar-processos/index.ts
git commit -m "feat: add notificar-processos Edge Function (FCM HTTP v1 sender)"
```

---

## Task 4: Agendamento via `pg_cron`/`pg_net` + documentação do Vault

**Files:**
- Create: `supabase/migrations/<timestamp>_agendar_notificar_processos.sql`

**Interfaces:**
- Consumes: `notificar-processos` (Task 3), extensões da Task 1.

- [ ] **Step 1: Criar a migration**

```sql
-- Agenda a checagem periódica de notificações (spec do Plano 2D, seção 3).
--
-- ATENÇÃO — passo manual obrigatório APÓS esta migration, antes que o cron
-- funcione: os dois segredos abaixo precisam ser criados no Vault (nunca
-- commitados em nenhum arquivo). Rode isto manualmente, uma vez, contra o
-- projeto Supabase (SQL Editor do dashboard, ou `node scripts/run_sql.mjs`
-- com um arquivo LOCAL não commitado):
--
--   select vault.create_secret(
--     'https://<PROJECT_REF>.supabase.co/functions/v1/notificar-processos',
--     'notificar_processos_url'
--   );
--   select vault.create_secret(
--     '<SUPABASE_SERVICE_ROLE_KEY>',
--     'notificar_processos_service_role_key'
--   );
--
-- Troque <PROJECT_REF> pelo ref do projeto e <SUPABASE_SERVICE_ROLE_KEY> pela
-- service role key real (a mesma já usada como secret das outras Edge
-- Functions — ver Supabase Dashboard > Project Settings > API).
create or replace function public.disparar_notificar_processos() returns void
language plpgsql
as $$
declare
  v_url text;
  v_service_role_key text;
begin
  select decrypted_secret into v_url from vault.decrypted_secrets where name = 'notificar_processos_url';
  select decrypted_secret into v_service_role_key from vault.decrypted_secrets where name = 'notificar_processos_service_role_key';

  if v_url is null or v_service_role_key is null then
    raise warning 'Segredos do Vault (notificar_processos_url / notificar_processos_service_role_key) ainda não configurados — pulando disparo.';
    return;
  end if;

  perform net.http_post(
    url := v_url,
    headers := jsonb_build_object('Authorization', 'Bearer ' || v_service_role_key, 'Content-Type', 'application/json'),
    body := '{}'::jsonb
  );
end;
$$;

-- A cada 5 minutos — menor que a janela de checagem de 10 minutos das funções
-- de elegibilidade (Task 2), para não perder eventos entre execuções.
select cron.schedule(
  'notificar-processos-periodico',
  '*/5 * * * *',
  $$select public.disparar_notificar_processos();$$
);
```

A função `disparar_notificar_processos()` degrada de forma segura (`raise warning` + retorna, sem lançar erro) se os segredos do Vault ainda não existirem — assim aplicar esta migration não quebra nada mesmo antes do passo manual ser feito.

- [ ] **Step 2: Commit**

```bash
git add supabase/migrations/<timestamp>_agendar_notificar_processos.sql
git commit -m "feat: schedule notificar-processos via pg_cron/pg_net, secrets via Vault"
```

---

## Task 5: Dependências Firebase no Android + `google-services.json` de placeholder

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `.gitignore`
- Create (local, NÃO commitado): `app/google-services.json`

**Interfaces:**
- Produces: `com.google.firebase:firebase-messaging` disponível — usado pela Task 8.

- [ ] **Step 1: Adicionar o plugin ao catálogo de versões e ao build raiz**

Em `gradle/libs.versions.toml`, seção `[versions]`:

```toml
googleServices = "4.4.4"
firebaseBom = "34.17.0"
```

Seção `[libraries]` (depois de `supabase-functions-kt`):

```toml
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-messaging = { group = "com.google.firebase", name = "firebase-messaging" }
```

Seção `[plugins]`:

```toml
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

- [ ] **Step 2: Aplicar o plugin e a dependência em `app/build.gradle.kts`**

No bloco `plugins { ... }`, adicione (depois de `alias(libs.plugins.ksp)`):

```kotlin
    alias(libs.plugins.google.services)
```

No bloco `dependencies { ... }`, depois de `implementation(libs.supabase.functions.kt)`:

```kotlin

    // Firebase Cloud Messaging (push)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
```

- [ ] **Step 3: Adicionar `google-services.json` ao `.gitignore`**

Em `.gitignore`, adicione uma linha (perto de `local.properties`, se houver uma seção de config local — senão, no fim do arquivo):

```
app/google-services.json
```

- [ ] **Step 4: Criar o placeholder local (NÃO commitar)**

Crie `app/google-services.json` com este conteúdo exato (forma válida, valores fictícios — o plugin só valida a forma no build):

```json
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "organize-processo-placeholder",
    "storage_bucket": "organize-processo-placeholder.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000",
        "android_client_info": {
          "package_name": "com.josiel.organizeprocesso"
        }
      },
      "oauth_client": [],
      "api_key": [
        { "current_key": "AIzaSyPLACEHOLDERPLACEHOLDERPLACEHOLDERPL" }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ],
  "configuration_version": "1"
}
```

- [ ] **Step 5: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Se o plugin `google-services` reclamar de algum campo ausente/formato, ajuste o placeholder acima conforme a mensagem de erro indicar (a estrutura exata pode variar ligeiramente entre versões do plugin) — o objetivo é só passar da etapa de parsing do plugin, não simular um projeto Firebase real.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts .gitignore
git commit -m "chore: add Firebase Messaging dependency and google-services plugin"
```

(NÃO faça `git add` em `app/google-services.json` — ele deve ficar de fora, é só um placeholder local para permitir a verificação de build desta e das próximas tasks.)

---

## Task 6: `DeviceTokenRepository`

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/data/repository/DeviceTokenRepository.kt`

**Interfaces:**
- Produces: `DeviceTokenRepository(client).registrar(perfilId: String, token: String)` — usado pela Task 8 (`onNewToken`) e pela Task 9 (registro no login).

`public.device_tokens` já existe desde o Plano 2A (`perfil_id`, `token_fcm`, `atualizado_em`, `unique(perfil_id, token_fcm)`) com RLS `device_tokens_dono` (`perfil_id = auth.uid()`) — este repositório só faz upsert na própria linha do usuário logado, permitido sem mudança de RLS.

- [ ] **Step 1: Criar o repositório**

```kotlin
package com.josiel.organizeprocesso.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Registra o token FCM do aparelho atual (spec do Plano 2D, seção 4). Sem
 * cache Room — é só uma escrita, sem tela de leitura. `unique(perfil_id,
 * token_fcm)` no banco permite múltiplos aparelhos por pessoa; o `upsert`
 * aqui é sobre esse par, então chamar de novo com o mesmo token é seguro
 * (só atualiza `atualizado_em`).
 */
class DeviceTokenRepository(private val client: SupabaseClient) {
    suspend fun registrar(perfilId: String, token: String) {
        val linha = buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("perfil_id", perfilId)
            put("token_fcm", token)
        }
        client.postgrest["device_tokens"].upsert(linha) {
            onConflict = "perfil_id, token_fcm"
        }
    }
}
```

Note que `id` é sempre um UUID novo mesmo em um upsert de conflito — isso é seguro porque o `onConflict` está em `(perfil_id, token_fcm)`, não em `id`: quando já existe uma linha com esse par, o Postgrest faz `UPDATE` (ignora o `id` novo enviado, mantém o `id` original da linha existente) em vez de inserir uma segunda linha duplicada.

- [ ] **Step 2: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Se `onConflict` não for reconhecido nesse formato pela versão do postgrest-kt em uso, ajuste conforme o erro do compilador indicar (checar a assinatura real de `PostgrestUpsertBuilder`/`.upsert(...)` na biblioteca) — mesma disciplina já usada com `functions.invoke()` no Plano 2C.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/data/repository/DeviceTokenRepository.kt
git commit -m "feat: add DeviceTokenRepository"
```

---

## Task 7: Parsing puro da mensagem recebida (função testável sem Firebase)

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/domain/usecase/InterpretarNotificacaoPush.kt`
- Test: `app/src/test/java/com/josiel/organizeprocesso/domain/usecase/InterpretarNotificacaoPushTest.kt`

**Interfaces:**
- Produces: `data class NotificacaoPushInterpretada(titulo: String, corpo: String, processoId: String?)` e `fun interpretarNotificacaoPush(dadosMensagem: Map<String, String>, tituloNotification: String?, corpoNotification: String?): NotificacaoPushInterpretada` — usado pela Task 8.

Espelha o payload que a Task 3 monta: `notification.title`/`notification.body` (viram os parâmetros `tituloNotification`/`corpoNotification` — o SDK do Firebase já separa isso do bloco `data`) e `data.processo_id` (vira uma entrada em `dadosMensagem`). Função pura, testável sem nenhuma dependência do Firebase real (spec, seção 5).

- [ ] **Step 1: Escrever o teste (falhando)**

```kotlin
package com.josiel.organizeprocesso.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InterpretarNotificacaoPushTest {
    @Test
    fun `extrai titulo corpo e processoId quando todos presentes`() {
        val resultado = interpretarNotificacaoPush(
            dadosMensagem = mapOf("processo_id" to "abc-123"),
            tituloNotification = "Prazo se aproximando",
            corpoNotification = "O processo 001/2026 tem prazo em 2026-09-10."
        )

        assertEquals("Prazo se aproximando", resultado.titulo)
        assertEquals("O processo 001/2026 tem prazo em 2026-09-10.", resultado.corpo)
        assertEquals("abc-123", resultado.processoId)
    }

    @Test
    fun `processoId nulo quando ausente do payload de dados`() {
        val resultado = interpretarNotificacaoPush(
            dadosMensagem = emptyMap(),
            tituloNotification = "Aviso",
            corpoNotification = "Mensagem sem processo associado."
        )

        assertNull(resultado.processoId)
    }

    @Test
    fun `titulo e corpo caem em texto padrao quando ausentes`() {
        val resultado = interpretarNotificacaoPush(
            dadosMensagem = mapOf("processo_id" to "xyz-789"),
            tituloNotification = null,
            corpoNotification = null
        )

        assertEquals("Organize Processo", resultado.titulo)
        assertEquals("Você tem uma atualização.", resultado.corpo)
    }
}
```

- [ ] **Step 2: Rodar o teste, confirmar que falha**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.InterpretarNotificacaoPushTest"`
Expected: FAIL (função não existe ainda).

- [ ] **Step 3: Implementar**

```kotlin
package com.josiel.organizeprocesso.domain.usecase

/** Resultado já pronto para montar a notificação local e o deep-link. */
data class NotificacaoPushInterpretada(
    val titulo: String,
    val corpo: String,
    val processoId: String?
)

/**
 * Interpreta o payload de uma mensagem FCM recebida (spec do Plano 2D, seção
 * 4) — função pura, sem dependência do SDK do Firebase, testável sem nenhuma
 * credencial real (spec, seção 5).
 */
fun interpretarNotificacaoPush(
    dadosMensagem: Map<String, String>,
    tituloNotification: String?,
    corpoNotification: String?
): NotificacaoPushInterpretada = NotificacaoPushInterpretada(
    titulo = tituloNotification?.takeIf { it.isNotBlank() } ?: "Organize Processo",
    corpo = corpoNotification?.takeIf { it.isNotBlank() } ?: "Você tem uma atualização.",
    processoId = dadosMensagem["processo_id"]?.takeIf { it.isNotBlank() }
)
```

- [ ] **Step 4: Rodar o teste, confirmar que passa**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.josiel.organizeprocesso.domain.usecase.InterpretarNotificacaoPushTest"`
Expected: PASS (3 testes).

- [ ] **Step 5: Verificar que o projeto inteiro compila e os testes passam**

Run: `./gradlew.bat :app:compileDebugKotlin`
Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` nos dois.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/domain/usecase/InterpretarNotificacaoPush.kt \
        app/src/test/java/com/josiel/organizeprocesso/domain/usecase/InterpretarNotificacaoPushTest.kt
git commit -m "feat: add pure push-notification payload parsing usecase"
```

---

## Task 8: `OrganizeFirebaseMessagingService` + declaração no Manifest + canal de notificação

**Files:**
- Create: `app/src/main/java/com/josiel/organizeprocesso/notifications/OrganizeFirebaseMessagingService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `DeviceTokenRepository` (Task 6), `interpretarNotificacaoPush` (Task 7).

- [ ] **Step 1: Criar o serviço**

```kotlin
package com.josiel.organizeprocesso.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.josiel.organizeprocesso.MainActivity
import com.josiel.organizeprocesso.R
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.DeviceTokenRepository
import com.josiel.organizeprocesso.domain.usecase.interpretarNotificacaoPush
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val CANAL_ID = "organize_processo_notificacoes"
private const val NOTIFICATION_ID_BASE = 1000

/**
 * Recebe pushes do FCM (spec do Plano 2D, seção 4). `onNewToken` registra o
 * token; `onMessageReceived` mostra uma notificação local com deep-link para
 * o processo relevante, quando houver um.
 */
class OrganizeFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val perfilId = SupabaseSessionManager.perfilAtual.value?.id ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DeviceTokenRepository(SupabaseSessionManager.client).registrar(perfilId, token)
            } catch (_: Exception) {
                // Falha ao registrar o token não é acionável pelo usuário aqui
                // (não há tela em foco necessariamente) — a próxima chamada de
                // onNewToken ou um novo login tenta de novo.
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val interpretada = interpretarNotificacaoPush(
            dadosMensagem = message.data,
            tituloNotification = message.notification?.title,
            corpoNotification = message.notification?.body
        )
        criarCanalSeNecessario()
        exibirNotificacao(interpretada.titulo, interpretada.corpo, interpretada.processoId)
    }

    private fun criarCanalSeNecessario() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(
            CANAL_ID,
            "Atualizações de processos",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(canal)
    }

    private fun exibirNotificacao(titulo: String, corpo: String, processoId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (processoId != null) putExtra(MainActivity.EXTRA_PROCESSO_ID_DEEP_LINK, processoId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            processoId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificacao = NotificationCompat.Builder(this, CANAL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(
            NOTIFICATION_ID_BASE + (processoId?.hashCode()?.let { it and 0xFFFF } ?: 0),
            notificacao
        )
    }
}
```

Verifique se `R.drawable.ic_launcher_foreground` existe no projeto (`app/src/main/res/drawable*/`) antes de usar — se não existir com esse nome exato, use outro drawable/mipmap já presente no projeto (ex: `R.mipmap.ic_launcher`) em vez de criar um recurso novo, para manter esta task pequena.

- [ ] **Step 2: Adicionar a constante de deep-link em `MainActivity`**

Em `MainActivity.kt`, adicione a constante (a leitura do extra e a navegação real ficam para a Task 9, que já tem acesso ao `NavController` via `AppNavHost`):

```kotlin
    companion object {
        const val EXTRA_PROCESSO_ID_DEEP_LINK = "extra_processo_id_deep_link"
    }
```

- [ ] **Step 3: Declarar o serviço no Manifest**

Em `AndroidManifest.xml`, dentro de `<application>`, depois da declaração de `<activity>`:

```xml
        <service
            android:name=".notifications.OrganizeFirebaseMessagingService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
```

- [ ] **Step 4: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/notifications/OrganizeFirebaseMessagingService.kt \
        app/src/main/java/com/josiel/organizeprocesso/MainActivity.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat: add FirebaseMessagingService, notification channel, manifest declaration"
```

---

## Task 9: Registrar token no login + navegação de deep-link

**Files:**
- Modify: `app/src/main/java/com/josiel/organizeprocesso/navigation/AppNavHost.kt`
- Modify: `app/src/main/java/com/josiel/organizeprocesso/MainActivity.kt`

**Interfaces:**
- Consumes: `DeviceTokenRepository` (Task 6), `MainActivity.EXTRA_PROCESSO_ID_DEEP_LINK` (Task 8).

- [ ] **Step 1: Registrar o token atual quando o perfil carrega**

Em `AppNavHost.kt`, no bloco `LaunchedEffect(sessionStatus)` já existente (o mesmo que carrega `perfilAtual` e sincroniza — não crie um segundo `LaunchedEffect` para isso, é parte do mesmo fluxo de "sessão ficou pronta"), depois que `SupabaseSessionManager.carregarPerfilAtual()` tiver sucesso, adicione a leitura e registro do token atual:

```kotlin
        if (autenticado && SupabaseSessionManager.perfilAtual.value == null) {
            try {
                SupabaseSessionManager.carregarPerfilAtual()
                val perfilId = SupabaseSessionManager.perfilAtual.value?.id
                if (perfilId != null) {
                    val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                    deviceTokenRepository.registrar(perfilId, token)
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                // Sem rede, ou sem Firebase configurado (placeholder): segue para o
                // app mesmo assim — onNewToken tenta de novo mais tarde, e o app
                // funciona normalmente sem push.
            }
        }
```

Adicione `deviceTokenRepository` junto dos outros `remember { ... Repository(...) }` já existentes no topo de `AppNavHost()`:

```kotlin
    val deviceTokenRepository = remember { DeviceTokenRepository(SupabaseSessionManager.client) }
```

E os imports: `com.josiel.organizeprocesso.data.repository.DeviceTokenRepository`, `kotlinx.coroutines.tasks.await` (do `kotlinx-coroutines-play-services` — **verifique se essa dependência já existe no projeto**; se não existir, adicione `implementation(libs.kotlinx.coroutines.play.services)` com a entrada correspondente em `gradle/libs.versions.toml`/`app/build.gradle.kts`, versão alinhada com `kotlinxCoroutinesAndroid` já usada — `FirebaseMessaging.getInstance().token` retorna um `Task<String>` do Google Play Services, não uma `suspend fun` nativa, e `.await()` é a forma padrão de converter isso para coroutines).

- [ ] **Step 2: Ler o extra de deep-link em `MainActivity` e repassar para `AppNavHost`**

Em `MainActivity.kt`, capture o `processoId` do `Intent` (tanto no `onCreate` quanto em `onNewIntent`, já que o app pode já estar aberto quando a notificação é tocada) e passe como parâmetro para `AppNavHost`:

```kotlin
package com.josiel.organizeprocesso

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.josiel.organizeprocesso.navigation.AppNavHost
import com.josiel.organizeprocesso.ui.theme.Organize_ProcessoTheme

class MainActivity : ComponentActivity() {
    private var processoIdDeepLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        processoIdDeepLink = intent?.getStringExtra(EXTRA_PROCESSO_ID_DEEP_LINK)
        setContent {
            Organize_ProcessoTheme {
                AppNavHost(processoIdDeepLink = processoIdDeepLink, onDeepLinkConsumido = { processoIdDeepLink = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processoIdDeepLink = intent.getStringExtra(EXTRA_PROCESSO_ID_DEEP_LINK)
    }

    companion object {
        const val EXTRA_PROCESSO_ID_DEEP_LINK = "extra_processo_id_deep_link"
    }
}
```

- [ ] **Step 3: Navegar até `ProcessoDetalhe` quando `processoIdDeepLink` chegar**

Em `AppNavHost.kt`, adicione os dois novos parâmetros à assinatura de `AppNavHost` e um `LaunchedEffect` que navega e consome o deep-link:

```kotlin
@Composable
fun AppNavHost(
    processoIdDeepLink: String? = null,
    onDeepLinkConsumido: () -> Unit = {}
) {
    val navController = rememberNavController()
    ...

    LaunchedEffect(processoIdDeepLink) {
        if (processoIdDeepLink != null) {
            navController.navigate(ProcessoDetalhe(processoIdDeepLink))
            onDeepLinkConsumido()
        }
    }

    ...
```

Coloque esse `LaunchedEffect` depois do `LaunchedEffect(sessionStatus)` já existente, não dentro dele — são independentes (o deep-link só faz sentido depois que o usuário já está autenticado e dentro do NavHost principal; se `processoIdDeepLink` chegar antes do login completar, este efeito simplesmente re-dispara quando `processoIdDeepLink` muda, e a navegação para `ProcessoDetalhe` funciona porque essa rota já está disponível assim que o `NavHost` existe, independente da aba atual).

- [ ] **Step 4: Verificar que o projeto compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/josiel/organizeprocesso/navigation/AppNavHost.kt \
        app/src/main/java/com/josiel/organizeprocesso/MainActivity.kt
git commit -m "feat: register FCM token on login, navigate to deep-linked processo"
```

(Se esta task's Step 1 precisou adicionar `kotlinx-coroutines-play-services`, inclua `gradle/libs.versions.toml`/`app/build.gradle.kts` neste `git add` também.)

---

## Task 10: Verificação final

**Files:** nenhum criado — só verificação.

- [ ] **Step 1: Build completo (limpo)**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin --rerun-tasks
```
Expected: `BUILD SUCCESSFUL` (com o `app/google-services.json` de placeholder da Task 5 ainda presente no disco, não commitado).

- [ ] **Step 2: Suíte de testes unitários completa**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`, todos os testes da Task 7 passando (mais os 34 já existentes).

- [ ] **Step 3: Confirmar que `app/google-services.json` não está rastreado pelo git**

```bash
git status --short app/google-services.json
```
Expected: sem saída (arquivo ignorado) ou, se aparecer, **não commitar** — é só o placeholder local desta sessão de verificação.

- [ ] **Step 4: Registrar o que fica pendente até haver as credenciais reais e acesso a um projeto Supabase/dispositivo**

Nada disto pode ser verificado nesta sessão, e fica registrado como pendência, não como bloqueador para fechar este plano:
- As migrations das Tasks 1, 2 e 4 nunca foram aplicadas contra um banco real (`supabase/.env.local` não existe neste worktree) — os testes pgTAP da Task 2 nunca rodaram de fato, só foram escritos seguindo o padrão dos arquivos já existentes.
- A Edge Function da Task 3 nunca foi implantada nem invocada — a chamada ao FCM HTTP v1 (`google-auth-library` via `npm:` no Deno) nunca foi exercitada contra credenciais reais.
- Os dois segredos do Vault (Task 4) e o `FCM_SERVICE_ACCOUNT_JSON` (Task 3) nunca foram criados — sem eles, `disparar_notificar_processos()` só emite um `warning` e não faz nada, por design.
- O `google-services.json` real nunca substituiu o placeholder — `FirebaseMessaging.getInstance().token` provavelmente falha ou retorna algo inválido em runtime contra um projeto Firebase que não existe; isso é esperado e não deveria travar o app (o `try/catch` da Task 9 Step 1 cobre isso), mas nunca foi testado em dispositivo.
- Nenhuma notificação foi de fato entregue e recebida — todo o caminho depois do envio HTTP para o FCM é hipótese, ainda que a lógica de `onMessageReceived`/deep-link tenha sido escrita com cuidado e a parte pura dela (Task 7) esteja testada.

Recomenda-se, quando o usuário tiver as credenciais: (1) substituir `app/google-services.json`; (2) rodar as 3 migrations contra o projeto real via `run_sql.mjs`; (3) configurar os dois segredos do Vault e `FCM_SERVICE_ACCOUNT_JSON`; (4) fazer login em um dispositivo/emulador real e confirmar que um `device_tokens` aparece; (5) forçar manualmente uma chamada a `notificar-processos` (via `node scripts/run_sql.mjs` com `select public.disparar_notificar_processos();`) e confirmar que a notificação chega.

- [ ] **Step 5: Commit (se necessário)**

Se os Steps 1-2 não exigiram nenhuma mudança de código, não há o que commitar.
