package com.example.healthpro.medicine.data.pharmacy

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Checks if pharmacy app is installed and redirects to Play Store if not.
 * Uses correct package names and proper intent handling.
 */
object PharmacyAppChecker {

    /**
     * Check if pharmacy app is installed using PackageManager.NameNotFoundException.
     */
    fun isPharmacyInstalled(context: Context, provider: PharmacyProvider): Boolean =
        isAppInstalled(context, provider.getPackageName())

    fun isAppInstalled(context: Context, packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    /**
     * Open pharmacy app or search URL. Prefers opening searchUrl so user lands on search results.
     * If app installed, try to open URL (may open in app via App Links); else open in browser.
     */
    fun openPharmacyOrPlayStore(context: Context, provider: PharmacyProvider, searchUrl: String) {
        try {
            if (isAppInstalled(context, provider.getPackageName())) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                openUrl(context, searchUrl)
            }
        } catch (e: ActivityNotFoundException) {
            openUrl(context, searchUrl)
        } catch (e: Exception) {
            openUrl(context, searchUrl)
        }
    }

    /**
     * Open Play Store for pharmacy app using proper intent format.
     * Uses HTTPS URL (finds app on Play Store reliably). Falls back to market:// if needed.
     */
    fun openPlayStore(context: Context, provider: PharmacyProvider) {
        val packageName = provider.getPackageName()
        val playStoreUrl = "https://play.google.com/store/apps/details?id=$packageName"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(playStoreUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            openUrl(context, playStoreUrl)
        } catch (e: Exception) {
            openUrl(context, playStoreUrl)
        }
    }

    private fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
