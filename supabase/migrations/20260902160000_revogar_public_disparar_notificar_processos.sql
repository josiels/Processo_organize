-- A migration da Task 4 (agendar_notificar_processos) esqueceu de revogar o
-- acesso de `public`, ao contrário das 4 funções de elegibilidade da Task 2
-- (achado da revisão final do Plano 2D). Sem isto, PostgREST expõe
-- POST /rpc/disparar_notificar_processos para anon/authenticated — não
-- explorável hoje (essas roles não têm usage no schema vault), mas é
-- superfície gratuita, e o job do pg_cron roda como o role que agendou o
-- job (não via PostgREST), então esta revogação não afeta o cron.
revoke all on function public.disparar_notificar_processos() from public;
grant execute on function public.disparar_notificar_processos() to service_role;
