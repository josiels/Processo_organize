# ROADMAP — Organize_Processo

Guia de implementação para uso no **Android Studio**, stack **Kotlin + Jetpack Compose nativo**. Este documento assume que `REQUISITOS.md`, `ARQUITETURA.md` e `DESIGN.md` estarão na raiz do projeto (ou em uma pasta `/docs`) como contexto permanente ao usar Claude durante o desenvolvimento.

## 1. Estrutura de pastas sugerida

```
organize_processo/
├── docs/
│   ├── REQUISITOS.md
│   ├── ARQUITETURA.md
│   ├── DESIGN.md
│   └── ROADMAP.md
├── app/
│   ├── src/main/java/com/josiel/organizeprocesso/
│   │   ├── data/
│   │   │   ├── local/          # Room: entities, DAOs, Database
│   │   │   ├── remote/         # Supabase: cliente, DTOs, chamadas
│   │   │   ├── repository/     # Repository pattern (decide local x remoto)
│   │   │   └── sync/           # Fila de sincronização, resolução de conflitos
│   │   ├── domain/
│   │   │   ├── model/          # Modelos de domínio (Processo, Fase, Pessoa, Item...)
│   │   │   └── usecase/        # Regras de negócio (bloqueio de campos, semáforo, etc.)
│   │   ├── ui/
│   │   │   ├── inicio/         # Dashboard
│   │   │   ├── processos/      # Lista + detalhe + avançar fase
│   │   │   ├── agenda/         # Calendário de prazos
│   │   │   ├── cadastro/       # Fases e Pessoas
│   │   │   ├── components/     # Componentes reutilizáveis (cards, badges, timeline, toggles, chevron)
│   │   │   └── theme/          # Cores, tipografia, tema Compose
│   │   ├── navigation/         # Jetpack Navigation Compose (grafo de navegação, rotas)
│   │   ├── notifications/      # WorkManager + AlarmManager
│   │   └── MainActivity.kt
│   └── build.gradle.kts
└── build.gradle.kts
```

## 2. Ordem de implementação sugerida

1. **Setup do projeto**: novo projeto no Android Studio (Empty Activity com Compose) + dependências base (Room, WorkManager, Navigation Compose, cliente Supabase Kotlin).
2. **Tema visual (Compose)**: cores, tipografia e componentes base (Card com barra lateral, Badge/pill, botão pill, Toggle, chevron de navegação) conforme `DESIGN.md`.
3. **Camada de dados local (Room)**: entidades e DAOs de `Pessoa`, `Fase`, `Processo`, `ProcessoFaseHistorico`, `ObservacaoVersao`, `Item`, `AnexoLink`.
4. **Navegação**: grafo de rotas com Navigation Compose (bottom nav de 4 abas + rotas de detalhe/avançar fase).
5. **Cadastros básicos**: telas de cadastro de Pessoas e Fases (necessárias antes de criar qualquer processo).
6. **CRUD de Processo + Itens**: tela de criação/edição de processo com seus itens.
7. **Lista de Processos**: card (com barra lateral, badge, chevron), busca, filtros em pills roláveis, ordenação via ícone de filtro.
8. **Detalhe do Processo**: abas (dados gerais, itens, timeline, anexos).
9. **Fluxo de Avançar/Retroceder Fase**: tela de ação central, com bloqueio de campos por fase e contador de caracteres na observação.
10. **Regras de bloqueio de campos por fase**: implementar lógica fixa (ex: valor de pesquisa liberado a partir da fase correspondente).
11. **Dashboard (Início)**: agregações e resumos (progresso geral com percentual, atualizações recentes) consumindo os dados já implementados.
12. **Agenda**: calendário de prazos.
13. **Notificações**: WorkManager (checagem diária de tempo parado e prazos) + AlarmManager (avisos pontuais); suporte nativo completo, sem restrições de background.
14. **Integração Supabase**: autenticação simples (usuário único), sincronização de dados, Storage para os poucos anexos de arquivo.
15. **Sincronização offline-first**: fila de sync, campos `synced`/`updated_at`/`device_origin`, resolução de conflitos (last-write-wins + versionamento de observações).
16. **Polimento visual e testes manuais** em celular e tablet (emulador + dispositivo físico via Android Studio).

## 3. Prompt inicial sugerido para trabalhar com Claude

Ao iniciar o desenvolvimento, use como primeira mensagem algo como:

> "Vou desenvolver o app Android 'Organize_Processo' no Android Studio, usando Kotlin + Jetpack Compose nativo. Aqui estão os arquivos REQUISITOS.md, ARQUITETURA.md e DESIGN.md com todo o levantamento de requisitos, arquitetura técnica e diretrizes de design já definidos. Quero começar pelo passo 1 do ROADMAP.md: o setup inicial do projeto com as dependências de Room, WorkManager, Navigation Compose e cliente Supabase. Vamos seguir a ordem de implementação do ROADMAP.md, uma etapa por vez, gerando os arquivos Kotlin que vou colar direto no Android Studio."

## 4. Pontos em aberto para decidir durante a implementação

Estes itens não foram fechados no levantamento e podem ser definidos durante o desenvolvimento, sem impacto na arquitetura já definida:

- Lista completa e ordenada das fases padrão (o usuário cadastrará manualmente no primeiro uso do app).
- Paleta exata de cores (códigos hex) — pode ser extraída/ajustada a partir das referências visuais durante a implementação do tema Compose.
- Estratégia de autenticação no Supabase (mesmo sendo usuário único, definir se será e-mail/senha simples ou outro método).
- Nome do pacote Kotlin (`com.josiel.organizeprocesso` é uma sugestão inicial, ajustável).
- Biblioteca Supabase para Kotlin: `supabase-kt` (oficial da comunidade) é a opção recomendada — confirmar versão compatível ao iniciar o setup.
