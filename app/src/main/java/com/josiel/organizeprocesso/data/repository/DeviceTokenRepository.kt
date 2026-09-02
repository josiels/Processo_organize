package com.josiel.organizeprocesso.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Registra o token FCM do aparelho atual (spec do Plano 2D, seção 4). Sem
 * cache Room — é só uma escrita, sem tela de leitura. `unique(perfil_id,
 * token_fcm)` no banco permite múltiplos aparelhos por pessoa; o `upsert`
 * aqui é sobre esse par, então chamar de novo com o mesmo token é seguro
 * (só atualiza `atualizado_em`).
 */
class DeviceTokenRepository(private val client: SupabaseClient) {
    suspend fun registrar(perfilId: String, token: String) {
        val linha = buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("perfil_id", perfilId)
            put("token_fcm", token)
        }
        client.postgrest["device_tokens"].upsert(linha) {
            onConflict = "perfil_id, token_fcm"
        }
    }
}
