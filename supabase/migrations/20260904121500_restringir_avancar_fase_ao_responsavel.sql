-- Ajuste #2 (sessão 2026-09-04): avançar/retornar fase deixa de seguir a
-- regra "admin, dono, ou órfão" — um processo órfão (sem responsável, ex:
-- logo após alguém devolver) não pode mais ter a fase avançada por ninguém
-- não-admin. Quem quiser avançar precisa primeiro "Assumir o processo".
--
-- Deliberadamente só nesta função, não na policy processos_update: essa
-- policy também governa edição geral de campos do processo e é usada por
-- outras tabelas (itens, diligências) onde o comportamento "órfão = qualquer
-- um edita" continua sendo o desejado.
create or replace function public.avancar_fase(
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
  select * into v_processo from public.processos where id = p_processo_id for update;
  if not found then
    raise exception 'Processo não encontrado.';
  end if;

  if public.auth_papel() <> 'admin' and v_processo.responsavel_id is distinct from auth.uid() then
    raise exception 'Somente o responsável atual pelo processo (ou um admin) pode avançar ou retornar a fase.';
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
