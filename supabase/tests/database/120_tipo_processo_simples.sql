begin;
select plan(5);

select has_column('public', 'tipos_processo', 'simples', 'tipos_processo.simples should exist');
select has_column('public', 'tipos_processo', 'fase_padrao_id', 'tipos_processo.fase_padrao_id should exist');

insert into auth.users (id, email, encrypted_password, email_confirmed_at)
values ('00000000-0000-0000-0000-000000000002', 'admin.simples@teste.com', 'x', now());
insert into public.organizacoes (id, nome) values ('10000000-0000-0000-0000-000000000001', 'Organização Simples');
insert into public.perfis (id, organizacao_id, papel, nome) values
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'admin', 'Admin Simples');

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;

insert into public.fases (id, organizacao_id, nome, ordem, dias_alerta_atencao, dias_alerta_critico)
values ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Execução', 1, 5, 10);

select throws_ok(
  $$insert into public.tipos_processo (organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico, simples)
    values ('10000000-0000-0000-0000-000000000001', 'Responder fornecedor', 5, 10, true)$$,
  '23514',
  null,
  'tipos_processo simples=true sem fase_padrao_id viola o check constraint'
);

insert into public.tipos_processo (organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico, simples, fase_padrao_id)
values ('10000000-0000-0000-0000-000000000001', 'Responder fornecedor', 5, 10, true, '20000000-0000-0000-0000-000000000001');
select ok(true, 'tipos_processo simples=true com fase_padrao_id válida é aceito');

insert into public.tipos_processo (organizacao_id, nome, dias_alerta_atencao, dias_alerta_critico)
values ('10000000-0000-0000-0000-000000000001', 'Instruir processo', 10, 20);
select results_eq(
  $$select simples from public.tipos_processo where nome = 'Instruir processo'$$,
  array[false],
  'tipos_processo sem simples especificado assume default false'
);

reset role;

select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
