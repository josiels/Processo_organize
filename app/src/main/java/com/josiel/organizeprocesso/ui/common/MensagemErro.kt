package com.josiel.organizeprocesso.ui.common

import io.github.jan.supabase.exceptions.RestException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Mensagem padrão de falha de escrita (spec do Plano 2B, seção 4.1:
 * "erro de rede vira a mensagem 'sem conexão, tente novamente'").
 */
const val MENSAGEM_ERRO_GENERICA = "Sem conexão. Tente novamente."

/** Mensagem exibida quando não há perfil carregado para montar o payload. */
const val MENSAGEM_SESSAO_AUSENTE = "Sessão não encontrada. Faça login novamente."

/**
 * Converte a exceção de uma escrita em texto exibível ao usuário.
 *
 * Rejeição de RLS/validação do Postgrest é um desfecho ESPERADO nesta
 * arquitetura (spec do Plano 2B, seção 2: a RLS é a barreira real), então
 * quando o servidor devolve uma mensagem própria ela é mostrada — é mais
 * informativa que "sem conexão", que seria enganoso. `RestException.error`
 * é o campo `message` do erro do PostgREST; o `message` da exceção inclui
 * URL/headers e não serve para a UI. Qualquer outra falha (I/O, timeout,
 * DNS) cai na mensagem genérica.
 *
 * `RestException.error` tem formatos diferentes dependendo do plugin do
 * supabase-kt que lançou a exceção: no postgrest-kt já é uma string pronta
 * para exibição (o `message` parseado da resposta do PostgREST); no
 * functions-kt (usado por `client.functions.invoke`, ex.: Edge Function
 * `criar-conta`) é o corpo BRUTO da resposta HTTP, e nossas Edge Functions
 * devolvem esse corpo como `{"error":"mensagem"}`. Por isso tentamos
 * primeiro interpretar `.error` como esse JSON; se não for (caso do
 * postgrest-kt, que já é texto plano), a tentativa falha silenciosamente e
 * caímos no texto bruto, preservando o comportamento atual para todos os
 * demais call sites.
 */
fun mensagemDeErro(e: Exception): String = when (e) {
    is RestException -> mensagemDoRestException(e.error)
    else -> MENSAGEM_ERRO_GENERICA
}

private fun mensagemDoRestException(corpoBruto: String): String {
    if (corpoBruto.isBlank()) return MENSAGEM_ERRO_GENERICA
    val mensagemDoJson = try {
        Json.parseToJsonElement(corpoBruto).jsonObject["error"]?.jsonPrimitive?.content
    } catch (e: Exception) {
        null
    }
    return mensagemDoJson?.takeIf { it.isNotBlank() } ?: corpoBruto
}
