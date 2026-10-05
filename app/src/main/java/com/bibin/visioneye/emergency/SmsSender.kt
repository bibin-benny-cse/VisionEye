package com.bibin.visioneye.emergency

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Result of an SMS dispatch operation.
 */
sealed interface SmsSendResult {
    /**
     * SMS was successfully submitted to the telephony subsystem for transmission.
     */
    data object Success : SmsSendResult

    /**
     * Dispatch failed due to an error (telephony error, network failure, invalid number).
     */
    data class Failure(val reason: String) : SmsSendResult

    /**
     * Dispatch could not proceed because [Manifest.permission.SEND_SMS] is not granted.
     */
    data class PermissionDenied(val message: String = "SEND_SMS permission not granted") : SmsSendResult
}

/**
 * Architectural contract for dispatching emergency SMS messages.
 */
interface SmsSender {
    /**
     * Checks if [Manifest.permission.SEND_SMS] runtime permission is currently granted.
     */
    fun hasPermission(): Boolean

    /**
     * Dispatches an emergency SMS message directly to [phoneNumber].
     */
    suspend fun sendSms(phoneNumber: String, message: String): SmsSendResult

    /**
     * Constructs a pre-filled [Intent.ACTION_SENDTO] intent as a fallback for explicit user-confirmed dispatch.
     */
    fun createSmsIntent(phoneNumber: String, message: String): Intent?
}

/**
 * Production implementation of [SmsSender] using the Android [SmsManager] telephony API.
 *
 * Handles:
 * - Runtime permission verification
 * - Modern API 31+ [Context.getSystemService] vs backward-compatible [SmsManager.getDefault]
 * - Multi-part message splitting for long GPS links
 * - Safe exception handling without application crashes
 */
class DefaultSmsSender(
    private val context: Context
) : SmsSender {

    override fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun sendSms(phoneNumber: String, message: String): SmsSendResult {
        val sanitizedPhone = EmergencyContact.sanitizePhoneNumber(phoneNumber)
        if (!EmergencyContact.isValidPhoneNumber(sanitizedPhone)) {
            Log.w(TAG, "sendSms rejected: Invalid phone number format: $phoneNumber")
            return SmsSendResult.Failure("Invalid recipient phone number")
        }

        if (!hasPermission()) {
            Log.w(TAG, "sendSms blocked: SEND_SMS permission is not granted.")
            return SmsSendResult.PermissionDenied()
        }

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
                    ?: @Suppress("DEPRECATION") SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(sanitizedPhone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(sanitizedPhone, null, message, null, null)
            }

            Log.d(TAG, "SMS successfully submitted to telephony stack for: $sanitizedPhone")
            SmsSendResult.Success
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException during SMS dispatch", se)
            SmsSendResult.PermissionDenied("SEND_SMS permission restricted by system: ${se.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during SMS dispatch", e)
            SmsSendResult.Failure(e.localizedMessage ?: "Failed to dispatch SMS")
        }
    }

    override fun createSmsIntent(phoneNumber: String, message: String): Intent? {
        return try {
            val sanitized = EmergencyContact.sanitizePhoneNumber(phoneNumber)
            Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$sanitized")
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error constructing SMS fallback intent", e)
            null
        }
    }

    companion object {
        private const val TAG = "DefaultSmsSender"
    }
}
