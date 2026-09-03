package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.ProcessoResumoDashboard
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import org.junit.Assert.assertEquals
import org.junit.Test

class SelecionarProcessosCriticosTest {
    private fun processo(
        id: String,
        statusGeral: StatusGeralProcesso = StatusGeralProcesso.EM_ANDAMENTO,
        statusSemaforo: StatusSemaforo = StatusSemaforo.CRITICO
    ) = ProcessoResumoDashboard(
        id = id,
        numero = id,
        objeto = "Objeto $id",
        statusGeral = statusGeral,
        statusSemaforo = statusSemaforo,
        prazoLimite = null
    )

    @Test
    fun `so processos criticos em andamento entram`() {
        val processos = listOf(
            processo("1", statusSemaforo = StatusSemaforo.CRITICO),
            processo("2", statusSemaforo = StatusSemaforo.OK),
            processo("3", statusGeral = StatusGeralProcesso.CONCLUIDO, statusSemaforo = StatusSemaforo.CRITICO)
        )
        val resultado = selecionarProcessosCriticos(processos)
        assertEquals(listOf("1"), resultado.map { it.id })
    }

    @Test
    fun `respeita o limite`() {
        val processos = (1..10).map { processo(it.toString()) }
        val resultado = selecionarProcessosCriticos(processos, limite = 5)
        assertEquals(5, resultado.size)
    }

    @Test
    fun `lista vazia retorna lista vazia`() {
        assertEquals(emptyList<ProcessoResumoDashboard>(), selecionarProcessosCriticos(emptyList()))
    }
}
