package com.example.groot

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class TaskAutomationManager(private val context: Context) {

    companion object {
        private const val TAG = "TaskAutomationManager"
        private const val PREFS_NAME = "groot_contacts"
        private const val KEY_CONTACTS = "contact_map"

        // Mutable contact map
        private val CONTACT_MAP = mutableMapOf(
            "mom" to "+918827613672",
            "mother" to "+918827613672",
            "maa" to "+918827613672",
            "dad" to "+919584613672",
            "father" to "+919584613672",
            "papa" to "+919584613672",
            "rammohan" to "+917879648737",
            "ram" to "+917879648737"
        )

        // App packages
        private val APP_PACKAGES = mapOf(
            "whatsapp" to "com.whatsapp",
            "gmail" to "com.google.android.gm",
            "chrome" to "com.android.chrome",
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "camera" to "com.android.camera2",
            "phone" to "com.android.dialer"
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        loadContactsFromPrefs()
    }

    suspend fun executeAction(action: String, target: String, confidence: Double) = withContext(Dispatchers.Main) {
        Log.i(TAG, "Executing: $action -> '$target'")

        when (action.lowercase().trim()) {
            "call" -> makeCall(target)
            "sms" -> sendSMS(target, "")
            "search" -> searchWeb(target)
            "open_app" -> openApp(target)
            "mobile_data" -> openMobileDataSettings()
            "wifi" -> openWifiSettings()
            "settings" -> openSettings()
            "add_contact" -> handleAddContact(target)
            else -> {
                if (target.isNotBlank()) {
                    makeCall(target)
                }
            }
        }
    }

    private fun makeCall(target: String) {
        try {
            val phoneNumber = resolveContact(target)
            Log.i(TAG, "Calling: $phoneNumber")

            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Call failed", e)
        }
    }

    private fun sendSMS(target: String, message: String) {
        try {
            val phoneNumber = resolveContact(target)
            SmsManager.getDefault().sendTextMessage(phoneNumber, null, message, null, null)
            Log.d(TAG, "SMS sent")
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed", e)
        }
    }

    private fun searchWeb(query: String) {
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
        }
    }

    private fun openApp(appName: String) {
        try {
            val packageName = resolveAppPackage(appName)
            Log.i(TAG, "Opening: $packageName")

            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Log.d(TAG, "App opened: $packageName")
            } else {
                Log.w(TAG, "App not found: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "App open failed", e)
        }
    }

    private fun openMobileDataSettings() {
        try {
            val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
            } else {
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            }.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened mobile data settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open data settings", e)
        }
    }

    private fun openWifiSettings() {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened WiFi settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open WiFi settings", e)
        }
    }

    private fun openSettings() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Settings failed", e)
        }
    }

    private fun handleAddContact(target: String) {
        try {
            var parts = target.split(":")
            if (parts.size == 1) {
                val words = target.trim().split(Regex("\\s+"))
                if (words.size >= 2) {
                    val name = words.dropLast(1).joinToString(" ")
                    val number = words.last()
                    parts = listOf(name, number)
                }
            }

            if (parts.size == 2) {
                val name = parts[0].trim()
                var number = parts[1].trim().replace(Regex("[^0-9+]"), "")

                if (!number.startsWith("+") && number.length == 10) {
                    number = "+91$number"
                }

                if (number.matches(Regex("^\\+?[0-9]{10,13}$"))) {
                    addContact(name, number)
                    Log.i(TAG, "Contact added: $name -> $number")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Add contact failed", e)
        }
    }

    private fun resolveContact(name: String): String {
        val cleanName = name.trim().lowercase()
            .removePrefix("call ")
            .removePrefix("phone ")
            .trim()

        if (cleanName.matches(Regex("^[+]?[0-9]{10,13}$"))) {
            return cleanName
        }

        CONTACT_MAP[cleanName]?.let { return it }

        CONTACT_MAP.entries.find { (key, _) ->
            cleanName.contains(key) || key.contains(cleanName)
        }?.let { return it.value }

        return name
    }

    private fun resolveAppPackage(appName: String): String {
        val cleanName = appName.trim().lowercase()
        APP_PACKAGES[cleanName]?.let { return it }

        APP_PACKAGES.entries.find { (key, _) ->
            cleanName.contains(key) || key.contains(cleanName)
        }?.let { return it.value }

        return appName
    }

    fun addContact(name: String, phoneNumber: String) {
        val cleanName = name.trim().lowercase()
        CONTACT_MAP[cleanName] = phoneNumber
        saveContactsToPrefs()
        Log.i(TAG, "Contact saved: $cleanName")
    }

    fun getAllContacts(): Map<String, String> {
        return CONTACT_MAP.toMap()
    }

    private fun saveContactsToPrefs() {
        try {
            val json = JSONObject()
            CONTACT_MAP.forEach { (name, number) ->
                json.put(name, number)
            }
            prefs.edit().putString(KEY_CONTACTS, json.toString()).apply()
            Log.d(TAG, "Saved ${CONTACT_MAP.size} contacts")
        } catch (e: Exception) {
            Log.e(TAG, "Save failed", e)
        }
    }

    private fun loadContactsFromPrefs() {
        try {
            val jsonString = prefs.getString(KEY_CONTACTS, null)
            if (jsonString != null) {
                val json = JSONObject(jsonString)
                val keys = json.keys()
                var count = 0

                while (keys.hasNext()) {
                    val name = keys.next()
                    val number = json.getString(name)
                    CONTACT_MAP[name] = number
                    count++
                }

                Log.d(TAG, "Loaded $count contacts")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Load failed", e)
        }
    }
}