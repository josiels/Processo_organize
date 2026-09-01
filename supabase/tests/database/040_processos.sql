begin;
select plan(9);

select has_table('public', 'processos', 'processos table should exist');
select has_enum('public', 'status_geral_processo', 'status_geral_processo enum should exist');

insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values
  ('00000000-0000-0000-0000-000000000002', 'admin.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000003', 'user.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000005', 'user.a2@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values ('10000000-0000-0000-0000-000000000001', 'Organização A');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'admin', 'Admin A'),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A'),
  ('00000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A2');
insert into public.fases (id, organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico)
values ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Pesquisa de Preços', 1, 5, 10);
insert into public.tipos_processo (id, organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico)
values ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Aquisição direta', 10, 20);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;
insert into public.processos (
  id, organizacao_id, numero, objeto, descricao, orgao_demandante,
  tipo_processo_id, valor_estimado_total, data_abertura, fase_atual_id, status_geral
) values (
  '40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
  '001/2026', 'Aquisição de equipamentos', '', '', '30000000-0000-0000-0000-000000000001',
  0, current_date, '20000000-0000-0000-0000-000000000001', 'em_andamento'
);
select ok(true, 'Admin can create a processo');
reset role;

set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
select throws_ok(
  $$insert into public.processos (
      organizacao_id, numero, objeto, descricao, orgao_demandante,
      tipo_processo_id, valor_estimado_total, data_abertura, fase_atual_id, status_geral
    ) values (
      '10000000-0000-0000-0000-000000000001', '002/2026', 'x', '', '',
      '30000000-0000-0000-0000-000000000001', 0, current_date,
      '20000000-0000-0000-0000-000000000001', 'em_andamento'
    )$$,
  '42501',
  null,
  'Non-admin user cannot create a processo'
);
select results_eq(
  $$select numero from public.processos$$,
  array['001/2026'],
  'Non-admin user can read the org''s processos (transparency)'
);
-- MECHANISM UNDER TEST: the processos_proteger_designacao trigger (column
-- protection), NOT RLS. The processos_update policy's USING clause does match
-- this row for this caller (it is orphan), so the UPDATE is not filtered out —
-- it reaches the trigger, which rejects it because responsavel_id changed.
-- Self-assignment must go through designar_processo() so that the designacoes
-- audit row and the perfis.ultimo_recebimento_em counter are written too.
select throws_ok(
  $$update public.processos set responsavel_id = '00000000-0000-0000-0000-000000000003'
    where id = '40000000-0000-0000-0000-000000000001'$$,
  'P0001',
  null,
  'Usuário cannot self-assign an orphan processo with a raw UPDATE (trigger blocks the designation columns)'
);
-- The sanctioned path: the same self-assignment via designar_processo()
-- succeeds, giving the rest of this file an actually-assigned processo.
select public.designar_processo(
  '40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003'
);
select results_eq(
  $$select responsavel_id from public.processos where id = '40000000-0000-0000-0000-000000000001'$$,
  array['00000000-0000-0000-0000-000000000003'::uuid],
  'Usuário can self-assign an orphan processo via designar_processo()'
);
-- MECHANISM UNDER TEST: RLS row-touch eligibility, NOT the trigger. A different
-- usuário is not the owner, not an admin, and the processo is no longer orphan,
-- so processos_update's USING clause does not match the row for them at all.
-- A non-matching UPDATE is silently filtered to 0 rows (unlike INSERT, it is
-- not an error), so assert the data is unchanged. Deliberately touching
-- `descricao` rather than `responsavel_id`: a designation column would trip the
-- trigger first and prove nothing about row eligibility.
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000005', true);
update public.processos set descricao = 'invadido'
where id = '40000000-0000-0000-0000-000000000001';
select results_eq(
  $$select descricao from public.processos where id = '40000000-0000-0000-0000-000000000001'$$,
  array[''],
  'A different usuário cannot edit a processo already assigned to someone else (RLS silently no-ops the UPDATE)'
);
-- MECHANISM UNDER TEST: the trigger again, this time for an admin. Admins keep
-- full row eligibility under processos_update (they can still edit objeto,
-- descricao, status_geral), but reassignment is no longer reachable by raw
-- UPDATE for them either — only designar_processo() can move the designation
-- columns, and 070_designacoes.sql covers that path for admins.
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
select throws_ok(
  $$update public.processos set responsavel_id = '00000000-0000-0000-0000-000000000005'
    where id = '40000000-0000-0000-0000-000000000001'$$,
  'P0001',
  null,
  'Admin cannot reassign a processo with a raw UPDATE either (trigger blocks the designation columns)'
);
reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
