package com.josiel.organizeprocesso

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.josiel.organizeprocesso.navigation.AppNavHost
import com.josiel.organizeprocesso.ui.theme.Organize_ProcessoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Organize_ProcessoTheme {
                AppNavHost()
            }
        }
    }
}
