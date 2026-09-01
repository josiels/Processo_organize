begin;
select plan(7);

select has_view('public', 'fila_distribuicao', 'fila_distribuicao view should exist');
select has_view('public', 'fila_distribuicao_por_tipo', 'fila_distribuicao_por_tipo view should exist');

-- Fixture: duas organizações. Na Organização A: dois perfis (um com designações, um sem), dois tipos de processo.
insert into auth.users (id, email, encrypted_password, email_confirmed_at, instance_id, aud, role)
values
  ('b0000000-0000-0000-0000-000000000001', 'admin.fd@teste.com', 'x', now(), '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('b0000000-0000-0000-0000-000000000002', 'user1.fd@teste.com', 'x', now(), '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('b0000000-0000-0000-0000-000000000003', 'user2.fd@teste.com', 'x', now(), '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('b0000000-0000-0000-0000-000000000004', 'userb.fd@teste.com', 'x', now(), '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');
insert into public.organizacoes (id, nome) values
  ('b1000000-0000-0000-0000-000000000001', 'Organização FD A'),
  ('b1000000-0000-0000-0000-000000000002', 'Organização FD B');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('b0000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', 'admin', 'Admin FD'),
  ('b0000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000001', 'usuario', 'User1 FD'),
  ('b0000000-0000-0000-0000-000000000003', 'b1000000-0000-0000-0000-000000000001', 'usuario', 'User2 FD'),
  ('b0000000-0000-0000-0000-000000000004', 'b1000000-0000-0000-0000-000000000002', 'usuario', 'UserB FD');
insert into public.tipos_processo (id, organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico) values
  ('b2000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', 'Tipo X', 10, 20),
  ('b2000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000001', 'Tipo Y', 10, 20);
insert into public.fases (id, organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico) values
  ('b3000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', 'Fase Única', 1, 5, 10);
insert into public.processos (id, organizacao_id, numero, objeto, tipo_processo_id, data_abertura, fase_atual_id) values
  ('b4000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', '001/FD', 'P1', 'b2000000-0000-0000-0000-000000000001', current_date, 'b3000000-0000-0000-0000-000000000001'),
  ('b4000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000001', '002/FD', 'P2', 'b2000000-0000-0000-0000-000000000002', current_date, 'b3000000-0000-0000-0000-000000000001');

-- User1 recebeu dois processos (um de cada tipo); User2 nunca recebeu nada.
insert into public.designacoes (processo_id, tipo_processo_id, perfil_id, designado_por) values
  ('b4000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000001'),
  ('b4000000-0000-0000-0000-000000000002', 'b2000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000001');
update public.perfis set ultimo_recebimento_em = now() where id = 'b0000000-0000-0000-0000-000000000002';

-- Isolamento entre organizações: usuário da Organização B não deve ver nada da Organização A
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000004', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;
-- Nota: userb.fd é um perfil ativo legítimo da Organização B, então ele
-- aparece com seu próprio registro (0 designações) em sua própria fila —
-- a asserção de isolamento verifica que nenhuma linha da Organização A
-- (id b1000000-...0001) aparece para esse usuário, não que a contagem
-- total seja zero.
select results_eq(
  $$select count(*)::int from public.fila_distribuicao where organizacao_id = 'b1000000-0000-0000-0000-000000000001'::uuid$$,
  array[0],
  'usuário da Organização B não vê nenhuma linha da Organização A em fila_distribuicao'
);
reset role;

-- Visão de dentro da Organização A
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000002', true);
set local role authenticated;
select results_eq(
  $$select total_designacoes from public.fila_distribuicao where perfil_id = 'b0000000-0000-0000-0000-000000000002'::uuid$$,
  array[2::bigint],
  'User1 aparece com 2 designações históricas'
);
select results_eq(
  $$select total_designacoes from public.fila_distribuicao where perfil_id = 'b0000000-0000-0000-0000-000000000003'::uuid$$,
  array[0::bigint],
  'User2 aparece com 0 designações (nunca recebeu nada)'
);
select results_eq(
  $$select count(*)::int from public.fila_distribuicao$$,
  array[3],
  'os 3 perfis ativos da Organização A aparecem na fila (admin incluso)'
);
select results_eq(
  $$select total from public.fila_distribuicao_por_tipo
    where perfil_id = 'b0000000-0000-0000-0000-000000000002'::uuid and tipo_processo_id = 'b2000000-0000-0000-0000-000000000001'::uuid$$,
  array[1::bigint],
  'a quebra por tipo mostra 1 designação do Tipo X para User1'
);
reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
