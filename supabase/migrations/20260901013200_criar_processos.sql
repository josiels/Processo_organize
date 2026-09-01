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
