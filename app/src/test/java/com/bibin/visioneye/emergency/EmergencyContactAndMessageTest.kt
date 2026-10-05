package com.bibin.visioneye.emergency

import com.bibin.visioneye.navigation.location.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [EmergencyContact], [EmergencyContactRepository], and [EmergencyMessageFormatter].
 */
class EmergencyContactAndMessageTest {

    @Test
    fun `valid phone numbers pass validation`() {
        assertTrue(EmergencyContact.isValidPhoneNumber("+919876543210"))
        assertTrue(EmergencyContact.isValidPhoneNumber("9876543210"))
        assertTrue(EmergencyContact.isValidPhoneNumber("+1 800-555-0199"))
        assertTrue(EmergencyContact.isValidPhoneNumber("(123) 456-7890"))
        assertTrue(EmergencyContact.isValidPhoneNumber("1234567"))
    }

    @Test
    fun `invalid phone numbers are rejected`() {
        assertFalse(EmergencyContact.isValidPhoneNumber(null))
        assertFalse(EmergencyContact.isValidPhoneNumber(""))
        assertFalse(EmergencyContact.isValidPhoneNumber("   "))
        assertFalse(EmergencyContact.isValidPhoneNumber("abc1234567"))
        assertFalse(EmergencyContact.isValidPhoneNumber("123")) // Too short (< 7)
        assertFalse(EmergencyContact.isValidPhoneNumber("12345678901234567890")) // Too long (> 15)
    }

    @Test
    fun `phone number sanitization strips whitespace and formatting symbols`() {
        assertEquals("+919876543210", EmergencyContact.sanitizePhoneNumber("+91 987-654 (3210)"))
        assertEquals("9876543210", EmergencyContact.sanitizePhoneNumber("987.654.3210"))
    }

    @Test
    fun `EmergencyContact validation validates name and phone`() {
        val valid = EmergencyContact("Mother", "+919876543210")
        assertTrue(valid.isValid())

        val emptyName = EmergencyContact("   ", "+919876543210")
        assertFalse(emptyName.isValid())

        val invalidPhone = EmergencyContact("Doctor", "invalid")
        assertFalse(invalidPhone.isValid())
    }

    @Test
    fun `InMemoryEmergencyContactRepository saves, retrieves, and clears contacts`() {
        val repo = InMemoryEmergencyContactRepository()
        assertNull(repo.getContact())

        val contact = EmergencyContact("Guardian", "+1 555-123-4567")
        repo.saveContact(contact)

        val retrieved = repo.getContact()
        assertNotNull(retrieved)
        assertEquals("Guardian", retrieved?.name)
        assertEquals("+15551234567", retrieved?.phoneNumber)

        repo.clearContact()
        assertNull(repo.getContact())
    }

    @Test
    fun `map link format uses correct Google Maps query syntax`() {
        val link = EmergencyMessageFormatter.formatMapLink(12.9716, 77.5946)
        assertEquals("https://maps.google.com/?q=12.9716,77.5946", link)
    }

    @Test
    fun `emergency message formatting with valid location includes link and coordinates`() {
        val fix = LocationFix(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 4.5f
        )
        val message = EmergencyMessageFormatter.formatMessage(fix)

        assertTrue(message.contains("VisionEye SOS: Emergency assistance requested."))
        assertTrue(message.contains("Location: https://maps.google.com/?q=12.9716,77.5946"))
        assertTrue(message.contains("Approximate coords: 12.9716, 77.5946 (~4m accuracy)"))
    }

    @Test
    fun `emergency message formatting without location indicates location unavailable`() {
        val message = EmergencyMessageFormatter.formatMessage(null)

        assertTrue(message.contains("VisionEye SOS: Emergency assistance requested."))
        assertTrue(message.contains("Location unavailable."))
        assertFalse(message.contains("https://maps.google.com"))
    }
}
