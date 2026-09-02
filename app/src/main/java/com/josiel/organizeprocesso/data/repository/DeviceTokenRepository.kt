package com.josiel.organizeprocesso.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Registra e remove o token FCM do aparelho atual (spec do Plano 2D, seção
 * 4). Sem cache Room — é só escrita, sem tela de leitura. `unique(perfil_id,
 * token_fcm)` no banco permite múltiplos aparelhos por pessoa; o `upsert`
 * de [registrar] é sobre esse par, então chamar de novo com o mesmo token é
 * seguro — reescreve `id` e `atualizado_em` a cada chamada (nada referencia
 * `device_tokens.id`, então isso é inofensivo, e mantém `atualizado_em`
 * fresco para uma futura poda de tokens por idade).
 */
class DeviceTokenRepository(private val client: SupabaseClient) {
    suspend fun registrar(perfilId: String, token: String) {
        val linha = buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("perfil_id", perfilId)
            put("token_fcm", token)
            put("atualizado_em", Instant.now().toString())
        }
        client.postgrest["device_tokens"].upsert(linha) {
            onConflict = "perfil_id, token_fcm"
        }
    }

    /**
     * Remove o token deste aparelho no logout — sem isso, quem logar em
     * seguida no mesmo aparelho continuaria recebendo as notificações do
     * usuário anterior (achado da revisão final do Plano 2D).
     */
    suspend fun removerDoAparelhoAtual(perfilId: String, token: String) {
        client.postgrest["device_tokens"].delete {
            filter {
                eq("perfil_id", perfilId)
                eq("token_fcm", token)
            }
        }
    }
}
