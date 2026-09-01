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
