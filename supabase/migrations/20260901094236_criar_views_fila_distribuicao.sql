create view public.fila_distribuicao
  with (security_invoker = true) as
select
  p.id as perfil_id,
  p.organizacao_id,
  p.nome,
  p.ultimo_recebimento_em,
  p.criado_em,
  count(d.id) as total_designacoes
from public.perfis p
left join public.designacoes d on d.perfil_id = p.id
where p.ativo
group by p.id;

create view public.fila_distribuicao_por_tipo
  with (security_invoker = true) as
select
  d.perfil_id,
  p.organizacao_id,
  d.tipo_processo_id,
  tp.nome as tipo_processo_nome,
  count(*) as total
from public.designacoes d
join public.perfis p on p.id = d.perfil_id
join public.tipos_processo tp on tp.id = d.tipo_processo_id
group by d.perfil_id, p.organizacao_id, d.tipo_processo_id, tp.nome;
