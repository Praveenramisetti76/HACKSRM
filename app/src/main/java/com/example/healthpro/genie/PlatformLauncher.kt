package com.example.healthpro.genie

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Handles app availability checks, deep link launching, and Play Store fallback.
 */
object PlatformLauncher {

    // ── Deep Link Patterns ───────────────────────────────────

    private fun getSearchDeepLink(platform: Platform, query: String): String {
        val encoded = Uri.encode(query)
        return when (platform) {
            // Food
            Platform.SWIGGY -> "https://www.swiggy.com/search?query=$encoded"
            Platform.ZOMATO -> "https://www.zomato.com/search?q=$encoded"
            Platform.DOMINOS -> "https://www.dominos.co.in/"
            Platform.MCDONALDS -> "https://www.mcdonalds.com/"
            Platform.KFC -> "https://online.kfc.co.in/"
            // Products
            Platform.AMAZON -> "https://www.amazon.in/s?k=$encoded"
            Platform.FLIPKART -> "https://www.flipkart.com/search?q=$encoded"
            Platform.MEESHO -> "https://meesho.com/search?q=$encoded"
            Platform.MYNTRA -> "https://www.myntra.com/$encoded"
            Platform.AJIO -> "https://www.ajio.com/search/?text=$encoded"
            // Medicine (mock — won't actually be used)
            Platform.TATA_1MG -> "https://www.1mg.com/search/all?name=$encoded"
            Platform.NETMEDS -> "https://www.netmeds.com/catalogsearch/result?q=$encoded"
            Platform.PHARMEASY -> "https://pharmeasy.in/search/all?name=$encoded"
            Platform.APOLLO -> "https://www.apollopharmacy.in/search-medicines/$encoded"
            Platform.PRACTO -> "https://www.practo.com/"
        }
    }

    // ── App Availability ─────────────────────────────────────

    fun isAppInstalled(context: Context, platform: Platform): Boolean {
        return try {
            context.packageManager.getLaunchIntentForPackage(platform.packageName) != null
        } catch (e: Exception) {
            false
        }
    }

    // ── Launch App ───────────────────────────────────────────

    /**
     * Launches the target app. Returns true if the app was launched.
     */
    fun launchApp(context: Context, platform: Platform): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(platform.packageName)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } else {
            false
        }
    }

    /**
     * Opens the app with a deep link to the search page for the given query.
     * Falls back to launching the app normally if deep link fails.
     */
    fun launchDeepLink(context: Context, platform: Platform, query: String): Boolean {
        return try {
            val deepLink = getSearchDeepLink(platform, query)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Try to open in the native app first
                setPackage(platform.packageName)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // Deep link with package failed — try without package (opens browser or app chooser)
            try {
                val deepLink = getSearchDeepLink(platform, query)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (e2: Exception) {
                // Last resort: just launch the app
                launchApp(context, platform)
            }
        }
    }

    // ── Play Store Fallback ──────────────────────────────────

    /**
     * Opens the Play Store listing for the given platform.
     */
    fun openPlayStore(context: Context, platform: Platform) {
        try {
            // Try Play Store app first
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=${platform.playStoreId}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to browser
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=${platform.playStoreId}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
