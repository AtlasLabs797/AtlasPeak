package com.atlaspeak

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.atlaspeak.presentation.navigation.AtlasPeakApp
import com.atlaspeak.presentation.theme.AtlasPeakTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Única Activity (single-activity + Navigation Compose).
 *
 * Fase 1: montar el tema Material 3 (DESIGN.md) y el NavHost raíz (launch → onboarding → app).
 * Las pantallas con datos de salud/entrenamiento aplican FLAG_SECURE por destino. Ver SECURITY.md.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AtlasPeakTheme {
                AtlasPeakApp()
            }
        }
    }
}
