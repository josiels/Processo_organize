-- Funções puras de elegibilidade para notificação (spec do Plano 2D, seções
-- 1 e 3) — cada uma responde "quem deveria ser notificado agora, e por quê",
-- sem enviar nada. A Edge Function `notificar-processos` (Task 3) chama as
-- quatro via RPC (com a service role key, que já ignora RLS) e faz o envio.
-- Testáveis via pgTAP sem precisar de FCM (spec, seção 5).
--
-- Janela de checagem fixa em 10 minutos — tem que ser MAIOR que o intervalo
-- do cron job (5 minutos, Task 4) para não perder eventos entre execuções.
-- Se o intervalo do cron mudar, revisar esta janela junto.

create or replace function public.perfis_a_notificar_avanco_fase()
returns table (perfil_id uuid, processo_id uuid, numero text, objeto text)
language sql
stable
as $$
  select distinct p.id as perfil_id, pr.id as processo_id, pr.numero, pr.objeto
  from public.processo_fase_historico h
  join public.processos pr on pr.id = h.processo_id
  join public.perfis p on p.organizacao_id = pr.organizacao_id and p.papel = 'admin' and p.ativo and p.notificar_avanco_fase
  where h.data_saida is null
    and h.criado_em >= now() - interval '10 minutes'
    -- Só conta como "avanço" se existir outra linha de histórico do mesmo
    -- processo — a primeira linha (criada junto com o processo desde o
    -- hotfix e7d8817) não é um avanço, é a fase inicial.
    and exists (
      select 1 from public.processo_fase_historico h2
      where h2.processo_id = h.processo_id and h2.id <> h.id
    );
$$;

create or replace function public.perfis_a_notificar_prazo()
returns table (perfil_id uuid, processo_id uuid, numero text, objeto text, prazo_limite date)
language sql
stable
as $$
  select p.id as perfil_id, pr.id as processo_id, pr.numero, pr.objeto, h.prazo_limite
  from public.processo_fase_historico h
  join public.processos pr on pr.id = h.processo_id
  join public.perfis p on p.id = pr.responsavel_id and p.ativo and p.notificar_prazo
  where h.data_saida is null
    and h.notificar_prazo
    and h.prazo_limite is not null
    -- Avisa 5 dias antes até o próprio dia do vencimento (spec original, §7).
    and h.prazo_limite between current_date and current_date + 5;
$$;

create or replace function public.perfis_a_notificar_tempo_parado_fase()
returns table (perfil_id uuid, processo_id uuid, numero text, objeto text, dias_parado int)
language sql
stable
as $$
  select p.id as perfil_id, pr.id as processo_id, pr.numero, pr.objeto,
    (current_date - h.data_entrada)::int as dias_parado
  from public.processo_fase_historico h
  join public.processos pr on pr.id = h.processo_id
  join public.fases f on f.id = h.fase_id
  join public.perfis p on p.id = pr.responsavel_id and p.ativo and p.notificar_tempo_parado
  where h.data_saida is null
    and (current_date - h.data_entrada) >= f.dias_alerta_atencao;
$$;

create or replace function public.perfis_a_notificar_tempo_desde_designado()
returns table (perfil_id uuid, processo_id uuid, numero text, objeto text, dias_designado int)
language sql
stable
as $$
  select p.id as perfil_id, pr.id as processo_id, pr.numero, pr.objeto,
    (current_date - pr.designado_em::date)::int as dias_designado
  from public.processos pr
  join public.tipos_processo tp on tp.id = pr.tipo_processo_id
  join public.perfis p on p.id = pr.responsavel_id and p.ativo and p.notificar_tempo_parado
  where pr.designado_em is not null
    and (current_date - pr.designado_em::date) >= tp.dias_alerta_atencao;
$$;

revoke all on function public.perfis_a_notificar_avanco_fase() from public;
revoke all on function public.perfis_a_notificar_prazo() from public;
revoke all on function public.perfis_a_notificar_tempo_parado_fase() from public;
revoke all on function public.perfis_a_notificar_tempo_desde_designado() from public;
grant execute on function public.perfis_a_notificar_avanco_fase() to service_role;
grant execute on function public.perfis_a_notificar_prazo() to service_role;
grant execute on function public.perfis_a_notificar_tempo_parado_fase() to service_role;
grant execute on function public.perfis_a_notificar_tempo_desde_designado() to service_role;
