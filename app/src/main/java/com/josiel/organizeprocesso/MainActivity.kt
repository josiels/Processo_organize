package com.josiel.organizeprocesso

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.josiel.organizeprocesso.navigation.AppNavHost
import com.josiel.organizeprocesso.ui.theme.Organize_ProcessoTheme

class MainActivity : ComponentActivity() {
    private var processoIdDeepLink by mutableStateOf<String?>(null)

    private val solicitarPermissaoNotificacao =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Negada: o app segue funcionando normalmente, só sem notificação
            // local visível até o usuário habilitar manualmente depois.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        solicitarPermissaoNotificacaoSeNecessario()
        processoIdDeepLink = extrairProcessoIdDeepLink(intent)
        setContent {
            Organize_ProcessoTheme {
                AppNavHost(processoIdDeepLink = processoIdDeepLink, onDeepLinkConsumido = { processoIdDeepLink = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processoIdDeepLink = extrairProcessoIdDeepLink(intent)
    }

    private fun solicitarPermissaoNotificacaoSeNecessario() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        solicitarPermissaoNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * `EXTRA_PROCESSO_ID_DEEP_LINK` é o extra próprio, presente só quando o
     * usuário toca no `PendingIntent` montado pelo `OrganizeFirebaseMessagingService`
     * (app em primeiro plano ao receber o push). Com o app em segundo plano
     * ou fechado, o FCM entrega a mensagem como notification message e monta
     * o Intent de abertura sozinho, sem esse extra — os pares de `data` da
     * mensagem chegam como extras crus, daí o fallback em `"processo_id"`
     * (achado da revisão final do Plano 2D).
     */
    private fun extrairProcessoIdDeepLink(intent: Intent?): String? =
        intent?.getStringExtra(EXTRA_PROCESSO_ID_DEEP_LINK) ?: intent?.getStringExtra("processo_id")

    companion object {
        const val EXTRA_PROCESSO_ID_DEEP_LINK = "extra_processo_id_deep_link"
    }
}
