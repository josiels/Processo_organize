begin;
select plan(13);

select has_table('public', 'designacoes', 'designacoes table should exist');
select has_function('public', 'designar_processo', array['uuid', 'uuid'], 'designar_processo function should exist');

insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values
  ('00000000-0000-0000-0000-000000000002', 'admin.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000003', 'user.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000005', 'user.a2@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000006', 'admin.b@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000007', 'user.b@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values
  ('10000000-0000-0000-0000-000000000001', 'Organização A'),
  ('10000000-0000-0000-0000-000000000002', 'Organização B');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'admin', 'Admin A'),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A'),
  ('00000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A2'),
  ('00000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000002', 'admin', 'Admin B'),
  ('00000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000002', 'usuario', 'Usuário B');
insert into public.fases (id, organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico)
values
  ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Pesquisa de Preços', 1, 5, 10),
  ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'Pesquisa de Preços', 1, 5, 10);
insert into public.tipos_processo (id, organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico)
values
  ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Aquisição direta', 10, 20),
  ('30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'Aquisição direta', 10, 20);
insert into public.processos (id, organizacao_id, numero, objeto, tipo_processo_id, data_abertura, fase_atual_id)
values
  ('40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
    '001/2026', 'x', '30000000-0000-0000-0000-000000000001', current_date, '20000000-0000-0000-0000-000000000001'),
  ('40000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002',
    '001/2026', 'x', '30000000-0000-0000-0000-000000000002', current_date, '20000000-0000-0000-0000-000000000002');

-- Admin designates the orphan processo to Usuário A
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;
select public.designar_processo('40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003');
reset role;
select results_eq(
  $$select responsavel_id from public.processos where id = '40000000-0000-0000-0000-000000000001'$$,
  array['00000000-0000-0000-0000-000000000003'::uuid],
  'Admin designation sets responsavel_id'
);
select results_eq(
  $$select count(*)::int from public.designacoes where perfil_id = '00000000-0000-0000-0000-000000000003'$$,
  array[1],
  'Designation is logged in designacoes'
);
select ok(
  (select ultimo_recebimento_em from public.perfis where id = '00000000-0000-0000-0000-000000000003') is not null,
  'ultimo_recebimento_em is updated for the recipient'
);

-- Critical: Org A's admin cannot designate a processo that belongs to Org B
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
set local role authenticated;
select throws_ok(
  $$select public.designar_processo('40000000-0000-0000-0000-000000000002', null)$$,
  'P0001',
  null,
  'Org A admin cannot designate a processo belonging to Org B'
);
reset role;

-- Critical: Org A's admin cannot designate an Org A processo to a user outside Org A
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
set local role authenticated;
select throws_ok(
  $$select public.designar_processo('40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000007')$$,
  'P0001',
  null,
  'Org A admin cannot designate an Org A processo to a user from Org B'
);
reset role;

-- Usuário A2 cannot transfer the processo directly to themselves (it is not orphan)
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000005', true);
set local role authenticated;
select throws_ok(
  $$select public.designar_processo('40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000005')$$,
  'P0001',
  null,
  'A non-admin cannot take a processo that is not orphan'
);
reset role;

-- Usuário A returns their own processo to orphan
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
set local role authenticated;
select public.designar_processo('40000000-0000-0000-0000-000000000001', null);
reset role;
select results_eq(
  $$select responsavel_id from public.processos where id = '40000000-0000-0000-0000-000000000001'$$,
  array[null::uuid],
  'Usuário can return their own processo to orphan'
);

-- Critical: a non-admin cannot designate an orphan processo to a third party (only to themselves)
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000005', true);
set local role authenticated;
select throws_ok(
  $$select public.designar_processo('40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003')$$,
  'P0001',
  null,
  'A non-admin cannot designate an orphan processo to a third party'
);
reset role;

-- Usuário A2 self-assigns the now-orphan processo
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000005', true);
set local role authenticated;
select public.designar_processo('40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000005');
reset role;
select results_eq(
  $$select responsavel_id from public.processos where id = '40000000-0000-0000-0000-000000000001'$$,
  array['00000000-0000-0000-0000-000000000005'::uuid],
  'Usuário can self-assign an orphan processo'
);

-- Important: admin reassigns an already-assigned processo to a different user
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
set local role authenticated;
select public.designar_processo('40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003');
reset role;
select results_eq(
  $$select responsavel_id from public.processos where id = '40000000-0000-0000-0000-000000000001'$$,
  array['00000000-0000-0000-0000-000000000003'::uuid],
  'Admin can reassign an already-assigned processo to a different user'
);
select results_eq(
  $$select count(*)::int from public.designacoes where perfil_id = '00000000-0000-0000-0000-000000000003'$$,
  array[2],
  'Reassignment inserts a second designacoes audit row for the recipient'
);

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
