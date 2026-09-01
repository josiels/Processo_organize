package com.josiel.organizeprocesso.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
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

private val TABELAS_MONITORADAS = listOf(
    "perfis", "tipos_processo", "fases", "processos",
    "itens", "processo_fase_historico", "diligencias", "observacao_versoes"
)

/**
 * Singleton manual (sem DI framework, mesmo padrão de `SupabaseSessionManager`)
 * que assina os canais Realtime das tabelas de negócio e reflete cada evento
 * na sincronização (Postgrest -> Room) do repositório correspondente.
 */
object RealtimeSyncManager {
    private val canais = mutableListOf<RealtimeChannel>()

    fun iniciar(client: SupabaseClient, escopo: CoroutineScope, repositorios: RepositoriosSincronizaveis) {
        TABELAS_MONITORADAS.forEach { tabela ->
            val canal = client.channel("cache-sync-$tabela")
            val callback: suspend () -> Unit = when (tabela) {
                "perfis" -> repositorios.perfis
                "tipos_processo" -> repositorios.tiposProcesso
                "fases" -> repositorios.fases
                else -> repositorios.processos
            }
            escopo.launch {
                canal.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = tabela
                }.collect { callback() }
            }
            escopo.launch { canal.subscribe() }
            canais.add(canal)
        }
    }

    suspend fun encerrar() {
        canais.forEach { it.unsubscribe() }
        canais.clear()
    }
}
