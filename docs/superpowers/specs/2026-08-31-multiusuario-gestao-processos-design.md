# Pivô multiusuário — Gerenciador de Processos (design)

Data: 2026-08-31

## 1. Contexto e motivação

O app "Organize_Processo" nasceu como ferramenta pessoal, single-user, offline-first (ver `REQUISITOS.md`, `ARQUITETURA.md`, `DESIGN.md`, `ROADMAP.md` originais — etapas 1 a 10 já implementadas e testadas: cadastro de Fases/Pessoas, CRUD de Processo+Itens, lista, detalhe, fluxo de Avançar/Retroceder Fase, bloqueio de campos por fase).

O usuário decidiu pivotar o propósito do app: de ferramenta pessoal para um **gerenciador de processos multiusuário e multiorganização**, com papéis de administrador e usuário, designação de processos entre pessoas, contadores de tempo por responsável e notificações push quando um processo avança de fase. Este documento registra o design acordado para esse pivô, substituindo as premissas de single-user/offline-first dos documentos originais.

Este documento não repete o que não muda (paleta de cores, componentes visuais, linguagem de card/badge — ver `DESIGN.md` original, que continua valendo). Foca no que muda: dados, permissões, backend e fluxos.

## 2. Papéis e organizações (multi-tenant)

- **`super_admin`**: papel único do dono do sistema (o usuário desta conversa). Cria organizações e designa o primeiro `admin` de cada uma. Não participa do dia a dia de nenhuma organização específica.
- **`admin`** (por organização):
  - Cadastra Fases da própria organização.
  - Cadastra Tipos de Processo da própria organização.
  - Cadastra contas (`admin` ou `usuario`) da própria organização.
  - Designa processos a qualquer conta da organização.
  - Pode ser executor de processos (mesmas ações de `usuario` sobre o que é seu).
- **`usuario`** (por organização):
  - Vê todos os processos da organização (transparência total de carga de trabalho).
  - Só avança fase / edita observação / registra diligência nos processos que são dele ou que estão órfãos.
  - Pode autoatribuir um processo órfão a si mesmo.
  - Pode devolver um processo seu para órfão (não pode transferir diretamente a um colega — isso é só do admin).

Cada conta pertence a exatamente uma organização. Não há contas compartilhadas entre organizações. Toda tabela de negócio (fases, tipos de processo, processos, itens, histórico, diligências) carrega `organizacao_id` e é isolada por Row-Level Security no Postgres — o isolamento é garantido no banco, não apenas na lógica do app.

## 3. Modelo de dados

### `organizacoes`
| Campo | Tipo | Obs |
|---|---|---|
| id | uuid (PK) | |
| nome | text | |
| criado_em | timestamptz | |

### `perfis` (estende `auth.users` do Supabase)
| Campo | Tipo | Obs |
|---|---|---|
| id | uuid (PK, = auth.users.id) | |
| organizacao_id | uuid (FK, nulo apenas para super_admin) | |
| papel | enum('super_admin','admin','usuario') | |
| nome | text | |
| cargo_setor | text | opcional |
| ativo | boolean | permite desativar sem apagar histórico |
| notificar_avanco_fase | boolean | default true — toggle pessoal de notificação |
| notificar_prazo | boolean | default true |
| notificar_tempo_parado | boolean | default true |
| ultimo_recebimento_em | timestamptz | nulo até o primeiro recebimento — usado para ordenar a fila |
| criado_em | timestamptz | |

### `device_tokens`
| Campo | Tipo | Obs |
|---|---|---|
| id | uuid (PK) | |
| perfil_id | uuid (FK → perfis) | |
| token_fcm | text | |
| atualizado_em | timestamptz | |

### `tipos_processo`
| Campo | Tipo | Obs |
|---|---|---|
| id | uuid (PK) | |
| organizacao_id | uuid (FK) | |
| nome | text | |
| dias_alerta_atencao | int | limite de "dias desde designado" → semáforo amarelo |
| dias_alerta_critico | int | limite de "dias desde designado" → semáforo vermelho |

### `fases` (sem mudança estrutural, ganha `organizacao_id`)
nome, ordem, descrição, dias_alerta_atencao, dias_alerta_critico — como já existia, agora escopado por organização.

### `processos`
Como já existia (número, objeto, descrição, órgão demandante, valor estimado total, data de abertura, fase_atual_id, status_geral), mais:
| Campo | Tipo | Obs |
|---|---|---|
| organizacao_id | uuid (FK) | |
| tipo_processo_id | uuid (FK → tipos_processo) | substitui o campo de texto livre "tipo" |
| responsavel_id | uuid (FK → perfis), nulo | nulo = órfão |
| designado_em | timestamptz, nulo | zera toda vez que muda de responsável; base do contador "dias desde designado" |
| designado_por | uuid (FK → perfis), nulo | quem fez a última designação (admin ou o próprio usuário, no autoatribuir) |

Campos de sincronização antigos (`synced`, `device_origin`) são removidos — escrita agora é direta ao servidor.

### `itens` (sem mudança estrutural)

### `processo_fase_historico` (sem mudança estrutural, ganha `organizacao_id` via processo)

### `diligencias` (nova)
| Campo | Tipo | Obs |
|---|---|---|
| id | uuid (PK) | |
| processo_fase_historico_id | uuid (FK) | vinculada à passagem específica pela fase |
| autor_id | uuid (FK → perfis) | quem registrou |
| conteudo | text | evento livre (ex: "Enviado ofício ao fornecedor X") |
| criado_em | timestamptz | |

Múltiplas diligências por passagem de fase — um mini-histórico de eventos, complementar (não substitui) à observação única versionada que já existia (`observacao_versoes`, sem mudança estrutural).

### `observacao_versoes` (sem mudança estrutural)

### `designacoes` (nova — log histórico, nunca sobrescrito)
| Campo | Tipo | Obs |
|---|---|---|
| id | uuid (PK) | |
| processo_id | uuid (FK → processos) | |
| tipo_processo_id | uuid (FK → tipos_processo) | denormalizado no momento da designação, para contagem por tipo mesmo se o tipo do processo mudar depois |
| perfil_id | uuid (FK → perfis) | quem recebeu |
| designado_por | uuid (FK → perfis) | admin ou o próprio usuário (autoatribuição) |
| criado_em | timestamptz | |

Cada designação (admin designa, ou usuário autoatribui um órfão) insere uma linha nova aqui — nunca atualiza uma existente. `processos.designado_em`/`responsavel_id` guardam só o estado atual (para os contadores/semáforo); esta tabela é o histórico completo que alimenta a Fila de Distribuição (contagem total e por tipo, mesmo depois de reatribuições).

## 4. Fila de distribuição (transparência de carga)

Tela nova, visível a todos os usuários da organização:
- Lista de pessoas da organização ordenada por `ultimo_recebimento_em` ascendente (quem está há mais tempo sem receber processo aparece no topo; quem nunca recebeu nada — `ultimo_recebimento_em IS NULL` — fica no topo de todos, ordenado por data de criação da conta).
- Ao lado de cada nome: contagem histórica total de processos recebidos por essa pessoa (`COUNT(*)` em `designacoes` onde `perfil_id` = ela, mesmo que o processo já tenha sido concluído ou devolvido depois).
- Ao tocar numa pessoa: mesma contagem agrupada por `tipo_processo_id`.
- Puramente informativo — não interfere na ação de designar/autoatribuir, que continua sendo escolha livre do admin/usuário.
- Toda vez que um processo é designado a alguém (por admin OU autoatribuição de órfão): insere uma linha em `designacoes`, atualiza `processos.responsavel_id`/`designado_em`/`designado_por`, e atualiza `perfis.ultimo_recebimento_em` dessa pessoa para agora (o que a manda para o final da fila).

## 5. Contadores e semáforos (dois independentes, coexistindo)

1. **Dias parado na fase** (já existia): hoje − `data_entrada` da fase corrente do processo, comparado aos limites da própria `Fase`.
2. **Dias desde designado** (novo): hoje − `designado_em` do processo, comparado aos limites do `Tipo de Processo` do processo.

Os dois semáforos aparecem lado a lado onde fizer sentido na UI (ex: card da lista, detalhe do processo) — cada um sinalizando uma coisa diferente (tempo na etapa vs. tempo com a pessoa atual).

## 6. Bloqueio de campos por fase

Sem mudança de regra (ver `ARQUITETURA.md` original, regra de negócio 1, e `domain/usecase/RegrasBloqueioCampos.kt` já implementado) — continua comparando nome de fase normalizado. Passa a operar sobre dados vindos do Supabase em vez de Room local, mas a lógica em si não muda.

## 7. Notificações e infraestrutura

- Cada dispositivo registra um token FCM em `device_tokens`, vinculado ao perfil logado (permite múltiplos aparelhos por pessoa).
- Um **cron job no Supabase** (Edge Function agendada, rodando a cada poucos minutos) verifica:
  - Processos que avançaram de fase desde a última checagem → notifica todos os `admin` da organização com `notificar_avanco_fase = true`.
  - Prazo limite se aproximando (5 dias antes + no dia do vencimento) → notifica o `responsavel_id` atual, se `notificar_prazo = true`.
  - "Dias parado na fase" cruzando os limites da `Fase` → notifica o responsável atual, se `notificar_tempo_parado = true`.
  - "Dias desde designado" cruzando os limites do `Tipo de Processo` → notifica o responsável atual, se `notificar_tempo_parado = true`.
- Push entregue via **Firebase Cloud Messaging**, disparado pela Edge Function.
- Tela de configurações pessoais no app com os três toggles (`notificar_avanco_fase`, `notificar_prazo`, `notificar_tempo_parado`).
- Alertas de prazo/tempo parado avisam **só o responsável atual** (como já era o plano original) — só o avanço de fase notifica os admins.

## 8. Contas privilegiadas (Edge Functions)

Duas operações não podem rodar direto no app Android (exigiriam a service role key do Supabase embutida no APK):
- **Criar organização** (só `super_admin`): Edge Function que cria a `organizacao` e o primeiro `admin` (conta no Supabase Auth + `perfil`).
- **Criar conta** (só `admin`, dentro da própria organização): Edge Function que cria a conta no Supabase Auth (e-mail + senha inicial ou convite) e o `perfil` correspondente, com `papel` = admin ou usuario.

Ambas validam o papel de quem chama (via JWT) antes de executar, usando a service role key apenas no lado do servidor.

## 9. Autenticação e app

- Login por e-mail/senha via Supabase Auth — tela de login é a porta de entrada do app, antes de qualquer outra tela.
- Sessão persistida localmente (token do Supabase Auth).
- Sem autocadastro — toda conta nasce de uma Edge Function chamada por super_admin ou admin.

## 10. Camada local do app (Room como cache) e fim do offline-first para escrita

- Room deixa de ser "fonte da verdade offline com fila de sincronização" e vira **cache de leitura**: espelha os dados da organização do usuário logado (fases, tipos de processo, processos, itens, histórico, diligências, perfis para a fila), atualizado via Supabase Realtime (assinatura nas tabelas da própria organização, filtrado por RLS) + refresh ao abrir o app.
- **Toda escrita exige conexão**: criar processo, avançar fase, designar, devolver a órfão, registrar diligência/observação, cadastrar fase/tipo/conta — todas essas ações chamam o Supabase diretamente. Sem internet, a ação falha com mensagem clara ("sem conexão, tente novamente"); não fica pendente numa fila local.
- Leitura (visualizar processos, listas, detalhe) continua funcionando offline com os últimos dados sincronizados.
- Os campos `synced`/`device_origin` e toda a lógica de resolução de conflito planejada no `ARQUITETURA.md` original são removidos — deixam de fazer sentido com escrita síncrona direto ao servidor.

## 11. Impacto no que já existe (etapas 5–10 implementadas)

- **Removido**: `PessoaEntity`/`PessoaDao`/`PessoaRepository` e as telas de cadastro de Pessoas — substituídos por contas reais (`perfis`) geridas pelo admin.
- **Ajustado**: `ProcessoEntity` ganha os campos de designação; o campo `tipo: String` livre vira `tipo_processo_id` (FK); `AppDatabase`/DAOs Room precisam refletir o novo schema (espelhando o Postgres, sem os campos de sync antigos).
- **Ajustado**: `ProcessoFormScreen`, `ProcessosScreen`, `ProcessoDetalheScreen`, `AvancarFaseScreen` precisam de checagem de papel (o que cada pessoa pode ver/fazer) e o dropdown de responsável passa a vir de contas reais da organização.
- **Novo**: tela de login, tela de gerenciar equipe/contas (admin), tela de fila de distribuição, tela de configurações de notificação, tela de cadastro de Tipo de Processo, UI de diligências dentro da fase.
- **Mantido como está**: paleta de cores, componentes visuais (`SideBarCard`, `StatusPill`, `PillButton`, `DropdownField`, `DateField`), `RegrasBloqueioCampos`, a estrutura geral de navegação por abas.
- Dado que o usuário autorizou refazer o quanto for necessário, não há compromisso de preservar código existente além do reaproveitamento visual — a camada de dados é reconstruída para o novo modelo.

## 12. Fora de escopo (por enquanto)

- Anexos/links de processo (CRUD ainda não existe — nem no plano antigo, nem neste pivô).
- Dashboard "Início" e tela "Agenda" (etapas 11–12 do roadmap antigo) — ficam para depois deste pivô.
- Qualquer coisa de relatórios, exportação, multi-idioma — não foi pedido.
- Trigger instantâneo de banco para notificações (optou-se por cron periódico — ver seção 7). Pode evoluir para instantâneo depois, se necessário.

## 13. Dependências externas (necessárias antes de implementar)

O usuário precisa, antes da implementação começar:
1. Criar um projeto no **Supabase** e fornecer a URL do projeto e a chave anônima (anon key).
2. Criar um projeto no **Firebase** (para FCM) e fornecer o arquivo `google-services.json`.

Sem essas duas credenciais, não é possível testar a autenticação, a sincronização multi-usuário nem as notificações push — isso não pode ser simulado localmente.
