begin;
select plan(8);

select has_function('public', 'perfis_a_notificar_avanco_fase', 'perfis_a_notificar_avanco_fase deveria existir');
select has_function('public', 'perfis_a_notificar_prazo', 'perfis_a_notificar_prazo deveria existir');
select has_function('public', 'perfis_a_notificar_tempo_parado_fase', 'perfis_a_notificar_tempo_parado_fase deveria existir');
select has_function('public', 'perfis_a_notificar_tempo_desde_designado', 'perfis_a_notificar_tempo_desde_designado deveria existir');

-- Fixture: uma organização, um admin (quer ser avisado de avanço), um usuário
-- (dono do processo, quer ser avisado de prazo e tempo parado), um segundo
-- usuário com todas as preferências desligadas (nunca deve aparecer em nada).
insert into auth.users (id, email, encrypted_password, email_confirmed_at, instance_id, aud, role)
values
  ('e0000000-0000-0000-0000-000000000001', 'admin.en@teste.com', 'x', now(), '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('e0000000-0000-0000-0000-000000000002', 'user.en@teste.com', 'x', now(), '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('e0000000-0000-0000-0000-000000000003', 'user.mudo.en@teste.com', 'x', now(), '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');
insert into public.organizacoes (id, nome) values ('e1000000-0000-0000-0000-000000000001', 'Organização EN');
insert into public.perfis (id, organizacao_id, papel, nome, notificar_avanco_fase, notificar_prazo, notificar_tempo_parado) values
  ('e0000000-0000-0000-0000-000000000001', 'e1000000-0000-0000-0000-000000000001', 'admin', 'Admin EN', true, true, true),
  ('e0000000-0000-0000-0000-000000000002', 'e1000000-0000-0000-0000-000000000001', 'usuario', 'User EN', true, true, true),
  ('e0000000-0000-0000-0000-000000000003', 'e1000000-0000-0000-0000-000000000001', 'usuario', 'User Mudo EN', false, false, false);
insert into public.fases (id, organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico) values
  ('e2000000-0000-0000-0000-000000000001', 'e1000000-0000-0000-0000-000000000001', 'Fase A EN', 1, 3, 5),
  ('e2000000-0000-0000-0000-000000000002', 'e1000000-0000-0000-0000-000000000001', 'Fase B EN', 2, 3, 5);
insert into public.tipos_processo (id, organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico) values
  ('e3000000-0000-0000-0000-000000000001', 'e1000000-0000-0000-0000-000000000001', 'Tipo EN', 4, 8);

set local role postgres;

-- Processo 1: acabou de ser designado ao User EN há 6 dias (atenção, tipo=4/8) e
-- avançou de fase agora mesmo (Fase A -> Fase B, duas linhas de historico).
insert into public.processos (id, organizacao_id, numero, objeto, tipo_processo_id, data_abertura, fase_atual_id, responsavel_id, designado_em, designado_por) values
  ('e4000000-0000-0000-0000-000000000001', 'e1000000-0000-0000-0000-000000000001', '001/EN', 'Processo EN 1', 'e3000000-0000-0000-0000-000000000001', current_date - 6, 'e2000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000002', now() - interval '6 days', 'e0000000-0000-0000-0000-000000000001');
insert into public.processo_fase_historico (id, processo_id, fase_id, responsavel_id, data_entrada, data_saida, prazo_limite, notificar_prazo, criado_em) values
  ('e5000000-0000-0000-0000-000000000001', 'e4000000-0000-0000-0000-000000000001', 'e2000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000002', current_date - 6, current_date, null, false, now() - interval '6 days');
insert into public.processo_fase_historico (id, processo_id, fase_id, responsavel_id, data_entrada, data_saida, prazo_limite, notificar_prazo, criado_em) values
  ('e5000000-0000-0000-0000-000000000002', 'e4000000-0000-0000-0000-000000000001', 'e2000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000002', current_date, null, current_date + 3, true, now());

-- Processo 2: recém-criado (uma única linha de historico, sem fase anterior) —
-- NUNCA deve aparecer em perfis_a_notificar_avanco_fase, mesmo sendo recente.
insert into public.processos (id, organizacao_id, numero, objeto, tipo_processo_id, data_abertura, fase_atual_id) values
  ('e4000000-0000-0000-0000-000000000002', 'e1000000-0000-0000-0000-000000000001', '002/EN', 'Processo EN 2', 'e3000000-0000-0000-0000-000000000001', current_date, 'e2000000-0000-0000-0000-000000000001');
insert into public.processo_fase_historico (id, processo_id, fase_id, data_entrada, data_saida, notificar_prazo, criado_em) values
  ('e5000000-0000-0000-0000-000000000003', 'e4000000-0000-0000-0000-000000000002', 'e2000000-0000-0000-0000-000000000001', current_date, null, false, now());

reset role;

-- 1) Avanço de fase: só o Processo 1 conta (tem uma linha anterior fechada);
--    o Admin (notificar_avanco_fase=true) aparece, o User Mudo não.
select results_eq(
  $$select perfil_id::text from public.perfis_a_notificar_avanco_fase() order by 1$$,
  $$values ('e0000000-0000-0000-0000-000000000001')$$,
  'so o admin com preferencia ligada deveria ser notificado de avanco de fase, e so pelo Processo 1'
);

-- 2) Prazo: a linha ativa do Processo 1 tem prazo em +3 dias e notificar_prazo=true
--    na entrada; o responsavel (User EN) tem a preferencia pessoal ligada.
select results_eq(
  $$select perfil_id::text from public.perfis_a_notificar_prazo() where processo_id = 'e4000000-0000-0000-0000-000000000001'::uuid$$,
  $$values ('e0000000-0000-0000-0000-000000000002')$$,
  'responsavel com prazo proximo e as duas preferencias ligadas deveria ser notificado'
);

-- 3) Tempo parado na fase: Processo 1 entrou na Fase B (dias_alerta_atencao=3)
--    hoje -> 0 dias parado, nao deveria disparar ainda.
select is_empty(
  $$select 1 from public.perfis_a_notificar_tempo_parado_fase() where processo_id = 'e4000000-0000-0000-0000-000000000001'::uuid$$,
  'processo que acabou de entrar na fase nao deveria disparar tempo parado ainda'
);

-- 4) Tempo desde designado: Processo 1 foi designado ha 6 dias, tipo tem
--    dias_alerta_atencao=4 -> deveria disparar para o responsavel.
select results_eq(
  $$select perfil_id::text from public.perfis_a_notificar_tempo_desde_designado() where processo_id = 'e4000000-0000-0000-0000-000000000001'::uuid$$,
  $$values ('e0000000-0000-0000-0000-000000000002')$$,
  'responsavel de processo designado ha mais dias que o limite do tipo deveria ser notificado'
);

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
