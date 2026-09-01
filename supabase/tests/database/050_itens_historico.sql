begin;
select plan(6);

select has_table('public', 'itens', 'itens table should exist');
select has_table('public', 'processo_fase_historico', 'processo_fase_historico table should exist');

insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values
  ('00000000-0000-0000-0000-000000000002', 'admin.a@teste.com', 'x', now()),
  ('00000000-0000-0000-0000-000000000003', 'user.a@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values ('10000000-0000-0000-0000-000000000001', 'Organização A');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'admin', 'Admin A'),
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

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;
insert into public.itens (processo_id, descricao, quantidade, unidade, valor_estimado_unit)
values ('40000000-0000-0000-0000-000000000001', 'Notebook', 1, 'un', 3500);
select ok(true, 'Responsible user can add an item to their own processo');

insert into public.processo_fase_historico (
  processo_id, fase_id, data_entrada, observacoes, notificar_prazo
) values (
  '40000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
  current_date, '', false
);
select ok(true, 'Responsible user can add a historico entry to their own processo');

reset role;

-- A colleague in the SAME organization — not the owner, not an admin — must
-- still be able to READ the item for workload transparency (spec section 2:
-- "vê todos os processos"), even though they cannot edit it.
insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values ('00000000-0000-0000-0000-000000000007', 'user.a2@teste.com', 'x', now());
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000001', 'usuario', 'Usuário A2');
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000007', true);
set local role authenticated;
select results_eq(
  $$select descricao from public.itens$$,
  array['Notebook'],
  'A colleague in the same organization can read itens for transparency, even though it is not theirs'
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
  $$select descricao from public.itens$$,
  array[]::text[],
  'A user from a different organization sees no itens from Organização A'
);
reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
