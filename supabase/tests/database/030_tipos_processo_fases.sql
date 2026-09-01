begin;
select plan(5);

select has_table('public', 'tipos_processo', 'tipos_processo table should exist');
select has_table('public', 'fases', 'fases table should exist');

insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values
  ('00000000-0000-0000-0000-000000000002', 'admin.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000003', 'user.a@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values ('10000000-0000-0000-0000-000000000001', 'Organização A');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'admin', 'Admin A'),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A');

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;
insert into public.tipos_processo (organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico)
values ('10000000-0000-0000-0000-000000000001', 'Aquisição direta', 10, 20);
insert into public.fases (organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico)
values ('10000000-0000-0000-0000-000000000001', 'Pesquisa de Preços', 1, 5, 10);
select ok(true, 'Admin can create tipos_processo and fases');
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
set local role authenticated;
select throws_ok(
  $$insert into public.tipos_processo (organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico)
    values ('10000000-0000-0000-0000-000000000001', 'Serviço', 5, 10)$$,
  '42501',
  null,
  'Non-admin user cannot create tipos_processo'
);
select results_eq(
  $$select nome from public.fases order by nome$$,
  array['Pesquisa de Preços'],
  'Non-admin user can still read fases of their org'
);
reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
