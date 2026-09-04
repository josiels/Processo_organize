-- Ajuste #1 (sessão 2026-09-04): devolver um processo (abrir mão de ser
-- responsável) passa a exigir justificativa, registrada no histórico de
-- designações — hoje `designacoes` só registrava quando alguém É designado,
-- nunca quando devolve. Assumir um processo órfão continua sem exigir nada.

alter table public.designacoes add column motivo text;

create or replace function public.designar_processo(
  p_processo_id uuid,
  p_novo_responsavel_id uuid,
  p_motivo text default null
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

  -- Devolução (novo responsável nulo, havia responsável antes): justificativa
  -- obrigatória no servidor, não só na UI — mesma barreira real de sempre.
  if p_novo_responsavel_id is null and v_processo.responsavel_id is not null then
    if p_motivo is null or btrim(p_motivo) = '' then
      raise exception 'Informe o motivo da devolução.';
    end if;

    insert into public.designacoes (processo_id, tipo_processo_id, perfil_id, designado_por, motivo)
    values (p_processo_id, v_processo.tipo_processo_id, v_processo.responsavel_id, auth.uid(), p_motivo);
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

revoke all on function public.designar_processo(uuid, uuid, text) from public;
grant execute on function public.designar_processo(uuid, uuid, text) to authenticated;

-- A assinatura antiga (2 parâmetros) não é mais chamada por nenhum cliente
-- depois deste deploy do app — remove pra não deixar duas versões da RPC
-- coexistindo com comportamentos diferentes (a antiga não exige motivo).
drop function if exists public.designar_processo(uuid, uuid);
