-- Habilita as extensões necessárias para agendar e disparar a chamada HTTP
-- periódica para a Edge Function de notificações (spec do Plano 2D, seção 3).
-- Mesma convenção já usada para pgtap: `with schema extensions`.
create extension if not exists pg_cron with schema extensions;
create extension if not exists pg_net with schema extensions;
create extension if not exists supabase_vault with schema extensions;
