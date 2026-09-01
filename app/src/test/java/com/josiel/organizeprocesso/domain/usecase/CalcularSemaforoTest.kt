package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import org.junit.Assert.assertEquals
import org.junit.Test

class CalcularSemaforoTest {
    @Test
    fun `abaixo do limite de atencao fica OK`() {
        assertEquals(StatusSemaforo.OK, calcularSemaforo(2, diasAlertaAtencao = 5, diasAlertaCritico = 10))
    }

    @Test
    fun `exatamente no limite de atencao fica ATENCAO`() {
        assertEquals(StatusSemaforo.ATENCAO, calcularSemaforo(5, diasAlertaAtencao = 5, diasAlertaCritico = 10))
    }

    @Test
    fun `entre atencao e critico fica ATENCAO`() {
        assertEquals(StatusSemaforo.ATENCAO, calcularSemaforo(7, diasAlertaAtencao = 5, diasAlertaCritico = 10))
    }

    @Test
    fun `exatamente no limite critico fica CRITICO`() {
        assertEquals(StatusSemaforo.CRITICO, calcularSemaforo(10, diasAlertaAtencao = 5, diasAlertaCritico = 10))
    }

    @Test
    fun `acima do limite critico fica CRITICO`() {
        assertEquals(StatusSemaforo.CRITICO, calcularSemaforo(30, diasAlertaAtencao = 5, diasAlertaCritico = 10))
    }

    @Test
    fun `zero dias decorridos fica OK`() {
        assertEquals(StatusSemaforo.OK, calcularSemaforo(0, diasAlertaAtencao = 5, diasAlertaCritico = 10))
    }
}
