# Backend: avancar_fase() e views da Fila de Distribuição Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Estender o backend Supabase já mesclado (Plano 1) com uma função RPC atômica para transição de fase (`avancar_fase()`) e duas views de leitura para a Fila de Distribuição (`fila_distribuicao`, `fila_distribuicao_por_tipo`), fechando as duas lacunas identificadas durante o brainstorming do Plano 2 antes que o código Android dependa delas.

**Architecture:** Mesma arquitetura do Plano 1 — Postgres/Supabase com RLS, migrações aplicadas via `node scripts/run_sql.mjs` contra o projeto real na nuvem (sem Docker local), testado com pgTAP. `avancar_fase()` é uma função `plpgsql` comum (não `security definer`) — a atomicidade vem de rodar como uma única chamada de função (uma transação implícita), e a autorização continua sendo a RLS de `processos`/`processo_fase_historico` já existente, avaliada normalmente porque a função roda com o privilégio de quem chama. As duas views usam `security_invoker = true`, obrigatório — sem essa opção uma view vaza dados entre organizações (comportamento confirmado empiricamente contra o projeto real antes de escrever este plano).

**Tech Stack:** Supabase CLI (projeto nuvem já vinculado), `node scripts/run_sql.mjs` (Management API sobre HTTPS), pgTAP.

**Spec:** `docs/superpowers/specs/2026-09-01-android-fundacao-dados-auth-design.md` (seção 2.4) e `docs/superpowers/specs/2026-09-01-android-equipe-transparencia-design.md` (seção 2)

## Global Constraints

- Todas as migrações e testes pgTAP rodam contra o projeto Supabase real na nuvem (ref `isjhxusoxeuxfoxooppe`), via `node scripts/run_sql.mjs <caminho-do-arquivo>` — nunca `supabase db push`, `supabase test db`, `supabase db reset`, ou `psql`.
- Credenciais só existem em `supabase/.env.local` (gitignored), carregadas automaticamente por `scripts/run_sql.mjs`. Nunca hardcodar chave/token/senha em arquivo commitado.
- Todo arquivo de teste pgTAP termina exatamente com `select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;` seguido de `rollback;` — não o `select * from finish();` puro. É isso que permite ao `run_sql.mjs` detectar sucesso/falha (a Management API só retorna o último resultset não-vazio de uma query multi-statement).
- Migrações são aditivas e permanentes no projeto assim que aplicadas — não existe `db reset` neste fluxo. Nenhuma migração já aplicada é editada; correções viram uma nova migração.
- Views que expõem dados de tabelas com RLS **precisam** de `with (security_invoker = true)` — sem essa opção, a view roda com o privilégio de quem a criou (que tem acesso irrestrito via as migrações), ignorando a RLS de quem realmente está consultando. Isto já foi confirmado como uma vulnerabilidade real neste mesmo projeto (ver Task 2).
- Nota de troubleshooting (herdada do Plano 1): fixtures de teste pgTAP inserem em `auth.users` só com `(id, email, encrypted_password, email_confirmed_at)`. Se um teste falhar com `null value in column "..." violates not-null constraint` em `auth.users` (não a falha de asserção esperada), adicionar `instance_id, aud, role` à lista de colunas e `'00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'` como os três primeiros valores de cada linha, depois testar de novo.

---

## Task 1: `avancar_fase()` — RPC atômica para transição de fase

**Files:**
- Create: migração via `npx supabase migration new criar_avancar_fase`
- Create: `supabase/tests/database/090_avancar_fase.sql`

**Interfaces:**
- Produces: função `public.avancar_fase(p_processo_id uuid, p_fase_destino_id uuid, p_observacao_inicial text default '', p_prazo_limite date default null, p_motivo_retorno text default null) returns void` — esta é a única forma sancionada de transição de fase a partir do Plano 2B (Android); nenhuma escrita direta em `processo_fase_historico`/`processos.fase_atual_id` deve ser feita pelo cliente.

- [ ] **Step 1: Write the failing test**

Create `supabase/tests/database/090_avancar_fase.sql`:

```sql
begin;
select plan(8);

select has_function('public', 'avancar_fase', 'avancar_fase function should exist');

-- Fixture: uma organização, um admin, dois usuários, três fases, um tipo de processo
insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values
  ('a0000000-0000-0000-0000-000000000001', 'admin.af@teste.com', 'x', now()),
  ('a0000000-0000-0000-0000-000000000002', 'user.af@teste.com', 'x', now()),
  ('a0000000-0000-0000-0000-000000000003', 'user2.af@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values
  ('a1000000-0000-0000-0000-000000000001', 'Organização AvancarFase');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('a0000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'admin', 'Admin AF'),
  ('a0000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001', 'usuario', 'User AF'),
  ('a0000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000001', 'usuario', 'User2 AF');
insert into public.fases (id, organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico) values
  ('a2000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'Fase A', 1, 5, 10),
  ('a2000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001', 'Fase B', 2, 5, 10),
  ('a2000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000001', 'Fase C', 3, 5, 10);
insert into public.tipos_processo (id, organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico) values
  ('a3000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'Tipo AF', 10, 20);

-- Processo 1: designado ao User AF, com uma entrada de histórico aberta na Fase A
select set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-000000000001', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;
insert into public.processos (
  id, organizacao_id, numero, objeto, tipo_processo_id, data_abertura,
  fase_atual_id, responsavel_id, designado_em, designado_por
) values (
  'a4000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001',
  '001/AF', 'Processo AF', 'a3000000-0000-0000-0000-000000000001', current_date,
  'a2000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000002',
  now(), 'a0000000-0000-0000-0000-000000000001'
);
insert into public.processo_fase_historico (processo_id, fase_id, responsavel_id, data_entrada)
values ('a4000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000002', current_date);

-- Processo 2: órfão, sem entrada de histórico ainda aberta na Fase A
insert into public.processos (
  id, organizacao_id, numero, objeto, tipo_processo_id, data_abertura, fase_atual_id
) values (
  'a4000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001',
  '002/AF', 'Processo AF Órfão', 'a3000000-0000-0000-0000-000000000001', current_date,
  'a2000000-0000-0000-0000-000000000001'
);
insert into public.processo_fase_historico (processo_id, fase_id, data_entrada)
values ('a4000000-0000-0000-0000-000000000002', 'a2000000-0000-0000-0000-000000000001', current_date);
reset role;

-- O dono (User AF) avança o Processo 1 da Fase A para a Fase B
select set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-000000000002', true);
set local role authenticated;
select avancar_fase('a4000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000002', 'Avançando para B', null, null);
select results_eq(
  $$select fase_atual_id from public.processos where id = 'a4000000-0000-0000-0000-000000000001'$$,
  array['a2000000-0000-0000-0000-000000000002'::uuid],
  'processos.fase_atual_id foi atualizado para a Fase B'
);
select results_eq(
  $$select count(*)::int from public.processo_fase_historico
    where processo_id = 'a4000000-0000-0000-0000-000000000001' and fase_id = 'a2000000-0000-0000-0000-000000000001' and data_saida is not null$$,
  array[1],
  'a entrada de histórico da Fase A foi fechada (data_saida preenchida)'
);
select results_eq(
  $$select count(*)::int from public.processo_fase_historico
    where processo_id = 'a4000000-0000-0000-0000-000000000001' and fase_id = 'a2000000-0000-0000-0000-000000000002' and data_saida is null$$,
  array[1],
  'uma nova entrada de histórico foi aberta na Fase B'
);
reset role;

-- User2 (nem dono, nem admin) tenta avançar o Processo 1 (que agora tem dono) — deve falhar sem deixar rastro
select set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-000000000003', true);
set local role authenticated;
select throws_ok(
  $$select avancar_fase('a4000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000003', 'tentativa indevida', null, null)$$,
  '42501',
  null,
  'Usuário sem permissão não pode avançar a fase de um processo de outra pessoa'
);
select results_eq(
  $$select count(*)::int from public.processo_fase_historico where processo_id = 'a4000000-0000-0000-0000-000000000001'$$,
  array[2],
  'a tentativa indevida não deixou nenhuma entrada de histórico órfã (atomicidade preservada)'
);
reset role;

-- User2 avança o Processo 2 (órfão) — deve funcionar, mesmo não sendo dono nem admin
select set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-000000000003', true);
set local role authenticated;
select avancar_fase('a4000000-0000-0000-0000-000000000002', 'a2000000-0000-0000-0000-000000000003', '', null, null);
select results_eq(
  $$select fase_atual_id from public.processos where id = 'a4000000-0000-0000-0000-000000000002'$$,
  array['a2000000-0000-0000-0000-000000000003'::uuid],
  'qualquer usuário pode avançar a fase de um processo órfão'
);
reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
```

- [ ] **Step 2: Run test, verify it fails**

Run: `node scripts/run_sql.mjs supabase/tests/database/090_avancar_fase.sql`
Expected: FAIL — a função `avancar_fase` ainda não existe (`has_function` falha, e as chamadas seguintes erroram com "function does not exist").

- [ ] **Step 3: Write the migration**

Run: `npx supabase migration new criar_avancar_fase`
Substitua o conteúdo do arquivo gerado por:

```sql
create or replace function public.avancar_fase(
  p_processo_id uuid,
  p_fase_destino_id uuid,
  p_observacao_inicial text default '',
  p_prazo_limite date default null,
  p_motivo_retorno text default null
) returns void
language plpgsql
as $$
declare
  v_processo record;
begin
  select * into v_processo from public.processos where id = p_processo_id for update;
  if not found then
    raise exception 'Processo não encontrado.';
  end if;

  update public.processo_fase_historico
  set data_saida = current_date
  where processo_id = p_processo_id and data_saida is null;

  insert into public.processo_fase_historico (
    processo_id, fase_id, responsavel_id, data_entrada, prazo_limite, observacoes, motivo_retorno, notificar_prazo
  ) values (
    p_processo_id, p_fase_destino_id, v_processo.responsavel_id, current_date, p_prazo_limite, p_observacao_inicial, p_motivo_retorno, false
  );

  update public.processos
  set fase_atual_id = p_fase_destino_id,
      atualizado_em = now()
  where id = p_processo_id;
end;
$$;

revoke all on function public.avancar_fase(uuid, uuid, text, date, text) from public;
grant execute on function public.avancar_fase(uuid, uuid, text, date, text) to authenticated;
```

**Nota de projeto:** esta função é `plpgsql` comum, **não** `security definer` — ao contrário de `designar_processo()`. A autorização continua sendo a RLS já existente de `processos`/`processo_fase_historico` (com o disjunto de órfão já corrigido na rodada final do Plano 1), avaliada normalmente porque a função roda com o privilégio de quem chama. Isso é suficiente: se o chamador não tem permissão de escrita, o `insert` falha com `42501` (violação de `with check`), e como toda a função roda como uma única instrução (transação implícita), qualquer `update` anterior dentro da mesma chamada é revertido automaticamente — não é necessário nenhum código de verificação de permissão manual dentro da função.

- [ ] **Step 4: Apply the migration and re-run**

```bash
node scripts/run_sql.mjs supabase/migrations/<timestamp>_criar_avancar_fase.sql
node scripts/run_sql.mjs supabase/tests/database/090_avancar_fase.sql
```
Expected: `RESULT: PASS` — todas as 8 asserções passam.

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations supabase/tests
git commit -m "feat: avancar_fase RPC for atomic phase transitions"
```

---

## Task 2: Views `fila_distribuicao` e `fila_distribuicao_por_tipo`

**Files:**
- Create: migração via `npx supabase migration new criar_views_fila_distribuicao`
- Create: `supabase/tests/database/100_fila_distribuicao.sql`

**Interfaces:**
- Produces: view `public.fila_distribuicao(perfil_id uuid, organizacao_id uuid, nome text, ultimo_recebimento_em timestamptz, criado_em timestamptz, total_designacoes bigint)` e view `public.fila_distribuicao_por_tipo(perfil_id uuid, organizacao_id uuid, tipo_processo_id uuid, tipo_processo_nome text, total bigint)` — ambas somente leitura, consumidas pelo Plano 2C (tela Fila de Distribuição).

**Contexto de segurança (já verificado antes deste plano existir):** uma view comum sobre tabelas com RLS, criada sem `security_invoker = true`, roda com o privilégio de quem a *criou* — que neste projeto é um papel com acesso irrestrito via as migrações — e portanto **ignora a RLS de quem está consultando**. Isso foi confirmado experimentalmente contra este mesmo projeto: uma view de teste sem essa opção vazou registros de todas as organizações para um usuário de uma única organização (5 linhas visíveis quando deveria ser 1); com `security_invoker = true`, o isolamento funcionou corretamente (1 linha). As duas views deste task **devem** incluir essa opção — o teste do Step 1 abaixo existe justamente para travar essa garantia.

- [ ] **Step 1: Write the failing test**

Create `supabase/tests/database/100_fila_distribuicao.sql`:

```sql
begin;
select plan(7);

select has_view('public', 'fila_distribuicao', 'fila_distribuicao view should exist');
select has_view('public', 'fila_distribuicao_por_tipo', 'fila_distribuicao_por_tipo view should exist');

-- Fixture: duas organizações. Na Organização A: dois perfis (um com designações, um sem), dois tipos de processo.
insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values
  ('b0000000-0000-0000-0000-000000000001', 'admin.fd@teste.com', 'x', now()),
  ('b0000000-0000-0000-0000-000000000002', 'user1.fd@teste.com', 'x', now()),
  ('b0000000-0000-0000-0000-000000000003', 'user2.fd@teste.com', 'x', now()),
  ('b0000000-0000-0000-0000-000000000004', 'userb.fd@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values
  ('b1000000-0000-0000-0000-000000000001', 'Organização FD A'),
  ('b1000000-0000-0000-0000-000000000002', 'Organização FD B');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('b0000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', 'admin', 'Admin FD'),
  ('b0000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000001', 'usuario', 'User1 FD'),
  ('b0000000-0000-0000-0000-000000000003', 'b1000000-0000-0000-0000-000000000001', 'usuario', 'User2 FD'),
  ('b0000000-0000-0000-0000-000000000004', 'b1000000-0000-0000-0000-000000000002', 'usuario', 'UserB FD');
insert into public.tipos_processo (id, organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico) values
  ('b2000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', 'Tipo X', 10, 20),
  ('b2000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000001', 'Tipo Y', 10, 20);
insert into public.fases (id, organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico) values
  ('b3000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', 'Fase Única', 1, 5, 10);
insert into public.processos (id, organizacao_id, numero, objeto, tipo_processo_id, data_abertura, fase_atual_id) values
  ('b4000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', '001/FD', 'P1', 'b2000000-0000-0000-0000-000000000001', current_date, 'b3000000-0000-0000-0000-000000000001'),
  ('b4000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000001', '002/FD', 'P2', 'b2000000-0000-0000-0000-000000000002', current_date, 'b3000000-0000-0000-0000-000000000001');

-- User1 recebeu dois processos (um de cada tipo); User2 nunca recebeu nada.
insert into public.designacoes (processo_id, tipo_processo_id, perfil_id, designado_por) values
  ('b4000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000001'),
  ('b4000000-0000-0000-0000-000000000002', 'b2000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000001');
update public.perfis set ultimo_recebimento_em = now() where id = 'b0000000-0000-0000-0000-000000000002';

-- Isolamento entre organizações: usuário da Organização B não deve ver nada da Organização A
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000004', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;
select results_eq(
  $$select count(*)::int from public.fila_distribuicao$$,
  array[0],
  'usuário da Organização B não vê nenhuma linha da Organização A em fila_distribuicao'
);
reset role;

-- Visão de dentro da Organização A
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000002', true);
set local role authenticated;
select results_eq(
  $$select total_designacoes from public.fila_distribuicao where perfil_id = 'b0000000-0000-0000-0000-000000000002'::uuid$$,
  array[2::bigint],
  'User1 aparece com 2 designações históricas'
);
select results_eq(
  $$select total_designacoes from public.fila_distribuicao where perfil_id = 'b0000000-0000-0000-0000-000000000003'::uuid$$,
  array[0::bigint],
  'User2 aparece com 0 designações (nunca recebeu nada)'
);
select results_eq(
  $$select count(*)::int from public.fila_distribuicao$$,
  array[3],
  'os 3 perfis ativos da Organização A aparecem na fila (admin incluso)'
);
select results_eq(
  $$select total from public.fila_distribuicao_por_tipo
    where perfil_id = 'b0000000-0000-0000-0000-000000000002'::uuid and tipo_processo_id = 'b2000000-0000-0000-0000-000000000001'::uuid$$,
  array[1::bigint],
  'a quebra por tipo mostra 1 designação do Tipo X para User1'
);
reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
```

- [ ] **Step 2: Run test, verify it fails**

Run: `node scripts/run_sql.mjs supabase/tests/database/100_fila_distribuicao.sql`
Expected: FAIL — as duas views ainda não existem (`has_view` falha para ambas).

- [ ] **Step 3: Write the migration**

Run: `npx supabase migration new criar_views_fila_distribuicao`
Substitua o conteúdo do arquivo gerado por:

```sql
create view public.fila_distribuicao
  with (security_invoker = true) as
select
  p.id as perfil_id,
  p.organizacao_id,
  p.nome,
  p.ultimo_recebimento_em,
  p.criado_em,
  count(d.id) as total_designacoes
from public.perfis p
left join public.designacoes d on d.perfil_id = p.id
where p.ativo
group by p.id;

create view public.fila_distribuicao_por_tipo
  with (security_invoker = true) as
select
  d.perfil_id,
  p.organizacao_id,
  d.tipo_processo_id,
  tp.nome as tipo_processo_nome,
  count(*) as total
from public.designacoes d
join public.perfis p on p.id = d.perfil_id
join public.tipos_processo tp on tp.id = d.tipo_processo_id
group by d.perfil_id, p.organizacao_id, d.tipo_processo_id, tp.nome;
```

- [ ] **Step 4: Apply the migration and re-run**

```bash
node scripts/run_sql.mjs supabase/migrations/<timestamp>_criar_views_fila_distribuicao.sql
node scripts/run_sql.mjs supabase/tests/database/100_fila_distribuicao.sql
```
Expected: `RESULT: PASS` — todas as 7 asserções passam, incluindo a de isolamento entre organizações (a que teria pego a vulnerabilidade se `security_invoker` estivesse ausente).

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations supabase/tests
git commit -m "feat: fila_distribuicao views for workload transparency"
```

---

## Task 3: Regressão final deste incremento

**Files:** nenhum criado — só verificação.

- [ ] **Step 1: Re-rodar toda a suíte pgTAP (agora 11 arquivos)**

```bash
node scripts/run_sql.mjs supabase/tests/database/000_smoke_test.sql
node scripts/run_sql.mjs supabase/tests/database/010_organizacoes_perfis.sql
node scripts/run_sql.mjs supabase/tests/database/020_device_tokens.sql
node scripts/run_sql.mjs supabase/tests/database/030_tipos_processo_fases.sql
node scripts/run_sql.mjs supabase/tests/database/040_processos.sql
node scripts/run_sql.mjs supabase/tests/database/050_itens_historico.sql
node scripts/run_sql.mjs supabase/tests/database/060_diligencias_observacoes.sql
node scripts/run_sql.mjs supabase/tests/database/070_designacoes.sql
node scripts/run_sql.mjs supabase/tests/database/080_ativo_enforcement.sql
node scripts/run_sql.mjs supabase/tests/database/090_avancar_fase.sql
node scripts/run_sql.mjs supabase/tests/database/100_fila_distribuicao.sql
```
Expected: todos os 11 arquivos imprimem `RESULT: PASS`. Se algum arquivo anterior a este incremento (000-080) passar a falhar, trate como uma regressão real introduzida por este plano — investigue antes de continuar, não ajuste o teste antigo para "concordar" com a nova migração sem entender por quê.

- [ ] **Step 2: Commit (se necessário)**

Se o Step 1 não exigiu nenhuma mudança de código, não há o que commitar — este task é só o portão de verificação final antes de considerar o incremento pronto para o Plano 2A/2C consumirem.
