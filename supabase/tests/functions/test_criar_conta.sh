#!/usr/bin/env bash
set -euo pipefail

ADMIN_EMAIL="${1:?usage: test_criar_conta.sh <admin_email> <admin_senha> <anon_key> <api_url>}"
ADMIN_PASSWORD="${2:?}"
ANON_KEY="${3:?}"
API_URL="${4:?}"

JWT=$(curl -s -X POST "$API_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $ANON_KEY" -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
  | node -e "process.stdin.once('data', d => console.log(JSON.parse(d).access_token))")

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/functions/v1/criar-conta" \
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" \
  -d '{"nome":"Usuário Teste","email":"usuario.teste@local.test","senha":"senha-teste-123","papel":"usuario"}')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

echo "HTTP $HTTP_CODE: $BODY"
if [ "$HTTP_CODE" != "200" ]; then
  echo "FAIL: expected HTTP 200"
  exit 1
fi
echo "$BODY" | grep -q "perfil_id" || { echo "FAIL: response missing perfil_id"; exit 1; }
echo "PASS"
