package com.atlaspeak

import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.atlaspeak.domain.model.settings.AppThemeMode
import com.atlaspeak.presentation.navigation.AtlasPeakApp
import com.atlaspeak.presentation.theme.AppThemeViewModel
import com.atlaspeak.presentation.theme.AtlasPeakTheme
import com.atlaspeak.presentation.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint

/**
 * Única Activity (single-activity + Navigation Compose).
 *
 * Fase 1: montar el tema Material 3 (DESIGN.md) y el NavHost raíz (launch → onboarding → app).
 * Las capturas estan permitidas en toda la app por decision de producto. Ver SECURITY.md.
 *
 * Extiende FragmentActivity (no ComponentActivity) porque `Identity.getAuthorizationClient()`
 * de Google Identity API requiere FragmentActivity para resolver el intent de autorización
 * de Drive. Cambiarlo exigiría refactorizar el flujo OAuth; el coste en tamaño de APK no
 * compensa el beneficio.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val themeViewModel: AppThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Tema flash en arranque en frio (auditoria): se mantiene el splash hasta que
        // AppThemeViewModel tenga un valor de tema definitivo (real, o el fallback a System si
        // la DB cifrada no abre), para que un usuario con Light/Dark forzado no vea un flash.
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !themeViewModel.isLoaded.value }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode = themeViewModel.themeMode.collectAsStateWithLifecycle().value
            AtlasPeakTheme(themeMode = themeMode.toPresentationThemeMode()) {
                AtlasPeakApp()
            }
        }
    }
}

private fun AppThemeMode.toPresentationThemeMode(): ThemeMode {
    return when (this) {
        AppThemeMode.System -> ThemeMode.SYSTEM
        AppThemeMode.Light -> ThemeMode.LIGHT
        AppThemeMode.Dark -> ThemeMode.DARK
    }
}
