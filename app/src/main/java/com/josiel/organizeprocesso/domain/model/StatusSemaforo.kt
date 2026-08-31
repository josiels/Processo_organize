package com.josiel.organizeprocesso.domain.model

/**
 * Semáforo de tempo parado na fase (ARQUITETURA.md, seção 5, regra 3).
 * O cálculo (comparar data_entrada com dias_alerta_atencao/critico da fase)
 * é responsabilidade de um usecase futuro — este tipo só representa o estado.
 */
enum class StatusSemaforo {
    OK,
    ATENCAO,
    CRITICO
}
