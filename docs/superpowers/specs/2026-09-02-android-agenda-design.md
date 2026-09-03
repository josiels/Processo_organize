# Agenda (design)

Data: 2026-09-02

## 0. Como ler este documento

Etapa 12 do ROADMAP.md antigo, o último item pendente do roadmap
pré-pivô — Dashboard "Início" (etapa 11) já está mesclado em master.
Depende de todo o backend/Android já existente — nenhuma tabela nova,
nenhuma RLS nova. Sub-projeto independente do Dashboard, tratado depois
por decisão do usuário.

## 1. Escopo

Substitui o placeholder `AgendaScreen.kt` (`Box` com o texto "Agenda") por
um calendário mensal funcional: grade de dias com indicador colorido nos
dias com prazo, navegação entre meses, e lista de processos com prazo no
dia selecionado (DESIGN.md, seção 6; REQUISITOS.md, seção 8).

## 2. Decisão de arquitetura: calendário feito do zero

Nenhuma biblioteca de calendário existe no projeto hoje, e todo o app usa
componentes de UI escritos à mão (nenhuma lib externa de UI em
`ui/components/`). **Decisão:** construir a grade do mês do zero, mesmo
padrão do resto do app. `java.time.YearMonth`/`LocalDate` (já em uso em
todo o app) cobre toda a matemática de dias/semanas necessária — sem
dependência nova.

## 3. Sem distinção por papel

Mesma justificativa do Dashboard Início (spec, seção 2): RLS de
`processos` já é org-wide para leitura, então a Agenda não tem lógica
condicional por papel.

## 4. Grade do calendário

Função pura nova, testável sem Android:

```kotlin
/**
 * Gera a grade do calendário para [mes]: uma lista de células (múltiplo de
 * 7, uma ou mais semanas completas), com `null` nas posições fora do mês
 * (antes do dia 1 ou depois do último dia) — semana começando no domingo.
 */
fun gerarGradeCalendario(mes: YearMonth): List<LocalDate?>
```

**Decisão:** semana começa no domingo (convenção do calendário nativo do
Android/Google Agenda no Brasil), não segunda — hardcoded, sem depender de
`Locale`/`WeekFields` (evita variação por configuração do aparelho).

## 5. Urgência do prazo (cor do indicador)

Função pura nova, reaproveitando o enum `StatusSemaforo` já existente (não
cria um vocabulário de cor novo):

```kotlin
/**
 * Urgência de um [prazo] em relação a [hoje] — vencido ou vence hoje é
 * CRÍTICO, dentro dos próximos 5 dias é ATENÇÃO, mais adiante é OK. Eixo
 * diferente do semáforo de "tempo parado na fase" (CalcularSemaforo.kt) —
 * este mede proximidade de um prazo, não duração numa fase.
 */
fun calcularUrgenciaPrazo(prazo: LocalDate, hoje: LocalDate): StatusSemaforo
```

Como todos os itens com o mesmo `prazoLimite` compartilham a mesma data, a
cor do indicador de um dia é só `calcularUrgenciaPrazo(dia, hoje)` — não
precisa agregar por item (um dia nunca tem indicadores de cores
diferentes).

## 6. Fonte de dados

Modelo novo, em `domain.model` (mesmo motivo do `ProcessoResumoDashboard`
do Dashboard Início — mantém `ui.agenda` desacoplado de `ui.processos`):

```kotlin
data class PrazoAgendaItem(
    val processoId: String,
    val numero: String,
    val objeto: String,
    val faseNome: String,
    val prazoLimite: LocalDate
)
```

`AgendaViewModel` combina `processoRepository.observarTodos()`,
`historicoDao.observarTodosAtivos()` (já existe desde o Plano 2B — a
entrada ativa de cada processo, única fonte de `prazoLimite` relevante) e
`faseRepository.observarTodas()` → lista de `PrazoAgendaItem`, restrita a
`status_geral = EM_ANDAMENTO` e `prazoLimite != null` (mesmo critério já
usado no card "Próximos prazos" do Dashboard Início). Nenhuma consulta
nova — reaproveita tudo. Nenhuma paginação/cache — mesmo padrão "org
pequena, tabela pequena" já assumido no resto do app.

## 7. Tela

- Cabeçalho com mês/ano por extenso + setas de navegação anterior/
  próximo mês — estado local da tela (`remember`), não do ViewModel:
  trocar de mês é um filtro em memória sobre a lista já carregada, não
  toca o Room.
- Grade de 7 colunas (dom-sáb) × semanas do mês (`gerarGradeCalendario`);
  dias fora do mês ficam em branco; dia de hoje tem destaque visual (borda
  ou cor de fundo diferenciada); dias com prazo mostram um indicador
  (bolinha) colorido por `calcularUrgenciaPrazo`.
- **Decisão:** dia selecionado por padrão é hoje (abre a tela já
  mostrando os prazos de hoje, se houver) — usuário toca outro dia da
  grade para trocar a seleção (estado local da tela).
- Abaixo da grade: lista dos itens do dia selecionado (número + objeto +
  fase), ou uma mensagem "Nenhum prazo neste dia" quando vazia. Tocar um
  item navega direto para `ProcessoDetalheScreen` (rota
  `ProcessoDetalhe(processoId)`, já existe) — mesmo padrão dos cards do
  Dashboard Início.

## 8. Navegação

Em `AppNavHost.kt`, `composable<Agenda> { AgendaScreen() }` ganha
`onProcessoClick`, mesmo padrão já usado em `composable<Inicio>`.

## 9. Testes

Teste unitário para `gerarGradeCalendario` (mês começando em cada dia da
semana — 7 casos ou um subconjunto representativo —, confirmando
deslocamento inicial correto, todos os dias do mês presentes na ordem
certa, e tamanho múltiplo de 7) e para `calcularUrgenciaPrazo` (prazo
vencido, prazo hoje, prazo em 5 dias — limite exato, prazo em 6 dias,
prazo bem no futuro). Verificação funcional em emulador: abrir a Agenda
com processos reais tendo prazos em datas variadas e confirmar que os
indicadores e a lista do dia batem com os dados reais.

## 10. Fora de escopo

Qualquer mudança em `AvancarFaseScreen`, aba Timeline, Dashboard, ou nas
regras de notificação. Visualização de semana/dia (só mês). Múltiplos
indicadores por dia distinguindo processos diferentes (um indicador só,
pela urgência mais alta do dia — que na prática é a única urgência do dia,
ver seção 5). Edição de prazo pela própria Agenda (prazo só é editado na
tela de Avançar Fase, já existente).
