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
    DisposableEffect(activity, route) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun shouldApplySecureFlag(route: String?): Boolean = false

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
