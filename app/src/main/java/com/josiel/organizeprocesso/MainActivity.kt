package com.josiel.organizeprocesso

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.josiel.organizeprocesso.navigation.AppNavHost
import com.josiel.organizeprocesso.ui.theme.Organize_ProcessoTheme

class MainActivity : ComponentActivity() {
    private var processoIdDeepLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        processoIdDeepLink = intent?.getStringExtra(EXTRA_PROCESSO_ID_DEEP_LINK)
        setContent {
            Organize_ProcessoTheme {
                AppNavHost(processoIdDeepLink = processoIdDeepLink, onDeepLinkConsumido = { processoIdDeepLink = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processoIdDeepLink = intent.getStringExtra(EXTRA_PROCESSO_ID_DEEP_LINK)
    }

    companion object {
        const val EXTRA_PROCESSO_ID_DEEP_LINK = "extra_processo_id_deep_link"
    }
}
