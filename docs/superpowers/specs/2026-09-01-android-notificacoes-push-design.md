# Plano 2D — Notificações push (design)

Data: 2026-09-01

## 0. Como ler este documento

Quarta e última sub-etapa do Plano 2 — ver a spec de 2A, seção 8, para o
roadmap completo. **Bloqueada por uma dependência externa que só você pode
resolver** (§2) — por isso este documento é mais enxuto que 2A/2B/2C:
detalha o suficiente para você validar a direção, mas não vale a pena
aprofundar cada detalhe de UI antes de saber que a base (credenciais)
existe.

## 1. Escopo

Os 4 gatilhos de notificação da spec original (§7): processo avançou de
fase (notifica admins), prazo se aproximando, dias parado na fase, dias
desde designado (estes três notificam o responsável atual).

## 2. Duas dependências externas — não uma só

A spec original (§13) já registrava a necessidade de um projeto Firebase +
`google-services.json`. Ao desenhar esta etapa, identifiquei que **na
verdade são duas credenciais distintas**, fáceis de confundir:

1. **`google-services.json`** — configura o app Android (cliente) para
   conseguir *receber* push do Firebase Cloud Messaging. Vai no projeto,
   não é segredo de servidor (é normal em apps Android, mas mesmo assim
   não deveria ir para um repositório público sem necessidade).
2. **Uma credencial de servidor para *enviar* push** — a Edge Function
   (rodando no Supabase, não no celular) precisa se autenticar contra a
   API do FCM para efetivamente disparar as notificações. Isso é uma
   **conta de serviço do Firebase** (JSON de service account, usado com
   OAuth2 para a FCM HTTP v1 API — o método atual recomendado pelo
   Google; a antiga "server key" legada está em processo de descontinuação
   pelo Google e não deveria ser a escolha para um projeto novo). Esta
   credencial é um segredo de verdade — vai como variável de ambiente da
   Edge Function (`supabase secrets set`), nunca commitada, mesmo padrão
   de disciplina já usado com a service role key do Plano 1.

**Antes de implementar esta etapa, preciso que você:**
1. Crie um projeto no Firebase Console.
2. Adicione o app Android a esse projeto e baixe o `google-services.json`.
3. Gere uma conta de serviço (Project Settings → Service Accounts → Generate
   new private key) e me forneça esse JSON (ou, melhor, configure você
   mesmo via `supabase secrets set` quando chegarmos nesta etapa, para o
   segredo nunca passar por mim/pelo chat).

Sem isso, dá para escrever o código desta etapa, mas não dá para testar
nada de ponta a ponta — mesma situação do Docker/rede no Plano 1.

## 3. Backend: job agendado

**Decisão:** usar a extensão `pg_cron` (já disponível em projetos Supabase)
agendando uma chamada HTTP (via `pg_net`, também já disponível) para uma
nova Edge Function `notificar-processos`, a cada poucos minutos (a spec
original não fixa um intervalo exato — sugiro 5 minutos como ponto de
partida, ajustável depois sem migração, já que fica no agendamento do
`cron.schedule`, não no código).

A Edge Function, a cada execução:
1. Consulta processos cujo estado mudou desde a última checagem (precisa
   de uma forma de saber "desde quando" — **decisão**: uma tabela pequena
   `public.notificacoes_enviadas_controle` guardando o timestamp da última
   execução bem-sucedida, ou comparar `atualizado_em`/`criado_em` contra
   `now() - intervalo`, evitando estado adicional — a segunda opção é mais
   simples e evita um ponto de falha a mais; prefiro ela, mas o intervalo
   de checagem tem que ser maior que o intervalo do cron para não perder
   eventos entre execuções, ex: cron a cada 5 min, checa mudanças dos
   últimos 10 min).
2. Para cada um dos 4 gatilhos (§1), monta a lista de destinatários
   (admins da org, ou o responsável atual, conforme o gatilho) filtrando
   pelo toggle de preferência correspondente (`notificar_avanco_fase`,
   etc.) e por `ativo = true`.
3. Busca os `device_tokens` de cada destinatário e envia via FCM HTTP v1
   API (usando a conta de serviço de §2).
4. Usa a service role key (já disponível como secret da Edge Function
   desde o Plano 1) para as consultas — roda com privilégio total,
   igual às duas Edge Functions já existentes.

## 4. Cliente Android

- Adiciona `firebase-messaging` (BOM do Firebase) + plugin
  `com.google.gms.google-services` ao `build.gradle.kts` — nenhuma dessas
  dependências existe hoje no projeto (confirmado na exploração do
  código).
- Uma `FirebaseMessagingService` própria:
  - `onNewToken(token)`: chama `DeviceTokenRepository.registrar(token)`
    (novo repositório de 2A/2D, insere/atualiza em `device_tokens` via
    Postgrest, vinculado ao `perfil_id` da sessão atual).
  - `onMessageReceived(message)`: exibe a notificação local (usando a API
    de notificações do Android, permissão `POST_NOTIFICATIONS` já
    declarada no manifesto) com deep-link para o processo relevante
    (`ProcessoDetalhe(processoId)`, já existe como rota).
- Ao logout (2A §2.6): **não** apaga o `device_tokens` do aparelho — só
  desassocia da sessão local. Isso é intencional: o token continua válido
  para o Firebase, e se a mesma pessoa logar de novo no mesmo aparelho, o
  próximo `onNewToken` (ou uma leitura do token atual no login) resolve
  sozinho. Se quiser que trocar de conta no mesmo aparelho remova o
  registro antigo explicitamente, isso é uma decisão a revisitar — não é
  crítico para o funcionamento básico.

## 5. Testes

Sem Firebase configurado, o máximo que dá para testar agora é a lógica pura
do lado do backend (quais processos/pessoas deveriam ser notificados dado
um estado do banco — testável via pgTAP, sem precisar realmente enviar
nada) e a lógica pura do lado do cliente (parsing da mensagem recebida,
montagem do deep-link). O envio real de ponta a ponta só é testável depois
das credenciais de §2 existirem.

## 6. Fora de escopo (nesta sub-etapa 2D)

Notificação instantânea por trigger de banco (a spec original §12 já
escolheu cron periódico deliberadamente); qualquer canal de notificação
além de push (e-mail, SMS) — não foi pedido em nenhum momento.
