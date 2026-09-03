package com.josiel.organizeprocesso.data.repository

import com.josiel.organizeprocesso.data.local.PerfilDao
import com.josiel.organizeprocesso.data.local.PerfilEntity
import com.josiel.organizeprocesso.data.remote.dto.PerfilDto
import com.josiel.organizeprocesso.data.remote.papelDoTexto
import com.josiel.organizeprocesso.data.remote.updateVerificado
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Criação de perfil é exclusivamente via Edge Function (nunca por este repositório).
 * Atualizações devem evitar campos protegidos pela trigger `perfis_proteger_campos`:
 * `organizacao_id`, `papel`, `ativo`, `ultimo_recebimento_em`.
 * Espelha `public.perfis` da organização logada (RLS restringe à própria).
 */
class PerfilRepository(
    private val dao: PerfilDao,
    private val client: SupabaseClient
) {
    fun observarTodos(): Flow<List<PerfilEntity>> = dao.observarTodos()

    suspend fun sincronizar() {
        val dtos = client.postgrest["perfis"].select().decodeList<PerfilDto>()
        dao.upsertTodos(dtos.map { it.paraEntity() })
    }

    suspend fun atualizarPreferenciasNotificacao(
        perfilId: String,
        notificarAvancoFase: Boolean,
        notificarPrazo: Boolean,
        notificarTempoParado: Boolean
    ) {
        val linha = buildJsonObject {
            put("notificar_avanco_fase", notificarAvancoFase)
            put("notificar_prazo", notificarPrazo)
            put("notificar_tempo_parado", notificarTempoParado)
        }
        client.postgrest["perfis"].updateVerificado(linha) {
            filter { eq("id", perfilId) }
        }
        sincronizar()
    }
}

private fun PerfilDto.paraEntity(): PerfilEntity = PerfilEntity(
    id = id,
    organizacaoId = organizacaoId,
    papel = papelDoTexto(papel),
    nome = nome,
    cargoSetor = cargoSetor,
    ativo = ativo,
    notificarAvancoFase = notificarAvancoFase,
    notificarPrazo = notificarPrazo,
    notificarTempoParado = notificarTempoParado,
    ultimoRecebimentoEm = ultimoRecebimentoEm?.let(Instant::parse),
    criadoEm = Instant.parse(criadoEm)
)
