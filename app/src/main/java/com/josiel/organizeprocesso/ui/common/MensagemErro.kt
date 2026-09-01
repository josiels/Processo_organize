package com.josiel.organizeprocesso.ui.common

import io.github.jan.supabase.exceptions.RestException

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
 */
fun mensagemDeErro(e: Exception): String = when (e) {
    is RestException -> e.error.takeIf { it.isNotBlank() } ?: MENSAGEM_ERRO_GENERICA
    else -> MENSAGEM_ERRO_GENERICA
}
