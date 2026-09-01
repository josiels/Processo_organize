#!/usr/bin/env node
// Runs a .sql file against the linked cloud Supabase project over HTTPS
// (the Management API's /database/query endpoint), since this network
// blocks outbound direct Postgres connections (ports 5432/6543) that
// `supabase db push` / `supabase test db` / `psql` would need.
//
// Usage: node scripts/run_sql.mjs <path-to-sql-file>
//
// For pgTAP test files: exit code reflects pass/fail by checking for a
// "summary" column equal to exactly "ALL TESTS PASSED" (see the
// finish()-aggregation convention documented in the plan). For plain
// migrations: exit code reflects the HTTP response status only.
//
// Uses process.exitCode (not process.exit()) throughout: calling
// process.exit() right after an awaited fetch() has crashed with a
// libuv assertion on this machine's Node/Windows combination.

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

  const [, , sqlFilePath] = process.argv;
  if (!sqlFilePath) {
    console.error('Usage: node scripts/run_sql.mjs <path-to-sql-file>');
    process.exitCode = 1;
    return;
  }

  const projectRef = process.env.SUPABASE_PROJECT_REF;
  const accessToken = process.env.SUPABASE_ACCESS_TOKEN;
  if (!projectRef || !accessToken) {
    console.error('Missing SUPABASE_PROJECT_REF / SUPABASE_ACCESS_TOKEN — check supabase/.env.local.');
    process.exitCode = 1;
    return;
  }

  const query = readFileSync(sqlFilePath, 'utf8');

  const response = await fetch(`https://api.supabase.com/v1/projects/${projectRef}/database/query`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query }),
  });

  const body = await response.json();

  if (!response.ok) {
    console.error(`FAIL (HTTP ${response.status}):`, body.message ?? JSON.stringify(body));
    process.exitCode = 1;
    return;
  }

  console.log(JSON.stringify(body, null, 2));

  const summaryRow = Array.isArray(body) ? body.find((row) => 'summary' in row) : null;
  if (summaryRow) {
    if (summaryRow.summary === 'ALL TESTS PASSED') {
      console.log('RESULT: PASS');
      process.exitCode = 0;
      return;
    }
    console.error('RESULT: FAIL —', summaryRow.summary);
    process.exitCode = 1;
    return;
  }

  process.exitCode = 0;
}

await main();
