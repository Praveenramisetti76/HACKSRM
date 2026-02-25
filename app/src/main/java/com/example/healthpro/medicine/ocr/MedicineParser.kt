package com.example.healthpro.medicine.ocr

import android.util.Log

/**
 * Parses medicine names, dosage, and frequency from OCR-extracted text.
 *
 * Handles common Indian prescription patterns:
 *   - Tab./Cap./Syp./Inj. prefixes
 *   - OD, BD, TDS frequency shorthands
 *   - 1-0-1, 1-1-1 dosage schedule patterns
 *   - Numbered lists (1. Medicine 500mg ...)
 *   - Table-format prescriptions
 *   - Abbreviations and mixed case
 *
 * IMPORTANT: Extracts ALL medicines individually, not just the first match.
 */
object MedicineParser {

    private const val TAG = "MedicineParser"

    data class ParsedMedicine(
        val name: String,
        val dosage: String,
        val frequencyPerDay: Int
    )

    // ── Frequency mapping ──
    private val FREQ_PATTERNS = mapOf(
        "od" to 1, "o.d" to 1, "o.d." to 1, "once" to 1,
        "1-0-0" to 1, "0-0-1" to 1, "0-1-0" to 1,
        "bd" to 2, "b.d" to 2, "b.d." to 2, "b.i.d" to 2, "twice" to 2,
        "1-0-1" to 2, "1-1-0" to 2, "0-1-1" to 2,
        "tds" to 3, "t.d.s" to 3, "t.d.s." to 3, "t.i.d" to 3,
        "thrice" to 3, "1-1-1" to 3,
        "qid" to 4, "q.i.d" to 4, "1-1-1-1" to 4,
        "sos" to 1, "prn" to 1, "stat" to 1, "hs" to 1, "h.s" to 1
    )

    // ── Dosage units ──
    private val DOSAGE_REGEX = Regex(
        """(\d+(?:\.\d+)?)\s*(mg|mcg|ml|g|iu|units?|%|drops?)""",
        RegexOption.IGNORE_CASE
    )

    // ── Common medicine name suffixes (pharmacological) ──
    private val MEDICINE_SUFFIXES = listOf(
        "in", "ol", "am", "ide", "one", "ate", "ine", "ole", "ril",
        "tan", "cin", "fen", "lin", "min", "pin", "rin", "tin", "vin",
        "zol", "ase", "mab", "nib", "vir", "pam", "lam", "zam",
        "pril", "lone", "done", "pine", "zine", "dine", "mide",
        "tide", "zide", "oxin", "icin", "mycin", "cillin", "azole",
        "prazole", "sartan", "statin", "gliptin", "floxacin"
    )

    // ── Non-medicine words to skip ──
    private val SKIP_WORDS = setOf(
        "patient", "doctor", "hospital", "clinic", "date", "name",
        "age", "sex", "male", "female", "diagnosis", "prescription",
        "signature", "stamp", "reg", "address", "phone", "mobile",
        "email", "blood", "pressure", "sugar", "height", "weight",
        "pulse", "temperature", "chief", "complaint", "history",
        "examination", "investigation", "advice", "follow", "review",
        "morning", "evening", "night", "daily", "weekly", "monday",
        "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "before", "after", "food", "meal", "water", "empty", "stomach",
        "total", "amount", "quantity", "duration", "days",
        "medicine", "drug", "tablet", "capsule", "syrup", "injection",
        "the", "and", "for", "with", "from", "this", "that", "not"
    )

    /**
     * Parse ALL medicines from raw OCR text.
     * Uses multiple parsing strategies to maximize extraction.
     */
    fun parse(text: String): List<ParsedMedicine> {
        if (text.isBlank()) return emptyList()

        val medicines = mutableListOf<ParsedMedicine>()
        val seenNames = mutableSetOf<String>()

        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        Log.d(TAG, "Parsing ${lines.size} lines from OCR text")

        for (line in lines) {
            // Strategy 1: Structured prefix — Tab./Cap./Syp./Inj. + Name
            extractStructuredMedicine(line)?.let { med ->
                addIfNew(med, seenNames, medicines, "Strategy1-Structured")
            }

            // Strategy 2: Numbered list — "1. MedicineName 500mg OD"
            extractNumberedListMedicine(line)?.let { med ->
                addIfNew(med, seenNames, medicines, "Strategy2-Numbered")
            }

            // Strategy 3: Line with dosage — "MedicineName 500mg twice daily"
            extractDosageLine(line)?.let { med ->
                addIfNew(med, seenNames, medicines, "Strategy3-DosageLine")
            }

            // Strategy 4: Table row — separated by tabs/multiple spaces
            extractTableRow(line).forEach { med ->
                addIfNew(med, seenNames, medicines, "Strategy4-Table")
            }
        }

        // Strategy 5: Fallback sweep — find "Word 500mg" anywhere in full text
        extractFallbackWordDose(text).forEach { med ->
            addIfNew(med, seenNames, medicines, "Strategy5-Fallback")
        }

        // Strategy 6: Bare medicine names (known pharma suffixes, no dosage)
        for (line in lines) {
            extractBareMedicineName(line)?.let { med ->
                addIfNew(med, seenNames, medicines, "Strategy6-BareName")
            }
        }

        Log.d(TAG, "Total extracted: ${medicines.size} medicines")
        return medicines
    }

    // ═══════════════════════════════════════════
    // STRATEGY 1: Structured prefix
    // ═══════════════════════════════════════════

    private val STRUCTURED_REGEX = Regex(
        """(?:Tab\.?|Cap\.?|Syp\.?|Inj\.?|Tablet|Capsule|Syrup|Injection)\s+([A-Za-z][A-Za-z0-9\s\-]+?)(?:\s+(\d+(?:\.\d+)?\s*(?:mg|mcg|ml|g|iu|units?|%)))?(?:\s*[-–:,]?\s*(.+))?$""",
        RegexOption.IGNORE_CASE
    )

    private fun extractStructuredMedicine(line: String): ParsedMedicine? {
        val match = STRUCTURED_REGEX.find(line) ?: return null
        val rawName = match.groupValues[1].trim().replace(Regex("\\s+"), " ")
        val dosage = match.groupValues[2].trim().ifBlank { "" }
        val freqStr = match.groupValues[3].trim()
        val name = cleanMedicineName(rawName)
        if (!isValidMedicineName(name)) return null
        return ParsedMedicine(name, dosage.ifBlank { "as prescribed" }, parseFrequency(freqStr))
    }

    // ═══════════════════════════════════════════
    // STRATEGY 2: Numbered list
    // ═══════════════════════════════════════════

    private val NUMBERED_REGEX = Regex(
        """^\s*\d+[.)]\s*(?:Tab\.?|Cap\.?|Syp\.?|Inj\.?)?\s*([A-Za-z][A-Za-z0-9\s\-]+?)(?:\s+(\d+(?:\.\d+)?\s*(?:mg|mcg|ml|g|iu|units?|%)))?(?:\s*[-–:,]?\s*(.+))?$""",
        RegexOption.IGNORE_CASE
    )

    private fun extractNumberedListMedicine(line: String): ParsedMedicine? {
        val match = NUMBERED_REGEX.find(line) ?: return null
        val rawName = match.groupValues[1].trim().replace(Regex("\\s+"), " ")
        val dosage = match.groupValues[2].trim().ifBlank { "" }
        val freqStr = match.groupValues[3].trim()
        val name = cleanMedicineName(rawName)
        if (!isValidMedicineName(name)) return null
        return ParsedMedicine(name, dosage.ifBlank { "as prescribed" }, parseFrequency(freqStr))
    }

    // ═══════════════════════════════════════════
    // STRATEGY 3: Line with dosage
    // ═══════════════════════════════════════════

    private val DOSAGE_LINE_REGEX = Regex(
        """([A-Z][a-zA-Z0-9]+(?:\s+[A-Z][a-zA-Z0-9]+)?)\s+(\d+(?:\.\d+)?\s*(?:mg|mcg|ml|g|iu|units?|%))\s*[-–:,]?\s*(.*)""",
        RegexOption.IGNORE_CASE
    )

    private fun extractDosageLine(line: String): ParsedMedicine? {
        val match = DOSAGE_LINE_REGEX.find(line) ?: return null
        val rawName = match.groupValues[1].trim()
        val dosage = match.groupValues[2].trim()
        val freqStr = match.groupValues[3].trim()
        val name = cleanMedicineName(rawName)
        if (!isValidMedicineName(name)) return null
        return ParsedMedicine(name, dosage, parseFrequency(freqStr))
    }

    // ═══════════════════════════════════════════
    // STRATEGY 4: Table row
    // ═══════════════════════════════════════════

    private fun extractTableRow(line: String): List<ParsedMedicine> {
        // Detect table-like separators (multiple spaces, tabs, |)
        if (!line.contains(Regex("\\s{3,}|\t|\\|"))) return emptyList()

        val parts = line.split(Regex("\\s{3,}|\t|\\|")).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 2) return emptyList()

        val results = mutableListOf<ParsedMedicine>()
        for (part in parts) {
            val dosageMatch = DOSAGE_REGEX.find(part)
            if (dosageMatch != null) {
                // This part has dosage, look for name in previous parts
                continue
            }
            val cleaned = cleanMedicineName(part)
            if (isValidMedicineName(cleaned)) {
                // Find dosage in adjacent parts
                val otherParts = parts.filter { it != part }
                val dosage = otherParts.firstNotNullOfOrNull { DOSAGE_REGEX.find(it)?.value }
                val freqStr = otherParts.joinToString(" ")
                results.add(ParsedMedicine(cleaned, dosage ?: "as prescribed", parseFrequency(freqStr)))
            }
        }
        return results
    }

    // ═══════════════════════════════════════════
    // STRATEGY 5: Fallback word+dosage sweep
    // ═══════════════════════════════════════════

    private val WORD_DOSE_REGEX = Regex(
        """([A-Z][a-zA-Z]{2,})\s+(\d+(?:\.\d+)?\s*(?:mg|mcg|ml|g|iu))""",
        RegexOption.IGNORE_CASE
    )

    private fun extractFallbackWordDose(text: String): List<ParsedMedicine> {
        val results = mutableListOf<ParsedMedicine>()
        WORD_DOSE_REGEX.findAll(text).forEach { match ->
            val name = cleanMedicineName(match.groupValues[1].trim())
            val dosage = match.groupValues[2].trim()
            if (isValidMedicineName(name)) {
                results.add(ParsedMedicine(name, dosage, 1))
            }
        }
        return results
    }

    // ═══════════════════════════════════════════
    // STRATEGY 6: Bare medicine name (pharma suffix)
    // ═══════════════════════════════════════════

    private fun extractBareMedicineName(line: String): ParsedMedicine? {
        // Only if line starts with a capitalized word ending in a pharma suffix
        val words = line.split(Regex("\\s+"))
        val firstWord = words.firstOrNull() ?: return null
        if (firstWord.length < 4) return null
        if (!firstWord.first().isUpperCase()) return null

        val lower = firstWord.lowercase()
        val hasPharmaSuffix = MEDICINE_SUFFIXES.any { lower.endsWith(it) }
        if (!hasPharmaSuffix) return null
        if (SKIP_WORDS.contains(lower)) return null

        // Look for dosage or frequency in remaining text
        val remaining = words.drop(1).joinToString(" ")
        val dosageMatch = DOSAGE_REGEX.find(remaining)
        val dosage = dosageMatch?.value ?: "as prescribed"
        val freq = parseFrequency(remaining)

        return ParsedMedicine(firstWord, dosage, freq)
    }

    // ═══════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════

    /**
     * Clean medicine name by removing trailing non-alphabetic chars and extra spaces.
     */
    private fun cleanMedicineName(raw: String): String {
        var name = raw.trim()
        // Remove leading number/dot (e.g., "1. " or "2) ")
        name = name.replace(Regex("^\\d+[.)\\s]+"), "")
        // Remove type prefixes
        name = name.replace(Regex("^(?:Tab\\.?|Cap\\.?|Syp\\.?|Inj\\.?|Tablet|Capsule|Syrup|Injection)\\s+", RegexOption.IGNORE_CASE), "")
        // Remove trailing dosage info
        name = name.replace(Regex("\\s+\\d+(?:\\.\\d+)?\\s*(?:mg|mcg|ml|g|iu|units?|%).*$", RegexOption.IGNORE_CASE), "")
        // Remove trailing punctuation
        name = name.replace(Regex("[,;:.\\-–]+$"), "")
        // Normalize whitespace
        name = name.replace(Regex("\\s+"), " ").trim()
        return name
    }

    /**
     * Validate that a string looks like a medicine name, not a common English word.
     */
    private fun isValidMedicineName(name: String): Boolean {
        if (name.length < 3) return false
        if (name.length > 50) return false
        if (!name.first().isLetter()) return false
        if (SKIP_WORDS.contains(name.lowercase())) return false
        // Must contain at least 3 letters
        if (name.count { it.isLetter() } < 3) return false
        return true
    }

    /**
     * Add medicine to list if not already seen (de-duplicate by lowercase name).
     */
    private fun addIfNew(
        med: ParsedMedicine,
        seenNames: MutableSet<String>,
        medicines: MutableList<ParsedMedicine>,
        strategy: String
    ) {
        val key = med.name.lowercase()
        if (!seenNames.contains(key)) {
            seenNames.add(key)
            medicines.add(med)
            Log.d(TAG, "[$strategy] Found: ${med.name} ${med.dosage} ${med.frequencyPerDay}x/day")
        }
    }

    /**
     * Map frequency keywords to times per day.
     * OD → 1, BD → 2, TDS → 3
     */
    fun parseFrequency(freqStr: String): Int {
        if (freqStr.isBlank()) return 1
        val lower = freqStr.lowercase()
        for ((keyword, count) in FREQ_PATTERNS) {
            if (lower.contains(keyword)) return count
        }
        if (lower.contains("morning") && lower.contains("night")) return 2
        if (lower.contains("twice")) return 2
        if (lower.contains("thrice")) return 3
        if (lower.contains("daily") || lower.contains("day")) return 1
        return 1
    }
}
