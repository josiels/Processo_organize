package com.josiel.organizeprocesso.domain.usecase

/** Resultado já pronto para montar a notificação local e o deep-link. */
data class NotificacaoPushInterpretada(
    val titulo: String,
    val corpo: String,
    val processoId: String?
)

/**
 * Interpreta o payload de uma mensagem FCM recebida (spec do Plano 2D, seção
 * 4) — função pura, sem dependência do SDK do Firebase, testável sem nenhuma
 * credencial real (spec, seção 5).
 */
fun interpretarNotificacaoPush(
    dadosMensagem: Map<String, String>,
    tituloNotification: String?,
    corpoNotification: String?
): NotificacaoPushInterpretada = NotificacaoPushInterpretada(
    titulo = tituloNotification?.takeIf { it.isNotBlank() } ?: "Organize Processo",
    corpo = corpoNotification?.takeIf { it.isNotBlank() } ?: "Você tem uma atualização.",
    processoId = dadosMensagem["processo_id"]?.takeIf { it.isNotBlank() }
)
