package com.josiel.organizeprocesso.domain.usecase

import com.josiel.organizeprocesso.domain.model.FilaDistribuicaoItem
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class OrdenarFilaDistribuicaoTest {
    private fun item(nome: String, ultimoRecebimentoEm: Instant?, criadoEm: Instant) = FilaDistribuicaoItem(
        perfilId = nome,
        nome = nome,
        ultimoRecebimentoEm = ultimoRecebimentoEm,
        criadoEm = criadoEm,
        totalDesignacoes = 0
    )

    @Test
    fun `quem nunca recebeu vem antes de quem ja recebeu`() {
        val jaRecebeu = item("Ana", ultimoRecebimentoEm = Instant.parse("2026-09-01T00:00:00Z"), criadoEm = Instant.parse("2026-01-01T00:00:00Z"))
        val nuncaRecebeu = item("Bruno", ultimoRecebimentoEm = null, criadoEm = Instant.parse("2026-06-01T00:00:00Z"))

        val ordenado = ordenarFilaDistribuicao(listOf(jaRecebeu, nuncaRecebeu))

        assertEquals(listOf("Bruno", "Ana"), ordenado.map { it.nome })
    }

    @Test
    fun `entre quem nunca recebeu, ordena por data de criacao da conta`() {
        val maisNovo = item("Carla", ultimoRecebimentoEm = null, criadoEm = Instant.parse("2026-08-01T00:00:00Z"))
        val maisAntigo = item("Diego", ultimoRecebimentoEm = null, criadoEm = Instant.parse("2026-01-01T00:00:00Z"))

        val ordenado = ordenarFilaDistribuicao(listOf(maisNovo, maisAntigo))

        assertEquals(listOf("Diego", "Carla"), ordenado.map { it.nome })
    }

    @Test
    fun `entre quem ja recebeu, ordena por ultimo recebimento ascendente`() {
        val recebeuRecente = item("Elis", ultimoRecebimentoEm = Instant.parse("2026-09-01T00:00:00Z"), criadoEm = Instant.parse("2026-01-01T00:00:00Z"))
        val recebeuAntigo = item("Fabio", ultimoRecebimentoEm = Instant.parse("2026-02-01T00:00:00Z"), criadoEm = Instant.parse("2026-01-01T00:00:00Z"))

        val ordenado = ordenarFilaDistribuicao(listOf(recebeuRecente, recebeuAntigo))

        assertEquals(listOf("Fabio", "Elis"), ordenado.map { it.nome })
    }

    @Test
    fun `caso combinado com todos os grupos`() {
        val nuncaRecebeuAntigo = item("Nunca-Antigo", ultimoRecebimentoEm = null, criadoEm = Instant.parse("2026-01-01T00:00:00Z"))
        val nuncaRecebeuNovo = item("Nunca-Novo", ultimoRecebimentoEm = null, criadoEm = Instant.parse("2026-06-01T00:00:00Z"))
        val recebeuAntigo = item("Recebeu-Antigo", ultimoRecebimentoEm = Instant.parse("2026-03-01T00:00:00Z"), criadoEm = Instant.parse("2026-01-01T00:00:00Z"))
        val recebeuNovo = item("Recebeu-Novo", ultimoRecebimentoEm = Instant.parse("2026-08-01T00:00:00Z"), criadoEm = Instant.parse("2026-01-01T00:00:00Z"))

        val ordenado = ordenarFilaDistribuicao(listOf(recebeuNovo, nuncaRecebeuNovo, recebeuAntigo, nuncaRecebeuAntigo))

        assertEquals(
            listOf("Nunca-Antigo", "Nunca-Novo", "Recebeu-Antigo", "Recebeu-Novo"),
            ordenado.map { it.nome }
        )
    }
}
