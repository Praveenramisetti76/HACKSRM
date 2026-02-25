package com.example.healthpro.data.contacts

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Represents a saved contact (family or emergency).
 */
data class SavedContact(
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null
) {
    companion object {
        private val gson = Gson()

        fun toJson(contacts: List<SavedContact>): String {
            return gson.toJson(contacts)
        }

        fun fromJson(json: String): List<SavedContact> {
            if (json.isBlank()) return emptyList()
            val type = object : TypeToken<List<SavedContact>>() {}.type
            return gson.fromJson(json, type)
        }
    }
}

/**
 * Represents a contact fetched from the device contacts provider.
 */
data class DeviceContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null
)
