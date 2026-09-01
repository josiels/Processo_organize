# Plano 2C — Equipe e transparência (design)

Data: 2026-09-01

## 0. Como ler este documento

Terceira sub-etapa do Plano 2 — ver a spec de 2A, seção 8, para o roadmap
completo. Depende de 2A (fundação de dados/auth); não depende de 2B.
Mesma ressalva: escrito sem sessão interativa, decisões marcadas como
**Decisão (ruling)**, nenhum código até aprovação.

## 1. Escopo

Três telas novas, nenhuma tela existente é tocada: gerenciar equipe/contas
(admin), Fila de Distribuição (todos), configurações de notificação
(todos, pessoal).

## 2. Achado importante: mais um pequeno complemento necessário no backend

A Fila de Distribuição (spec original §4) precisa de uma contagem agregada
de designações por pessoa (e por pessoa+tipo, no detalhe). O PostgREST não
faz `GROUP BY` arbitrário a partir do cliente — a forma correta é uma
**view** no backend.

**Verifiquei empiricamente** (contra o projeto real, dentro de uma
transação `begin;...rollback;`, sem deixar rastro) que uma view comum
criada por uma migração (dona = papel usado pelo `run_sql.mjs`, que tem
privilégio elevado) **vaza dados entre organizações** — uma consulta como
usuário da Organização A viu registros de todas as organizações, porque o
Postgres por padrão executa a view com o privilégio do *dono* da view, não
de quem consulta, e o dono aqui ignora RLS. A correção é explícita:
`create view ... with (security_invoker = true) as ...` — testei esta
segunda versão e confirmei que aí sim o isolamento por organização funciona
corretamente (1 linha visível para o usuário da Organização A, das duas
linhas totais no banco).

**Decisão:** adicionar duas views ao backend já mesclado, na mesma migração
(ou uma nova pequena, seguindo a mesma convenção `run_sql.mjs`/pgTAP do
Plano 1):

```sql
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
```

Isso soma dois complementos ao backend originados do brainstorming do
Plano 2 (o outro é o `avancar_fase()` de 2A) — ambos pequenos, aditivos, e
usando exatamente a infraestrutura já validada do Plano 1. Recomendo
implementá-los juntos, num único incremento pequeno de backend antes de
começar o código Android de 2A/2C (ou como a primeira tarefa de cada plano
de implementação correspondente — a decidir na hora de escrever o plano).

## 3. Tela: gerenciar equipe/contas (admin)

- Lista os perfis da própria organização (via `PerfilRepository` de 2A —
  RLS já restringe à própria org automaticamente) — nome, papel, cargo,
  status ativo/inativo (só leitura, ver limitação abaixo).
- Botão "Criar conta" → formulário (tela cheia, não diálogo — evita
  qualquer risco do problema já conhecido de `DropdownField` em diálogo,
  ver Plano 2B §5) com nome, e-mail, senha inicial, e escolha de papel
  (`admin`/`usuario` — dois valores só, `RadioButton` em vez de dropdown).
  Ao confirmar, chama a Edge Function `criar-conta` (via
  `client.functions.invoke("criar-conta", ...)` do módulo `functions-kt`
  do `supabase-kt` — **nota: este módulo ainda não está nas dependências,
  precisa ser adicionado** — `postgrest-kt`/`auth-kt`/`storage-kt` já
  existem mas `functions-kt` não).
- **Limitação conhecida, herdada do Plano 1 (já registrada na revisão final
  daquele plano):** não existe endpoint de desativação de conta. Esta tela
  mostra o status `ativo` mas não oferece um jeito de mudá-lo — é
  puramente informativo por enquanto. Não é regressão desta etapa, é uma
  lacuna já conhecida e documentada, a fechar num plano futuro se/quando
  fizer sentido.
- **Decisão:** criação de *organização* (a ação exclusiva de
  `super_admin`) **não** ganha tela no app — é uma ação rara,
  administrativa, de bootstrap de uma nova organização inteira, mais
  apropriada para um script de linha de comando (mesmo padrão de
  `scripts/bootstrap_super_admin.mjs`) do que uma tela dentro de um app
  cujo público principal são `admin`/`usuario` de uma organização já
  existente. Se isso mudar de ideia, é fácil adicionar depois — só não é
  valor investir agora.

## 4. Tela: Fila de Distribuição

- Acessível a todos (transparência é o objetivo da tela).
- Lista pessoas da organização ordenadas por `ultimo_recebimento_em`
  ascendente, nulos primeiro (ordenados por `criado_em` entre si) — exige
  ordenação em duas colunas, que o Postgrest suporta via múltiplos
  parâmetros `order`.
- Ao lado de cada nome: `total_designacoes` da view `fila_distribuicao`.
- Ao tocar: navega para uma tela de detalhe simples que consulta
  `fila_distribuicao_por_tipo` filtrada por aquele `perfil_id`.
- **Decisão (reforça 2A §2.2):** esta tela não é cacheada no Room — é uma
  consulta de rede ao vivo toda vez que a tela abre (com um
  loading/estado de erro simples se não houver conexão; nada crítico
  depende desta tela funcionar offline).

## 5. Tela: configurações de notificação

- Três `AppToggle` (componente já existente): `notificar_avanco_fase`,
  `notificar_prazo`, `notificar_tempo_parado` — refletem e gravam direto
  os três campos booleanos do próprio `perfil` do usuário logado.
- Grava via `PerfilRepository.atualizarPreferenciasNotificacao(...)` →
  Postgrest `update` na própria linha de `perfis` (permitido pela política
  `perfis_update_propria_conta`, que não toca nos campos protegidos pelo
  trigger — sem conflito).
- Ocupa o card "Configurações" que já existe desabilitado ("Em breve") em
  `MaisScreen` — só precisa ser habilitado e ligado a esta tela nova.

## 6. Testes

Mesma abordagem das etapas anteriores: teste unitário para a lógica de
ordenação da fila (função pura, dado uma lista de perfis com
`ultimoRecebimentoEm` variados incluindo nulos, produz a ordem esperada).
Verificação funcional em emulador: criar uma conta via a tela nova e
confirmar login real com ela; conferir que a fila reflete designações reais
feitas no Plano 2B; conferir que o toggle de notificação persiste entre
reaberturas do app.

## 7. Fora de escopo (nesta sub-etapa 2C)

Desativação de conta (ver §3), criação de organização via UI (ver §3),
notificações push de fato — esta etapa só grava a *preferência*, o envio
real é o Plano 2D. Anexos, dashboard Início, Agenda seguem fora de escopo
do pivô inteiro.
