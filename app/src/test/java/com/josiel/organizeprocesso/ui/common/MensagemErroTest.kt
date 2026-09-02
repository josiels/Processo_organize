package com.josiel.organizeprocesso.ui.common

import io.github.jan.supabase.exceptions.RestException
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cobre o fallback de duas etapas em [mensagemDeErro] para [RestException]:
 *
 * - postgrest-kt preenche `.error` com uma string já pronta para exibição.
 * - functions-kt (usado por `client.functions.invoke`, ex.: `criar-conta`)
 *   preenche `.error` com o corpo bruto da resposta, que é um JSON no
 *   formato `{"error":"mensagem"}` produzido pela Edge Function.
 */
class MensagemErroTest {

    @Test
    fun `RestException estilo postgrest-kt com mensagem em texto puro retorna a mensagem inalterada`() {
        val excecao = mockk<RestException>()
        every { excecao.error } returns "Você só pode autoatribuir um processo órfão a si mesmo"

        val resultado = mensagemDeErro(excecao)

        assertEquals("Você só pode autoatribuir um processo órfão a si mesmo", resultado)
    }

    @Test
    fun `RestException estilo functions-kt com corpo JSON extrai o campo error`() {
        val excecao = mockk<RestException>()
        every { excecao.error } returns """{"error":"Apenas admin pode criar contas"}"""

        val resultado = mensagemDeErro(excecao)

        assertEquals("Apenas admin pode criar contas", resultado)
    }

    @Test
    fun `excecao que nao e RestException retorna a mensagem generica`() {
        val excecao = RuntimeException("timeout de rede")

        val resultado = mensagemDeErro(excecao)

        assertEquals(MENSAGEM_ERRO_GENERICA, resultado)
    }
}
