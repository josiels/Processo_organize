create or replace function public.avancar_fase(
  p_processo_id uuid,
  p_fase_destino_id uuid,
  p_observacao_inicial text default '',
  p_prazo_limite date default null,
  p_motivo_retorno text default null
) returns void
language plpgsql
as $$
declare
  v_processo record;
begin
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
    p_processo_id, p_fase_destino_id, v_processo.responsavel_id, current_date, p_prazo_limite, p_observacao_inicial, p_motivo_retorno, false
  );

  update public.processos
  set fase_atual_id = p_fase_destino_id,
      atualizado_em = now()
  where id = p_processo_id;
end;
$$;

revoke all on function public.avancar_fase(uuid, uuid, text, date, text) from public;
grant execute on function public.avancar_fase(uuid, uuid, text, date, text) to authenticated;
