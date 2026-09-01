# Supabase backend (cloud project)

This backend targets a real cloud Supabase project — there is no local Docker
stack. All commands read credentials from `supabase/.env.local` (gitignored,
never committed).

Run a migration or test file: `node scripts/run_sql.mjs <path-to-sql-file>`
Bootstrap a super_admin account: `node scripts/bootstrap_super_admin.mjs <email> <senha> [nome]`
Deploy an Edge Function: `npx supabase functions deploy <name> --use-api`

Plan 2 (Android data layer) connects the app to the `SUPABASE_URL` and
`SUPABASE_ANON_KEY` values in `supabase/.env.local`.
