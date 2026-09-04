-- Ajuste #1 desta sessão (notificações): designação e devolução de processo
-- nunca disparavam push nenhum — nenhum dos 4 gatilhos existentes cobre
-- esses eventos. Usa a própria tabela designacoes (uma linha por evento,
-- já grava tanto designação quanto devolução desde a migration de hoje que
-- adicionou justificativa de devolução) como base: notificado_em marca cada
-- linha como já processada, então cada evento só notifica uma vez — sem o
-- problema de spam que os outros 4 gatilhos têm (achado parqueado, não
-- corrigido aqui).

alter table public.designacoes add column notificado_em timestamptz;

-- designacoes.motivo é NULL numa designação (ASSUMIR/DESIGNAR) e
-- preenchido numa devolução (DEVOLVER) — ver designar_processo().
create or replace function public.perfis_a_notificar_designacao()
returns table (perfil_id uuid, processo_id uuid, numero text, objeto text, designacao_id uuid)
language sql
stable
as $$
  select d.perfil_id, pr.id as processo_id, pr.numero, pr.objeto, d.id as designacao_id
  from public.designacoes d
  join public.processos pr on pr.id = d.processo_id
  join public.perfis p on p.id = d.perfil_id and p.ativo
  where d.motivo is null
    and d.notificado_em is null;
$$;

create or replace function public.perfis_a_notificar_devolucao()
returns table (perfil_id uuid, processo_id uuid, numero text, objeto text, motivo text, devolvido_por_nome text, designacao_id uuid)
language sql
stable
as $$
  select admin.id as perfil_id, pr.id as processo_id, pr.numero, pr.objeto, d.motivo,
    coalesce(autor.nome, 'Alguém') as devolvido_por_nome, d.id as designacao_id
  from public.designacoes d
  join public.processos pr on pr.id = d.processo_id
  join public.perfis admin on admin.organizacao_id = pr.organizacao_id and admin.papel = 'admin' and admin.ativo
  left join public.perfis autor on autor.id = d.perfil_id
  where d.motivo is not null
    and d.notificado_em is null;
$$;

revoke all on function public.perfis_a_notificar_designacao() from public;
revoke all on function public.perfis_a_notificar_devolucao() from public;
grant execute on function public.perfis_a_notificar_designacao() to service_role;
grant execute on function public.perfis_a_notificar_devolucao() to service_role;
