begin;
select plan(8);

-- perfis.ativo existed but was checked nowhere, so a deactivated account kept
-- full access. Enforcement is centralized in auth_organizacao_id()/auth_papel(),
-- which every RLS policy in the system keys off, plus an explicit `and ativo`
-- inside designar_processo() (which reads perfis directly, bypassing RLS).

insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values
  ('00000000-0000-0000-0000-000000000002', 'admin.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000003', 'user.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000005', 'user.a2@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values ('10000000-0000-0000-0000-000000000001', 'Organização A');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'admin', 'Admin A'),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário Desativado'),
  ('00000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário Ativo');
insert into public.fases (id, organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico)
values ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Pesquisa de Preços', 1, 5, 10);
insert into public.tipos_processo (id, organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico)
values ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Aquisição direta', 10, 20);
insert into public.processos (id, organizacao_id, numero, objeto, tipo_processo_id, data_abertura, fase_atual_id)
values ('40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
  '001/2026', 'x', '30000000-0000-0000-0000-000000000001', current_date, '20000000-0000-0000-0000-000000000001');

-- Deactivate Usuário A. This runs on the test's own connection (role postgres,
-- not `authenticated`), which is exactly how a service-role/admin deactivation
-- would happen — the perfis_proteger_campos trigger bypasses postgres, and
-- there is no Edge Function for this yet.
update public.perfis set ativo = false where id = '00000000-0000-0000-0000-000000000003';

-- The deactivated user: both helpers must go null, so every policy that keys
-- off them stops matching.
select set_config('request.jwt.claim.role', 'authenticated', true);
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
set local role authenticated;
select is(public.auth_organizacao_id(), null::uuid,
  'auth_organizacao_id() returns null for a deactivated perfil');
select is(public.auth_papel(), null::public.papel_usuario,
  'auth_papel() returns null for a deactivated perfil');
select results_eq(
  $$select count(*)::int from public.processos$$,
  array[0],
  'A deactivated user sees no processos even though their organization has one'
);
select results_eq(
  $$select count(*)::int from public.perfis$$,
  array[0],
  'A deactivated user sees no perfis, not even their own'
);
-- A deactivated user cannot claim the orphan processo either: designar_processo
-- reads perfis directly (SECURITY DEFINER) and now filters on `ativo`, so the
-- caller falls into the existing "Perfil não encontrado" branch.
select throws_ok(
  $$select public.designar_processo('40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003')$$,
  'P0001',
  null,
  'A deactivated user cannot call designar_processo'
);
reset role;

-- REGRESSION GUARD (the important half): a still-active user in the SAME
-- organization must be completely unaffected by all of the above.
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000005', true);
set local role authenticated;
select is(public.auth_organizacao_id(), '10000000-0000-0000-0000-000000000001'::uuid,
  'auth_organizacao_id() still resolves for an active perfil in the same organization');
select is(public.auth_papel(), 'usuario'::public.papel_usuario,
  'auth_papel() still resolves for an active perfil in the same organization');
select results_eq(
  $$select numero from public.processos$$,
  array['001/2026'],
  'An active user still sees their organization''s processos');
reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
