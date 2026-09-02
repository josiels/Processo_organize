-- Agenda a checagem periódica de notificações (spec do Plano 2D, seção 3).
--
-- ATENÇÃO — passo manual obrigatório APÓS esta migration, antes que o cron
-- funcione: os dois segredos abaixo precisam ser criados no Vault (nunca
-- commitados em nenhum arquivo). Rode isto manualmente, uma vez, contra o
-- projeto Supabase (SQL Editor do dashboard, ou `node scripts/run_sql.mjs`
-- com um arquivo LOCAL não commitado):
--
--   select vault.create_secret(
--     'https://<PROJECT_REF>.supabase.co/functions/v1/notificar-processos',
--     'notificar_processos_url'
--   );
--   select vault.create_secret(
--     '<SUPABASE_SERVICE_ROLE_KEY>',
--     'notificar_processos_service_role_key'
--   );
--
-- Troque <PROJECT_REF> pelo ref do projeto e <SUPABASE_SERVICE_ROLE_KEY> pela
-- service role key real (a mesma já usada como secret das outras Edge
-- Functions — ver Supabase Dashboard > Project Settings > API).
create or replace function public.disparar_notificar_processos() returns void
language plpgsql
as $$
declare
  v_url text;
  v_service_role_key text;
begin
  select decrypted_secret into v_url from vault.decrypted_secrets where name = 'notificar_processos_url';
  select decrypted_secret into v_service_role_key from vault.decrypted_secrets where name = 'notificar_processos_service_role_key';

  if v_url is null or v_service_role_key is null then
    raise warning 'Segredos do Vault (notificar_processos_url / notificar_processos_service_role_key) ainda não configurados — pulando disparo.';
    return;
  end if;

  perform net.http_post(
    url := v_url,
    headers := jsonb_build_object('Authorization', 'Bearer ' || v_service_role_key, 'Content-Type', 'application/json'),
    body := '{}'::jsonb
  );
end;
$$;

-- A cada 5 minutos — menor que a janela de checagem de 10 minutos das funções
-- de elegibilidade (Task 2), para não perder eventos entre execuções.
select cron.schedule(
  'notificar-processos-periodico',
  '*/5 * * * *',
  $$select public.disparar_notificar_processos();$$
);
