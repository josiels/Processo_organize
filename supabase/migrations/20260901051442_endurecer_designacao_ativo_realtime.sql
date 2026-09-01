-- Cross-task hardening pass from the whole-branch final review.
-- Findings A/B (designation is only writable through designar_processo, and
-- orphan processos are writable by any usuario of the org), C (diligencias
-- authorship cannot be forged), D (perfis.ativo is actually enforced) and
-- E (Realtime publication for Plan 2's Room-as-cache design).

-- ---------------------------------------------------------------------------
-- Finding A: the three designation columns on public.processos may only ever
-- be written by public.designar_processo(), which also writes the designacoes
-- audit row and bumps perfis.ultimo_recebimento_em (both required by spec
-- section 4's Fila de Distribuição). Before this trigger, the processos_update
-- policy let a usuario self-assign — or an admin reassign — with a raw UPDATE,
-- silently skipping the audit row and the counter.
--
-- Mirrors public.perfis_proteger_campos(): designar_processo() is SECURITY
-- DEFINER and owned by postgres, so current_user resolves to 'postgres' inside
-- it and it passes straight through the bypass below. processos_update's own
-- policy text is deliberately left untouched — it still governs which ROWS a
-- usuario may UPDATE at all (admin, owner, or orphan) for legitimate edits to
-- objeto/descricao/status_geral; this trigger only protects the three columns.
create or replace function public.processos_proteger_designacao() returns trigger
language plpgsql as $$
begin
  if current_user in ('postgres', 'service_role') then
    return new;
  end if;
  if new.responsavel_id is distinct from old.responsavel_id
     or new.designado_em is distinct from old.designado_em
     or new.designado_por is distinct from old.designado_por then
    raise exception 'Designação só pode ser alterada por designar_processo().';
  end if;
  return new;
end;
$$;

drop trigger if exists processos_proteger_designacao_trigger on public.processos;
create trigger processos_proteger_designacao_trigger
before update on public.processos
for each row execute function public.processos_proteger_designacao();

-- ---------------------------------------------------------------------------
-- Finding B: spec section 2 says a usuario "só avança fase / edita observação /
-- registra diligência nos processos que são dele ou que estão órfãos". The four
-- write policies below were missing the orphan disjunct that processos_update
-- already had, so a usuario could not act on an orphan processo's items,
-- history, diligências or observações before formally claiming it. Altered in
-- place (never dropped and recreated) so there is no window with no policy.

alter policy "itens_acesso" on public.itens
using (
  exists (
    select 1 from public.processos p
    where p.id = itens.processo_id
      and p.organizacao_id = public.auth_organizacao_id()
      and (public.auth_papel() = 'admin' or p.responsavel_id = auth.uid() or p.responsavel_id is null)
  )
)
with check (
  exists (
    select 1 from public.processos p
    where p.id = itens.processo_id
      and p.organizacao_id = public.auth_organizacao_id()
      and (public.auth_papel() = 'admin' or p.responsavel_id = auth.uid() or p.responsavel_id is null)
  )
);

alter policy "processo_fase_historico_acesso" on public.processo_fase_historico
using (
  exists (
    select 1 from public.processos p
    where p.id = processo_fase_historico.processo_id
      and p.organizacao_id = public.auth_organizacao_id()
      and (public.auth_papel() = 'admin' or p.responsavel_id = auth.uid() or p.responsavel_id is null)
  )
)
with check (
  exists (
    select 1 from public.processos p
    where p.id = processo_fase_historico.processo_id
      and p.organizacao_id = public.auth_organizacao_id()
      and (public.auth_papel() = 'admin' or p.responsavel_id = auth.uid() or p.responsavel_id is null)
  )
);

alter policy "observacao_versoes_acesso" on public.observacao_versoes
using (
  exists (
    select 1 from public.processo_fase_historico h
    join public.processos p on p.id = h.processo_id
    where h.id = observacao_versoes.processo_fase_historico_id
      and p.organizacao_id = public.auth_organizacao_id()
      and (public.auth_papel() = 'admin' or p.responsavel_id = auth.uid() or p.responsavel_id is null)
  )
)
with check (
  exists (
    select 1 from public.processo_fase_historico h
    join public.processos p on p.id = h.processo_id
    where h.id = observacao_versoes.processo_fase_historico_id
      and p.organizacao_id = public.auth_organizacao_id()
      and (public.auth_papel() = 'admin' or p.responsavel_id = auth.uid() or p.responsavel_id is null)
  )
);

-- ---------------------------------------------------------------------------
-- Finding C: diligencias.autor_id was client-supplied and never validated, so
-- any caller who could write to a processo could attribute the entry to any
-- perfis.id at all — including a perfil in a different organization, since the
-- FK is global. Default it to the caller and require it to BE the caller. There
-- is no legitimate "on behalf of" case: an admin writing a diligência is still
-- the one who wrote it. This alter also carries Finding B's orphan disjunct.
alter table public.diligencias alter column autor_id set default auth.uid();

alter policy "diligencias_acesso" on public.diligencias
using (
  exists (
    select 1 from public.processo_fase_historico h
    join public.processos p on p.id = h.processo_id
    where h.id = diligencias.processo_fase_historico_id
      and p.organizacao_id = public.auth_organizacao_id()
      and (public.auth_papel() = 'admin' or p.responsavel_id = auth.uid() or p.responsavel_id is null)
  )
)
with check (
  diligencias.autor_id = auth.uid()
  and exists (
    select 1 from public.processo_fase_historico h
    join public.processos p on p.id = h.processo_id
    where h.id = diligencias.processo_fase_historico_id
      and p.organizacao_id = public.auth_organizacao_id()
      and (public.auth_papel() = 'admin' or p.responsavel_id = auth.uid() or p.responsavel_id is null)
  )
);

-- ---------------------------------------------------------------------------
-- Finding D: perfis.ativo existed but was enforced nowhere, so a deactivated
-- account kept full access. Every RLS policy in the system keys off one or both
-- of these two helpers, so filtering on `ativo` here makes a deactivated user
-- look like a user with no perfil at all — no organization, no papel, hence no
-- rows anywhere — without touching a single policy.
create or replace function public.auth_organizacao_id() returns uuid
language sql stable security definer set search_path = public as $$
  select organizacao_id from public.perfis where id = auth.uid() and ativo;
$$;

create or replace function public.auth_papel() returns public.papel_usuario
language sql stable security definer set search_path = public as $$
  select papel from public.perfis where id = auth.uid() and ativo;
$$;

-- designar_processo queries perfis directly (SECURITY DEFINER, bypassing RLS),
-- so it does not inherit the helpers' new `ativo` filter — add it explicitly to
-- both the caller lookup (a deactivated caller falls into the existing
-- "Perfil não encontrado" branch) and the target lookup (a deactivated target is
-- treated exactly like a nonexistent one: v_org_alvo comes back null and the
-- existing same-organization check raises).
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
  from public.perfis where id = auth.uid() and ativo;

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
    select organizacao_id into v_org_alvo from public.perfis
    where id = p_novo_responsavel_id and ativo;
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

-- ---------------------------------------------------------------------------
-- Finding E: spec section 10 has Plan 2's Android Room database acting as a
-- read cache "atualizado via Supabase Realtime (assinatura nas tabelas da
-- própria organização, filtrado por RLS)". No table had been added to the
-- supabase_realtime publication, so no subscription would ever receive data.
-- device_tokens is deliberately excluded: it is per-device FCM registration
-- state, not cached business data, and has no read-transparency policy.
alter publication supabase_realtime add table
  public.organizacoes,
  public.perfis,
  public.tipos_processo,
  public.fases,
  public.processos,
  public.itens,
  public.processo_fase_historico,
  public.diligencias,
  public.observacao_versoes,
  public.designacoes;
