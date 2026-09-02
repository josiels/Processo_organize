package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.remote.dto.FilaDistribuicaoDto
import com.josiel.organizeprocesso.data.remote.dto.FilaDistribuicaoPorTipoDto
import com.josiel.organizeprocesso.data.remote.dto.paraItem
import com.josiel.organizeprocesso.domain.model.FilaDistribuicaoItem
import com.josiel.organizeprocesso.domain.usecase.ordenarFilaDistribuicao
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

/**
 * Não é cacheada no Room (spec do Plano 2C, seção 4, reforça 2A seção 2.2)
 * — consulta de rede ao vivo toda vez que a tela abre.
 */
class FilaDistribuicaoRepository(private val client: SupabaseClient) {
    suspend fun listar(): List<FilaDistribuicaoItem> {
        val dtos = client.postgrest["fila_distribuicao"].select().decodeList<FilaDistribuicaoDto>()
        return ordenarFilaDistribuicao(dtos.map { it.paraItem() })
    }

    suspend fun listarPorTipo(perfilId: String): List<FilaDistribuicaoPorTipoDto> =
        client.postgrest["fila_distribuicao_por_tipo"].select {
            filter { eq("perfil_id", perfilId) }
        }.decodeList()
}
