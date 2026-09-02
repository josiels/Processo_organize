package com.josiel.organizeprocesso.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InterpretarNotificacaoPushTest {
    @Test
    fun `extrai titulo corpo e processoId quando todos presentes`() {
        val resultado = interpretarNotificacaoPush(
            dadosMensagem = mapOf("processo_id" to "abc-123"),
            tituloNotification = "Prazo se aproximando",
            corpoNotification = "O processo 001/2026 tem prazo em 2026-09-10."
        )

        assertEquals("Prazo se aproximando", resultado.titulo)
        assertEquals("O processo 001/2026 tem prazo em 2026-09-10.", resultado.corpo)
        assertEquals("abc-123", resultado.processoId)
    }

    @Test
    fun `processoId nulo quando ausente do payload de dados`() {
        val resultado = interpretarNotificacaoPush(
            dadosMensagem = emptyMap(),
            tituloNotification = "Aviso",
            corpoNotification = "Mensagem sem processo associado."
        )

        assertNull(resultado.processoId)
    }

    @Test
    fun `titulo e corpo caem em texto padrao quando ausentes`() {
        val resultado = interpretarNotificacaoPush(
            dadosMensagem = mapOf("processo_id" to "xyz-789"),
            tituloNotification = null,
            corpoNotification = null
        )

        assertEquals("Organize Processo", resultado.titulo)
        assertEquals("Você tem uma atualização.", resultado.corpo)
    }
}
