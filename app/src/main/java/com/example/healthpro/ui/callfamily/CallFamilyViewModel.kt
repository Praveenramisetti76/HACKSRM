package com.example.healthpro.ui.callfamily

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.healthpro.data.contacts.ContactsRepository
import com.example.healthpro.data.contacts.DeviceContact
import com.example.healthpro.data.contacts.SavedContact

class CallFamilyViewModel : ViewModel() {

    private var repository: ContactsRepository? = null

    var deviceContacts by mutableStateOf<List<DeviceContact>>(emptyList())
        private set

    var familyContacts by mutableStateOf<List<SavedContact>>(emptyList())
        private set

    var selectedContacts by mutableStateOf<Set<String>>(emptySet())
        private set

    var searchQuery by mutableStateOf("")

    var isLoading by mutableStateOf(false)
        private set

    var showContactPicker by mutableStateOf(false)

    fun init(context: Context) {
        if (repository == null) {
            repository = ContactsRepository(context.applicationContext)
            loadFamilyContacts()
        }
    }

    fun loadFamilyContacts() {
        repository?.let {
            familyContacts = it.getFamilyContacts()
        }
    }

    fun loadDeviceContacts() {
        isLoading = true
        repository?.let {
            deviceContacts = it.fetchDeviceContacts()
            isLoading = false
        }
    }

    fun toggleContactSelection(contactId: String) {
        selectedContacts = if (contactId in selectedContacts) {
            selectedContacts - contactId
        } else {
            selectedContacts + contactId
        }
    }

    fun saveSelectedAsFamilyContacts() {
        repository?.let { repo ->
            val selected = deviceContacts.filter { it.id in selectedContacts }
            selected.forEach { device ->
                repo.addFamilyContact(
                    SavedContact(
                        name = device.name,
                        phoneNumber = device.phoneNumber,
                        photoUri = device.photoUri
                    )
                )
            }
            familyContacts = repo.getFamilyContacts()
            selectedContacts = emptySet()
            showContactPicker = false
        }
    }

    fun removeFamilyContact(contact: SavedContact) {
        repository?.let { repo ->
            repo.removeFamilyContact(contact)
            familyContacts = repo.getFamilyContacts()
        }
    }

    fun getFilteredDeviceContacts(): List<DeviceContact> {
        if (searchQuery.isBlank()) return deviceContacts
        return deviceContacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.phoneNumber.contains(searchQuery)
        }
    }
}
