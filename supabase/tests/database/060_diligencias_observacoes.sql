begin;
select plan(14);

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
-- An ORPHAN processo (responsavel_id is null) plus its historico row, for the
-- Finding B / Finding C coverage further down.
insert into public.processos (
  id, organizacao_id, numero, objeto, tipo_processo_id, data_abertura, fase_atual_id, responsavel_id
) values (
  '40000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
  '002/2026', 'x', '30000000-0000-0000-0000-000000000001', current_date,
  '20000000-0000-0000-0000-000000000001', null
);
insert into public.processo_fase_historico (id, processo_id, fase_id, data_entrada, observacoes)
values ('50000000-0000-0000-0000-000000000002', '40000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', current_date, '');

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

-- Spec section 2: a usuario acts on the processos "que são dele **ou que estão
-- órfãos**". Usuário A2 is neither an admin nor the responsável for anything,
-- but processo 002/2026 has responsavel_id is null, so the orphan disjunct of
-- diligencias_acesso / observacao_versoes_acesso must let them write to it.
insert into public.diligencias (processo_fase_historico_id, autor_id, conteudo)
values ('50000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000007', 'Diligência em processo órfão');
select results_eq(
  $$select conteudo from public.diligencias
    where processo_fase_historico_id = '50000000-0000-0000-0000-000000000002'$$,
  array['Diligência em processo órfão'],
  'A usuário who is neither admin nor owner can log a diligência on an ORPHAN processo'
);

insert into public.observacao_versoes (processo_fase_historico_id, conteudo)
values ('50000000-0000-0000-0000-000000000002', 'Observação em processo órfão');
select results_eq(
  $$select conteudo from public.observacao_versoes
    where processo_fase_historico_id = '50000000-0000-0000-0000-000000000002'$$,
  array['Observação em processo órfão'],
  'A usuário who is neither admin nor owner can log an observação version on an ORPHAN processo'
);

-- Finding C: autor_id is client-supplied, so authorship was forgeable by anyone
-- with write access to the processo. The processo half of diligencias_acesso's
-- WITH CHECK passes here (the processo is orphan, so Usuário A2 may write to
-- it) — the insert must still be rejected purely because autor_id names someone
-- other than the caller.
select throws_ok(
  $$insert into public.diligencias (processo_fase_historico_id, autor_id, conteudo)
    values ('50000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', 'Diligência forjada')$$,
  '42501',
  null,
  'A caller cannot attribute a diligência to someone else''s autor_id'
);

-- The column now defaults to auth.uid(), so the honest client never has to send
-- autor_id at all and cannot get it wrong.
insert into public.diligencias (processo_fase_historico_id, conteudo)
values ('50000000-0000-0000-0000-000000000002', 'Diligência sem autor_id explícito');
select results_eq(
  $$select autor_id from public.diligencias where conteudo = 'Diligência sem autor_id explícito'$$,
  array['00000000-0000-0000-0000-000000000007'::uuid],
  'autor_id defaults to auth.uid() when the client omits it'
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
