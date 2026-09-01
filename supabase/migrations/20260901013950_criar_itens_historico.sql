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
