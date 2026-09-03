package com.josiel.organizeprocesso.domain.usecase

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GerarGradeCalendarioTest {
    @Test
    fun `mes que comeca no domingo nao tem celulas vazias`() {
        // Fevereiro de 2026 começa num domingo e tem exatamente 4 semanas (28 dias).
        val grade = gerarGradeCalendario(YearMonth.of(2026, 2))
        assertEquals(28, grade.size)
        assertEquals(LocalDate.of(2026, 2, 1), grade.first())
        assertEquals(LocalDate.of(2026, 2, 28), grade.last())
        assertEquals(0, grade.count { it == null })
    }

    @Test
    fun `mes que comeca no meio da semana tem celulas vazias no inicio e no fim`() {
        // Abril de 2026 começa numa quarta-feira (deslocamento 3) e tem 30 dias.
        val grade = gerarGradeCalendario(YearMonth.of(2026, 4))
        assertEquals(35, grade.size) // 5 semanas completas
        assertNull(grade[0])
        assertNull(grade[1])
        assertNull(grade[2])
        assertEquals(LocalDate.of(2026, 4, 1), grade[3])
        assertEquals(LocalDate.of(2026, 4, 30), grade[32])
        assertNull(grade[33])
        assertNull(grade[34])
    }

    @Test
    fun `tamanho da grade e sempre multiplo de 7`() {
        for (mes in 1..12) {
            val grade = gerarGradeCalendario(YearMonth.of(2026, mes))
            assertEquals(0, grade.size % 7)
        }
    }

    @Test
    fun `todos os dias do mes aparecem na grade, em ordem`() {
        val mes = YearMonth.of(2026, 4)
        val grade = gerarGradeCalendario(mes)
        val diasNaoNulos = grade.filterNotNull()
        assertEquals((1..30).map { mes.atDay(it) }, diasNaoNulos)
    }
}
