# Backend Supabase (schema, RLS, Edge Functions) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and test the complete Postgres schema, Row-Level Security policies, and Edge Functions that back the multiusuário/multiorganização pivot of Organize_Processo — against a real cloud Supabase project (no Docker), producing a backend that Plan 2 (Android data layer) can connect to.

**Architecture:** Multi-tenant Postgres schema under Supabase, isolated by `organizacao_id` and enforced by RLS (not just app logic). Privileged operations (creating organizations, creating accounts, designating a processo) go through either Edge Functions (Deno/TypeScript, using the service role key) or a `SECURITY DEFINER` Postgres function (`designar_processo`) that explicitly re-validates permissions before bypassing RLS for a coordinated multi-table write.

**Tech Stack:** Supabase CLI (linked to a cloud project, not a local stack), a real Supabase cloud project (Postgres 17), pgTAP for database tests, Deno + TypeScript for Edge Functions, Node.js for `scripts/run_sql.mjs` and one bootstrap script.

**Spec:** `docs/superpowers/specs/2026-08-31-multiusuario-gestao-processos-design.md`

## Why this plan targets a cloud project, not local Docker

This plan originally targeted a fully local Supabase stack (`supabase start` under Docker). That was abandoned after two real blockers, in order:

1. **Docker Desktop couldn't be installed.** The user is on a remote session and cannot approve the interactive Windows UAC elevation prompt Docker's installer requires. A scheduled-task elevation bypass was attempted with the user's explicit authorization and was denied by this machine's domain group policy (it appears to be a government-domain machine with deliberate anti-privilege-escalation hardening) — this was not pursued further.
2. **This network blocks outbound direct Postgres connections** (ports 5432 and 6543) entirely — confirmed by both `db.<ref>.supabase.co` failing DNS resolution and the connection-pooler host timing out on both transaction (6543) and session (5432) modes. This means even a linked *cloud* project can't be reached via `supabase db push`, `supabase test db`, or `psql` from this machine — only HTTPS (443) gets through.

The workaround, verified working end-to-end before this plan was rewritten: the Supabase **Management API**'s `POST https://api.supabase.com/v1/projects/{ref}/database/query` endpoint runs arbitrary SQL over HTTPS. `scripts/run_sql.mjs` (Task 1) wraps this endpoint. Every migration and every pgTAP test file in this plan is applied by running `node scripts/run_sql.mjs <file>` instead of `supabase db push` / `supabase test db`. Edge Functions deploy via `supabase functions deploy --use-api`, which bundles server-side over HTTPS instead of using a local Docker-based bundler — also verified working on this network.

If a future session on a different machine has Docker available, everything in this plan (migration files, test files, Edge Function code) is unchanged and portable to the local-stack workflow — only the *invocation* commands in each task's steps would need to switch back to `supabase db push`/`supabase test db`/`supabase functions serve`.

## Global Constraints

- All schema/RLS changes and pgTAP tests run against the **linked cloud Supabase project** (`isjhxusoxeuxfoxooppe`), invoked via `node scripts/run_sql.mjs <path-to-sql-file>` — never `supabase db push`, `supabase test db`, `supabase db reset`, or `psql` (all blocked on this network; see above).
- Credentials live only in `supabase/.env.local` (gitignored) and are loaded automatically by `scripts/run_sql.mjs`. Never hardcode a key/token/password in a committed file.
- Every pgTAP test file ends with `select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;` (not the bare `select * from finish();` an all-local plan would use) — this is what lets `run_sql.mjs` detect pass/fail from a single deterministic row, since the Management API only returns the last non-empty resultset from a multi-statement query.
- Because there is no `db reset` here, migrations are additive and permanent on the cloud project from the moment they're applied — there is no "wipe and start over" step. A test file's own `begin; ... rollback;` wrapping is what keeps its fixture data from persisting, not a database reset.
- Every table that holds organization-scoped data has Row-Level Security **enabled and forced** — no table is left with RLS disabled.
- No client (the future Android app, using the anon key) can create accounts or organizations directly — those two operations only exist as Edge Functions using the service role key.
- Enum/table/column names are in Portuguese, matching the spec and the existing Kotlin domain vocabulary (`fase`, `processo`, `designacao`, etc.).
- `status_geral_processo` enum values are lowercase snake_case mirroring the existing Kotlin `StatusGeralProcesso` enum: `em_andamento`, `suspenso`, `concluido`, `cancelado`.

**Troubleshooting note (applies to every task from Task 3 onward):** every pgTAP test file inserts test fixture rows directly into `auth.users` with only `(id, email, encrypted_password, email_confirmed_at)` — these fixtures are never used to actually log in (RLS tests simulate the caller via `set_config('request.jwt.claim.sub', ...)`, not real login), so this minimal column set is normally sufficient. If a test fails with a Postgres `null value in column "..." of relation "users" violates not-null constraint` error instead of the expected assertion failure, the cloud project's Supabase Auth schema version requires more columns — add `instance_id, aud, role` to the column list and `'00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'` as the first three values of every row in that file, then re-run.

---

## Task 1: Cloud Supabase project + HTTPS SQL runner

**Files:**
- Create: `supabase/config.toml` (generated by `supabase init`)
- Create: `package.json` (project root, to pin the Supabase CLI as a dev dependency)
- Create: `scripts/run_sql.mjs` (runs a `.sql` file against the linked cloud project over HTTPS)
- Create: `supabase/.env.local` (gitignored — real project URL/keys/token/password)

**Interfaces:**
- Produces: a linked cloud Supabase project plus `node scripts/run_sql.mjs <path-to-sql-file>` — every later task's migrations and tests run through this command instead of `supabase db push` / `supabase test db` (both unreachable on this network; see "Why this plan targets a cloud project" above).
- `run_sql.mjs` contract: reads `supabase/.env.local` for `SUPABASE_PROJECT_REF` and `SUPABASE_ACCESS_TOKEN`, POSTs the file's contents as `{ query }` to `https://api.supabase.com/v1/projects/{ref}/database/query`, prints the JSON result, and sets a non-zero exit code on an HTTP error OR when the response contains a row with a `summary` column not equal to exactly `'ALL TESTS PASSED'` (the pgTAP pass/fail signal — see Global Constraints).

A real Supabase account and an empty cloud project ("Projeto_Organize" or similar) must exist before this task starts — creating the account/project itself is a manual step outside this plan (done via the Supabase dashboard).

- [ ] **Step 1: Add the Supabase CLI as a dev dependency**

At the project root (`C:\Users\03557061485\AndroidStudioProjects\Organize_Processo`), run:

```bash
npm init -y
npm install --save-dev supabase
```

Add `node_modules/` to `.gitignore` if not already present.

- [ ] **Step 2: Initialize the Supabase project**

Run: `npx supabase init`
Expected: creates a `supabase/` directory with `config.toml`, `migrations/`, `functions/`, `seed.sql`, and a `supabase/.gitignore` that already excludes `.branches`, `.temp`, `.env.keys`, `.env.local`, `.env.*.local`.

- [ ] **Step 3: Record project credentials**

Create `supabase/.env.local` (already covered by `supabase/.gitignore` — confirm with `git check-ignore -v supabase/.env.local` before continuing) with the real project's values, obtained from the Supabase dashboard (Project Settings → API, and Project Settings → Access Tokens for a personal access token):

```
SUPABASE_URL=<Project URL, e.g. https://<ref>.supabase.co>
SUPABASE_ANON_KEY=<anon public key>
SUPABASE_SERVICE_ROLE_KEY=<service_role secret key>
SUPABASE_PROJECT_REF=<project ref, the subdomain of SUPABASE_URL>
SUPABASE_ACCESS_TOKEN=<personal access token, starts with sbp_>
SUPABASE_DB_PASSWORD=<the database password chosen when the project was created>
```

Never commit this file, print its contents in a commit message, or paste its values into any file under version control.

- [ ] **Step 4: Log in and link the CLI to the cloud project**

```bash
npx supabase login --token <SUPABASE_ACCESS_TOKEN value>
npx supabase link --project-ref <SUPABASE_PROJECT_REF value> --password '<SUPABASE_DB_PASSWORD value>'
```

Expected: `login` reports success; `link` reports "Finished supabase link" (it may warn that it cannot reach the database directly to compare migration history — that warning is expected on this network and does not block later steps, since this plan never uses `supabase db push`/`db pull`).

- [ ] **Step 5: Write `scripts/run_sql.mjs`**

Create `scripts/run_sql.mjs`:

```javascript
#!/usr/bin/env node
// Runs a .sql file against the linked cloud Supabase project over HTTPS
// (the Management API's /database/query endpoint), since this network
// blocks outbound direct Postgres connections (ports 5432/6543) that
// `supabase db push` / `supabase test db` / `psql` would need.
//
// Usage: node scripts/run_sql.mjs <path-to-sql-file>
//
// For pgTAP test files: exit code reflects pass/fail by checking for a
// "summary" column equal to exactly "ALL TESTS PASSED" (see the
// finish()-aggregation convention documented in the plan). For plain
// migrations: exit code reflects the HTTP response status only.
//
// Uses process.exitCode (not process.exit()) throughout: calling
// process.exit() right after an awaited fetch() has crashed with a
// libuv assertion on this machine's Node/Windows combination.

import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(scriptDir, '..');

function loadEnvLocal() {
  const envPath = join(repoRoot, 'supabase', '.env.local');
  if (!existsSync(envPath)) return;
  for (const line of readFileSync(envPath, 'utf8').split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq === -1) continue;
    const key = trimmed.slice(0, eq).trim();
    const value = trimmed.slice(eq + 1).trim();
    if (!(key in process.env)) process.env[key] = value;
  }
}

async function main() {
  loadEnvLocal();

  const [, , sqlFilePath] = process.argv;
  if (!sqlFilePath) {
    console.error('Usage: node scripts/run_sql.mjs <path-to-sql-file>');
    process.exitCode = 1;
    return;
  }

  const projectRef = process.env.SUPABASE_PROJECT_REF;
  const accessToken = process.env.SUPABASE_ACCESS_TOKEN;
  if (!projectRef || !accessToken) {
    console.error('Missing SUPABASE_PROJECT_REF / SUPABASE_ACCESS_TOKEN — check supabase/.env.local.');
    process.exitCode = 1;
    return;
  }

  const query = readFileSync(sqlFilePath, 'utf8');

  const response = await fetch(`https://api.supabase.com/v1/projects/${projectRef}/database/query`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query }),
  });

  const body = await response.json();

  if (!response.ok) {
    console.error(`FAIL (HTTP ${response.status}):`, body.message ?? JSON.stringify(body));
    process.exitCode = 1;
    return;
  }

  console.log(JSON.stringify(body, null, 2));

  const summaryRow = Array.isArray(body) ? body.find((row) => 'summary' in row) : null;
  if (summaryRow) {
    if (summaryRow.summary === 'ALL TESTS PASSED') {
      console.log('RESULT: PASS');
      process.exitCode = 0;
      return;
    }
    console.error('RESULT: FAIL —', summaryRow.summary);
    process.exitCode = 1;
    return;
  }

  process.exitCode = 0;
}

await main();
```

- [ ] **Step 6: Verify the runner works end-to-end**

Create a throwaway file `supabase/tests/database/_manual_check.sql` containing `select 1 as ok;`, run `node scripts/run_sql.mjs supabase/tests/database/_manual_check.sql`, confirm it prints the JSON row `{"ok": 1}` and exits 0, then delete the throwaway file (it is not part of the test suite Task 2 builds).

- [ ] **Step 7: Commit**

```bash
git add package.json package-lock.json .gitignore supabase/config.toml supabase/.gitignore scripts/run_sql.mjs
git commit -m "chore: link cloud Supabase project, add HTTPS SQL runner"
```

Do NOT `git add supabase/.env.local` — verify with `git status` that it does not appear as a staged or trackable file before committing.

---

## Task 2: pgTAP test harness

**Files:**
- Create: `supabase/tests/database/000_smoke_test.sql`

**Interfaces:**
- Consumes: `scripts/run_sql.mjs` from Task 1.
- Produces: a working `node scripts/run_sql.mjs <file>` pattern that later tasks' migrations and test files plug into (test files live under `supabase/tests/database/*.sql`, applied and run in filename order).

- [ ] **Step 1: Enable the pgtap extension via a migration**

Run: `npx supabase migration new habilitar_pgtap`
This creates `supabase/migrations/<timestamp>_habilitar_pgtap.sql`. Replace its contents with:

```sql
create extension if not exists pgtap with schema extensions;
```

- [ ] **Step 2: Write the smoke test**

Create `supabase/tests/database/000_smoke_test.sql`:

```sql
begin;
select plan(1);
select ok(1 = 1, 'pgTAP test harness is wired up');
select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
```

- [ ] **Step 3: Apply the migration and run the test**

Run:
```bash
node scripts/run_sql.mjs supabase/migrations/<timestamp>_habilitar_pgtap.sql
node scripts/run_sql.mjs supabase/tests/database/000_smoke_test.sql
```
Expected: the migration run prints an empty successful result (`[]`); the test run prints `RESULT: PASS` (the `summary` row equals `'ALL TESTS PASSED'`) and exits 0.

- [ ] **Step 4: Commit**

```bash
git add supabase/migrations supabase/tests
git commit -m "test: wire up pgTAP test harness"
```

---

## Task 3: `organizacoes`, `perfis`, RLS helper functions

**Files:**
- Create: migration via `npx supabase migration new criar_organizacoes_perfis`
- Create: `supabase/tests/database/010_organizacoes_perfis.sql`

**Interfaces:**
- Produces:
  - Table `public.organizacoes(id uuid, nome text, criado_em timestamptz)`.
  - Enum `public.papel_usuario` with values `'super_admin'`, `'admin'`, `'usuario'`.
  - Table `public.perfis(id uuid, organizacao_id uuid null, papel papel_usuario, nome text, cargo_setor text null, ativo boolean, notificar_avanco_fase boolean, notificar_prazo boolean, notificar_tempo_parado boolean, ultimo_recebimento_em timestamptz null, criado_em timestamptz)`.
  - Functions `public.auth_organizacao_id() returns uuid` and `public.auth_papel() returns public.papel_usuario` — every later RLS policy in this plan calls these two.
  - Trigger `perfis_proteger_campos_trigger` blocking direct client changes to `organizacao_id`, `papel`, `ativo`, `ultimo_recebimento_em` (bypassed only when `current_user in ('postgres', 'service_role')`).

- [ ] **Step 1: Write the failing test**

Create `supabase/tests/database/010_organizacoes_perfis.sql`:

```sql
begin;
select plan(9);

-- Schema exists
select has_table('public', 'organizacoes', 'organizacoes table should exist');
select has_table('public', 'perfis', 'perfis table should exist');
select has_enum('public', 'papel_usuario', 'papel_usuario enum should exist');

-- Fixture data: two orgs, one super_admin, one admin per org, one usuario in org A
insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values
  ('00000000-0000-0000-0000-000000000001', 'super@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000002', 'admin.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000003', 'user.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000004', 'admin.b@teste.com', 'x', now());

insert into public.organizacoes (id, nome) values
  ('10000000-0000-0000-0000-000000000001', 'Organização A'),
  ('10000000-0000-0000-0000-000000000002', 'Organização B');

insert into public.perfis (id, organizacao_id, papel, nome, ativo) values
  ('00000000-0000-0000-0000-000000000001', null, 'super_admin', 'Super Admin', true),
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'admin', 'Admin A', true),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A', true),
  ('00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002', 'admin', 'Admin B', true);

-- Helper functions resolve correctly
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
select is(public.auth_organizacao_id(), '10000000-0000-0000-0000-000000000001'::uuid, 'auth_organizacao_id resolves Admin A''s org');
select is(public.auth_papel(), 'admin'::public.papel_usuario, 'auth_papel resolves Admin A''s papel');

-- RLS: Usuário A sees only Organização A's perfis, not Organização B's
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
set local role authenticated;
select results_eq(
  $$select nome from public.perfis where organizacao_id = '10000000-0000-0000-0000-000000000002' order by nome$$,
  array[]::text[],
  'Usuário A cannot see Organização B perfis'
);
select results_eq(
  $$select nome from public.perfis order by nome$$,
  array['Admin A', 'Usuário A'],
  'Usuário A sees only Organização A perfis'
);
reset role;

-- Protected columns: a plain client update to papel must fail
set local role authenticated;
select throws_ok(
  $$update public.perfis set papel = 'admin' where id = '00000000-0000-0000-0000-000000000003'$$,
  'P0001',
  'Campo protegido não pode ser alterado diretamente.',
  'Direct client update of papel is blocked'
);
reset role;

-- Self-editable columns: a user can update their own notification toggle
set local role authenticated;
update public.perfis set notificar_prazo = false where id = '00000000-0000-0000-0000-000000000003';
select ok(true, 'Self-editing notificar_prazo did not raise');
reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `node scripts/run_sql.mjs supabase/tests/database/010_organizacoes_perfis.sql`
Expected: FAIL — `organizacoes`/`perfis`/`papel_usuario` do not exist yet.

- [ ] **Step 3: Write the migration**

Run: `npx supabase migration new criar_organizacoes_perfis`
Replace the generated file's contents with:

```sql
create type public.papel_usuario as enum ('super_admin', 'admin', 'usuario');

create table public.organizacoes (
  id uuid primary key default gen_random_uuid(),
  nome text not null,
  criado_em timestamptz not null default now()
);

create table public.perfis (
  id uuid primary key references auth.users (id) on delete cascade,
  organizacao_id uuid references public.organizacoes (id),
  papel public.papel_usuario not null,
  nome text not null,
  cargo_setor text,
  ativo boolean not null default true,
  notificar_avanco_fase boolean not null default true,
  notificar_prazo boolean not null default true,
  notificar_tempo_parado boolean not null default true,
  ultimo_recebimento_em timestamptz,
  criado_em timestamptz not null default now(),
  constraint perfis_organizacao_obrigatoria_exceto_super_admin
    check (papel = 'super_admin' or organizacao_id is not null)
);

create index perfis_organizacao_id_idx on public.perfis (organizacao_id);

create or replace function public.auth_organizacao_id() returns uuid
language sql stable security definer set search_path = public as $$
  select organizacao_id from public.perfis where id = auth.uid();
$$;

create or replace function public.auth_papel() returns public.papel_usuario
language sql stable security definer set search_path = public as $$
  select papel from public.perfis where id = auth.uid();
$$;

create or replace function public.perfis_proteger_campos() returns trigger
language plpgsql as $$
begin
  if current_user in ('postgres', 'service_role') then
    return new;
  end if;
  if new.organizacao_id is distinct from old.organizacao_id
     or new.papel is distinct from old.papel
     or new.ativo is distinct from old.ativo
     or new.ultimo_recebimento_em is distinct from old.ultimo_recebimento_em then
    raise exception 'Campo protegido não pode ser alterado diretamente.';
  end if;
  return new;
end;
$$;

create trigger perfis_proteger_campos_trigger
before update on public.perfis
for each row execute function public.perfis_proteger_campos();

alter table public.organizacoes enable row level security;
alter table public.organizacoes force row level security;
alter table public.perfis enable row level security;
alter table public.perfis force row level security;

create policy "organizacoes_select" on public.organizacoes
for select using (
  id = public.auth_organizacao_id() or public.auth_papel() = 'super_admin'
);

create policy "perfis_select_mesma_organizacao" on public.perfis
for select using (
  organizacao_id = public.auth_organizacao_id() or public.auth_papel() = 'super_admin'
);

create policy "perfis_update_propria_conta" on public.perfis
for update using (id = auth.uid())
with check (id = auth.uid());
```

- [ ] **Step 4: Apply the migration and run the test again**

Run:
```bash
node scripts/run_sql.mjs supabase/migrations/<timestamp>_criar_organizacoes_perfis.sql
node scripts/run_sql.mjs supabase/tests/database/010_organizacoes_perfis.sql
```
Expected: `RESULT: PASS` — all 9 assertions in `010_organizacoes_perfis.sql` pass.

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations supabase/tests
git commit -m "feat: organizacoes, perfis, RLS helper functions"
```

---

## Task 4: `device_tokens`

**Files:**
- Create: migration via `npx supabase migration new criar_device_tokens`
- Create: `supabase/tests/database/020_device_tokens.sql`

**Interfaces:**
- Consumes: `public.perfis`, `public.auth_organizacao_id()`.
- Produces: table `public.device_tokens(id uuid, perfil_id uuid, token_fcm text, atualizado_em timestamptz)`.

- [ ] **Step 1: Write the failing test**

Create `supabase/tests/database/020_device_tokens.sql`:

```sql
begin;
select plan(4);

select has_table('public', 'device_tokens', 'device_tokens table should exist');

insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values
  ('00000000-0000-0000-0000-000000000001', 'super@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000002', 'admin.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000003', 'user.a@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values ('10000000-0000-0000-0000-000000000001', 'Organização A');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000001', null, 'super_admin', 'Super Admin'),
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'admin', 'Admin A'),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A');

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;

insert into public.device_tokens (perfil_id, token_fcm) values ('00000000-0000-0000-0000-000000000003', 'token-abc');
select ok(true, 'User can insert their own device token');

select results_eq(
  $$select token_fcm from public.device_tokens where perfil_id = '00000000-0000-0000-0000-000000000003'$$,
  array['token-abc'],
  'User can read their own device token'
);

select throws_ok(
  $$insert into public.device_tokens (perfil_id, token_fcm) values ('00000000-0000-0000-0000-000000000002', 'token-xyz')$$,
  '42501',
  null,
  'User cannot insert a device token for someone else'
);

reset role;
select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
```

- [ ] **Step 2: Run test, verify it fails**

Run: `node scripts/run_sql.mjs supabase/tests/database/020_device_tokens.sql` → FAIL (table doesn't exist).

- [ ] **Step 3: Write the migration**

Run: `npx supabase migration new criar_device_tokens`

```sql
create table public.device_tokens (
  id uuid primary key default gen_random_uuid(),
  perfil_id uuid not null references public.perfis (id) on delete cascade,
  token_fcm text not null,
  atualizado_em timestamptz not null default now(),
  unique (perfil_id, token_fcm)
);

create index device_tokens_perfil_id_idx on public.device_tokens (perfil_id);

alter table public.device_tokens enable row level security;
alter table public.device_tokens force row level security;

create policy "device_tokens_dono" on public.device_tokens
for all using (perfil_id = auth.uid())
with check (perfil_id = auth.uid());
```

- [ ] **Step 4: Apply the migration and re-run**

```bash
node scripts/run_sql.mjs supabase/migrations/<timestamp>_criar_device_tokens.sql
node scripts/run_sql.mjs supabase/tests/database/020_device_tokens.sql
```
Expected: `RESULT: PASS`.

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations supabase/tests
git commit -m "feat: device_tokens table"
```

---

## Task 5: `tipos_processo`, `fases`

**Files:**
- Create: migration via `npx supabase migration new criar_tipos_processo_fases`
- Create: `supabase/tests/database/030_tipos_processo_fases.sql`

**Interfaces:**
- Produces: `public.tipos_processo(id uuid, organizacao_id uuid, nome text, dias_alerta_atencao int, dias_alerta_critico int)`; `public.fases(id uuid, organizacao_id uuid, nome text, ordem int, descricao text null, dias_alerta_atencao int, dias_alerta_critico int)`.

- [ ] **Step 1: Write the failing test**

Create `supabase/tests/database/030_tipos_processo_fases.sql`:

```sql
begin;
select plan(6);

select has_table('public', 'tipos_processo', 'tipos_processo table should exist');
select has_table('public', 'fases', 'fases table should exist');

insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values
  ('00000000-0000-0000-0000-000000000002', 'admin.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000003', 'user.a@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values ('10000000-0000-0000-0000-000000000001', 'Organização A');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'admin', 'Admin A'),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A');

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;
insert into public.tipos_processo (organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico)
values ('10000000-0000-0000-0000-000000000001', 'Aquisição direta', 10, 20);
insert into public.fases (organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico)
values ('10000000-0000-0000-0000-000000000001', 'Pesquisa de Preços', 1, 5, 10);
select ok(true, 'Admin can create tipos_processo and fases');
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
set local role authenticated;
select throws_ok(
  $$insert into public.tipos_processo (organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico)
    values ('10000000-0000-0000-0000-000000000001', 'Serviço', 5, 10)$$,
  '42501',
  null,
  'Non-admin user cannot create tipos_processo'
);
select results_eq(
  $$select nome from public.fases order by nome$$,
  array['Pesquisa de Preços'],
  'Non-admin user can still read fases of their org'
);
reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
```

- [ ] **Step 2: Run test, verify it fails** — `node scripts/run_sql.mjs supabase/tests/database/030_tipos_processo_fases.sql`

- [ ] **Step 3: Write the migration**

Run: `npx supabase migration new criar_tipos_processo_fases`

```sql
create table public.tipos_processo (
  id uuid primary key default gen_random_uuid(),
  organizacao_id uuid not null references public.organizacoes (id),
  nome text not null,
  dias_alerta_atencao int not null,
  dias_alerta_critico int not null
);

create table public.fases (
  id uuid primary key default gen_random_uuid(),
  organizacao_id uuid not null references public.organizacoes (id),
  nome text not null,
  ordem int not null,
  descricao text,
  dias_alerta_atencao int not null,
  dias_alerta_critico int not null
);

create index tipos_processo_organizacao_id_idx on public.tipos_processo (organizacao_id);
create index fases_organizacao_id_idx on public.fases (organizacao_id);

alter table public.tipos_processo enable row level security;
alter table public.tipos_processo force row level security;
alter table public.fases enable row level security;
alter table public.fases force row level security;

create policy "tipos_processo_select" on public.tipos_processo
for select using (organizacao_id = public.auth_organizacao_id());

create policy "tipos_processo_admin_escreve" on public.tipos_processo
for all using (
  organizacao_id = public.auth_organizacao_id() and public.auth_papel() = 'admin'
)
with check (
  organizacao_id = public.auth_organizacao_id() and public.auth_papel() = 'admin'
);

create policy "fases_select" on public.fases
for select using (organizacao_id = public.auth_organizacao_id());

create policy "fases_admin_escreve" on public.fases
for all using (
  organizacao_id = public.auth_organizacao_id() and public.auth_papel() = 'admin'
)
with check (
  organizacao_id = public.auth_organizacao_id() and public.auth_papel() = 'admin'
);
```

- [ ] **Step 4: Apply the migration and re-run** — `node scripts/run_sql.mjs supabase/migrations/<timestamp>_criar_tipos_processo_fases.sql` then `node scripts/run_sql.mjs supabase/tests/database/030_tipos_processo_fases.sql` → `RESULT: PASS`.

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations supabase/tests
git commit -m "feat: tipos_processo and fases tables"
```

---

## Task 6: `processos`

**Files:**
- Create: migration via `npx supabase migration new criar_processos`
- Create: `supabase/tests/database/040_processos.sql`

**Interfaces:**
- Consumes: `public.fases`, `public.tipos_processo`, `public.perfis`.
- Produces: `public.processos(id uuid, organizacao_id uuid, numero text, objeto text, descricao text, orgao_demandante text, tipo_processo_id uuid, valor_estimado_total numeric, data_abertura date, fase_atual_id uuid, status_geral status_geral_processo, responsavel_id uuid null, designado_em timestamptz null, designado_por uuid null, criado_em timestamptz, atualizado_em timestamptz)`.

- [ ] **Step 1: Write the failing test**

Create `supabase/tests/database/040_processos.sql`:

```sql
begin;
select plan(7);

select has_table('public', 'processos', 'processos table should exist');
select has_enum('public', 'status_geral_processo', 'status_geral_processo enum should exist');

insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values
  ('00000000-0000-0000-0000-000000000002', 'admin.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000003', 'user.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000005', 'user.a2@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values ('10000000-0000-0000-0000-000000000001', 'Organização A');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'admin', 'Admin A'),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A'),
  ('00000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A2');
insert into public.fases (id, organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico)
values ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Pesquisa de Preços', 1, 5, 10);
insert into public.tipos_processo (id, organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico)
values ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Aquisição direta', 10, 20);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;
insert into public.processos (
  id, organizacao_id, numero, objeto, descricao, orgao_demandante,
  tipo_processo_id, valor_estimado_total, data_abertura, fase_atual_id, status_geral
) values (
  '40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
  '001/2026', 'Aquisição de equipamentos', '', '', '30000000-0000-0000-0000-000000000001',
  0, current_date, '20000000-0000-0000-0000-000000000001', 'em_andamento'
);
select ok(true, 'Admin can create a processo');
reset role;

set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
select throws_ok(
  $$insert into public.processos (
      organizacao_id, numero, objeto, descricao, orgao_demandante,
      tipo_processo_id, valor_estimado_total, data_abertura, fase_atual_id, status_geral
    ) values (
      '10000000-0000-0000-0000-000000000001', '002/2026', 'x', '', '',
      '30000000-0000-0000-0000-000000000001', 0, current_date,
      '20000000-0000-0000-0000-000000000001', 'em_andamento'
    )$$,
  '42501',
  null,
  'Non-admin user cannot create a processo'
);
select results_eq(
  $$select numero from public.processos$$,
  array['001/2026'],
  'Non-admin user can read the org''s processos (transparency)'
);
-- orphan process: usuário can self-assign
update public.processos set responsavel_id = '00000000-0000-0000-0000-000000000003'
where id = '40000000-0000-0000-0000-000000000001';
select ok(true, 'Usuário can self-assign an orphan processo');
-- Another usuário cannot steal an already-assigned processo. This UPDATE's
-- USING clause does not match the row for this caller, so Postgres RLS
-- silently filters it to 0 rows affected rather than raising an exception
-- (unlike INSERT, a non-matching UPDATE is not an error) — assert the data
-- is unchanged, not that an exception was thrown.
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000005', true);
update public.processos set responsavel_id = '00000000-0000-0000-0000-000000000005'
where id = '40000000-0000-0000-0000-000000000001';
select results_eq(
  $$select responsavel_id from public.processos where id = '40000000-0000-0000-0000-000000000001'$$,
  array['00000000-0000-0000-0000-000000000003'::uuid],
  'A different usuário cannot take a processo already assigned to someone else (RLS silently no-ops the UPDATE)'
);
reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
```

- [ ] **Step 2: Run test, verify it fails** — `node scripts/run_sql.mjs supabase/tests/database/040_processos.sql`

- [ ] **Step 3: Write the migration**

Run: `npx supabase migration new criar_processos`

```sql
create type public.status_geral_processo as enum ('em_andamento', 'suspenso', 'concluido', 'cancelado');

create table public.processos (
  id uuid primary key default gen_random_uuid(),
  organizacao_id uuid not null references public.organizacoes (id),
  numero text not null,
  objeto text not null,
  descricao text not null default '',
  orgao_demandante text not null default '',
  tipo_processo_id uuid not null references public.tipos_processo (id),
  valor_estimado_total numeric not null default 0,
  data_abertura date not null,
  fase_atual_id uuid not null references public.fases (id),
  status_geral public.status_geral_processo not null default 'em_andamento',
  responsavel_id uuid references public.perfis (id),
  designado_em timestamptz,
  designado_por uuid references public.perfis (id),
  criado_em timestamptz not null default now(),
  atualizado_em timestamptz not null default now()
);

create index processos_organizacao_id_idx on public.processos (organizacao_id);
create index processos_responsavel_id_idx on public.processos (responsavel_id);

alter table public.processos enable row level security;
alter table public.processos force row level security;

create policy "processos_select" on public.processos
for select using (organizacao_id = public.auth_organizacao_id());

create policy "processos_admin_insere" on public.processos
for insert with check (
  organizacao_id = public.auth_organizacao_id() and public.auth_papel() = 'admin'
);

create policy "processos_update" on public.processos
for update using (
  organizacao_id = public.auth_organizacao_id() and (
    public.auth_papel() = 'admin' or responsavel_id = auth.uid() or responsavel_id is null
  )
)
with check (
  organizacao_id = public.auth_organizacao_id() and (
    public.auth_papel() = 'admin' or responsavel_id = auth.uid() or responsavel_id is null
  )
);

create policy "processos_admin_deleta" on public.processos
for delete using (
  organizacao_id = public.auth_organizacao_id() and public.auth_papel() = 'admin'
);
```

- [ ] **Step 4: Apply the migration and re-run** — `node scripts/run_sql.mjs supabase/migrations/<timestamp>_criar_processos.sql` then `node scripts/run_sql.mjs supabase/tests/database/040_processos.sql` → `RESULT: PASS`.

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations supabase/tests
git commit -m "feat: processos table with designation-aware RLS"
```

---

## Task 7: `itens`, `processo_fase_historico`

**Files:**
- Create: migration via `npx supabase migration new criar_itens_historico`
- Create: `supabase/tests/database/050_itens_historico.sql`

**Interfaces:**
- Consumes: `public.processos`.
- Produces: `public.itens(id uuid, processo_id uuid, descricao text, quantidade numeric, unidade text, valor_estimado_unit numeric, valor_pesquisa_unit numeric null)`; `public.processo_fase_historico(id uuid, processo_id uuid, fase_id uuid, responsavel_id uuid null, data_entrada date, data_saida date null, prazo_limite date null, observacoes text, motivo_retorno text null, notificar_prazo boolean, criado_em timestamptz)`.

- [ ] **Step 1: Write the failing test**

Create `supabase/tests/database/050_itens_historico.sql`:

```sql
begin;
select plan(6);

select has_table('public', 'itens', 'itens table should exist');
select has_table('public', 'processo_fase_historico', 'processo_fase_historico table should exist');

insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values
  ('00000000-0000-0000-0000-000000000002', 'admin.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000003', 'user.a@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values ('10000000-0000-0000-0000-000000000001', 'Organização A');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'admin', 'Admin A'),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A');
insert into public.fases (id, organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico)
values ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Pesquisa de Preços', 1, 5, 10);
insert into public.tipos_processo (id, organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico)
values ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Aquisição direta', 10, 20);
insert into public.processos (
  id, organizacao_id, numero, objeto, tipo_processo_id, data_abertura, fase_atual_id, responsavel_id
) values (
  '40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
  '001/2026', 'x', '30000000-0000-0000-0000-000000000001', current_date,
  '20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;
insert into public.itens (processo_id, descricao, quantidade, unidade, valor_estimado_unit)
values ('40000000-0000-0000-0000-000000000001', 'Notebook', 1, 'un', 3500);
select ok(true, 'Responsible user can add an item to their own processo');

insert into public.processo_fase_historico (
  processo_id, fase_id, data_entrada, observacoes, notificar_prazo
) values (
  '40000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
  current_date, '', false
);
select ok(true, 'Responsible user can add a historico entry to their own processo');

reset role;

-- A colleague in the SAME organization — not the owner, not an admin — must
-- still be able to READ the item for workload transparency (spec section 2:
-- "vê todos os processos"), even though they cannot edit it.
insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values ('00000000-0000-0000-0000-000000000007', 'user.a2@teste.com', 'x', now());
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A2');
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000007', true);
set local role authenticated;
select results_eq(
  $$select descricao from public.itens$$,
  array['Notebook'],
  'A colleague in the same organization can read itens for transparency, even though it is not theirs'
);
reset role;

insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values ('00000000-0000-0000-0000-000000000006', 'user.b@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values ('10000000-0000-0000-0000-000000000002', 'Organização B');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000002', 'admin', 'Admin B');
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000006', true);
set local role authenticated;
select results_eq(
  $$select descricao from public.itens$$,
  array[]::text[],
  'A user from a different organization sees no itens from Organização A'
);
reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
```

- [ ] **Step 2: Run test, verify it fails** — `node scripts/run_sql.mjs supabase/tests/database/050_itens_historico.sql`

- [ ] **Step 3: Write the migration**

Run: `npx supabase migration new criar_itens_historico`

```sql
create table public.itens (
  id uuid primary key default gen_random_uuid(),
  processo_id uuid not null references public.processos (id) on delete cascade,
  descricao text not null,
  quantidade numeric not null,
  unidade text not null,
  valor_estimado_unit numeric not null,
  valor_pesquisa_unit numeric
);

create table public.processo_fase_historico (
  id uuid primary key default gen_random_uuid(),
  processo_id uuid not null references public.processos (id) on delete cascade,
  fase_id uuid not null references public.fases (id),
  responsavel_id uuid references public.perfis (id),
  data_entrada date not null,
  data_saida date,
  prazo_limite date,
  observacoes text not null default '',
  motivo_retorno text,
  notificar_prazo boolean not null default false,
  criado_em timestamptz not null default now()
);

create index itens_processo_id_idx on public.itens (processo_id);
create index processo_fase_historico_processo_id_idx on public.processo_fase_historico (processo_id);

alter table public.itens enable row level security;
alter table public.itens force row level security;
alter table public.processo_fase_historico enable row level security;
alter table public.processo_fase_historico force row level security;

create policy "itens_acesso" on public.itens
for all using (
  exists (
    select 1 from public.processos p
    where p.id = itens.processo_id
      and p.organizacao_id = public.auth_organizacao_id()
      and (public.auth_papel() = 'admin' or p.responsavel_id = auth.uid())
  )
)
with check (
  exists (
    select 1 from public.processos p
    where p.id = itens.processo_id
      and p.organizacao_id = public.auth_organizacao_id()
      and (public.auth_papel() = 'admin' or p.responsavel_id = auth.uid())
  )
);

create policy "processo_fase_historico_acesso" on public.processo_fase_historico
for all using (
  exists (
    select 1 from public.processos p
    where p.id = processo_fase_historico.processo_id
      and p.organizacao_id = public.auth_organizacao_id()
      and (public.auth_papel() = 'admin' or p.responsavel_id = auth.uid())
  )
)
with check (
  exists (
    select 1 from public.processos p
    where p.id = processo_fase_historico.processo_id
      and p.organizacao_id = public.auth_organizacao_id()
      and (public.auth_papel() = 'admin' or p.responsavel_id = auth.uid())
  )
);

-- The two policies above correctly gate WRITES to admin-or-owner, but a
-- plain USING/WITH CHECK pair also gates SELECT the same way — which would
-- stop a colleague from even reading another user's processo detail,
-- breaking the "vê todos os processos" transparency requirement (spec
-- section 2). Add a second, purely-permissive SELECT policy: Postgres ORs
-- permissive policies together for the same command, so this widens read
-- access to the whole organization without loosening the write policies
-- above at all.
create policy "itens_select_transparencia" on public.itens
for select using (
  exists (
    select 1 from public.processos p
    where p.id = itens.processo_id and p.organizacao_id = public.auth_organizacao_id()
  )
);

create policy "processo_fase_historico_select_transparencia" on public.processo_fase_historico
for select using (
  exists (
    select 1 from public.processos p
    where p.id = processo_fase_historico.processo_id and p.organizacao_id = public.auth_organizacao_id()
  )
);
```

- [ ] **Step 4: Apply the migration and re-run** — `node scripts/run_sql.mjs supabase/migrations/<timestamp>_criar_itens_historico.sql` then `node scripts/run_sql.mjs supabase/tests/database/050_itens_historico.sql` → `RESULT: PASS`.

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations supabase/tests
git commit -m "feat: itens and processo_fase_historico tables"
```

---

## Task 8: `diligencias`, `observacao_versoes`

**Files:**
- Create: migration via `npx supabase migration new criar_diligencias_observacoes`
- Create: `supabase/tests/database/060_diligencias_observacoes.sql`

**Interfaces:**
- Consumes: `public.processo_fase_historico`.
- Produces: `public.diligencias(id uuid, processo_fase_historico_id uuid, autor_id uuid, conteudo text, criado_em timestamptz)`; `public.observacao_versoes(id uuid, processo_fase_historico_id uuid, conteudo text, criado_em timestamptz)`.

- [ ] **Step 1: Write the failing test**

Create `supabase/tests/database/060_diligencias_observacoes.sql`:

```sql
begin;
select plan(6);

select has_table('public', 'diligencias', 'diligencias table should exist');
select has_table('public', 'observacao_versoes', 'observacao_versoes table should exist');

insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values ('00000000-0000-0000-0000-000000000003', 'user.a@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values ('10000000-0000-0000-0000-000000000001', 'Organização A');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A');
insert into public.fases (id, organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico)
values ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Pesquisa de Preços', 1, 5, 10);
insert into public.tipos_processo (id, organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico)
values ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Aquisição direta', 10, 20);
insert into public.processos (
  id, organizacao_id, numero, objeto, tipo_processo_id, data_abertura, fase_atual_id, responsavel_id
) values (
  '40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
  '001/2026', 'x', '30000000-0000-0000-0000-000000000001', current_date,
  '20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003'
);
insert into public.processo_fase_historico (id, processo_id, fase_id, data_entrada, observacoes)
values ('50000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', current_date, '');

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;

insert into public.diligencias (processo_fase_historico_id, autor_id, conteudo)
values ('50000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', 'Enviado ofício ao fornecedor X');
select ok(true, 'Responsible user can log a diligência');

insert into public.observacao_versoes (processo_fase_historico_id, conteudo)
values ('50000000-0000-0000-0000-000000000001', 'Aguardando resposta');
select ok(true, 'Responsible user can log an observação version');

reset role;

-- A colleague in the SAME organization — not the owner, not an admin — must
-- still be able to READ diligências/observações for workload transparency
-- (spec section 2: "vê todos os processos"), even though they cannot write.
insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values ('00000000-0000-0000-0000-000000000007', 'user.a2@teste.com', 'x', now());
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A2');
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000007', true);
set local role authenticated;
select results_eq(
  $$select conteudo from public.diligencias$$,
  array['Enviado ofício ao fornecedor X'],
  'A colleague in the same organization can read diligências for transparency, even though it is not theirs'
);
select results_eq(
  $$select conteudo from public.observacao_versoes$$,
  array['Aguardando resposta'],
  'A colleague in the same organization can read observação versions for transparency, even though it is not theirs'
);
reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
```

- [ ] **Step 2: Run test, verify it fails** — `node scripts/run_sql.mjs supabase/tests/database/060_diligencias_observacoes.sql`

- [ ] **Step 3: Write the migration**

Run: `npx supabase migration new criar_diligencias_observacoes`

```sql
create table public.diligencias (
  id uuid primary key default gen_random_uuid(),
  processo_fase_historico_id uuid not null references public.processo_fase_historico (id) on delete cascade,
  autor_id uuid not null references public.perfis (id),
  conteudo text not null,
  criado_em timestamptz not null default now()
);

create table public.observacao_versoes (
  id uuid primary key default gen_random_uuid(),
  processo_fase_historico_id uuid not null references public.processo_fase_historico (id) on delete cascade,
  conteudo text not null,
  criado_em timestamptz not null default now()
);

create index diligencias_historico_id_idx on public.diligencias (processo_fase_historico_id);
create index observacao_versoes_historico_id_idx on public.observacao_versoes (processo_fase_historico_id);

alter table public.diligencias enable row level security;
alter table public.diligencias force row level security;
alter table public.observacao_versoes enable row level security;
alter table public.observacao_versoes force row level security;

create policy "diligencias_acesso" on public.diligencias
for all using (
  exists (
    select 1 from public.processo_fase_historico h
    join public.processos p on p.id = h.processo_id
    where h.id = diligencias.processo_fase_historico_id
      and p.organizacao_id = public.auth_organizacao_id()
      and (public.auth_papel() = 'admin' or p.responsavel_id = auth.uid())
  )
)
with check (
  exists (
    select 1 from public.processo_fase_historico h
    join public.processos p on p.id = h.processo_id
    where h.id = diligencias.processo_fase_historico_id
      and p.organizacao_id = public.auth_organizacao_id()
      and (public.auth_papel() = 'admin' or p.responsavel_id = auth.uid())
  )
);

create policy "observacao_versoes_acesso" on public.observacao_versoes
for all using (
  exists (
    select 1 from public.processo_fase_historico h
    join public.processos p on p.id = h.processo_id
    where h.id = observacao_versoes.processo_fase_historico_id
      and p.organizacao_id = public.auth_organizacao_id()
      and (public.auth_papel() = 'admin' or p.responsavel_id = auth.uid())
  )
)
with check (
  exists (
    select 1 from public.processo_fase_historico h
    join public.processos p on p.id = h.processo_id
    where h.id = observacao_versoes.processo_fase_historico_id
      and p.organizacao_id = public.auth_organizacao_id()
      and (public.auth_papel() = 'admin' or p.responsavel_id = auth.uid())
  )
);

-- Same reasoning as Task 7's *_select_transparencia policies: the two
-- policies above correctly gate WRITES to admin-or-owner, but also happen to
-- gate SELECT the same way unless widened — add a purely-permissive,
-- org-wide SELECT policy per table so a colleague can read (never write)
-- someone else's diligências/observações, per the transparency requirement.
create policy "diligencias_select_transparencia" on public.diligencias
for select using (
  exists (
    select 1 from public.processo_fase_historico h
    join public.processos p on p.id = h.processo_id
    where h.id = diligencias.processo_fase_historico_id
      and p.organizacao_id = public.auth_organizacao_id()
  )
);

create policy "observacao_versoes_select_transparencia" on public.observacao_versoes
for select using (
  exists (
    select 1 from public.processo_fase_historico h
    join public.processos p on p.id = h.processo_id
    where h.id = observacao_versoes.processo_fase_historico_id
      and p.organizacao_id = public.auth_organizacao_id()
  )
);
```

- [ ] **Step 4: Apply the migration and re-run** — `node scripts/run_sql.mjs supabase/migrations/<timestamp>_criar_diligencias_observacoes.sql` then `node scripts/run_sql.mjs supabase/tests/database/060_diligencias_observacoes.sql` → `RESULT: PASS`.

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations supabase/tests
git commit -m "feat: diligencias and observacao_versoes tables"
```

---

## Task 9: `designacoes` + `designar_processo` function

**Files:**
- Create: migration via `npx supabase migration new criar_designacoes_e_funcao`
- Create: `supabase/tests/database/070_designacoes.sql`

**Interfaces:**
- Consumes: `public.processos`, `public.perfis`, `public.tipos_processo`.
- Produces: table `public.designacoes(id uuid, processo_id uuid, tipo_processo_id uuid, perfil_id uuid, designado_por uuid, criado_em timestamptz)`; function `public.designar_processo(p_processo_id uuid, p_novo_responsavel_id uuid) returns void` — **this is the only way `processos.responsavel_id`/`designado_em`, `perfis.ultimo_recebimento_em`, and `designacoes` rows should ever be written from the app** (Plan 2's repository calls this via `supabase.rpc('designar_processo', ...)`, never raw table writes for designation).

- [ ] **Step 1: Write the failing test**

Create `supabase/tests/database/070_designacoes.sql`:

```sql
begin;
select plan(8);

select has_table('public', 'designacoes', 'designacoes table should exist');
select has_function('public', 'designar_processo', array['uuid', 'uuid'], 'designar_processo function should exist');

insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values
  ('00000000-0000-0000-0000-000000000002', 'admin.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000003', 'user.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000005', 'user.a2@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values ('10000000-0000-0000-0000-000000000001', 'Organização A');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'admin', 'Admin A'),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A'),
  ('00000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A2');
insert into public.fases (id, organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico)
values ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Pesquisa de Preços', 1, 5, 10);
insert into public.tipos_processo (id, organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico)
values ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Aquisição direta', 10, 20);
insert into public.processos (id, organizacao_id, numero, objeto, tipo_processo_id, data_abertura, fase_atual_id)
values ('40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
  '001/2026', 'x', '30000000-0000-0000-0000-000000000001', current_date, '20000000-0000-0000-0000-000000000001');

-- Admin designates the orphan processo to Usuário A
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;
select public.designar_processo('40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003');
reset role;
select results_eq(
  $$select responsavel_id from public.processos where id = '40000000-0000-0000-0000-000000000001'$$,
  array['00000000-0000-0000-0000-000000000003'::uuid],
  'Admin designation sets responsavel_id'
);
select results_eq(
  $$select count(*)::int from public.designacoes where perfil_id = '00000000-0000-0000-0000-000000000003'$$,
  array[1],
  'Designation is logged in designacoes'
);
select ok(
  (select ultimo_recebimento_em from public.perfis where id = '00000000-0000-0000-0000-000000000003') is not null,
  'ultimo_recebimento_em is updated for the recipient'
);

-- Usuário A2 cannot transfer the processo directly to themselves (it is not orphan)
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000005', true);
set local role authenticated;
select throws_ok(
  $$select public.designar_processo('40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000005')$$,
  'P0001',
  null,
  'A non-admin cannot take a processo that is not orphan'
);
reset role;

-- Usuário A returns their own processo to orphan
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
set local role authenticated;
select public.designar_processo('40000000-0000-0000-0000-000000000001', null);
reset role;
select results_eq(
  $$select responsavel_id from public.processos where id = '40000000-0000-0000-0000-000000000001'$$,
  array[null::uuid],
  'Usuário can return their own processo to orphan'
);

-- Usuário A2 self-assigns the now-orphan processo
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000005', true);
set local role authenticated;
select public.designar_processo('40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000005');
reset role;
select results_eq(
  $$select responsavel_id from public.processos where id = '40000000-0000-0000-0000-000000000001'$$,
  array['00000000-0000-0000-0000-000000000005'::uuid],
  'Usuário can self-assign an orphan processo'
);

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
```

- [ ] **Step 2: Run test, verify it fails** — `node scripts/run_sql.mjs supabase/tests/database/070_designacoes.sql`

- [ ] **Step 3: Write the migration**

Run: `npx supabase migration new criar_designacoes_e_funcao`

```sql
create table public.designacoes (
  id uuid primary key default gen_random_uuid(),
  processo_id uuid not null references public.processos (id) on delete cascade,
  tipo_processo_id uuid not null references public.tipos_processo (id),
  perfil_id uuid not null references public.perfis (id),
  designado_por uuid not null references public.perfis (id),
  criado_em timestamptz not null default now()
);

create index designacoes_perfil_id_idx on public.designacoes (perfil_id);
create index designacoes_processo_id_idx on public.designacoes (processo_id);

alter table public.designacoes enable row level security;
alter table public.designacoes force row level security;

create policy "designacoes_select" on public.designacoes
for select using (
  exists (
    select 1 from public.perfis me
    where me.id = auth.uid() and me.organizacao_id = (
      select p.organizacao_id from public.processos p where p.id = designacoes.processo_id
    )
  )
);
-- No insert/update/delete policy for designacoes: rows are only ever written
-- by designar_processo(), which runs SECURITY DEFINER and bypasses RLS.

create or replace function public.designar_processo(
  p_processo_id uuid,
  p_novo_responsavel_id uuid
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_processo record;
  v_papel public.papel_usuario;
  v_org_caller uuid;
  v_org_alvo uuid;
begin
  select organizacao_id, papel into v_org_caller, v_papel
  from public.perfis where id = auth.uid();

  if v_papel is null then
    raise exception 'Perfil não encontrado para o usuário autenticado.';
  end if;

  select * into v_processo from public.processos where id = p_processo_id for update;
  if not found then
    raise exception 'Processo não encontrado.';
  end if;

  if v_processo.organizacao_id is distinct from v_org_caller then
    raise exception 'Processo não pertence à sua organização.';
  end if;

  if p_novo_responsavel_id is not null then
    select organizacao_id into v_org_alvo from public.perfis where id = p_novo_responsavel_id;
    if v_org_alvo is distinct from v_org_caller then
      raise exception 'A pessoa designada precisa ser da mesma organização.';
    end if;
  end if;

  if v_papel <> 'admin' then
    if not (
      (v_processo.responsavel_id is null and p_novo_responsavel_id = auth.uid())
      or (v_processo.responsavel_id = auth.uid() and p_novo_responsavel_id is null)
    ) then
      raise exception 'Você só pode autoatribuir um processo órfão a si mesmo ou devolver o seu.';
    end if;
  end if;

  update public.processos
  set responsavel_id = p_novo_responsavel_id,
      designado_em = case when p_novo_responsavel_id is null then null else now() end,
      designado_por = auth.uid(),
      atualizado_em = now()
  where id = p_processo_id;

  if p_novo_responsavel_id is not null then
    insert into public.designacoes (processo_id, tipo_processo_id, perfil_id, designado_por)
    values (p_processo_id, v_processo.tipo_processo_id, p_novo_responsavel_id, auth.uid());

    update public.perfis
    set ultimo_recebimento_em = now()
    where id = p_novo_responsavel_id;
  end if;
end;
$$;

revoke all on function public.designar_processo(uuid, uuid) from public;
grant execute on function public.designar_processo(uuid, uuid) to authenticated;
```

- [ ] **Step 4: Apply the migration and re-run** — `node scripts/run_sql.mjs supabase/migrations/<timestamp>_criar_designacoes_e_funcao.sql` then `node scripts/run_sql.mjs supabase/tests/database/070_designacoes.sql` → `RESULT: PASS`.

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations supabase/tests
git commit -m "feat: designacoes table and designar_processo RPC"
```

---

## Task 10: Bootstrap script for the first `super_admin`

**Files:**
- Create: `scripts/bootstrap_super_admin.mjs`

**Interfaces:**
- Consumes: the cloud project's service role key, loaded from `supabase/.env.local` (same convention as `scripts/run_sql.mjs` from Task 1).
- Produces: one real, login-capable `super_admin` account in the cloud project — Task 11/12's Edge Function tests need a real JWT for a super_admin caller, which can only come from a real GoTrue user (the pgTAP fixtures in earlier tasks insert directly into `auth.users` for RLS testing only, and cannot log in for real).

- [ ] **Step 1: Install the Supabase JS client**

```bash
npm install @supabase/supabase-js
```

- [ ] **Step 2: Write the script**

Create `scripts/bootstrap_super_admin.mjs`. It reuses the same `supabase/.env.local` loader convention as `scripts/run_sql.mjs` (Task 1), and — per that script's documented fix for this machine — never calls `process.exit()` after an `await`, only `process.exitCode` with an explicit `return`:

```javascript
import { createClient } from '@supabase/supabase-js';
import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(scriptDir, '..');

function loadEnvLocal() {
  const envPath = join(repoRoot, 'supabase', '.env.local');
  if (!existsSync(envPath)) return;
  for (const line of readFileSync(envPath, 'utf8').split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq === -1) continue;
    const key = trimmed.slice(0, eq).trim();
    const value = trimmed.slice(eq + 1).trim();
    if (!(key in process.env)) process.env[key] = value;
  }
}

async function main() {
  loadEnvLocal();

  const SUPABASE_URL = process.env.SUPABASE_URL;
  const SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const EMAIL = process.argv[2];
  const PASSWORD = process.argv[3];
  const NOME = process.argv[4] ?? 'Super Admin';

  if (!SUPABASE_URL || !SERVICE_ROLE_KEY) {
    console.error('Missing SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY — check supabase/.env.local.');
    process.exitCode = 1;
    return;
  }
  if (!EMAIL || !PASSWORD) {
    console.error('Usage: node scripts/bootstrap_super_admin.mjs <email> <senha> [nome]');
    process.exitCode = 1;
    return;
  }

  const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
    auth: { autoRefreshToken: false, persistSession: false }
  });

  const { data: userData, error: userError } = await admin.auth.admin.createUser({
    email: EMAIL,
    password: PASSWORD,
    email_confirm: true
  });
  if (userError) {
    console.error('Failed to create auth user:', userError.message);
    process.exitCode = 1;
    return;
  }

  const { error: perfilError } = await admin.from('perfis').insert({
    id: userData.user.id,
    organizacao_id: null,
    papel: 'super_admin',
    nome: NOME
  });
  if (perfilError) {
    console.error('Failed to create perfil row:', perfilError.message);
    process.exitCode = 1;
    return;
  }

  console.log(`super_admin created: ${EMAIL} (id: ${userData.user.id})`);
}

await main();
```

- [ ] **Step 3: Run it against the cloud project**

Run (credentials come from `supabase/.env.local`, loaded automatically):

```bash
node scripts/bootstrap_super_admin.mjs super@local.test senha-teste-123 "Super Admin Local"
```

Expected: prints `super_admin created: super@local.test (id: <uuid>)`.

- [ ] **Step 4: Commit**

```bash
git add scripts/bootstrap_super_admin.mjs package.json package-lock.json
git commit -m "chore: add local super_admin bootstrap script"
```

---

## Task 11: Edge Function `criar-organizacao`

**Files:**
- Create: `supabase/functions/criar-organizacao/index.ts`
- Create: `supabase/functions/_shared/cors.ts`
- Create: `supabase/tests/functions/test_criar_organizacao.sh`

**Interfaces:**
- Consumes: the bootstrap super_admin account from Task 10.
- Produces: an Edge Function deployed to the cloud project, reachable at `<SUPABASE_URL>/functions/v1/criar-organizacao`, accepting `POST { nome_organizacao, admin_nome, admin_email, admin_senha }` with an `Authorization: Bearer <super_admin JWT>` header, returning `{ organizacao_id, admin_id }` on success.

- [ ] **Step 1: Write the shared CORS helper**

Create `supabase/functions/_shared/cors.ts`:

```typescript
export const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};
```

- [ ] **Step 2: Write the Edge Function**

Create `supabase/functions/criar-organizacao/index.ts`:

```typescript
import { createClient } from 'jsr:@supabase/supabase-js@2';
import { corsHeaders } from '../_shared/cors.ts';

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const authHeader = req.headers.get('Authorization');
    if (!authHeader) {
      return new Response(JSON.stringify({ error: 'Missing Authorization header' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const anonKey = Deno.env.get('SUPABASE_ANON_KEY')!;
    const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;

    const callerClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authHeader } },
    });
    const { data: userResult, error: userError } = await callerClient.auth.getUser();
    if (userError || !userResult.user) {
      return new Response(JSON.stringify({ error: 'Invalid session' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const adminClient = createClient(supabaseUrl, serviceRoleKey);

    const { data: perfilCaller, error: perfilError } = await adminClient
      .from('perfis')
      .select('papel')
      .eq('id', userResult.user.id)
      .single();
    if (perfilError || perfilCaller?.papel !== 'super_admin') {
      return new Response(JSON.stringify({ error: 'Apenas super_admin pode criar organizações' }), {
        status: 403,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const body = await req.json();
    const { nome_organizacao, admin_nome, admin_email, admin_senha } = body;
    if (!nome_organizacao || !admin_nome || !admin_email || !admin_senha) {
      return new Response(JSON.stringify({ error: 'Campos obrigatórios ausentes' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const { data: org, error: orgError } = await adminClient
      .from('organizacoes')
      .insert({ nome: nome_organizacao })
      .select('id')
      .single();
    if (orgError) {
      return new Response(JSON.stringify({ error: orgError.message }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const { data: novoUsuario, error: novoUsuarioError } = await adminClient.auth.admin.createUser({
      email: admin_email,
      password: admin_senha,
      email_confirm: true,
    });
    if (novoUsuarioError) {
      // Compensating cleanup: the org has no admin without this user, don't leave it orphaned.
      await adminClient.from('organizacoes').delete().eq('id', org.id);
      return new Response(JSON.stringify({ error: novoUsuarioError.message }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const { error: perfilInsertError } = await adminClient.from('perfis').insert({
      id: novoUsuario.user.id,
      organizacao_id: org.id,
      papel: 'admin',
      nome: admin_nome,
    });
    if (perfilInsertError) {
      // Compensating cleanup: an auth user with no perfil is a ghost account
      // that can log in but can't do anything — and the org still has no admin.
      await adminClient.auth.admin.deleteUser(novoUsuario.user.id);
      await adminClient.from('organizacoes').delete().eq('id', org.id);
      return new Response(JSON.stringify({ error: perfilInsertError.message }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    return new Response(JSON.stringify({ organizacao_id: org.id, admin_id: novoUsuario.user.id }), {
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

- [ ] **Step 3: Write the integration test script**

Create `supabase/tests/functions/test_criar_organizacao.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

SUPER_ADMIN_EMAIL="${1:?usage: test_criar_organizacao.sh <super_admin_email> <super_admin_senha> <anon_key> <api_url>}"
SUPER_ADMIN_PASSWORD="${2:?}"
ANON_KEY="${3:?}"
API_URL="${4:?}"

JWT=$(curl -s -X POST "$API_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $ANON_KEY" -H "Content-Type: application/json" \
  -d "{\"email\":\"$SUPER_ADMIN_EMAIL\",\"password\":\"$SUPER_ADMIN_PASSWORD\"}" \
  | node -e "process.stdin.once('data', d => console.log(JSON.parse(d).access_token))")

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/functions/v1/criar-organizacao" \
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" \
  -d '{"nome_organizacao":"Setor de Teste","admin_nome":"Admin Teste","admin_email":"admin.teste@local.test","admin_senha":"senha-teste-123"}')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

echo "HTTP $HTTP_CODE: $BODY"
if [ "$HTTP_CODE" != "200" ]; then
  echo "FAIL: expected HTTP 200"
  exit 1
fi
echo "$BODY" | grep -q "organizacao_id" || { echo "FAIL: response missing organizacao_id"; exit 1; }
echo "PASS"
```

- [ ] **Step 4: Deploy the function and run the test**

Deploy over HTTPS (no Docker-based bundler needed — this is the same `--use-api` path verified working on this network):

```bash
npx supabase functions deploy criar-organizacao --use-api
```

Then, with `SUPABASE_URL` and `SUPABASE_ANON_KEY` from `supabase/.env.local`:

```bash
chmod +x supabase/tests/functions/test_criar_organizacao.sh
./supabase/tests/functions/test_criar_organizacao.sh super@local.test senha-teste-123 <SUPABASE_ANON_KEY value> <SUPABASE_URL value>
```
Expected: `PASS`.

- [ ] **Step 5: Commit**

```bash
git add supabase/functions supabase/tests/functions
git commit -m "feat: criar-organizacao Edge Function"
```

(`supabase/.env.local` is already gitignored from Task 1 — confirm it does not appear in `git status` before committing.)

---

## Task 12: Edge Function `criar-conta`

**Files:**
- Create: `supabase/functions/criar-conta/index.ts`
- Create: `supabase/tests/functions/test_criar_conta.sh`

**Interfaces:**
- Consumes: the admin account created by Task 11's `criar-organizacao` run.
- Produces: `POST /functions/v1/criar-conta { nome, email, senha, papel }` (papel: `'admin'|'usuario'`), authenticated as an `admin`, creating a new account scoped to the caller's own `organizacao_id`.

- [ ] **Step 1: Write the Edge Function**

Create `supabase/functions/criar-conta/index.ts`:

```typescript
import { createClient } from 'jsr:@supabase/supabase-js@2';
import { corsHeaders } from '../_shared/cors.ts';

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const authHeader = req.headers.get('Authorization');
    if (!authHeader) {
      return new Response(JSON.stringify({ error: 'Missing Authorization header' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const anonKey = Deno.env.get('SUPABASE_ANON_KEY')!;
    const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;

    const callerClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authHeader } },
    });
    const { data: userResult, error: userError } = await callerClient.auth.getUser();
    if (userError || !userResult.user) {
      return new Response(JSON.stringify({ error: 'Invalid session' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const adminClient = createClient(supabaseUrl, serviceRoleKey);

    const { data: perfilCaller, error: perfilError } = await adminClient
      .from('perfis')
      .select('papel, organizacao_id')
      .eq('id', userResult.user.id)
      .single();
    if (perfilError || perfilCaller?.papel !== 'admin' || !perfilCaller.organizacao_id) {
      return new Response(JSON.stringify({ error: 'Apenas admin pode criar contas' }), {
        status: 403,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const body = await req.json();
    const { nome, email, senha, papel } = body;
    if (!nome || !email || !senha || !['admin', 'usuario'].includes(papel)) {
      return new Response(JSON.stringify({ error: 'Campos obrigatórios ausentes ou papel inválido' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const { data: novoUsuario, error: novoUsuarioError } = await adminClient.auth.admin.createUser({
      email,
      password: senha,
      email_confirm: true,
    });
    if (novoUsuarioError) {
      return new Response(JSON.stringify({ error: novoUsuarioError.message }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const { error: perfilInsertError } = await adminClient.from('perfis').insert({
      id: novoUsuario.user.id,
      organizacao_id: perfilCaller.organizacao_id,
      papel,
      nome,
    });
    if (perfilInsertError) {
      // Compensating cleanup: an auth user with no perfil is a ghost account
      // that can log in but can't do anything.
      await adminClient.auth.admin.deleteUser(novoUsuario.user.id);
      return new Response(JSON.stringify({ error: perfilInsertError.message }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    return new Response(JSON.stringify({ perfil_id: novoUsuario.user.id }), {
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

- [ ] **Step 2: Write the integration test script**

Create `supabase/tests/functions/test_criar_conta.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

ADMIN_EMAIL="${1:?usage: test_criar_conta.sh <admin_email> <admin_senha> <anon_key> <api_url>}"
ADMIN_PASSWORD="${2:?}"
ANON_KEY="${3:?}"
API_URL="${4:?}"

JWT=$(curl -s -X POST "$API_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $ANON_KEY" -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
  | node -e "process.stdin.once('data', d => console.log(JSON.parse(d).access_token))")

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/functions/v1/criar-conta" \
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" \
  -d '{"nome":"Usuário Teste","email":"usuario.teste@local.test","senha":"senha-teste-123","papel":"usuario"}')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

echo "HTTP $HTTP_CODE: $BODY"
if [ "$HTTP_CODE" != "200" ]; then
  echo "FAIL: expected HTTP 200"
  exit 1
fi
echo "$BODY" | grep -q "perfil_id" || { echo "FAIL: response missing perfil_id"; exit 1; }
echo "PASS"
```

- [ ] **Step 3: Deploy the function and run the test**

```bash
npx supabase functions deploy criar-conta --use-api
```

Then, with `SUPABASE_URL` and `SUPABASE_ANON_KEY` from `supabase/.env.local`:

```bash
chmod +x supabase/tests/functions/test_criar_conta.sh
./supabase/tests/functions/test_criar_conta.sh admin.teste@local.test senha-teste-123 <SUPABASE_ANON_KEY value> <SUPABASE_URL value>
```
Expected: `PASS`.

- [ ] **Step 4: Commit**

```bash
git add supabase/functions supabase/tests/functions
git commit -m "feat: criar-conta Edge Function"
```

---

## Task 13: Full regression pass against the cloud project

**Files:**
- Create: `supabase/README.md`

There is no `db reset` in this plan's cloud workflow (Global Constraints) — every migration from Tasks 2–9 is already applied, permanently, to the linked project. "Full regression" here means: re-run every existing test file against the current live schema (each wraps its assertions in `begin; ... rollback;`, so re-running is safe and non-destructive — see Task 2), and re-verify both deployed Edge Functions without recreating accounts that already exist from Tasks 10–12 (recreating them would fail on a duplicate email, since nothing was reset).

- [ ] **Step 1: Re-run the full pgTAP suite**

Run each test file in order and confirm every one prints `RESULT: PASS`:

```bash
node scripts/run_sql.mjs supabase/tests/database/000_smoke_test.sql
node scripts/run_sql.mjs supabase/tests/database/010_organizacoes_perfis.sql
node scripts/run_sql.mjs supabase/tests/database/020_device_tokens.sql
node scripts/run_sql.mjs supabase/tests/database/030_tipos_processo_fases.sql
node scripts/run_sql.mjs supabase/tests/database/040_processos.sql
node scripts/run_sql.mjs supabase/tests/database/050_itens_historico.sql
node scripts/run_sql.mjs supabase/tests/database/060_diligencias_observacoes.sql
node scripts/run_sql.mjs supabase/tests/database/070_designacoes.sql
```
Expected: all 8 print `RESULT: PASS` and exit 0. If any fails, treat it as a real regression — a later task's migration broke an earlier task's RLS/schema assumption — and fix the schema before continuing, not the test.

- [ ] **Step 2: Smoke-test both deployed Edge Functions**

Task 11/12's test scripts (`test_criar_organizacao.sh`, `test_criar_conta.sh`) hardcode fixed admin/user emails that already exist in the cloud project from when those tasks first ran them successfully. Re-running the same scripts now is still a valid smoke test — it confirms the deployed functions are still reachable and still enforcing the same checks — but expect a duplicate-email failure this time instead of the original `PASS`, since nothing was reset in between:

```bash
./supabase/tests/functions/test_criar_organizacao.sh super@local.test senha-teste-123 <SUPABASE_ANON_KEY value> <SUPABASE_URL value>
./supabase/tests/functions/test_criar_conta.sh admin.teste@local.test senha-teste-123 <SUPABASE_ANON_KEY value> <SUPABASE_URL value>
```
Expected: both calls reach the function and return a clean, well-formed JSON error whose message indicates the email already exists (proving the function is up, authenticating the caller, and enforcing its checks correctly) — a `PASS` on either script here would mean its hardcoded email was somehow available again, e.g. after a manual cleanup, and is equally fine. The actual regression signal is a timeout, a 5xx, or an unreachable-host error — any of those means investigate before continuing, not just note it and move on.

- [ ] **Step 3: Record the cloud connection details for Plan 2**

Create `supabase/README.md` (values from `supabase/.env.local` — do not paste real secret values into this committed file, only the variable names and the commands that read them):

```markdown
# Supabase backend (cloud project)

This backend targets a real cloud Supabase project — there is no local Docker
stack. All commands read credentials from `supabase/.env.local` (gitignored,
never committed).

Run a migration or test file: `node scripts/run_sql.mjs <path-to-sql-file>`
Bootstrap a super_admin account: `node scripts/bootstrap_super_admin.mjs <email> <senha> [nome]`
Deploy an Edge Function: `npx supabase functions deploy <name> --use-api`

Plan 2 (Android data layer) connects the app to the `SUPABASE_URL` and
`SUPABASE_ANON_KEY` values in `supabase/.env.local`.
```

- [ ] **Step 4: Commit**

```bash
git add supabase/README.md
git commit -m "docs: cloud Supabase backend usage notes"
```
