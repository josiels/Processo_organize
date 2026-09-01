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
