package com.josiel.organizeprocesso.data.remote

import com.josiel.organizeprocesso.data.repository.EscritaSemEfeitoException
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import io.github.jan.supabase.postgrest.query.PostgrestRequestBuilder
import kotlinx.serialization.json.JsonElement

/**
 * Substituto de `update()` que detecta o caso "sucesso, mas 0 linhas
 * afetadas" — indistinguível de um update real sem isto, porque a RLS
 * filtra a linha do `WHERE` em vez de rejeitar a requisição. Pede a
 * contagem exata ao servidor (`Prefer: count=exact`) e lança
 * [EscritaSemEfeitoException] quando ela vier zero.
 */
suspend fun PostgrestQueryBuilder.updateVerificado(
    linha: JsonElement,
    request: PostgrestRequestBuilder.() -> Unit
) {
    val resultado = update(linha) {
        count(Count.EXACT)
        request()
    }
    if ((resultado.countOrNull() ?: 0L) == 0L) {
        throw EscritaSemEfeitoException()
    }
}
