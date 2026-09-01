begin;
select plan(4);

select has_table('public', 'device_tokens', 'device_tokens table should exist');

insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values
  ('00000000-0000-0000-0000-000000000001', 'super@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000002', 'admin.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000003', 'user.a@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values ('10000000-0000-0000-0000-000000000001', 'Organização A');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000001', null, 'super_admin', 'Super Admin'),
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'admin', 'Admin A'),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A');

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;

insert into public.device_tokens (perfil_id, token_fcm) values ('00000000-0000-0000-0000-000000000003', 'token-abc');
select ok(true, 'User can insert their own device token');

select results_eq(
  $$select token_fcm from public.device_tokens where perfil_id = '00000000-0000-0000-0000-000000000003'$$,
  array['token-abc'],
  'User can read their own device token'
);

select throws_ok(
  $$insert into public.device_tokens (perfil_id, token_fcm) values ('00000000-0000-0000-0000-000000000002', 'token-xyz')$$,
  '42501',
  null,
  'User cannot insert a device token for someone else'
);

reset role;
select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
