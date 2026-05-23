package com.atlaspeak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * Única Activity (single-activity + Navigation Compose).
 *
 * Fase 1: montar el tema Material 3 (DESIGN.md) y el NavHost raíz (auth → onboarding → app).
 * Las pantallas sensibles (Login, Biometría, Perfil, Backup) deben aplicar FLAG_SECURE
 * a nivel de su propio destino. Ver SECURITY.md.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // TODO(Fase 1): AtlasPeakTheme { AtlasPeakNavHost() }
        }
    }
}
