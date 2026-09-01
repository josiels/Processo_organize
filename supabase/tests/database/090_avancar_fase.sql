begin;
select plan(7);

select has_function('public', 'avancar_fase', 'avancar_fase function should exist');

-- Fixture: uma organização, um admin, dois usuários, três fases, um tipo de processo
insert into auth.users (id, email, encrypted_password, email_confirmed_at, instance_id, aud, role)
values
  ('a0000000-0000-0000-0000-000000000001', 'admin.af@teste.com', 'x', now(), '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('a0000000-0000-0000-0000-000000000002', 'user.af@teste.com', 'x', now(), '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('a0000000-0000-0000-0000-000000000003', 'user2.af@teste.com', 'x', now(), '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');
insert into public.organizacoes (id, nome) values
  ('a1000000-0000-0000-0000-000000000001', 'Organização AvancarFase');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('a0000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'admin', 'Admin AF'),
  ('a0000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001', 'usuario', 'User AF'),
  ('a0000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000001', 'usuario', 'User2 AF');
insert into public.fases (id, organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico) values
  ('a2000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'Fase A', 1, 5, 10),
  ('a2000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001', 'Fase B', 2, 5, 10),
  ('a2000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000001', 'Fase C', 3, 5, 10);
insert into public.tipos_processo (id, organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico) values
  ('a3000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'Tipo AF', 10, 20);

-- Processo 1: designado ao User AF, com uma entrada de histórico aberta na Fase A
select set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-000000000001', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;
insert into public.processos (
  id, organizacao_id, numero, objeto, tipo_processo_id, data_abertura,
  fase_atual_id, responsavel_id, designado_em, designado_por
) values (
  'a4000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001',
  '001/AF', 'Processo AF', 'a3000000-0000-0000-0000-000000000001', current_date,
  'a2000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000002',
  now(), 'a0000000-0000-0000-0000-000000000001'
);
insert into public.processo_fase_historico (processo_id, fase_id, responsavel_id, data_entrada)
values ('a4000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000002', current_date);

-- Processo 2: órfão, sem entrada de histórico ainda aberta na Fase A
insert into public.processos (
  id, organizacao_id, numero, objeto, tipo_processo_id, data_abertura, fase_atual_id
) values (
  'a4000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001',
  '002/AF', 'Processo AF Órfão', 'a3000000-0000-0000-0000-000000000001', current_date,
  'a2000000-0000-0000-0000-000000000001'
);
insert into public.processo_fase_historico (processo_id, fase_id, data_entrada)
values ('a4000000-0000-0000-0000-000000000002', 'a2000000-0000-0000-0000-000000000001', current_date);
reset role;

-- O dono (User AF) avança o Processo 1 da Fase A para a Fase B
select set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-000000000002', true);
set local role authenticated;
select avancar_fase('a4000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000002', 'Avançando para B', null, null);
select results_eq(
  $$select fase_atual_id from public.processos where id = 'a4000000-0000-0000-0000-000000000001'$$,
  array['a2000000-0000-0000-0000-000000000002'::uuid],
  'processos.fase_atual_id foi atualizado para a Fase B'
);
select results_eq(
  $$select count(*)::int from public.processo_fase_historico
    where processo_id = 'a4000000-0000-0000-0000-000000000001' and fase_id = 'a2000000-0000-0000-0000-000000000001' and data_saida is not null$$,
  array[1],
  'a entrada de histórico da Fase A foi fechada (data_saida preenchida)'
);
select results_eq(
  $$select count(*)::int from public.processo_fase_historico
    where processo_id = 'a4000000-0000-0000-0000-000000000001' and fase_id = 'a2000000-0000-0000-0000-000000000002' and data_saida is null$$,
  array[1],
  'uma nova entrada de histórico foi aberta na Fase B'
);
reset role;

-- User2 (nem dono, nem admin) tenta avançar o Processo 1 (que agora tem dono) — deve falhar sem deixar rastro
select set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-000000000003', true);
set local role authenticated;
select throws_ok(
  $$select avancar_fase('a4000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000003', 'tentativa indevida', null, null)$$,
  'P0001',
  null,
  'Usuário sem permissão não pode avançar a fase de um processo de outra pessoa'
);
select results_eq(
  $$select count(*)::int from public.processo_fase_historico where processo_id = 'a4000000-0000-0000-0000-000000000001'$$,
  array[2],
  'a tentativa indevida não deixou nenhuma entrada de histórico órfã (atomicidade preservada)'
);
reset role;

-- User2 avança o Processo 2 (órfão) — deve funcionar, mesmo não sendo dono nem admin
select set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-000000000003', true);
set local role authenticated;
select avancar_fase('a4000000-0000-0000-0000-000000000002', 'a2000000-0000-0000-0000-000000000003', '', null, null);
select results_eq(
  $$select fase_atual_id from public.processos where id = 'a4000000-0000-0000-0000-000000000002'$$,
  array['a2000000-0000-0000-0000-000000000003'::uuid],
  'qualquer usuário pode avançar a fase de um processo órfão'
);
reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
