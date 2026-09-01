begin;
select plan(10);

select has_table('public', 'diligencias', 'diligencias table should exist');
select has_table('public', 'observacao_versoes', 'observacao_versoes table should exist');

insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values ('00000000-0000-0000-0000-000000000003', 'user.a@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values ('10000000-0000-0000-0000-000000000001', 'Organização A');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A');
insert into public.fases (id, organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico)
values ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Pesquisa de Preços', 1, 5, 10);
insert into public.tipos_processo (id, organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico)
values ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Aquisição direta', 10, 20);
insert into public.processos (
  id, organizacao_id, numero, objeto, tipo_processo_id, data_abertura, fase_atual_id, responsavel_id
) values (
  '40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
  '001/2026', 'x', '30000000-0000-0000-0000-000000000001', current_date,
  '20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003'
);
insert into public.processo_fase_historico (id, processo_id, fase_id, data_entrada, observacoes)
values ('50000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', current_date, '');

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;

insert into public.diligencias (processo_fase_historico_id, autor_id, conteudo)
values ('50000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', 'Enviado ofício ao fornecedor X');
select ok(true, 'Responsible user can log a diligência');

insert into public.observacao_versoes (processo_fase_historico_id, conteudo)
values ('50000000-0000-0000-0000-000000000001', 'Aguardando resposta');
select ok(true, 'Responsible user can log an observação version');

reset role;

-- A colleague in the SAME organization — not the owner, not an admin — must
-- still be able to READ diligências/observações for workload transparency
-- (spec section 2: "vê todos os processos"), even though they cannot write.
insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values ('00000000-0000-0000-0000-000000000007', 'user.a2@teste.com', 'x', now());
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A2');
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000007', true);
set local role authenticated;
select results_eq(
  $$select conteudo from public.diligencias$$,
  array['Enviado ofício ao fornecedor X'],
  'A colleague in the same organization can read diligências for transparency, even though it is not theirs'
);
select results_eq(
  $$select conteudo from public.observacao_versoes$$,
  array['Aguardando resposta'],
  'A colleague in the same organization can read observação versions for transparency, even though it is not theirs'
);

-- Reads are organization-wide now, but writes must remain admin-or-owner
-- only. A colleague trying to insert a diligência/observação into a
-- processo they do not own must be rejected by the WITH CHECK clause of
-- the respective "*_acesso" policy (the purely-permissive select policy
-- above does not apply to INSERT).
select throws_ok(
  $$insert into public.diligencias (processo_fase_historico_id, autor_id, conteudo)
    values ('50000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000007', 'Diligência rejeitada')$$,
  '42501',
  null,
  'A colleague in the same organization cannot insert a diligência into a processo they do not own'
);

select throws_ok(
  $$insert into public.observacao_versoes (processo_fase_historico_id, conteudo)
    values ('50000000-0000-0000-0000-000000000001', 'Observação rejeitada')$$,
  '42501',
  null,
  'A colleague in the same organization cannot insert an observação version into a processo they do not own'
);
reset role;

insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values ('00000000-0000-0000-0000-000000000006', 'user.b@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values ('10000000-0000-0000-0000-000000000002', 'Organização B');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000002', 'admin', 'Admin B');
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000006', true);
set local role authenticated;
select results_eq(
  $$select conteudo from public.diligencias$$,
  array[]::text[],
  'A user from a different organization sees no diligências from Organização A'
);
select results_eq(
  $$select conteudo from public.observacao_versoes$$,
  array[]::text[],
  'A user from a different organization sees no observação versions from Organização A'
);
reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
