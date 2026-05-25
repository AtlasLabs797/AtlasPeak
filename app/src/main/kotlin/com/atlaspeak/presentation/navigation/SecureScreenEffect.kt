package com.atlaspeak.presentation.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.atlaspeak.BuildConfig

@Composable
fun SecureScreenEffect(route: String?) {
    val activity = LocalContext.current.findActivity() ?: return
    val secure = shouldApplySecureFlag(route)
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

internal fun shouldApplySecureFlag(
    route: String?,
    debugBuild: Boolean = BuildConfig.DEBUG,
    emulator: Boolean = isAndroidEmulator(),
): Boolean = isSensitiveRoute(route) && !(debugBuild && emulator)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun isAndroidEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT.lowercase()
    val model = Build.MODEL.lowercase()
    val manufacturer = Build.MANUFACTURER.lowercase()
    val brand = Build.BRAND.lowercase()
    val device = Build.DEVICE.lowercase()
    val product = Build.PRODUCT.lowercase()
    val hardware = Build.HARDWARE.lowercase()
    return fingerprint.startsWith("generic") ||
        fingerprint.contains("emulator") ||
        model.contains("sdk") ||
        model.contains("emulator") ||
        manufacturer.contains("genymotion") ||
        brand.startsWith("generic") ||
        device.startsWith("generic") ||
        product.contains("sdk") ||
        hardware.contains("ranchu") ||
        hardware.contains("goldfish")
}
