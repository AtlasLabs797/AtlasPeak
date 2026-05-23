package com.atlaspeak.presentation.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
fun SecureScreenEffect(route: String?) {
    val activity = LocalContext.current.findActivity() ?: return
    val secure = route in secureRoutes
    DisposableEffect(activity, secure) {
        if (secure) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (secure) {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

private val secureRoutes = setOf(
    AppRoute.Login.route,
    AppRoute.Biometric.route,
    AppRoute.Onboarding.route,
    AppRoute.Profile.route,
    AppRoute.BackupRestore.route,
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
