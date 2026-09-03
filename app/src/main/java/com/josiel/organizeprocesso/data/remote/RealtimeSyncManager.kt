package com.josiel.organizeprocesso.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Uma função `sincronizar()` por tabela de negócio — chamada sempre que
 * chega um evento `postgres_changes` para essa tabela (não tenta aplicar o
 * payload do evento diretamente no Room; simplesmente refaz a leitura
 * completa da tabela via Postgrest, mais simples e robusto que reconciliar
 * insert/update/delete evento a evento).
 */
data class RepositoriosSincronizaveis(
    val perfis: suspend () -> Unit,
    val tiposProcesso: suspend () -> Unit,
    val fases: suspend () -> Unit,
    val processos: suspend () -> Unit
)

/**
 * Só as 4 tabelas que têm um `sincronizar()` de tabela inteira em
 * [RepositoriosSincronizaveis]. `itens`, `processo_fase_historico`,
 * `diligencias` e `observacao_versoes` são sincronizadas sob demanda, por
 * processo/histórico, quando a tela correspondente abre — assinar um canal
 * para elas aqui só disparava um re-sync de `processos` (no-op para os dados
 * daquelas tabelas).
 */
private val TABELAS_MONITORADAS = listOf("perfis", "tipos_processo", "fases", "processos")

/**
 * Singleton manual (sem DI framework, mesmo padrão de `SupabaseSessionManager`)
 * que assina os canais Realtime das tabelas de negócio e reflete cada evento
 * na sincronização (Postgrest -> Room) do repositório correspondente.
 */
object RealtimeSyncManager {
    private val canais = mutableListOf<RealtimeChannel>()

    /**
     * Idempotente: `canais` é estado do singleton, mas os coletores de evento
     * vivem no [escopo] da composição. Se o `AppNavHost` for recriado com a
     * sessão ainda válida (recriação da Activity, por exemplo), `iniciar()`
     * roda de novo — sem o teardown abaixo as assinaturas antigas ficariam
     * penduradas e um novo jogo se acumularia por cima a cada recriação.
     * Simplesmente sair no início também não serve: os coletores antigos já
     * morreram com o escopo antigo e o Realtime ficaria surdo até o próximo
     * login. Reaproveitar o canal antigo não é opção — `postgresChangeFlow`
     * só pode ser registrado antes do `subscribe()`.
     */
    fun iniciar(client: SupabaseClient, escopo: CoroutineScope, repositorios: RepositoriosSincronizaveis) {
        val anteriores = canais.toList()
        canais.clear()

        escopo.launch {
            // Sequencial (e não em paralelo com o subscribe abaixo) para não
            // reusar um tópico que ainda está saindo.
            anteriores.forEach { canal ->
                try {
                    client.realtime.removeChannel(canal)
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        throw e
                    }
                    // Canal já derrubado pelo servidor: nada a fazer.
                }
            }

            TABELAS_MONITORADAS.forEach { tabela ->
                val canal = client.channel("cache-sync-$tabela")
                val callback: suspend () -> Unit = when (tabela) {
                    "perfis" -> repositorios.perfis
                    "tipos_processo" -> repositorios.tiposProcesso
                    "fases" -> repositorios.fases
                    else -> repositorios.processos
                }
                launch {
                    canal.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = tabela
                    }.collect { callback() }
                }
                canais.add(canal)
                canal.subscribe()
            }
        }
    }

    suspend fun encerrar(client: SupabaseClient) {
        canais.forEach { client.realtime.removeChannel(it) }
        canais.clear()
    }
}
