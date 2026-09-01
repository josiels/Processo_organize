import { createClient } from '@supabase/supabase-js';
import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(scriptDir, '..');

function loadEnvLocal() {
  const envPath = join(repoRoot, 'supabase', '.env.local');
  if (!existsSync(envPath)) return;
  for (const line of readFileSync(envPath, 'utf8').split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq === -1) continue;
    const key = trimmed.slice(0, eq).trim();
    const value = trimmed.slice(eq + 1).trim();
    if (!(key in process.env)) process.env[key] = value;
  }
}

async function main() {
  loadEnvLocal();

  const SUPABASE_URL = process.env.SUPABASE_URL;
  const SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const EMAIL = process.argv[2];
  const PASSWORD = process.argv[3];
  const NOME = process.argv[4] ?? 'Super Admin';

  if (!SUPABASE_URL || !SERVICE_ROLE_KEY) {
    console.error('Missing SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY — check supabase/.env.local.');
    process.exitCode = 1;
    return;
  }
  if (!EMAIL || !PASSWORD) {
    console.error('Usage: node scripts/bootstrap_super_admin.mjs <email> <senha> [nome]');
    process.exitCode = 1;
    return;
  }

  const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
    auth: { autoRefreshToken: false, persistSession: false }
  });

  const { data: userData, error: userError } = await admin.auth.admin.createUser({
    email: EMAIL,
    password: PASSWORD,
    email_confirm: true
  });
  if (userError) {
    console.error('Failed to create auth user:', userError.message);
    process.exitCode = 1;
    return;
  }

  const { error: perfilError } = await admin.from('perfis').insert({
    id: userData.user.id,
    organizacao_id: null,
    papel: 'super_admin',
    nome: NOME
  });
  if (perfilError) {
    console.error('Failed to create perfil row:', perfilError.message);
    process.exitCode = 1;
    return;
  }

  console.log(`super_admin created: ${EMAIL} (id: ${userData.user.id})`);
}

await main();
