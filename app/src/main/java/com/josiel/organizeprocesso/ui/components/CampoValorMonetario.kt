package com.josiel.organizeprocesso.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import java.text.NumberFormat
import java.util.Locale

private val formatoMoeda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))

/** "R$ 1.234,56" a partir de um Double — usado para semear o campo com o valor já salvo. */
fun formatarComoReais(valor: Double): String = formatoMoeda.format(valor)

/**
 * Reformata o texto inteiro a partir dos dígitos digitados, dígito a dígito
 * entrando pela direita (centavos primeiro) — mesmo padrão de app de banco.
 * Campo vazio continua vazio, não força "R$ 0,00" antes do usuário digitar.
 */
private fun reformatarDigitos(textoDigitado: String): String {
    val digitos = textoDigitado.filter { it.isDigit() }
    if (digitos.isBlank()) return ""
    val centavos = digitos.toLongOrNull() ?: return ""
    return formatoMoeda.format(centavos / 100.0)
}

/** Extrai o valor numérico de um texto já formatado por este campo (ex: "R$ 1.234,56" -> 1234.56). */
fun valorMonetarioParaDouble(textoFormatado: String): Double {
    val digitos = textoFormatado.filter { it.isDigit() }
    return (digitos.toLongOrNull() ?: 0L) / 100.0
}

/**
 * Campo de valor em reais com máscara "digitando da direita pra esquerda"
 * (DESIGN.md — nenhum ponto/vírgula digitado manualmente, o campo formata
 * sozinho a cada dígito). Ler o valor final com [valorMonetarioParaDouble].
 */
@Composable
fun CampoValorMonetario(
    valor: String,
    onValorChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = valor,
        onValueChange = { onValorChange(reformatarDigitos(it)) },
        label = { Text(label) },
        enabled = enabled,
        supportingText = supportingText,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}
