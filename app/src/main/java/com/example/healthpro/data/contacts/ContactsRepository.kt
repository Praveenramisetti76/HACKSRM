package com.example.healthpro.data.contacts

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.provider.ContactsContract

/**
 * Repository for managing device contacts and persisted family/emergency contacts.
 */
class ContactsRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sahay_contacts", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FAMILY_CONTACTS = "family_contacts"
        private const val KEY_EMERGENCY_CONTACTS = "emergency_contacts"
    }

    // ─── Device Contacts ─────────────────────────────────────────

    fun fetchDeviceContacts(): List<DeviceContact> {
        val contacts = mutableListOf<DeviceContact>()
        val contentResolver: ContentResolver = context.contentResolver

        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

            val seen = mutableSetOf<String>()

            while (it.moveToNext()) {
                val id = it.getString(idIdx) ?: continue
                val name = it.getString(nameIdx) ?: continue
                val number = it.getString(numberIdx) ?: continue
                val photo = if (photoIdx >= 0) it.getString(photoIdx) else null

                // Normalize the number for dedup
                val normalized = number.replace(Regex("[^\\d+]"), "")
                val key = "$name|$normalized"
                if (key !in seen) {
                    seen.add(key)
                    contacts.add(
                        DeviceContact(
                            id = id,
                            name = name,
                            phoneNumber = number,
                            photoUri = photo
                        )
                    )
                }
            }
        }

        return contacts
    }

    // ─── Family Contacts ─────────────────────────────────────────

    fun saveFamilyContacts(contacts: List<SavedContact>) {
        prefs.edit()
            .putString(KEY_FAMILY_CONTACTS, SavedContact.toJson(contacts))
            .apply()
    }

    fun getFamilyContacts(): List<SavedContact> {
        val json = prefs.getString(KEY_FAMILY_CONTACTS, "") ?: ""
        return SavedContact.fromJson(json)
    }

    fun addFamilyContact(contact: SavedContact) {
        val existing = getFamilyContacts().toMutableList()
        // Avoid duplicates by phone number
        if (existing.none { it.phoneNumber.replace(Regex("[^\\d+]"), "") == contact.phoneNumber.replace(Regex("[^\\d+]"), "") }) {
            existing.add(contact)
            saveFamilyContacts(existing)
        }
    }

    fun removeFamilyContact(contact: SavedContact) {
        val existing = getFamilyContacts().toMutableList()
        existing.removeAll { it.phoneNumber.replace(Regex("[^\\d+]"), "") == contact.phoneNumber.replace(Regex("[^\\d+]"), "") }
        saveFamilyContacts(existing)
    }

    // ─── Emergency Contacts ──────────────────────────────────────

    fun saveEmergencyContacts(contacts: List<SavedContact>) {
        prefs.edit()
            .putString(KEY_EMERGENCY_CONTACTS, SavedContact.toJson(contacts))
            .apply()
    }

    fun getEmergencyContacts(): List<SavedContact> {
        val json = prefs.getString(KEY_EMERGENCY_CONTACTS, "") ?: ""
        return SavedContact.fromJson(json)
    }

    fun addEmergencyContact(contact: SavedContact) {
        val existing = getEmergencyContacts().toMutableList()
        if (existing.none { it.phoneNumber.replace(Regex("[^\\d+]"), "") == contact.phoneNumber.replace(Regex("[^\\d+]"), "") }) {
            existing.add(contact)
            saveEmergencyContacts(existing)
        }
    }

    fun removeEmergencyContact(contact: SavedContact) {
        val existing = getEmergencyContacts().toMutableList()
        existing.removeAll { it.phoneNumber.replace(Regex("[^\\d+]"), "") == contact.phoneNumber.replace(Regex("[^\\d+]"), "") }
        saveEmergencyContacts(existing)
    }
}
