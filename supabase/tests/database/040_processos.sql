begin;
select plan(8);

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
-- orphan process: usuário can self-assign
update public.processos set responsavel_id = '00000000-0000-0000-0000-000000000003'
where id = '40000000-0000-0000-0000-000000000001';
select ok(true, 'Usuário can self-assign an orphan processo');
-- Another usuário cannot steal an already-assigned processo. This UPDATE's
-- USING clause does not match the row for this caller, so Postgres RLS
-- silently filters it to 0 rows affected rather than raising an exception
-- (unlike INSERT, a non-matching UPDATE is not an error) — assert the data
-- is unchanged, not that an exception was thrown.
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000005', true);
update public.processos set responsavel_id = '00000000-0000-0000-0000-000000000005'
where id = '40000000-0000-0000-0000-000000000001';
select results_eq(
  $$select responsavel_id from public.processos where id = '40000000-0000-0000-0000-000000000001'$$,
  array['00000000-0000-0000-0000-000000000003'::uuid],
  'A different usuário cannot take a processo already assigned to someone else (RLS silently no-ops the UPDATE)'
);
-- Admin can reassign a processo that is already assigned to someone else,
-- via the public.auth_papel() = 'admin' disjunct of the processos_update
-- policy. This is the mechanism Tasks 7-9's designation/reassignment
-- workflows depend on.
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
update public.processos set responsavel_id = '00000000-0000-0000-0000-000000000005'
where id = '40000000-0000-0000-0000-000000000001';
select results_eq(
  $$select responsavel_id from public.processos where id = '40000000-0000-0000-0000-000000000001'$$,
  array['00000000-0000-0000-0000-000000000005'::uuid],
  'Admin can reassign a processo already assigned to a different usuário'
);
reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
