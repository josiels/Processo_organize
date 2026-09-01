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
