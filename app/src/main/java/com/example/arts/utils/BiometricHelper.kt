package com.example.arts.utils

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat

/**
 * Thin wrapper around androidx.biometric so fragments don't have to deal
 * with BiometricPrompt/CryptoObject boilerplate directly.
 */
object BiometricHelper {

    enum class Availability {
        AVAILABLE,
        NO_HARDWARE,
        HARDWARE_UNAVAILABLE,
        NONE_ENROLLED,
        UNKNOWN
    }

    private const val AUTH_TYPES = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /**
     * Authenticators checked: STRONG (fingerprint), WEAK (face), and DEVICE_CREDENTIAL (PIN/Pattern).
     */
    fun checkAvailability(fragment: Fragment): Availability {
        val ctx = fragment.context ?: return Availability.UNKNOWN
        val manager = BiometricManager.from(ctx)
        
        return when (manager.canAuthenticate(AUTH_TYPES)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Availability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Availability.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NONE_ENROLLED
            else -> Availability.UNKNOWN
        }
    }

    fun authenticate(
        fragment: Fragment,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val ctx = fragment.context ?: return
        val executor = ContextCompat.getMainExecutor(ctx)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }
        }

        val prompt = BiometricPrompt(fragment, executor, callback)

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTH_TYPES)

        // Android requirement: setNegativeButtonText CANNOT be used if DEVICE_CREDENTIAL is allowed.
        // The system provides its own "Use PIN" or "Cancel" buttons in that case.
        
        try {
            prompt.authenticate(builder.build())
        } catch (e: Exception) {
            onError("Biometric authentication failed to start: ${e.message}")
        }
    }
}
