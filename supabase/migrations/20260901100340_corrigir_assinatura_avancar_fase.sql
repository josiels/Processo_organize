-- Final whole-branch review, Finding 1: avancar_fase()'s original signature
-- hardcoded responsavel_id => processos.responsavel_id (the processo-wide
-- owner) and notificar_prazo => false on the new processo_fase_historico row,
-- with no way for the caller to override either.
--
-- Spec 2.3 (fundação de dados) is explicit that processo_fase_historico.
-- responsavel_id is a DIFFERENT concept from processos.responsavel_id: it is
-- "quem executou esta passagem específica pela fase", not the current
-- processo-wide designation. Spec 2B section 4.4 plans to rename the existing
-- Kotlin atualizarResponsavel(pessoaId) to atualizarExecutor(perfilId),
-- explicitly keeping it editable. The existing Kotlin
-- HistoricoFaseRepository.mudarFase(...) already accepts responsavelId and
-- notificarPrazo as caller-supplied parameters (see AvancarFaseScreen.kt's
-- DropdownField "Responsável" and AppToggle "Notificar sobre prazo desta
-- fase?"). The RPC needs equivalent inputs.
--
-- CREATE OR REPLACE FUNCTION cannot change a function's argument list, and
-- nothing in the codebase calls avancar_fase() yet, so it is safe to drop and
-- recreate with a corrected signature.
drop function if exists public.avancar_fase(uuid, uuid, text, date, text);

create function public.avancar_fase(
  p_processo_id uuid,
  p_fase_destino_id uuid,
  p_executor_id uuid default null,
  p_observacao_inicial text default '',
  p_prazo_limite date default null,
  p_notificar_prazo boolean default false,
  p_motivo_retorno text default null
) returns uuid
language plpgsql
as $$
declare
  v_processo record;
  v_nova_entrada_id uuid;
begin
  -- This function is SECURITY INVOKER (the default), so this SELECT ... FOR
  -- UPDATE runs under the caller's own role and is subject to RLS. Per
  -- Postgres's row-security docs, SELECT ... FOR UPDATE/SHARE enforces not
  -- only the table's SELECT policy but also the USING expression of its
  -- UPDATE policy (processos_update: organizacao_id = auth_organizacao_id()
  -- and admin/owner/orphan) before a row is returned. A caller who is
  -- authorized to SEE the processo (same org) but not to WRITE it (not admin,
  -- not the owner, and the processo is not orphan) therefore never gets a row
  -- here at all — the lock attempt silently returns nothing, hitting the
  -- branch below — rather than failing later at a WITH CHECK violation
  -- (42501) on the INSERT/UPDATE. Same for a caller in a different
  -- organization entirely: processos_select's own org-scoping already
  -- excludes the row. Both cases surface as this single message.
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
    p_processo_id, p_fase_destino_id, coalesce(p_executor_id, v_processo.responsavel_id), current_date, p_prazo_limite, p_observacao_inicial, p_motivo_retorno, p_notificar_prazo
  ) returning id into v_nova_entrada_id;

  update public.processos
  set fase_atual_id = p_fase_destino_id,
      atualizado_em = now()
  where id = p_processo_id;

  return v_nova_entrada_id;
end;
$$;

-- p_executor_id defaults to null, in which case the function falls back to
-- the processo's current responsavel_id (preserves prior behavior for a
-- caller that doesn't pass it — including the orphan-processo case, where
-- there is no current responsável to fall back to, so
-- coalesce(null, null) correctly stays null, matching what the field means
-- for an unclaimed processo).
--
-- Returns uuid (the new processo_fase_historico.id) instead of void, since a
-- client will often need it immediately after (e.g. to attach a diligência)
-- — cheap to do now, expensive once a client depends on void.
revoke all on function public.avancar_fase(uuid, uuid, uuid, text, date, boolean, text) from public;
grant execute on function public.avancar_fase(uuid, uuid, uuid, text, date, boolean, text) to authenticated;
