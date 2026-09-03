package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.ProcessoResumoDashboard
import com.josiel.organizeprocesso.domain.model.StatusGeralProcesso
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SelecionarProximosPrazosTest {
    private val hoje = LocalDate.of(2026, 9, 2)

    private fun processo(
        id: String,
        prazoLimite: LocalDate?,
        statusGeral: StatusGeralProcesso = StatusGeralProcesso.EM_ANDAMENTO
    ) = ProcessoResumoDashboard(
        id = id,
        numero = id,
        objeto = "Objeto $id",
        statusGeral = statusGeral,
        statusSemaforo = StatusSemaforo.OK,
        prazoLimite = prazoLimite
    )

    @Test
    fun `ordena por prazo mais proximo primeiro`() {
        val processos = listOf(
            processo("1", hoje.plusDays(10)),
            processo("2", hoje.plusDays(2)),
            processo("3", hoje.plusDays(5))
        )
        val resultado = selecionarProximosPrazos(processos, hoje)
        assertEquals(listOf("2", "3", "1"), resultado.map { it.id })
    }

    @Test
    fun `exclui prazos vencidos`() {
        val processos = listOf(
            processo("1", hoje.minusDays(1)),
            processo("2", hoje.plusDays(1))
        )
        val resultado = selecionarProximosPrazos(processos, hoje)
        assertEquals(listOf("2"), resultado.map { it.id })
    }

    @Test
    fun `exclui processos sem prazo definido`() {
        val processos = listOf(processo("1", null), processo("2", hoje.plusDays(1)))
        val resultado = selecionarProximosPrazos(processos, hoje)
        assertEquals(listOf("2"), resultado.map { it.id })
    }

    @Test
    fun `prazo igual a hoje conta como proximo, nao vencido`() {
        val processos = listOf(processo("1", hoje))
        val resultado = selecionarProximosPrazos(processos, hoje)
        assertEquals(listOf("1"), resultado.map { it.id })
    }

    @Test
    fun `respeita o limite`() {
        val processos = (1..10).map { processo(it.toString(), hoje.plusDays(it.toLong())) }
        val resultado = selecionarProximosPrazos(processos, hoje, limite = 5)
        assertEquals(5, resultado.size)
    }
}
