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
