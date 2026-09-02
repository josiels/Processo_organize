-- Sub-etapa "tipo de processo simples vs. com etapas" (ver spec
-- docs/superpowers/specs/2026-09-02-android-tipo-processo-simples-design.md,
-- seção 2). Um tipo simples usa sempre a mesma fase única, escolhida no
-- cadastro do tipo em vez de escolhida por processo.
alter table public.tipos_processo
  add column simples boolean not null default false,
  add column fase_padrao_id uuid references public.fases (id);

alter table public.tipos_processo
  add constraint tipos_processo_fase_padrao_quando_simples
  check (not simples or fase_padrao_id is not null);
