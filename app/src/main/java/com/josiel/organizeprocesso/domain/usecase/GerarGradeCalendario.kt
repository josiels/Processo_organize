package com.josiel.organizeprocesso.domain.usecase

import java.time.LocalDate
import java.time.YearMonth

/**
 * Gera a grade do calendário para [mes]: uma lista de células (múltiplo de
 * 7, uma ou mais semanas completas), com `null` nas posições fora do mês
 * (antes do dia 1 ou depois do último dia) — semana começando no domingo
 * (spec da Agenda, seção 4).
 */
fun gerarGradeCalendario(mes: YearMonth): List<LocalDate?> {
    val primeiroDia = mes.atDay(1)
    // DayOfWeek.value: MONDAY=1..SUNDAY=7 — domingo precisa virar
    // deslocamento 0, daí o módulo por 7.
    val deslocamento = primeiroDia.dayOfWeek.value % 7
    val dias: List<LocalDate?> = (1..mes.lengthOfMonth()).map { mes.atDay(it) }
    val celulasIniciais: List<LocalDate?> = List(deslocamento) { null }
    val celulas = celulasIniciais + dias
    val restante = (7 - celulas.size % 7) % 7
    val celulasFinais: List<LocalDate?> = List(restante) { null }
    return celulas + celulasFinais
}
