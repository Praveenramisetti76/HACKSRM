package com.example.healthpro.hospital

/**
 * Represents a hospital found via Places API or Maps search.
 */
data class HospitalInfo(
    val name: String,
    val address: String = "",
    val phoneNumber: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val distanceMeters: Double = 0.0
)
