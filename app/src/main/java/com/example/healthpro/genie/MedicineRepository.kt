package com.example.healthpro.genie

/**
 * Mock medicine data representing prescriptions from "Sahay internal DB".
 * Used for the simulated medicine ordering flow.
 *
 * Privacy: No real patient data. All entries are fictional.
 */

data class MedicineEntry(
    val id: Int,
    val name: String,
    val dosage: String,
    val frequency: String,
    val lastOrdered: String,
    val estimatedPrice: Double,
    val requiresPrescription: Boolean = false
)

object MedicineRepository {

    fun getMedicineHistory(): List<MedicineEntry> = listOf(
        MedicineEntry(
            id = 1,
            name = "Metformin",
            dosage = "500mg",
            frequency = "Twice daily",
            lastOrdered = "2026-01-15",
            estimatedPrice = 85.0,
            requiresPrescription = true
        ),
        MedicineEntry(
            id = 2,
            name = "Amlodipine",
            dosage = "5mg",
            frequency = "Once daily",
            lastOrdered = "2026-01-20",
            estimatedPrice = 120.0,
            requiresPrescription = true
        ),
        MedicineEntry(
            id = 3,
            name = "Vitamin D3",
            dosage = "60,000 IU",
            frequency = "Once weekly",
            lastOrdered = "2026-01-10",
            estimatedPrice = 45.0,
            requiresPrescription = false
        ),
        MedicineEntry(
            id = 4,
            name = "Pantoprazole",
            dosage = "40mg",
            frequency = "Once daily (before breakfast)",
            lastOrdered = "2026-02-01",
            estimatedPrice = 65.0,
            requiresPrescription = true
        ),
        MedicineEntry(
            id = 5,
            name = "Atorvastatin",
            dosage = "10mg",
            frequency = "Once daily (at bedtime)",
            lastOrdered = "2026-01-25",
            estimatedPrice = 95.0,
            requiresPrescription = true
        ),
        MedicineEntry(
            id = 6,
            name = "Cetirizine",
            dosage = "10mg",
            frequency = "As needed",
            lastOrdered = "2026-01-05",
            estimatedPrice = 30.0,
            requiresPrescription = false
        )
    )
}
