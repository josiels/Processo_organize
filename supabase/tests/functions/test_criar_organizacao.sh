#!/usr/bin/env bash
set -euo pipefail

SUPER_ADMIN_EMAIL="${1:?usage: test_criar_organizacao.sh <super_admin_email> <super_admin_senha> <anon_key> <api_url>}"
SUPER_ADMIN_PASSWORD="${2:?}"
ANON_KEY="${3:?}"
API_URL="${4:?}"

JWT=$(curl -s -X POST "$API_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $ANON_KEY" -H "Content-Type: application/json" \
  -d "{\"email\":\"$SUPER_ADMIN_EMAIL\",\"password\":\"$SUPER_ADMIN_PASSWORD\"}" \
  | node -e "process.stdin.once('data', d => console.log(JSON.parse(d).access_token))")

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/functions/v1/criar-organizacao" \
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" \
  -d '{"nome_organizacao":"Setor de Teste","admin_nome":"Admin Teste","admin_email":"admin.teste@local.test","admin_senha":"senha-teste-123"}')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

echo "HTTP $HTTP_CODE: $BODY"
if [ "$HTTP_CODE" != "200" ]; then
  echo "FAIL: expected HTTP 200"
  exit 1
fi
echo "$BODY" | grep -q "organizacao_id" || { echo "FAIL: response missing organizacao_id"; exit 1; }
echo "PASS"
