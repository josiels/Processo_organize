# Dashboard "Início" (design)

Data: 2026-09-02

## 0. Como ler este documento

Etapa 11 do ROADMAP.md antigo, retomada agora que o pivô multiusuário
(Planos 1/2A-2D) e a sub-etapa "tipo de processo simples vs. com etapas"
já estão mesclados em master. A pivot spec (seção 12) e o ROADMAP original
já descreviam esta tela; este documento fecha o design real, adaptado ao
modelo multiorganização e ao novo conceito de processo simples. Depende
de todo o backend/Android já existente — nenhuma tabela nova, nenhuma RLS
nova. A tela "Agenda" (etapa 12) é um sub-projeto separado, tratado depois
deste por decisão do usuário.

## 1. Escopo

Substitui o placeholder `InicioScreen.kt` (`Box` com o texto "Início") por
um dashboard funcional: cabeçalho com saudação, card de progresso geral,
resumo curto de atualizações recentes, dois cards de destaque roláveis
("Processos críticos" e "Próximos prazos"), botão "Ver todos os
processos". Nenhuma tela existente muda de comportamento, exceto uma
pequena adição em `ProcessoRepository.atualizar()` (seção 4) e uma nova
consulta em `ProcessoFaseHistoricoDao` (seção 3).

## 2. Sem distinção por papel

A política RLS `processos_select` já é org-wide para leitura — `admin` e
`usuario` veem exatamente os mesmos processos. O Dashboard não tem
nenhuma lógica condicional por papel; só a saudação usa o nome do perfil
logado (`SupabaseSessionManager.perfilAtual.value?.nome`).

## 3. Fonte de dados

Reaproveita os repositórios/DAOs já existentes, mesmo padrão de
`ProcessoListViewModel` (spec do Plano 2B, seção 4.2): `processoRepository.observarTodos()`,
`faseRepository.observarTodas()`, `historicoDao.observarTodosAtivos()`
(semáforo/prazo da fase corrente de cada processo).

**Uma consulta nova é necessária** para o resumo de "atualizações
recentes" (seção 5): `observarTodosAtivos()` só devolve a entrada ATIVA de
cada processo, mas uma fase que avançou hoje fecha a entrada antiga e abre
uma nova — a antiga não aparece mais em `observarTodosAtivos()`, mas sua
`dataSaida` de hoje já é capturada pela nova entrada ativa. Ainda assim,
processos que avançaram de fase e SAÍRAM do estado "ativo hoje" por
qualquer motivo (nenhum caso real neste modelo, mas por clareza) e,
principalmente, para não depender de uma leitura incompleta, adiciona-se:

```kotlin
// ProcessoFaseHistoricoDao.kt
/** Toda a tabela (a RLS já limita à organização) — usado pelo Dashboard para achar avanços de fase de hoje, mesmo em entradas já fechadas. */
@Query("SELECT * FROM processo_fase_historico")
fun observarTodos(): Flow<List<ProcessoFaseHistoricoEntity>>
```

Já populada pelo sync existente (`HistoricoFaseRepository.sincronizarTodos()`,
chamado no login e via Realtime) — nenhuma mudança de sincronização
necessária, só uma nova forma de ler o que já está no Room.

## 4. Ajuste em `ProcessoRepository.atualizar()`

Hoje `processos.atualizado_em` só é tocado pelas RPCs `avancar_fase` e
`designar_processo` — uma edição direta de campo via `ProcessoFormScreen`,
ou a conclusão de um processo simples via `ProcessoDetalheViewModel.concluir()`
(ambos passam por `ProcessoRepository.atualizar()`, que faz um `update`
comum via Postgrest), NÃO bumpam essa coluna. Sem correção, esses dois
eventos ficariam invisíveis para o resumo de "atualizações recentes"
(seção 5).

**Decisão:** `ProcessoRepository.atualizar()` passa a sempre incluir
`atualizado_em` no payload:

```kotlin
val linhaProcesso = buildJsonObject {
    put("numero", processo.numero)
    put("objeto", processo.objeto)
    put("descricao", processo.descricao)
    put("orgao_demandante", processo.orgaoDemandante)
    put("tipo_processo_id", processo.tipoProcessoId)
    put("valor_estimado_total", valorTotal)
    put("status_geral", processo.statusGeral.name.lowercase())
    put("atualizado_em", Instant.now().toString())
}
```

Sem trigger de banco novo — é só uma linha a mais no JSON já montado pelo
app (mesmo padrão de correção usado no `DeviceTokenRepository.registrar()`
do Plano 2D). Cobre as três fontes de "atualização" que não passam por
`processo_fase_historico`: edição direta de campo, conclusão de processo
simples, e (efeito colateral aceitável) uma edição que não muda nada de
fato ainda conta como "atualização" — irrelevante na prática, ninguém abre
o formulário e salva sem mudar nada.

## 5. Resumo de atualizações recentes

Texto curto, sem feed detalhado (REQUISITOS.md, seção 8): "N processos
atualizados hoje". N = contagem de processos distintos que têm OU uma
entrada de `processo_fase_historico` com `dataEntrada = hoje` (avanço/
retorno de fase) OU `processos.atualizado_em` na data de hoje (edição
direta, conclusão de processo simples, designação). União simples de dois
conjuntos de IDs — lógica trivial demais para uma função pura própria
(diferente dos itens 6 e 7, que têm critério de filtro/ordenação real a
testar); fica inline no ViewModel.

## 6. Card de progresso geral

"X de Y processos em dia" + barra de progresso + percentual numérico
(DESIGN.md, seção 2). Y = processos com `status_geral = EM_ANDAMENTO`
(processos concluídos/cancelados/suspensos saem do cálculo — não faz
sentido medir semáforo de tempo parado de algo que já terminou ou está
suspenso). X = quantos desses têm semáforo de tempo parado na fase = OK
(mesmo `calcularSemaforo` já usado na lista de Processos e no Detalhe).

Função pura nova:

```kotlin
data class ProgressoGeral(val emDia: Int, val total: Int) {
    val percentual: Int get() = if (total == 0) 0 else (emDia * 100) / total
}

fun calcularProgressoGeral(processos: List<ProcessoResumoDashboard>): ProgressoGeral
```

## 7. Cards de destaque roláveis

Dois cards horizontais roláveis (DESIGN.md, seção 2), cada um com uma
mini-lista tocável (top 5) de número + objeto — tocar um item navega
direto para `ProcessoDetalheScreen` (rota `ProcessoDetalhe(processoId)`,
já existe). Ambos restritos a `status_geral = EM_ANDAMENTO` (mesmo
raciocínio da seção 6).

- **"Processos críticos"**: semáforo de tempo parado na fase = CRÍTICO.
- **"Próximos prazos"**: `processo_fase_historico.prazoLimite` (da entrada
  ativa) mais próximo, ordenado ascendente. **Decisão:** exclui prazos já
  vencidos (`prazoLimite < hoje`) — um prazo vencido não é "próximo", e já
  aparece coberto pelo sinal de "críticos" se o processo cruzar o limiar de
  dias de alerta da fase.

Duas funções puras novas:

```kotlin
fun selecionarProcessosCriticos(processos: List<ProcessoResumoDashboard>, limite: Int = 5): List<ProcessoResumoDashboard>

fun selecionarProximosPrazos(processos: List<ProcessoResumoDashboard>, hoje: LocalDate, limite: Int = 5): List<ProcessoResumoDashboard>
```

Modelo de dados compartilhado pelas duas (novo, em `domain.model` — não
reusa `ProcessoListItem` de `ui.processos`, que pertence a outra tela e
não tem `prazoLimite`; mantém `ui.inicio` desacoplado de `ui.processos`):

```kotlin
data class ProcessoResumoDashboard(
    val id: String,
    val numero: String,
    val objeto: String,
    val statusGeral: StatusGeralProcesso,
    val statusSemaforo: StatusSemaforo,
    val prazoLimite: LocalDate?
)
```

## 8. Cabeçalho e botão de destaque

Saudação por horário do dia (`Bom dia`/`Boa tarde`/`Boa noite`, limites às
12h/18h) + nome do perfil logado. Botão "Ver todos os processos" navega
para a rota `Processos` já existente, sem parâmetros — nenhuma mudança de
navegação.

## 9. Testes

Teste unitário para as 3 funções puras novas (`calcularProgressoGeral`,
`selecionarProcessosCriticos`, `selecionarProximosPrazos`), cobrindo:
lista vazia, todos em dia, todos críticos, `total = 0` (sem dividir por
zero), prazos vencidos excluídos, limite de 5 itens respeitado, processos
concluídos/suspensos excluídos do denominador e das duas listas.
Verificação funcional em emulador: abrir a aba Início com processos reais
em diferentes estados e confirmar que os números batem com a lista de
Processos.

## 10. Fora de escopo

Filtro pré-aplicado na navegação para a lista de Processos (os cards já
navegam direto para o Detalhe do processo, não precisam de filtro — ver
seção 7). Qualquer mudança em `AvancarFaseScreen`, aba Timeline, ou nas
regras de notificação. Tela "Agenda" (etapa 12, sub-projeto separado).
Cache/paginação do resumo — a organização é pequena, mesmo padrão "org
pequena, tabela pequena" já usado no resto do app.
