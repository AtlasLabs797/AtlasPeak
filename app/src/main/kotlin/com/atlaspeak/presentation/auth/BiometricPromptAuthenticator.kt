package com.atlaspeak.presentation.auth

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.atlaspeak.domain.usecase.auth.BiometricAuthPolicy

class BiometricPromptAuthenticator(
    private val activity: FragmentActivity,
    private val policy: BiometricAuthPolicy,
) {
    fun authenticate(
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onSuccess: () -> Unit,
        onError: () -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError()
                }

                override fun onAuthenticationFailed() {
                    onError()
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(policy.allowedAuthenticators)
            .build()

        prompt.authenticate(promptInfo)
    }
}
