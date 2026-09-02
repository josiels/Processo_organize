package com.josiel.organizeprocesso.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.josiel.organizeprocesso.MainActivity
import com.josiel.organizeprocesso.R
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.DeviceTokenRepository
import com.josiel.organizeprocesso.domain.usecase.interpretarNotificacaoPush
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val CANAL_ID = "organize_processo_notificacoes"
private const val NOTIFICATION_ID_BASE = 1000

/**
 * Recebe pushes do FCM (spec do Plano 2D, seção 4). `onNewToken` registra o
 * token; `onMessageReceived` mostra uma notificação local com deep-link para
 * o processo relevante, quando houver um.
 */
class OrganizeFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val perfilId = SupabaseSessionManager.perfilAtual.value?.id ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DeviceTokenRepository(SupabaseSessionManager.client).registrar(perfilId, token)
            } catch (_: Exception) {
                // Falha ao registrar o token não é acionável pelo usuário aqui
                // (não há tela em foco necessariamente) — a próxima chamada de
                // onNewToken ou um novo login tenta de novo.
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val interpretada = interpretarNotificacaoPush(
            dadosMensagem = message.data,
            tituloNotification = message.notification?.title,
            corpoNotification = message.notification?.body
        )
        criarCanalSeNecessario()
        exibirNotificacao(interpretada.titulo, interpretada.corpo, interpretada.processoId)
    }

    private fun criarCanalSeNecessario() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(
            CANAL_ID,
            "Atualizações de processos",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(canal)
    }

    private fun exibirNotificacao(titulo: String, corpo: String, processoId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (processoId != null) putExtra(MainActivity.EXTRA_PROCESSO_ID_DEEP_LINK, processoId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            processoId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificacao = NotificationCompat.Builder(this, CANAL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(
            NOTIFICATION_ID_BASE + (processoId?.hashCode()?.let { it and 0xFFFF } ?: 0),
            notificacao
        )
    }
}
