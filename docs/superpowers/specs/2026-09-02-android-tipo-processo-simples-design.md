# Tipo de processo simples vs. com etapas (design)

Data: 2026-09-02

## 0. Como ler este documento

Sub-etapa nova do Plano 2, descoberta durante o brainstorming das etapas
11-12 do ROADMAP antigo (Dashboard "Início" e Agenda). Não é uma etapa do
roadmap original — é um achado de negócio: nem todo processo cadastrado
precisa do fluxo completo de múltiplas fases (ex: "inserir documento",
"responder fornecedor" são tarefas de uma etapa só; "instruir processo" é
multi-etapa). Depende dos Planos 1/2A/2B (fundação, fluxos por papéis) já
mesclados em master. Precede e informa o design do Dashboard, cuja métrica
de "progresso geral" precisa saber lidar com os dois tipos de processo.

## 1. Escopo

Nenhuma tela nova. Três telas existentes ganham um comportamento
condicional: Cadastro de Tipos de Processo, criação de Processo, e detalhe
do Processo. Zero mudança nas telas de Avançar Fase, Timeline, Dashboard ou
nas regras de notificação — o objetivo explícito é reaproveitar 100% da
infraestrutura de `Fase`/`processo_fase_historico`/semáforo já existente,
só simplificando a experiência de criar e concluir um processo de uma
etapa só.

## 2. Modelo de dados

`public.tipos_processo` ganha duas colunas:

```sql
alter table public.tipos_processo
  add column simples boolean not null default false,
  add column fase_padrao_id uuid references public.fases (id);

alter table public.tipos_processo
  add constraint tipos_processo_fase_padrao_quando_simples
  check (not simples or fase_padrao_id is not null);
```

**Decisão:** o `check` garante que todo tipo marcado como `simples` tem uma
`fase_padrao_id` preenchida (não pode salvar um tipo simples sem escolher a
fase única). Não existe validação de banco garantindo que `fase_padrao_id`
pertence à mesma `organizacao_id` do tipo — o dropdown de escolha no app já
só lista fases da própria organização (mesmo padrão de todo outro dropdown
de Fase no app), então a única forma de uma referência cruzada acontecer é
manipulação direta do banco fora do app, fora do modelo de ameaça deste
projeto.

`TipoProcessoEntity` (Room) ganha os mesmos dois campos:
`simples: Boolean` e `fasePadraoId: String?`. `TipoProcessoDto` e
`TipoProcessoRepository.salvar(...)` recebem os parâmetros correspondentes.

## 3. Tela: Cadastro de Tipos de Processo

`TipoProcessoFormDialog` ganha um `AppToggle` "Processo simples (uma única
etapa)?" logo abaixo dos campos de dias de alerta. Quando ligado, aparece
um `DropdownField` "Fase única deste tipo" — populado pelo mesmo catálogo
de `Fase` da organização já usado em `ProcessoFormScreen`, obrigatório para
salvar enquanto o toggle estiver ligado (espelha o `check` do banco no
lado do app, mesmo padrão de validação client-side já usado em
`estado.valido` de outras telas).

Os dois campos de dias de alerta (`diasAlertaAtencao`/`diasAlertaCritico`)
continuam existindo e sendo usados para os dois tipos (simples ou com
etapas) — são o que alimenta o semáforo do processo através da fase
(única, para tipo simples; escolhida por avanço de fase, para tipo com
etapas).

## 4. Tela: criação de processo (`ProcessoFormScreen`)

Quando o `TipoProcesso` selecionado no dropdown "Tipo de processo" tem
`simples = true`:

- O `DropdownField` "Fase inicial" some da tela.
- `ProcessoFormViewModel` preenche `faseSelecionadaId` automaticamente com
  o `fasePadraoId` do tipo selecionado, assim que o usuário escolhe o
  tipo (antes de qualquer seleção manual de fase — se o usuário trocar de
  tipo simples para outro tipo simples, a fase-padrão é re-preenchida; se
  trocar para um tipo com etapas, o campo passa a exigir escolha manual
  como hoje).
- Nenhuma mudança em `ProcessoRepository.criar()` — a linha inicial de
  `processo_fase_historico` continua sendo criada exatamente como hoje
  (fix do hotfix pré-2D), só que com uma fase já decidida pelo tipo em vez
  de escolhida pelo usuário na hora.

Quando o tipo tem `simples = false` (ou nenhum tipo foi escolhido ainda): a
tela se comporta exatamente como hoje, sem nenhuma mudança.

## 5. Tela: detalhe do processo (`ProcessoDetalheScreen` → `AbaDadosGerais`)

O botão único "Avançar fase" (hoje sempre navega para `AvancarFaseScreen`)
passa a checar o `simples` do `TipoProcesso` do processo:

- **Tipo com etapas (comportamento atual, sem mudança):** botão
  "Avançar fase", navega para `AvancarFaseScreen`.
- **Tipo simples:** botão vira "Concluir processo" — não navega para
  nenhuma tela nova. Chama diretamente
  `ProcessoRepository.atualizar(...)` (método já existente) com
  `statusGeral = StatusGeralProcesso.CONCLUIDO`, mantendo os demais campos
  do processo intactos. Sem RPC nova, sem fechar/reabrir linha de
  `processo_fase_historico` (a linha ativa da fase única permanece aberta
  — não há "próxima fase" para a qual fechar essa entrada; ver §7 sobre a
  aba Timeline).
- Botão só aparece quando `estado.podeEditar` é verdadeiro, igual hoje —
  nenhuma mudança na regra de permissão (dono/admin/órfão).

**Decisão:** processo simples concluído continua editável normalmente
depois (reabrir para corrigir campos, mudar `status_geral` de volta), pelo
mesmo caminho de edição que já existe — não há necessidade de um fluxo de
"reabertura" dedicado.

## 6. Impacto no Dashboard e nas notificações

Nenhum. Um processo de tipo simples ainda tem exatamente uma linha ativa
em `processo_fase_historico` (a fase-padrão do tipo), então:

- `calcularSemaforo`/"dias parado na fase" funciona sem mudança — mede
  dias desde a criação, comparado aos limites de alerta da fase-padrão.
- A métrica de "progresso geral" do Dashboard (a implementar na próxima
  sub-etapa) não precisa de lógica especial para tipo simples — ela já
  vai contar corretamente com base no semáforo + `status_geral`.
- As 4 regras de notificação (avanço de fase, prazo, tempo parado na fase,
  tempo desde designado) continuam se aplicando sem distinção — um
  processo simples parado além do limite de alerta gera notificação de
  "tempo parado na fase" normalmente (o que faz sentido: mesmo uma tarefa
  de etapa única pode ficar esquecida).

## 7. Timeline (aba já existente em `ProcessoDetalheScreen`)

Um processo de tipo simples mostra uma Timeline com uma única entrada (a
fase-padrão, sem `data_saida` mesmo depois de concluído — como descrito em
§5). Nenhuma mudança de código na aba Timeline: ela já lista quantas
entradas de histórico existirem, e uma lista de um item só é um caso já
coberto pela implementação atual.

## 8. Testes

Teste unitário para a lógica de preenchimento automático de
`faseSelecionadaId` em `ProcessoFormViewModel` (dado um tipo simples com
`fasePadraoId` definido, selecionar esse tipo preenche a fase
automaticamente; dado um tipo com etapas, selecionar o tipo não altera a
fase já escolhida manualmente). Teste unitário para a condição do botão
"Avançar fase"/"Concluir processo" em `ProcessoDetalheViewModel` ou
equivalente (dado um processo de tipo simples vs. com etapas, o rótulo e a
ação corretos são expostos ao Composable). Verificação funcional em
emulador: cadastrar um tipo simples, criar um processo desse tipo,
confirmar que a tela de criação não pede fase, concluir pelo botão novo, e
conferir que ele aparece como "Concluído" na lista de Processos.

## 9. Fora de escopo

Migração de tipos de processo já existentes para `simples = true` (o
`default false` cobre todos os tipos cadastrados antes desta sub-etapa —
eles continuam com etapas normalmente; o admin decide manualmente quais
tipos futuros marcar como simples). Qualquer mudança na tela de Avançar
Fase, Timeline (além do já coberto em §7), Dashboard ou Agenda — essas
seguem como sub-etapas separadas. Um fluxo de "reabertura" dedicado para
processo simples concluído (ver §5). Validação de banco garantindo que
`fase_padrao_id` pertence à mesma organização do tipo (ver §2).
