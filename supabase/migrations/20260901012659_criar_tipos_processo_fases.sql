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
