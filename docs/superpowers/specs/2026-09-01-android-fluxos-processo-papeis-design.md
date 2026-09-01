# Plano 2B — Fluxos de processo adaptados aos papéis (design)

Data: 2026-09-01

## 0. Como ler este documento

Segunda sub-etapa do Plano 2 — ver
`docs/superpowers/specs/2026-09-01-android-fundacao-dados-auth-design.md`
seção 8 para o roadmap completo. Depende inteiramente de 2A (fundação de
dados/auth) já estar aprovada e implementada — nenhuma decisão aqui muda se
2A mudar, mas o código desta etapa não compila sem o de 2A.

Mesma ressalva de 2A: escrito sem sessão interativa, decisões marcadas como
**Decisão (ruling)** para revisão, nenhum código escrito até aprovação.

## 1. Contexto

Com a fundação de dados pronta (Room-cache, repositórios que falam com
Postgrest, sessão/papel do usuário disponíveis), esta etapa reescreve as
telas de processo existentes para operar sobre o modelo multiusuário e
adiciona o que falta: cadastro de Tipo de Processo, UI de diligências, e a
remoção definitiva de Pessoa.

## 2. Regra de permissão por ação (fonte da verdade: RLS do backend)

A UI não inventa regra nova — cada decisão de mostrar/esconder uma ação
reflete exatamente o que a RLS do Plano 1 já permite (a RLS é a barreira
real; a UI só evita que o usuário tente uma ação que o servidor vai
rejeitar, para uma experiência melhor):

| Ação | Quem pode | RLS correspondente |
|---|---|---|
| Criar processo | Só `admin` | `processos_admin_insere` |
| Editar campos gerais do processo (objeto, valor, etc.) | `admin`, ou dono atual, ou qualquer um se órfão | `processos_update` |
| Designar/reatribuir a qualquer pessoa | Só `admin` | `designar_processo()` — ramo admin |
| Autoatribuir um processo órfão a si mesmo | Qualquer `usuario`/`admin` | `designar_processo()` — ramo não-admin, `responsavel_id is null` |
| Devolver um processo seu para órfão | Só quem é o dono atual | `designar_processo()` — ramo não-admin, devolução |
| Avançar fase / registrar diligência / editar observação | `admin`, dono atual, ou qualquer um se órfão | `itens_acesso`/`diligencias_acesso`/etc. (com o disjunto de órfão da rodada final de correção) |
| Ver qualquer processo da organização (detalhe, itens, histórico, diligências) | Todos (transparência) | as políticas `*_select_transparencia` |
| Cadastrar Fase / Tipo de Processo | Só `admin` | `fases_admin_escreve`/`tipos_processo_admin_escreve` |

## 3. Telas novas

### 3.1 Cadastro de Tipo de Processo

Espelha exatamente o padrão já existente de `FaseCadastroViewModel` /
`FasesScreen` / `FaseFormDialog` (arquivo por arquivo:
`TipoProcessoCadastroViewModel`, `TiposProcessoScreen`,
`TipoProcessoFormDialog`), usando o novo `TipoProcessoRepository` (de 2A) em
vez de `FaseRepository`. Campos do formulário: nome, dias de alerta
atenção/crítico (mesmos campos que Fase já tem, reaproveitando os mesmos
componentes visuais `DateField`/campos numéricos já existentes). Acessível
só a `admin` — a aba "Mais" ganha este card ao lado de "Cadastro de Fases".

### 3.2 Diligências dentro da fase

Dentro de `AvancarFaseScreen` (ou uma sub-seção do detalhe do processo — a
decidir no momento da implementação, mas a spec original (§3) diz que
diligências são "vinculadas à passagem específica pela fase", então o lugar
natural é dentro da tela onde já se vê a entrada de histórico ativa): uma
lista das diligências já registradas para a entrada de histórico atual
(mais recentes primeiro) + um botão "Registrar diligência" que abre um
diálogo simples de texto livre (sem formulário complexo — é só
`conteudo: text`, autor e timestamp vêm do servidor). Usa o novo
`DiligenciaRepository` de 2A.

**Decisão:** diligência é *append-only* na UI também — não há edição nem
exclusão de uma diligência já registrada (a RLS já não previa isso: as
políticas de escrita gates em `autor_id = auth.uid()` na rodada final de
correção cobrem INSERT/UPDATE, mas não há caso de uso descrito na spec para
editar um evento já registrado; a interpretação mais simples e alinhada ao
"mini-histórico de eventos" da spec §3 é tratar como log).

## 4. Telas ajustadas

### 4.1 `ProcessoFormViewModel` / `ProcessoFormScreen`

- Campo `tipo: String` (texto livre) vira um dropdown ligado a
  `TipoProcessoRepository.observarTodos()` — usa `DropdownField` (já
  existe, funciona bem fora de diálogo modal).
- Botão "Criar processo" só aparece se `SupabaseSessionManager` indicar
  papel `admin` (ver tabela §2). Edição de processo existente: campos
  ficam editáveis conforme a mesma regra de `processos_update` (dono,
  órfão, ou admin) — se nenhuma das três condições vale, a tela abre em
  modo somente-leitura em vez de esconder a tela inteira (mantém a
  transparência: qualquer um pode *ver* os dados, só não editar).
- `salvar()` chama o `ProcessoRepository` novo (2A), que fala com
  Postgrest; erro de rede vira a mensagem "sem conexão, tente novamente".

### 4.2 `ProcessoListViewModel` / `ProcessosScreen`

- Continua listando todos os processos da organização (RLS já garante
  isolamento entre organizações; transparência garante visibilidade
  completa dentro da mesma organização — a lista não precisa de filtro de
  papel).
- Resolve nome do responsável via `PerfilRepository` (novo) em vez de
  `PessoaRepository` (removido). Processo órfão mostra "Não designado" em
  vez de um nome.
- **Decisão:** a duplicação de cálculo de semáforo (hoje repetida
  idêntica em `ProcessoListViewModel` e `AvancarFaseViewModel`) vira uma
  função pura só uma vez —
  `calcularSemaforo(diasDecorridos: Int, atencao: Int, critico: Int):
  StatusSemaforo` em `domain/usecase/` — usada pelos dois semáforos agora
  independentes da spec (§5): "dias parado na fase" (contra os limites da
  `Fase`) e "dias desde designado" (contra os limites do `TipoProcesso`).
  Ambos aparecem lado a lado no card da lista e no detalhe.

### 4.3 `ProcessoDetalheViewModel` / `ProcessoDetalheScreen`

- Resolve responsável/histórico via `PerfilRepository`.
- Mostra `designadoEm`/`designadoPor` (novo) além do que já existia.
- Botão de ação contextual conforme a tabela §2:
  - `admin`: "Designar" → abre um diálogo (ver §5, reaproveitando o
    padrão de `FaseDestinoDialog`) para escolher qualquer perfil ativo da
    organização, ou "Tornar órfão".
  - `usuario`, processo órfão: "Assumir processo" → chama
    `designar_processo(processo_id, auth.uid())` direto, sem diálogo (é
    uma ação de um clique só, a spec não pede confirmação extra aqui).
  - `usuario`, processo já seu: "Devolver" → chama
    `designar_processo(processo_id, null)`.
  - `usuario`, processo de outra pessoa: nenhum botão de designação (só
    visualização, mais os botões de avançar fase/diligência se aplicável
    — mas processo de outra pessoa não-órfão não permite avançar fase
    nem diligência, por §2).

### 4.4 `AvancarFaseViewModel` / `AvancarFaseScreen`

- `mudarFase(...)` passa a chamar o novo RPC `avancar_fase()` (dependência
  de 2A §2.4) em vez da transação Room local — uma única chamada de
  rede, atômica no servidor.
- `atualizarResponsavel(pessoaId)` (hoje mexe no responsável *da entrada de
  histórico*, um conceito que continua existindo e é diferente da
  designação do processo — ver 2A §2.3) é **renomeado** para
  `atualizarExecutor(perfilId)` para eliminar a ambiguidade de nome que a
  fundação de dados já identificou. Continua editável por quem tem
  permissão de editar aquela entrada (mesma regra da tabela §2).
- Adiciona a lista de diligências (§3.2).

### 4.5 `FaseCadastroViewModel`

Só ganha a checagem de papel admin (mesmo padrão do novo
`TipoProcessoCadastroViewModel`) — sem mudança estrutural além disso.

### 4.6 Remoção de Pessoa

`PessoaCadastroViewModel`, `PessoasScreen`, `PessoaFormDialog`,
`PessoaEntity`, `PessoaDao`, `PessoaRepository` — todos removidos. O card
"Cadastro de Pessoas" sai de `MaisScreen` (a gestão de contas reais é o
Plano 2C, uma tela conceitualmente diferente — CRUD de conta via Edge
Function, não CRUD de tabela livre).

## 5. Padrão de UI para escolher uma pessoa (lição já aprendida no projeto)

**Nunca usar `DropdownField` dentro de um `AlertDialog`** — já documentado
como problema conhecido deste projeto (o `Popup` do
`ExposedDropdownMenuBox` não renderiza corretamente aninhado dentro de
outro `Popup`/`Dialog`). O diálogo "Designar" (§4.3) e qualquer outro
seletor de pessoa dentro de diálogo modal usa uma lista inline de
`RadioButton`, exatamente como `FaseDestinoDialog` já resolveu isso para
escolha de fase destino.

## 6. `RegrasBloqueioCampos`

Sem mudança nenhuma — continua comparando nome de fase normalizado, e o
nome de fase continua vindo do Room (agora populado via sincronização em
vez de cadastro 100% local, mas a lógica de comparação de string não sabe
nem precisa saber disso).

## 7. Testes

Mesma abordagem de 2A (§5 daquela spec): testes unitários para
`calcularSemaforo` (função pura, fácil de testar exaustivamente) e para a
lógica de permissão por ação (dado um papel + estado de designação, qual
botão aparece — também função pura, testável sem Android/Room). Verificação
funcional dos fluxos completos (criar processo, avançar fase, designar,
autoatribuir, devolver, registrar diligência) em emulador contra o projeto
Supabase real, com pelo menos duas contas de teste (um admin, um usuario)
para exercitar as duas perspectivas de papel.

## 8. Fora de escopo (nesta sub-etapa 2B)

Tela de gerenciar equipe/contas, Fila de Distribuição, configurações de
notificação (todas Plano 2C); notificações push (Plano 2D); anexos,
dashboard Início, Agenda (fora de escopo do pivô inteiro, spec original
§12).
