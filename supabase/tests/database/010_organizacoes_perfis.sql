begin;
select plan(9);

-- Schema exists
select has_table('public', 'organizacoes', 'organizacoes table should exist');
select has_table('public', 'perfis', 'perfis table should exist');
select has_enum('public', 'papel_usuario', 'papel_usuario enum should exist');

-- Fixture data: two orgs, one super_admin, one admin per org, one usuario in org A
insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values
  ('00000000-0000-0000-0000-000000000001', 'super@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000002', 'admin.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000003', 'user.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000004', 'admin.b@teste.com', 'x', now());

insert into public.organizacoes (id, nome) values
  ('10000000-0000-0000-0000-000000000001', 'Organização A'),
  ('10000000-0000-0000-0000-000000000002', 'Organização B');

insert into public.perfis (id, organizacao_id, papel, nome, ativo) values
  ('00000000-0000-0000-0000-000000000001', null, 'super_admin', 'Super Admin', true),
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'admin', 'Admin A', true),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A', true),
  ('00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002', 'admin', 'Admin B', true);

-- Helper functions resolve correctly
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
select is(public.auth_organizacao_id(), '10000000-0000-0000-0000-000000000001'::uuid, 'auth_organizacao_id resolves Admin A''s org');
select is(public.auth_papel(), 'admin'::public.papel_usuario, 'auth_papel resolves Admin A''s papel');

-- RLS: Usuário A sees only Organização A's perfis, not Organização B's
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
set local role authenticated;
select results_eq(
  $$select nome from public.perfis where organizacao_id = '10000000-0000-0000-0000-000000000002' order by nome$$,
  array[]::text[],
  'Usuário A cannot see Organização B perfis'
);
select results_eq(
  $$select nome from public.perfis order by nome$$,
  array['Admin A', 'Usuário A'],
  'Usuário A sees only Organização A perfis'
);
reset role;

-- Protected columns: a plain client update to papel must fail
set local role authenticated;
select throws_ok(
  $$update public.perfis set papel = 'admin' where id = '00000000-0000-0000-0000-000000000003'$$,
  'P0001',
  'Campo protegido não pode ser alterado diretamente.',
  'Direct client update of papel is blocked'
);
reset role;

-- Self-editable columns: a user can update their own notification toggle
set local role authenticated;
update public.perfis set notificar_prazo = false where id = '00000000-0000-0000-0000-000000000003';
select ok(true, 'Self-editing notificar_prazo did not raise');
reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
