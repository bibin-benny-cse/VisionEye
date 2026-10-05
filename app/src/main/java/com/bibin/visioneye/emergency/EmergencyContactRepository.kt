package com.bibin.visioneye.emergency

import android.content.Context
import android.content.SharedPreferences

/**
 * Architectural contract for managing persisted emergency contacts.
 */
interface EmergencyContactRepository {
    /**
     * Retrieves the currently configured primary emergency contact, or null if none is saved.
     */
    fun getContact(): EmergencyContact?

    /**
     * Persists or updates the primary emergency contact.
     */
    fun saveContact(contact: EmergencyContact)

    /**
     * Removes the configured emergency contact.
     */
    fun clearContact()
}

/**
 * Production implementation of [EmergencyContactRepository] using Android [SharedPreferences].
 *
 * Persists the contact locally on-device. No data is sent to cloud servers.
 */
class SharedPreferencesEmergencyContactRepository(
    context: Context
) : EmergencyContactRepository {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val lock = Any()

    override fun getContact(): EmergencyContact? = synchronized(lock) {
        val name = prefs.getString(KEY_CONTACT_NAME, null)
        val phone = prefs.getString(KEY_CONTACT_PHONE, null)
        if (!name.isNullOrBlank() && !phone.isNullOrBlank()) {
            EmergencyContact(name = name, phoneNumber = phone)
        } else {
            null
        }
    }

    override fun saveContact(contact: EmergencyContact) = synchronized(lock) {
        val sanitizedPhone = EmergencyContact.sanitizePhoneNumber(contact.phoneNumber)
        prefs.edit()
            .putString(KEY_CONTACT_NAME, contact.name.trim())
            .putString(KEY_CONTACT_PHONE, sanitizedPhone)
            .apply()
    }

    override fun clearContact() = synchronized(lock) {
        prefs.edit()
            .remove(KEY_CONTACT_NAME)
            .remove(KEY_CONTACT_PHONE)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "visioneye_sos_contacts"
        private const val KEY_CONTACT_NAME = "key_contact_name"
        private const val KEY_CONTACT_PHONE = "key_contact_phone"
    }
}

/**
 * In-memory implementation of [EmergencyContactRepository] for deterministic testing.
 */
class InMemoryEmergencyContactRepository(
    initialContact: EmergencyContact? = null
) : EmergencyContactRepository {

    private val lock = Any()
    private var storedContact: EmergencyContact? = initialContact

    override fun getContact(): EmergencyContact? = synchronized(lock) {
        storedContact
    }

    override fun saveContact(contact: EmergencyContact) = synchronized(lock) {
        val sanitized = EmergencyContact(
            name = contact.name.trim(),
            phoneNumber = EmergencyContact.sanitizePhoneNumber(contact.phoneNumber)
        )
        storedContact = sanitized
    }

    override fun clearContact() = synchronized(lock) {
        storedContact = null
    }
}
