package com.example.healthpro.genie

/**
 * Genie Intent Parser — rule-based NLP with Hinglish support.
 *
 * Extracts structured intent from free-text voice commands.
 * Supports English and basic romanized Hindi (Hinglish).
 */

// ── Data Models ──────────────────────────────────────────────

enum class IntentType { FOOD, PRODUCT, MEDICINE }

enum class Platform(
    val packageName: String,
    val appName: String,
    val playStoreId: String,
    val category: IntentType
) {
    // Food delivery
    SWIGGY("in.swiggy.android", "Swiggy", "in.swiggy.android", IntentType.FOOD),
    ZOMATO("com.application.zomato", "Zomato", "com.application.zomato", IntentType.FOOD),
    DOMINOS("com.dominos.android", "Domino's", "com.dominos.android", IntentType.FOOD),
    MCDONALDS("com.mcdonalds.mobileapp", "McDonald's", "com.mcdonalds.mobileapp", IntentType.FOOD),
    KFC("com.yum.kfcindia", "KFC", "com.yum.kfcindia", IntentType.FOOD),

    // E-commerce / products
    AMAZON("in.amazon.mShop.android.shopping", "Amazon", "in.amazon.mShop.android.shopping", IntentType.PRODUCT),
    FLIPKART("com.flipkart.android", "Flipkart", "com.flipkart.android", IntentType.PRODUCT),
    MEESHO("com.meesho.supply", "Meesho", "com.meesho.supply", IntentType.PRODUCT),
    MYNTRA("com.myntra.android", "Myntra", "com.myntra.android", IntentType.PRODUCT),
    AJIO("com.ril.ajio", "Ajio", "com.ril.ajio", IntentType.PRODUCT),

    // Medicine (mocked)
    TATA_1MG("com.aranoah.healthkart.plus", "Tata 1mg", "com.aranoah.healthkart.plus", IntentType.MEDICINE),
    NETMEDS("com.netmeds.app", "Netmeds", "com.netmeds.app", IntentType.MEDICINE),
    PHARMEASY("com.pharmeasy.pharmeasy", "PharmEasy", "com.pharmeasy.pharmeasy", IntentType.MEDICINE),
    APOLLO("com.apollopharmacy.online", "Apollo Pharmacy", "com.apollopharmacy.online", IntentType.MEDICINE),
    PRACTO("com.practo.fabric", "Practo", "com.practo.fabric", IntentType.MEDICINE);

    companion object {
        fun defaultForType(type: IntentType): Platform = when (type) {
            IntentType.FOOD -> SWIGGY
            IntentType.PRODUCT -> AMAZON
            IntentType.MEDICINE -> TATA_1MG
        }
    }
}

data class GenieIntent(
    val type: IntentType,
    val platform: Platform,
    val item: String
)

// ── Hinglish Normalization ───────────────────────────────────

private val hinglishMap = mapOf(
    // Food-related
    "khana" to "food", "khane" to "food", "kha" to "eat",
    "pizza" to "pizza", "biryani" to "biryani",
    "chai" to "tea", "nashta" to "breakfast",
    "roti" to "roti", "daal" to "dal", "sabzi" to "vegetables",
    // Action-related
    "khareedna" to "buy", "kharidna" to "buy", "kharid" to "buy",
    "mangwa" to "order", "mangao" to "order", "manga" to "order",
    "bhej" to "send", "lao" to "get", "la" to "get",
    // Medicine-related
    "dawai" to "medicine", "dawa" to "medicine", "goli" to "tablet",
    "aushadhi" to "medicine",
    // General
    "mujhe" to "me", "mera" to "my", "se" to "from",
    "chahiye" to "want", "do" to "give", "de" to "give"
)

private fun normalizeHinglish(text: String): String {
    var result = text.lowercase().trim()
    hinglishMap.forEach { (hindi, english) ->
        result = result.replace("\\b$hindi\\b".toRegex(), english)
    }
    return result
}

// ── Keyword Sets ─────────────────────────────────────────────

private val foodKeywords = setOf(
    "eat", "food", "hungry", "lunch", "dinner", "breakfast",
    "snack", "order food", "restaurant", "deliver food",
    "pizza", "burger", "sandwich", "biryani", "dal", "roti",
    "pasta", "noodles", "rice", "curry", "chicken", "paneer",
    "thali", "dosa", "idli", "tea", "coffee", "cake"
)

private val productKeywords = setOf(
    "buy", "purchase", "product", "shopping", "shop",
    "charger", "phone", "laptop", "headphone", "earphone",
    "shoes", "shirt", "clothes", "watch", "bag", "book",
    "electronics", "gadget", "camera", "tablet", "mouse",
    "keyboard", "cable", "cover", "case", "stand"
)

private val medicineKeywords = setOf(
    "medicine", "prescription", "pills", "tablet", "pharmacy",
    "reorder", "medical", "health", "drug", "capsule",
    "syrup", "ointment", "inhaler", "insulin", "painkiller"
)

private val platformAliases = mapOf(
    "swiggy" to Platform.SWIGGY,
    "zomato" to Platform.ZOMATO,
    "dominos" to Platform.DOMINOS, "domino's" to Platform.DOMINOS, "domino" to Platform.DOMINOS,
    "mcdonald" to Platform.MCDONALDS, "mcdonald's" to Platform.MCDONALDS, "mcdonalds" to Platform.MCDONALDS,
    "kfc" to Platform.KFC,
    "amazon" to Platform.AMAZON,
    "flipkart" to Platform.FLIPKART,
    "meesho" to Platform.MEESHO,
    "myntra" to Platform.MYNTRA,
    "ajio" to Platform.AJIO,
    "1mg" to Platform.TATA_1MG, "onemg" to Platform.TATA_1MG, "tata 1mg" to Platform.TATA_1MG,
    "netmeds" to Platform.NETMEDS,
    "pharmeasy" to Platform.PHARMEASY,
    "apollo" to Platform.APOLLO, "apollo pharmacy" to Platform.APOLLO,
    "practo" to Platform.PRACTO
)

// Words to strip when extracting the item name
private val noiseWords = setOf(
    "order", "me", "a", "an", "the", "some", "please", "can",
    "you", "i", "want", "to", "get", "from", "on", "my",
    "need", "would", "like", "give", "food", "buy", "purchase",
    "deliver", "send", "book", "for", "and", "with", "of"
)

// ── Parser ───────────────────────────────────────────────────

object GenieIntentParser {

    fun parse(rawText: String): GenieIntent? {
        if (rawText.isBlank()) return null

        val normalized = normalizeHinglish(rawText)
        val words = normalized.split("\\s+".toRegex())

        // 1) Detect platform
        val platform = detectPlatform(normalized)

        // 2) Detect intent type
        val intentType = detectIntentType(words, platform)
            ?: return null // Could not determine intent

        // 3) Resolve platform (use detected or default for intent type)
        val resolvedPlatform = platform ?: Platform.defaultForType(intentType)

        // 4) Extract item
        val item = extractItem(normalized, resolvedPlatform)

        return GenieIntent(
            type = intentType,
            platform = resolvedPlatform,
            item = item
        )
    }

    private fun detectPlatform(text: String): Platform? {
        // Check multi-word aliases first (longer matches)
        platformAliases.entries
            .sortedByDescending { it.key.length }
            .forEach { (alias, platform) ->
                if (text.contains(alias)) return platform
            }
        return null
    }

    private fun detectIntentType(words: List<String>, detectedPlatform: Platform?): IntentType? {
        // If platform was detected, use its category
        if (detectedPlatform != null) return detectedPlatform.category

        // Score each intent type by keyword hits
        val foodScore = words.count { it in foodKeywords }
        val productScore = words.count { it in productKeywords }
        val medicineScore = words.count { it in medicineKeywords }

        val maxScore = maxOf(foodScore, productScore, medicineScore)
        if (maxScore == 0) return null

        return when (maxScore) {
            foodScore -> IntentType.FOOD
            productScore -> IntentType.PRODUCT
            medicineScore -> IntentType.MEDICINE
            else -> null
        }
    }

    private fun extractItem(text: String, platform: Platform): String {
        var cleaned = text

        // Remove platform name
        platformAliases.forEach { (alias, p) ->
            if (p == platform) {
                cleaned = cleaned.replace(alias, "")
            }
        }

        // Remove noise words and clean up
        val itemWords = cleaned.split("\\s+".toRegex())
            .filter { it.isNotBlank() && it !in noiseWords && it.length > 1 }

        return itemWords.joinToString(" ").trim().ifBlank { "food" }
    }
}
