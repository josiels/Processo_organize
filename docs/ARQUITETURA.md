# ARQUITETURA — Organize_Processo

## 1. Stack tecnológico

> **Histórico de decisões:** o projeto passou por Android Studio (Kotlin nativo) → VS Code (Kotlin nativo) → VS Code (Expo/React Native) → **de volta ao Android Studio com Kotlin/Jetpack Compose nativo**, decisão final. O modelo de dados, regras de negócio e estratégia de sincronização nunca mudaram — só a camada de implementação. Kotlin nativo evita as limitações de notificação em background que o Expo Go apresentava.

| Camada | Tecnologia | Justificativa |
|---|---|---|
| Linguagem/UI | **Kotlin + Jetpack Compose** | Padrão atual de desenvolvimento Android nativo, suportado nativamente pelo Android Studio |
| Persistência local | **Room (SQLite)** | Fonte de verdade offline — app sempre lê/escreve local primeiro |
| Backend/Sync | **Supabase (Postgres + Auth + Storage)** | Sincronização multi-dispositivo com usuário único; gratuito no volume de uso esperado |
| Notificações | **WorkManager + AlarmManager** | WorkManager para checagens periódicas (tempo parado); AlarmManager para prazos exatos — suporte nativo completo, sem as limitações de background que existem no Expo Go |
| Navegação | **Jetpack Navigation Compose** | Navegação entre telas/abas nativa do Compose |
| Padrão de arquitetura | MVVM + Repository Pattern | Repository abstrai se a leitura/escrita vai para Room ou Supabase |

## 2. Ambiente de desenvolvimento

- **IDE:** Android Studio (com suporte nativo a Gradle, emulador, Layout Inspector, e debug integrado).
- **Build:** Gradle (integrado à IDE, ou via terminal `./gradlew assembleDebug` / `./gradlew installDebug`).
- **Debug/Deploy:** emulador Android integrado ou dispositivo físico via USB/Wi-Fi debugging, direto pela IDE.
- **Desenvolvedor:** Claude (via chat/Claude Code, gerando os arquivos Kotlin que serão colados/criados no projeto Android Studio), a partir dos documentos deste levantamento.

## 3. Estratégia de sincronização offline-first

- Toda escrita acontece primeiro no Room local.
- Cada registro sincronizável tem:
  - `updated_at` (timestamp)
  - `synced` (boolean) — marca se já subiu para o Supabase
  - `device_origin` (identificador do dispositivo que fez a última alteração) — usado para exibir alertas de conflito
- Fila de sincronização: alterações offline ficam `synced = false` e sobem automaticamente quando o app detecta conexão.
- **Conflitos em campos estruturados** (datas, valores, status): last-write-wins — o registro mais recente prevalece, mas se dois dispositivos alteraram o mesmo campo em uma janela de tempo próxima, o app exibe um alerta visual pedindo revisão manual.
- **Conflitos em campos de texto livre** (observações): nunca sobrescreve. Cada edição é preservada como uma entrada de histórico (com timestamp e dispositivo de origem), permitindo consultar versões anteriores sem perda de conteúdo.

## 4. Modelo de dados (ERD)

### `Pessoa`
| Campo | Tipo | Obs |
|---|---|---|
| id | UUID (PK) | |
| nome | text | |
| cargo_setor | text | opcional |
| ativo | boolean | permite desativar sem apagar histórico |

### `Fase` (catálogo reutilizável, cadastro manual pelo usuário)
| Campo | Tipo | Obs |
|---|---|---|
| id | UUID (PK) | |
| nome | text | ex: "Elaboração do ETP" |
| ordem | int | posição sugerida no fluxo (referência, não trava avanço) |
| descricao | text | opcional |
| dias_alerta_atencao | int | limite de dias parado → semáforo amarelo |
| dias_alerta_critico | int | limite de dias parado → semáforo vermelho |
| padrao | boolean | distingue fase do fluxo padrão vs. fase customizada |

> Observação: `campos_habilitados` (quais campos do processo essa fase libera) **não é dinâmico/configurável pelo usuário** — a lógica de liberação de campos por fase é fixa no código da aplicação.

### `Processo`
| Campo | Tipo | Obs |
|---|---|---|
| id | UUID (PK) | |
| numero | text | |
| objeto | text | |
| descricao | text | |
| orgao_demandante | text | |
| tipo | text/enum | SRP, aquisição direta, serviço, etc. |
| valor_estimado_total | decimal | pode ser calculado a partir dos itens |
| data_abertura | date | |
| fase_atual_id | FK → Fase | |
| status_geral | enum | em andamento, suspenso, concluído, cancelado |
| created_at | timestamp | |
| updated_at | timestamp | controle de sync |
| synced | boolean | |
| device_origin | text | |

### `ProcessoFaseHistorico` (núcleo do histórico de fases)
| Campo | Tipo | Obs |
|---|---|---|
| id | UUID (PK) | |
| processo_id | FK → Processo | |
| fase_id | FK → Fase | |
| responsavel_id | FK → Pessoa | nullable |
| data_entrada | date | |
| data_saida | date | nullable — null = fase corrente/ativa |
| prazo_limite | date | nullable — usado no cálculo de notificação por prazo |
| observacoes | text | versionado (ver `ObservacaoVersao`) |
| motivo_retorno | text | preenchido apenas quando é retrocesso de fase |
| notificar_prazo | boolean | toggle definido na tela de avanço de fase |

### `ObservacaoVersao` (histórico de versões de texto livre)
| Campo | Tipo | Obs |
|---|---|---|
| id | UUID (PK) | |
| processo_fase_historico_id | FK → ProcessoFaseHistorico | |
| conteudo | text | |
| criado_em | timestamp | |
| device_origin | text | |

### `Item`
| Campo | Tipo | Obs |
|---|---|---|
| id | UUID (PK) | |
| processo_id | FK → Processo | |
| descricao | text | |
| quantidade | decimal | |
| unidade | text | un, cx, kg, licença, etc. |
| valor_estimado_unit | decimal | |
| valor_pesquisa_unit | decimal | editável apenas a partir da fase de pesquisa (regra fixa no código) |

### `AnexoLink`
| Campo | Tipo | Obs |
|---|---|---|
| id | UUID (PK) | |
| processo_fase_historico_id | FK → ProcessoFaseHistorico | vinculado à fase específica |
| tipo | enum | link externo ou arquivo (Supabase Storage) |
| url_ou_path | text | |
| descricao | text | ex: "TR assinado", "print e-mail impugnação" |

## 5. Regras de negócio centrais

1. **Bloqueio de campos por fase**: `Item.valor_pesquisa_unit` e datas específicas só ficam editáveis quando `Processo.fase_atual_id` (ou o histórico de fases já percorridas) atingir a fase correspondente. Lógica fixa no código, não configurável.
2. **Avanço/retrocesso de fase**: nunca automático. Toda mudança de fase abre uma lista de fases cadastradas para escolha manual — permite pular etapas ou retornar a uma fase anterior livremente.
3. **Cálculo de semáforo**: comparar `data_entrada` da fase corrente com a data atual, contra os limites `dias_alerta_atencao` / `dias_alerta_critico` daquela fase específica (não um valor global).
4. **Notificações**:
   - Prazo: dispara 5 dias antes de `prazo_limite` e no dia do vencimento.
   - Tempo parado: dispara ao cruzar `dias_alerta_atencao` e `dias_alerta_critico`.
   - Processos em estado crítico não são reordenados automaticamente na lista — apenas o indicador visual muda.
