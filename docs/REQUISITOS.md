# REQUISITOS — Organize_Processo

App Android pessoal para controle de processos de licitação por fase, uso individual (celular + tablet), com sincronização multi-dispositivo.

## 1. Contexto e problema

- Usuário único (sem equipe/multiusuário).
- Setor de planejamento de licitações — trabalha com ~10 processos simultâneos em fases distintas.
- Dificuldades atuais:
  1. Esquecer em que fase cada processo está.
  2. Perder prazos entre fases.
  3. Não saber quantos processos estão parados numa mesma etapa.

## 2. Uso pretendido

- Tela inicial: dashboard (alertas, resumo de alterações, progresso geral, atalho para lista de processos).
- Lista geral de processos como tela principal de trabalho.
- Uso intenso de digitação (observações longas por fase).
- Uso tanto no celular quanto em tablet — precisa manter dados sincronizados entre os dois.
- Nem sempre há internet disponível no ambiente de trabalho → app deve operar **offline** e sincronizar quando conectar.

## 3. Fases do processo

- Fluxo de fases é **fixo/padrão**, mas o usuário pode **cadastrar novas fases** quando surgir um processo diferente.
- Fases podem ser **puladas** e o processo pode **retroceder** para uma fase anterior (ex: diligência, impugnação).
- Cada fase pode ter um **responsável diferente** — cadastro de Pessoas reutilizável, selecionado por dropdown ao definir responsável da fase.
- Cadastro de fases padrão **não é pré-populado** pelo app — o usuário cadastra manualmente tudo no primeiro uso (liberdade total desde o início).
- Cada fase tem limites configuráveis de alerta (dias) — ver seção Notificações.

## 4. Dados do processo

### Dados-mãe do processo
- Número do processo
- Objeto
- Descrição
- Órgão demandante
- Tipo (SRP, aquisição direta, serviço, etc.)
- Valor estimado total (pode ser derivado da soma dos itens)
- Data de abertura
- Fase atual
- Status geral (em andamento, suspenso, concluído, cancelado)

### Itens do processo (1:N)
- Descrição do item
- Quantidade
- Unidade
- Valor estimado unitário
- Valor de pesquisa unitário

### Regra de bloqueio de campos por fase
- Alguns campos (ex: valor de pesquisa, determinadas datas) **só ficam disponíveis para preenchimento a partir da fase correspondente** (ex: valor de pesquisa só é editável a partir da fase "Pesquisa de Preços").
- O app deve **bloquear visualmente** esses campos até a fase liberar — não é apenas uma sugestão, é uma trava.
- Essa liberação de campos por fase segue uma lógica **fixa no código** (não configurável dinamicamente pelo usuário na V1).

### Informações por fase (histórico)
Cada passagem do processo por uma fase registra:
- Responsável (do cadastro de Pessoas)
- Data de entrada
- Data de saída (nula = fase corrente)
- Prazo limite (opcional)
- Observação em texto livre (com histórico de versões — ver seção Sincronização)
- Motivo do retorno (quando é um retrocesso de fase)
- Anexos/links vinculados a essa fase específica

## 5. Anexos e links

- Preferência por **links/URLs de documentos externos** em vez de upload de arquivos.
- Upload de arquivo (Supabase Storage) é suportado, mas deve ser o mínimo possível — o usuário prioriza referenciar documentos já hospedados em outro lugar (SEI, PNCP, Contratos.gov.br, etc.).
- Anexos/links são vinculados à **fase específica** do processo (não ao processo como um todo).

## 6. Notificações

- Dois gatilhos:
  1. **Prazo definido** — antecipação de 5 dias antes do vencimento + aviso no dia.
  2. **Tempo parado na fase** — cada fase tem limites próprios e configuráveis:
     - `dias_alerta_atencao` (semáforo amarelo)
     - `dias_alerta_critico` (semáforo vermelho)
- Notificação push mesmo com o app fechado.
- Processos em estado crítico **não sobem automaticamente** na lista — apenas o indicador visual (semáforo) muda. A ordem da lista não é reordenada por urgência automaticamente (a menos que o usuário escolha essa ordenação manualmente).

## 7. Sincronização multi-dispositivo

- App **offline-first**: sempre lê/escreve local primeiro.
- Sincroniza com Supabase quando há conexão.
- Estratégia de conflito:
  - Campos **estruturados** (datas, valores, status): last-write-wins, com alerta visual se dois dispositivos alterarem o mesmo campo em janela de tempo próxima.
  - Campos de **texto livre** (observações): nunca sobrescreve — mantém histórico de versões anteriores, preservando a informação de ambos os dispositivos para revisão manual.

## 8. Requisitos de navegação (resumo funcional)

- **Início**: dashboard com alertas de prazo/crítico, resumo curto de alterações recentes (ex: "3 processos atualizados hoje"), progresso geral, botão para lista completa de processos.
- **Processos**: lista geral com busca (por número/objeto) e filtros/ordenação (por urgência, por fase, por data de abertura).
- **Agenda**: calendário de prazos de fase, cruzando todos os processos.
- **Mais**: cadastro de Fases, cadastro de Pessoas, status de sincronização, configurações.

## 9. Fluxo de avanço/retrocesso de fase

- Nunca avança automaticamente para a "próxima fase" da ordem — sempre abre uma **lista de fases disponíveis** para escolha manual (permite pular ou retroceder livremente).
- Tela de ação inclui: seleção de fase, responsável (dropdown), data, prazo opcional, observação (texto livre versionado), anexos/links da fase, toggle para notificação de prazo.
