package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CalcularUrgenciaPrazoTest {
    private val hoje = LocalDate.of(2026, 9, 2)

    @Test
    fun `prazo vencido e critico`() {
        assertEquals(StatusSemaforo.CRITICO, calcularUrgenciaPrazo(hoje.minusDays(1), hoje))
    }

    @Test
    fun `prazo hoje e critico`() {
        assertEquals(StatusSemaforo.CRITICO, calcularUrgenciaPrazo(hoje, hoje))
    }

    @Test
    fun `prazo em 5 dias e atencao (limite exato)`() {
        assertEquals(StatusSemaforo.ATENCAO, calcularUrgenciaPrazo(hoje.plusDays(5), hoje))
    }

    @Test
    fun `prazo em 1 dia e atencao`() {
        assertEquals(StatusSemaforo.ATENCAO, calcularUrgenciaPrazo(hoje.plusDays(1), hoje))
    }

    @Test
    fun `prazo em 6 dias e ok`() {
        assertEquals(StatusSemaforo.OK, calcularUrgenciaPrazo(hoje.plusDays(6), hoje))
    }

    @Test
    fun `prazo bem no futuro e ok`() {
        assertEquals(StatusSemaforo.OK, calcularUrgenciaPrazo(hoje.plusDays(30), hoje))
    }
}
