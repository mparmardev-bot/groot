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
    }

    suspend fun executeAction(action: String, target: String, confidence: Double) = withContext(Dispatchers.Main) {
        Log.i(TAG, "Executing action: $action with target: $target")

        when (action.lowercase()) {
            "call" -> makeCall(target)
            "sms" -> sendSMS(target, "")
            "search" -> searchWeb(target)
            "open_app" -> openApp(target)
            "mobile_data" -> toggleMobileData(target == "on")
            "wifi" -> toggleWifi(target == "on")
            "hotspot" -> toggleHotspot(target == "on")
            "settings" -> openSettings()
            else -> Log.w(TAG, "Unknown action: $action")
        }
    }

    private fun makeCall(target: String) {
        try {
            val phoneNumber = resolveContact(target)
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call", e)
        }
    }

    private fun sendSMS(target: String, message: String) {
        try {
            val phoneNumber = resolveContact(target)
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
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
            Log.e(TAG, "Failed to search web", e)
        }
    }

    private fun openApp(appName: String) {
        try {
            val packageName = resolveAppPackage(appName)
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            intent?.let { context.startActivity(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app", e)
        }
    }

    private fun toggleMobileData(enable: Boolean) {
        try {
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
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled = enable
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle WiFi", e)
        }
    }

    private fun toggleHotspot(enable: Boolean) {
        try {
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
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings", e)
        }
    }

    private fun resolveContact(name: String): String {
        // Implement contact resolution logic
        // For now, return the name as-is or implement contact lookup
        return when (name.lowercase()) {
            "mom", "mother" -> "+918827613672" // Replace with actual numbers
            "dad", "father" -> "+919584613672"
            "Rammohan", "friend" -> "+917879648737"
            else -> name
        }
    }

    private fun resolveAppPackage(appName: String): String {
        return when (appName.lowercase()) {
            "whatsapp" -> "com.whatsapp"
            "gmail" -> "com.google.android.gm"
            "chrome" -> "com.android.chrome"
            "youtube" -> "com.google.android.youtube"
            else -> appName
        }
    }
}
