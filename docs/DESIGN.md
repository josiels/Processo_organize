# DESIGN — Organize_Processo

Referências visuais: apps pessoais já desenvolvidos pelo usuário ("Casa Mensal" e "Aprendiz Musical"). Manter consistência de linguagem visual entre os projetos.

## 1. Estilo visual geral

- **Tema:** claro.
- **Cor primária:** índigo/roxo (referência: header e botões do "Casa Mensal").
- **Cor de ação/destaque:** laranja (referência: botão "Acessar Aula" do "Aprendiz Musical").
- **Cores de status/semáforo:** verde (ok/recente), amarelo (atenção), vermelho/laranja escuro (crítico).
- **Cards:** cantos arredondados, fundo branco sobre fundo lilás/cinza claro, com **barra lateral colorida** indicando status ou fase.
- **Ícones:** dentro de círculos com fundo suave (tonalidade pastel da cor relacionada).
- **Badges de status:** pill colorido com texto (ex: "Pendente", "Paga", "Vencida" no app de referência → no Organize_Processo: nome da fase ou status do processo).
- **Navegação:** bottom nav fixo com 4 ícones + label, item ativo destacado em índigo.

## 2. Tela Início (Dashboard)

Inspirada na tela "Bom dia, Maria!" do Aprendiz Musical.

- Saudação/cabeçalho.
- Cards de destaque horizontais roláveis: notificações recentes, alertas de prazo.
- Card "Próximos prazos" ou "Processos críticos" em destaque, com botão de ação.
- Card de progresso geral (ex: "X de Y processos em dia" com barra de progresso) — exibir também o **percentual numérico** (ex: "70%") junto da barra, não só a fração.
- Resumo curto de alterações recentes (ex: "3 processos atualizados hoje") — sem feed detalhado.
- Botão de destaque: "Ver todos os processos".

## 3. Tela Processos (lista geral)

Inspirada na tela "Contas" do Casa Mensal.

- Campo de busca no topo ("Buscar processo...") — busca por número ou objeto — com **ícone de filtro** ao lado, para acesso rápido às opções de ordenação (evita poluir a linha de pills com o controle de ordenação).
- Pills de filtro horizontais: Todos, Em andamento, Críticos, Concluídos (ajustar conforme status_geral) — com **scroll horizontal** quando não couberem todas na largura da tela (evitar texto cortado nas bordas).
- Controle de ordenação (acessado pelo ícone de filtro): por urgência (prazo mais próximo primeiro), por fase, por data de abertura.
- Cada card de processo exibe:
  - Barra lateral colorida = **semáforo de tempo parado** (verde/amarelo/vermelho) — sinal 1.
  - Badge de fase atual (cor própria da fase, distinta do semáforo) — sinal 2.
  - Número do processo.
  - Objeto (resumido/truncado).
  - Responsável atual da fase.
  - **Seta ">" (chevron) à direita**, indicando que o card é clicável/navegável.
- Botão flutuante ou pill inferior: "+ Novo processo".

## 4. Tela Detalhe do Processo

Estrutura em abas:
1. **Dados gerais** — número, objeto, descrição, órgão demandante, tipo, valor estimado total, data de abertura, status geral.
2. **Itens** — lista de itens do processo (descrição, quantidade, unidade, valor estimado, valor de pesquisa — este último visualmente bloqueado/cinza até a fase liberar).
3. **Linha do tempo de fases** — timeline vertical (bolinhas conectadas por linha, estilo rastreio de encomenda), mostrando cada fase percorrida com datas de entrada/saída, responsável e indicador de retrocesso quando aplicável.
4. **Anexos/Links** — lista de links/documentos organizados por fase.

## 5. Tela "Avançar Fase" (ação central do app)

Inspirada diretamente na tela de detalhe de hino do Aprendiz Musical (Imagem 3 de referência).

- Nome da fase atual em destaque no topo + badge de status colorido (Em andamento / Atenção / Crítico, conforme semáforo).
- Botões de atalho: "Ver anexos/links da fase" e "Ver histórico de fases" (grid de 2 botões, ícone + label).
- Campo de observação em caixa de texto grande ("Adicionar uma observação...") — com **contador de caracteres** (ex: "0/500") no canto inferior do campo, para dar noção de limite e uso.
- Grade de botões de ação coloridos (padrão visual de status da referência):
  - Selecionar fase de destino (abre lista de fases cadastradas — nunca avanço automático).
  - Opção de retorno de fase (com campo de motivo).
- Campo de responsável: dropdown alimentado pelo cadastro de Pessoas.
- Campo de data (preenchida automaticamente, editável) e prazo limite (opcional).
- Toggle "Notificar sobre prazo desta fase?" — switch verde/cinza, no padrão liga/desliga já usado nos outros apps do usuário.

## 6. Tela Agenda

Inspirada na tela "Agenda" do Aprendiz Musical.

- Calendário mensal com indicadores coloridos por dia (bolinhas nas datas com prazos de fase).
- Ao selecionar um dia: lista de processos/fases com prazo naquela data.

## 7. Tela Mais (configurações e cadastros)

- Cadastro de Fases: nome, ordem, dias de alerta (atenção/crítico), descrição.
- Cadastro de Pessoas: nome, cargo/setor, ativo/inativo (toggle).
- Status de sincronização (última sincronização, pendências offline).
- Configurações gerais do app.

## 8. Padrões de interação transversais

- **Toggles** (switch liga/desliga) para todas as opções binárias — nunca checkbox tradicional.
- **Botões de ação em grade colorida** para decisões de status/fase — reforça reconhecimento visual rápido.
- **Semáforo sempre com 2 sinais distintos no card**: cor de fase (identificação) + cor de tempo parado (urgência) — nunca uma cor só fazendo os dois papéis.
- Sem reordenação automática de lista por urgência — apenas indicador visual muda.

## 9. Protótipos visuais gerados (referência)

Protótipos gerados por IA de imagem (ChatGPT/DALL-E) validaram a direção visual descrita neste documento e trouxeram 3 refinamentos incorporados nas seções acima: ícone de filtro ao lado da busca, seta de navegação (chevron) nos cards da lista, percentual numérico na barra de progresso e contador de caracteres no campo de observação. Números de processo e nomes usados nesses protótipos são fictícios/inconsistentes (gerados pela IA de imagem) e não devem ser usados como referência de dado real.
