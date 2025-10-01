package com.example.groot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TaskAutomationManager(private val context: Context) {

    companion object {
        private const val TAG = "TaskAutomationManager"

        // Contact map with multiple variations
        private val CONTACT_MAP = mapOf(
            // Mom variations
            "mom" to "+918827613672",
            "mother" to "+918827613672",
            "mummy" to "+918827613672",
            "mumma" to "+918827613672",
            "maa" to "+918827613672",
            "ma" to "+918827613672",

            // Dad variations
            "dad" to "+919584613672",
            "father" to "+919584613672",
            "daddy" to "+919584613672",
            "papa" to "+919584613672",
            "paa" to "+919584613672",

            // Friend variations
            "rammohan" to "+917879648737",
            "ram mohan" to "+917879648737",
            "ram" to "+917879648737",
            "friend" to "+917879648737",

            // Add more contacts here
            // "name" to "+91XXXXXXXXXX",
        )

        // App package name mappings
        private val APP_PACKAGES = mapOf(
            "whatsapp" to "com.whatsapp",
            "gmail" to "com.google.android.gm",
            "chrome" to "com.android.chrome",
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "twitter" to "com.twitter.android",
            "telegram" to "org.telegram.messenger",
            "camera" to "com.android.camera2",
            "gallery" to "com.google.android.apps.photos",
            "maps" to "com.google.android.apps.maps",
            "phone" to "com.android.dialer",
            "messages" to "com.google.android.apps.messaging",
            "settings" to "com.android.settings"
        )
    }

    suspend fun executeAction(action: String, target: String, confidence: Double) = withContext(Dispatchers.Main) {
        Log.i(TAG, "Executing action: $action with target: '$target' (confidence: $confidence)")

        when (action.lowercase().trim()) {
            "call" -> makeCall(target)
            "sms" -> sendSMS(target, "")
            "search" -> searchWeb(target)
            "open_app" -> openApp(target)
            "mobile_data" -> toggleMobileData(target == "on")
            "wifi" -> toggleWifi(target == "on")
            "hotspot" -> toggleHotspot(target == "on")
            "settings" -> openSettings()
            else -> {
                Log.w(TAG, "Unknown action: $action")
                // Try to handle it as a call if target looks like a name
                if (target.isNotBlank() && isContactName(target)) {
                    Log.i(TAG, "Attempting to call '$target' as fallback")
                    makeCall(target)
                }
            }
        }
    }

    private fun makeCall(target: String) {
        try {
            val phoneNumber = resolveContact(target)
            Log.i(TAG, "Making call to: $phoneNumber (resolved from: '$target')")

            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Call initiated successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for making call", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call to '$target'", e)
        }
    }

    private fun sendSMS(target: String, message: String) {
        try {
            val phoneNumber = resolveContact(target)
            Log.i(TAG, "Sending SMS to: $phoneNumber")

            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d(TAG, "SMS sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
        }
    }

    private fun searchWeb(query: String) {
        try {
            Log.i(TAG, "Searching web for: '$query'")
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search web", e)
        }
    }

    private fun openApp(appName: String) {
        try {
            val packageName = resolveAppPackage(appName)
            Log.i(TAG, "Opening app: $packageName")

            val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (intent != null) {
                context.startActivity(intent)
                Log.d(TAG, "App opened successfully")
            } else {
                Log.w(TAG, "App not found: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app: '$appName'", e)
        }
    }

    private fun toggleMobileData(enable: Boolean) {
        try {
            Log.i(TAG, "Toggling mobile data: $enable")
            // Note: Direct mobile data control requires system permissions
            // Opening data settings as fallback
            val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle mobile data", e)
        }
    }

    private fun toggleWifi(enable: Boolean) {
        try {
            Log.i(TAG, "Toggling WiFi: $enable")
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = enable
            Log.d(TAG, "WiFi toggled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle WiFi", e)
        }
    }

    private fun toggleHotspot(enable: Boolean) {
        try {
            Log.i(TAG, "Toggling hotspot: $enable")
            // Hotspot control requires system permissions
            // Opening hotspot settings as fallback
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle hotspot", e)
        }
    }

    private fun openSettings() {
        try {
            Log.i(TAG, "Opening settings")
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings", e)
        }
    }

    /**
     * Resolve contact name to phone number
     * Handles multiple variations and formats
     */
    private fun resolveContact(name: String): String {
        // Clean and normalize the input
        val cleanName = name.trim().lowercase()
            .removePrefix("call ")
            .removePrefix("phone ")
            .removePrefix("dial ")
            .trim()

        Log.d(TAG, "Resolving contact: '$name' -> cleaned: '$cleanName'")

        // Check if it's already a phone number
        if (cleanName.matches(Regex("^[+]?[0-9]{10,13}$"))) {
            Log.d(TAG, "Input is already a phone number: $cleanName")
            return cleanName
        }

        // Try exact match first
        CONTACT_MAP[cleanName]?.let {
            Log.d(TAG, "Found exact match: '$cleanName' -> $it")
            return it
        }

        // Try partial match (contains)
        CONTACT_MAP.entries.find { (key, _) ->
            cleanName.contains(key) || key.contains(cleanName)
        }?.let {
            Log.d(TAG, "Found partial match: '${it.key}' -> ${it.value}")
            return it.value
        }

        // No match found - return original (might be a number)
        Log.w(TAG, "No contact match found for: '$cleanName', returning original")
        return name
    }

    /**
     * Check if the string looks like a contact name (not a number)
     */
    private fun isContactName(text: String): Boolean {
        val cleaned = text.trim().lowercase()
        return !cleaned.matches(Regex("^[+]?[0-9]{10,13}$"))
    }

    /**
     * Resolve app name to package name
     */
    private fun resolveAppPackage(appName: String): String {
        val cleanName = appName.trim().lowercase()
        Log.d(TAG, "Resolving app package: '$appName' -> cleaned: '$cleanName'")

        // Try exact match
        APP_PACKAGES[cleanName]?.let {
            Log.d(TAG, "Found app package: '$cleanName' -> $it")
            return it
        }

        // Try partial match
        APP_PACKAGES.entries.find { (key, _) ->
            cleanName.contains(key) || key.contains(cleanName)
        }?.let {
            Log.d(TAG, "Found partial app match: '${it.key}' -> ${it.value}")
            return it.value
        }

        // Return original as package name
        Log.w(TAG, "No app package match found, returning original: $cleanName")
        return appName
    }

    /**
     * Add a new contact dynamically
     */
    fun addContact(name: String, phoneNumber: String) {
        Log.i(TAG, "Adding new contact: '$name' -> $phoneNumber")
        // Note: This would require modifying CONTACT_MAP to be mutable
        // For now, contacts should be added directly in the CONTACT_MAP above
    }
}