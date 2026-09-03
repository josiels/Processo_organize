package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.ProcessoResumoDashboard
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import org.junit.Assert.assertEquals
import org.junit.Test

class CalcularProgressoGeralTest {
    private fun processo(
        statusGeral: StatusGeralProcesso = StatusGeralProcesso.EM_ANDAMENTO,
        statusSemaforo: StatusSemaforo = StatusSemaforo.OK
    ) = ProcessoResumoDashboard(
        id = "id",
        numero = "001",
        objeto = "Objeto",
        statusGeral = statusGeral,
        statusSemaforo = statusSemaforo,
        prazoLimite = null
    )

    @Test
    fun `lista vazia nao divide por zero`() {
        val resultado = calcularProgressoGeral(emptyList())
        assertEquals(0, resultado.total)
        assertEquals(0, resultado.emDia)
        assertEquals(0, resultado.percentual)
    }

    @Test
    fun `todos em dia da 100 por cento`() {
        val processos = listOf(
            processo(statusSemaforo = StatusSemaforo.OK),
            processo(statusSemaforo = StatusSemaforo.OK)
        )
        val resultado = calcularProgressoGeral(processos)
        assertEquals(2, resultado.total)
        assertEquals(2, resultado.emDia)
        assertEquals(100, resultado.percentual)
    }

    @Test
    fun `processos criticos nao contam como em dia`() {
        val processos = listOf(
            processo(statusSemaforo = StatusSemaforo.OK),
            processo(statusSemaforo = StatusSemaforo.CRITICO)
        )
        val resultado = calcularProgressoGeral(processos)
        assertEquals(2, resultado.total)
        assertEquals(1, resultado.emDia)
        assertEquals(50, resultado.percentual)
    }

    @Test
    fun `processos concluidos saem do denominador`() {
        val processos = listOf(
            processo(statusGeral = StatusGeralProcesso.EM_ANDAMENTO, statusSemaforo = StatusSemaforo.CRITICO),
            processo(statusGeral = StatusGeralProcesso.CONCLUIDO, statusSemaforo = StatusSemaforo.OK)
        )
        val resultado = calcularProgressoGeral(processos)
        assertEquals(1, resultado.total)
        assertEquals(0, resultado.emDia)
    }
}
