package com.example.groot

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Offline Intelligence System
 * Handles basic queries and tasks without server connection
 */
class OfflineIntelligence {

    companion object {
        private const val TAG = "OfflineIntelligence"
    }

    data class OfflineResponse(
        val reply: String,
        val action: String,
        val target: String,
        val handled: Boolean // true if we can handle offline
    )

    /**
     * Try to handle the command offline
     * Returns OfflineResponse with handled=true if successful
     */
    fun handleOffline(command: String): OfflineResponse {
        Log.d(TAG, "original cmd : $command")
        val lowerCommand = command.lowercase().trim()
        val isHindi = command.matches(Regex(".*[\\u0900-\\u097F].*"))

        Log.d(TAG, "Attempting to handle offline: $lowerCommand")

        // Identity questions
        when {
            lowerCommand.contains("what is your name") ||
                    lowerCommand.contains("who are you") ||
                    lowerCommand.contains("your name") ||
                    lowerCommand.contains("नाम") ||
                    lowerCommand.contains("तुम्हारा नाम क्या है ") ||
                    lowerCommand.contains("तेरा नाम क्या है") ||
                    lowerCommand.contains("आपका नाम क्या है") -> {
                return OfflineResponse(
                    reply = if (isHindi) "मैं ग्रूट हूँ, आपका निजी AI सहायक। कॉल, मैसेज और दैनिक कार्यों में आपकी मदद करने के लिए यहाँ हूँ!"
                    else "I am Groot, your personal AI assistant. I'm here to help with calls, messages, and daily tasks!",
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            lowerCommand.contains("how are you") ||
                    lowerCommand.contains("kaise ho") ||
                    lowerCommand.contains("गुरूट तुम कैसे हो") ||
                    lowerCommand.contains("तुम कैसे हो") ||
                    lowerCommand.contains("कैसा है") ||
                    lowerCommand.contains("तू कैसा है") ||
                    lowerCommand.contains("how r u") -> {
                return OfflineResponse(
                    reply = if (isHindi) "मैं बहुत अच्छा हूँ और आपकी मदद के लिए तैयार हूँ! आप आज कैसे हैं?"
                    else "I'm doing great and ready to assist! How can I help you today?",
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            lowerCommand.contains("hello") ||
                    lowerCommand.contains("hi groot") ||
                    lowerCommand.contains("हेलो") ||
                    lowerCommand.contains("हेलो हेलो") ||
                    lowerCommand.contains("नमस्ते") ||
                    lowerCommand.contains("namaste") -> {
                return OfflineResponse(
                    reply = if (isHindi) "नमस्ते! मैं ग्रूट, आपका A.I सहायक हूँ। मैं आपकी कैसे मदद कर सकता हूँ?"
                    else "Hello! I'm Groot, your AI assistant. How may I help you?",
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            lowerCommand.contains("good morning") ||
                    lowerCommand.contains("गुड मॉर्निंग") ||
                    lowerCommand.contains("सुप्रभात ☀\uFE0F") -> {
                return OfflineResponse(
                    reply = if (isHindi) "सुप्रभात! आपके दिन की शानदार शुरुआत हो!"
                    else "Good morning! Hope you have a wonderful day ahead!",
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            lowerCommand.contains("good night") ||
                    lowerCommand.contains("शुभ रात्रि") -> {
                return OfflineResponse(
                    reply = if (isHindi) "शुभ रात्रि! अच्छे सपने देखें!"
                    else "Good night! Sleep well and sweet dreams!",
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            lowerCommand.contains("thank you") ||
                    lowerCommand.contains("thanks") ||
                    lowerCommand.contains("थैंक्यू सो मच \uD83D\uDE4F") ||
                    lowerCommand.contains("धन्यवाद \uD83C\uDF38") ||
                    lowerCommand.contains("dhanyavad") -> {
                return OfflineResponse(
                    reply = if (isHindi) "आपका स्वागत है! मैं हमेशा मदद के लिए यहाँ हूँ!"
                    else "You're welcome! I'm always here to help!",
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            // Time queries
            lowerCommand.contains("time") ||
                    lowerCommand.contains("समय क्या हुआ है") ||
                    lowerCommand.contains("समय क्या है") ||
                    lowerCommand.contains("अभी समय क्या हुआ है") ||
                    lowerCommand.contains("समय") ||
                    lowerCommand.contains("samay") -> {
                val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                return OfflineResponse(
                    reply = if (isHindi) "वर्तमान समय $currentTime है"
                    else "The current time is $currentTime",
                    action = "time",
                    target = "",
                    handled = true
                )
            }

            // Date queries
            lowerCommand.contains("date") ||
                    lowerCommand.contains("आज कौन-सी तारीख है") ||
                    lowerCommand.contains("तारीख") ||
                    lowerCommand.contains("आज तिथि क्या है") ||
                    lowerCommand.contains("तारीख / दिनांक") ||
                    lowerCommand.contains("आज की तारीख") ||
                    lowerCommand.contains("टुडे डेट") ||
                    lowerCommand.contains("डेट टुडे") ||
                    lowerCommand.contains("आज तारीख क्या है") ||
                    lowerCommand.contains("आज क्या तारीख है") ||
                    lowerCommand.contains("today") ||
                    lowerCommand.contains("tarikh") -> {
                val currentDate = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(Date())
                return OfflineResponse(
                    reply = if (isHindi) "आज $currentDate है"
                    else "Today is $currentDate",
                    action = "date",
                    target = "",
                    handled = true
                )
            }

            // Day queries
            lowerCommand.contains("what day") ||
                    lowerCommand.contains("आज कौन सा दिन है?") ||
                    lowerCommand.contains("आज दिन कौन सा है?") ||
                    lowerCommand.contains("आज का दिन कौन सा है?") ||
                    lowerCommand.contains("दिन क्या है आज?") ||
                    lowerCommand.contains("आज सप्ताह का कौन सा दिन है") ||
                    lowerCommand.contains("आज रविवार/सोमवार/मंगलवार… है क्या?") ||
                    lowerCommand.contains("आज शुक्रवार है क्या?") ||
                    lowerCommand.contains("आज शनिवार/रविवार… है क्या") -> {
                val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
                return OfflineResponse(
                    reply = if (isHindi) "आज $day है"
                    else "Today is $day",
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            // Call commands
            lowerCommand.contains("call") ||
                    lowerCommand.contains("phone") ||
                    lowerCommand.contains("कॉल मॉम") ||
                    lowerCommand.contains("कॉल ") ||
                    lowerCommand.contains("कॉल करो") ||
                    lowerCommand.contains("dial") -> {
                val contact = extractContact(lowerCommand)
                if (contact.isNotEmpty()) {
                    return OfflineResponse(
                        reply = if (isHindi) "$contact को कॉल कर रहा हूँ"
                        else "Calling $contact",
                        action = "call",
                        target = contact,
                        handled = true
                    )
                } else {
                    return OfflineResponse(
                        reply = if (isHindi) "किसे कॉल करना चाहेंगे?"
                        else "Who would you like me to call?",
                        action = "none",
                        target = "",
                        handled = true
                    )
                }
            }

            // SMS commands
            lowerCommand.contains("message") ||
                    lowerCommand.contains("send") ||
                    lowerCommand.contains("text") ||
                    lowerCommand.contains("sms") -> {
                val (contact, message) = extractMessage(lowerCommand)
                if (contact.isNotEmpty() && message.isNotEmpty()) {
                    return OfflineResponse(
                        reply = if (isHindi) "$contact को मैसेज भेज रहा हूँ: $message"
                        else "Sending message to $contact: $message",
                        action = "sms",
                        target = "$contact:$message",
                        handled = true
                    )
                } else {
                    return OfflineResponse(
                        reply = if (isHindi) "कृपया बताएँ कि किसे और क्या मैसेज भेजना है।"
                        else "Please specify who to message and what to say.",
                        action = "none",
                        target = "",
                        handled = true
                    )
                }
            }

            // Open app commands
            lowerCommand.contains("open") &&
                    !lowerCommand.contains("can you open") -> {
                val app = extractAppName(lowerCommand)
                if (app.isNotEmpty()) {
                    return OfflineResponse(
                        reply = if (isHindi) "$app खोल रहा हूँ"
                        else "Opening $app",
                        action = "open_app",
                        target = app,
                        handled = true
                    )
                }
            }

            // WiFi controls
            lowerCommand.contains("wifi on") ||
                    lowerCommand.contains("turn on wifi") ||
                    lowerCommand.contains("enable wifi") -> {
                return OfflineResponse(
                    reply = if (isHindi) "WiFi चालू कर रहा हूँ"
                    else "Turning on WiFi",
                    action = "wifi",
                    target = "on",
                    handled = true
                )
            }

            lowerCommand.contains("wifi off") ||
                    lowerCommand.contains("turn off wifi") ||
                    lowerCommand.contains("disable wifi") -> {
                return OfflineResponse(
                    reply = if (isHindi) "WiFi बंद कर रहा हूँ"
                    else "Turning off WiFi",
                    action = "wifi",
                    target = "off",
                    handled = true
                )
            }

            // Mobile data controls
            lowerCommand.contains("mobile data on") ||
                    lowerCommand.contains("turn on mobile data") ||
                    lowerCommand.contains("enable mobile data") -> {
                return OfflineResponse(
                    reply = if (isHindi) "मोबाइल डेटा चालू कर रहा हूँ"
                    else "Turning on mobile data",
                    action = "mobile_data",
                    target = "on",
                    handled = true
                )
            }

            lowerCommand.contains("mobile data off") ||
                    lowerCommand.contains("turn off mobile data") ||
                    lowerCommand.contains("disable mobile data") -> {
                return OfflineResponse(
                    reply = if (isHindi) "मोबाइल डेटा बंद कर रहा हूँ"
                    else "Turning off mobile data",
                    action = "mobile_data",
                    target = "off",
                    handled = true
                )
            }

            // Hotspot controls
            lowerCommand.contains("hotspot on") ||
                    lowerCommand.contains("turn on hotspot") ||
                    lowerCommand.contains("enable hotspot") -> {
                return OfflineResponse(
                    reply = if (isHindi) "हॉटस्पॉट चालू कर रहा हूँ"
                    else "Turning on hotspot",
                    action = "hotspot",
                    target = "on",
                    handled = true
                )
            }

            lowerCommand.contains("hotspot off") ||
                    lowerCommand.contains("turn off hotspot") ||
                    lowerCommand.contains("disable hotspot") -> {
                return OfflineResponse(
                    reply = if (isHindi) "हॉटस्पॉट बंद कर रहा हूँ"
                    else "Turning off hotspot",
                    action = "hotspot",
                    target = "off",
                    handled = true
                )
            }

            // Bluetooth controls
            lowerCommand.contains("bluetooth on") ||
                    lowerCommand.contains("turn on bluetooth") ||
                    lowerCommand.contains("enable bluetooth") -> {
                return OfflineResponse(
                    reply = if (isHindi) "ब्लूटूथ चालू कर रहा हूँ"
                    else "Turning on Bluetooth",
                    action = "bluetooth",
                    target = "on",
                    handled = true
                )
            }

            lowerCommand.contains("bluetooth off") ||
                    lowerCommand.contains("turn off bluetooth") ||
                    lowerCommand.contains("disable bluetooth") -> {
                return OfflineResponse(
                    reply = if (isHindi) "ब्लूटूथ बंद कर रहा हूँ"
                    else "Turning off Bluetooth",
                    action = "bluetooth",
                    target = "off",
                    handled = true
                )
            }

            // Settings
            lowerCommand.contains("open settings") ||
                    lowerCommand.contains("settings") -> {
                return OfflineResponse(
                    reply = if (isHindi) "सेटिंग्स खोल रहा हूँ"
                    else "Opening settings",
                    action = "settings",
                    target = "",
                    handled = true
                )
            }

            // Add contact
            lowerCommand.contains("add contact") ||
                    lowerCommand.contains("save contact") ||
                    (lowerCommand.contains("save") && lowerCommand.contains("number")) -> {
                val words = command.split(Regex("\\s+"))
                val addIndex = words.indexOfFirst {
                    it.lowercase().contains("contact") || it.lowercase().contains("save")
                }

                if (addIndex != -1 && words.size > addIndex + 2) {
                    val remaining = words.subList(addIndex + 1, words.size).joinToString(" ")
                    return OfflineResponse(
                        reply = if (isHindi) "संपर्क सहेज रहा हूँ"
                        else "Saving contact",
                        action = "add_contact",
                        target = remaining,
                        handled = true
                    )
                }
            }

            // Help/Capabilities
            lowerCommand.contains("what can you do") ||
                    lowerCommand.contains("help") ||
                    lowerCommand.contains("commands") -> {
                return OfflineResponse(
                    reply = if (isHindi) "मैं कॉल करने, मैसेज भेजने, ऐप्स खोलने, समय और तारीख बताने, WiFi/डेटा/हॉटस्पॉट नियंत्रित करने, गणनाएँ करने, और बहुत कुछ कर सकता हूँ! बस पूछें!"
                    else "I can help with: making calls, sending messages, opening apps, checking time/date, controlling WiFi/data/hotspot, calculations, and more! Just ask!",
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            // Jokes
            lowerCommand.contains("joke") ||
                    lowerCommand.contains("funny") -> {
                val jokes = listOf(
                    if (isHindi) "कंप्यूटर ने ब्रेक क्यों माँगा? क्योंकि वह क्रैश कर रहा था!"
                    else "Why did the computer take a break? Because it was crashing!",
                    if (isHindi) "स्मार्टफोन स्कूल क्यों गया? अपनी रिसेप्शन सुधारने के लिए!"
                    else "Why did the smartphone go to school? To improve its reception!",
                    if (isHindi) "मैं आलसी नहीं हूँ, मैं बस एनर्जी-सेविंग मोड में हूँ!"
                    else "I'm not lazy, I'm just in energy-saving mode!"
                )
                return OfflineResponse(
                    reply = jokes.random(),
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            // Creator/Developer
            lowerCommand.contains("who made you") ||
                    lowerCommand.contains("who created you") ||
                    lowerCommand.contains("your creator") -> {
                return OfflineResponse(
                    reply = if (isHindi) "मुझे आपके लिए एक निजी AI सहायक के रूप में बनाया गया था!"
                    else "I was created to be your personal AI assistant!",
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            // Calculations
            lowerCommand.contains("calculate") ||
                    lowerCommand.matches(Regex(".*[0-9+\\-*/].*")) -> {
                val result = calculate(lowerCommand)
                if (result != null) {
                    return OfflineResponse(
                        reply = if (isHindi) "परिणाम: $result"
                        else "Result: $result",
                        action = "none",
                        target = "",
                        handled = true
                    )
                }
            }

            // Basic Q&A
            lowerCommand.contains("capital") ||
                    lowerCommand.contains("city") ||
                    lowerCommand.contains("country") -> {
                val qaMap = mapOf(
                    "capital of france" to "Paris",
                    "capital of india" to "New Delhi",
                    "capital of usa" to "Washington, D.C.",
                    "फ्रांस की राजधानी" to "पेरिस",
                    "भारत की राजधानी" to "नई दिल्ली",
                    "अमेरिका की राजधानी" to "वाशिंगटन, डी.सी."
                )
                qaMap.entries.find { lowerCommand.contains(it.key) }?.let {
                    return OfflineResponse(
                        reply = if (isHindi) "${it.key}: ${it.value}"
                        else "The ${it.key} is ${it.value}",
                        action = "none",
                        target = "",
                        handled = true
                    )
                }
            }

            // Capabilities specific
            lowerCommand.contains("can you") -> {
                return when {
                    lowerCommand.contains("call") -> OfflineResponse(
                        reply = if (isHindi) "हाँ, मैं आपके लिए कॉल कर सकता हूँ! बस 'कॉल' के बाद संपर्क का नाम बताएँ।"
                        else "Yes, I can make calls for you! Just say 'Call' followed by the contact name.",
                        action = "none",
                        target = "",
                        handled = true
                    )
                    lowerCommand.contains("message") || lowerCommand.contains("text") -> OfflineResponse(
                        reply = if (isHindi) "हाँ, मैं मैसेज भेज सकता हूँ! बस 'मैसेज' के बाद संपर्क और संदेश बताएँ।"
                        else "Yes, I can send messages! Just say 'Message' followed by contact and message.",
                        action = "none",
                        target = "",
                        handled = true
                    )
                    else -> OfflineResponse(
                        reply = if (isHindi) "मैं बहुत कुछ कर सकता हूँ! कॉल, ऐप्स खोलने, या समय पूछने की कोशिश करें।"
                        else "I can do many things! Try asking me to make calls, open apps, or check the time.",
                        action = "none",
                        target = "",
                        handled = true
                    )
                }
            }
        }

        // If we couldn't handle it offline
        return OfflineResponse(
            reply = if (isHindi) "मुझे यह ऑफलाइन समझने में दिक्कत हो रही है। क्या आप इसे दोहरा सकते हैं?"
            else "I'm having trouble understanding that offline. Can you try again?",
            action = "none",
            target = "",
            handled = false
        )
    }

    /**
     * Extract contact name from call command
     */
    private fun extractContact(command: String): String {
        val words = command.split(Regex("\\s+"))
        val callIndex = words.indexOfFirst {
            it.contains("call") || it.contains("phone") || it.contains("dial")
        }

        if (callIndex != -1 && callIndex + 1 < words.size) {
            return words.subList(callIndex + 1, words.size)
                .joinToString(" ")
                .replace(Regex(".*?(call|phone|dial)"), "")
                .trim()
        }

        return ""
    }

    /**
     * Extract contact and message from SMS command
     */
    private fun extractMessage(command: String): Pair<String, String> {
        val parts = command.split(":", limit = 2)
        if (parts.size == 2) {
            val contact = parts[0]
                .replace(Regex(".*?(message|send|text|sms)"), "")
                .trim()
            val message = parts[1].trim()
            if (contact.isNotEmpty() && message.isNotEmpty()) {
                return Pair(contact, message)
            }
        }
        return Pair("", "")
    }

    /**
     * Extract app name from open command
     */
    private fun extractAppName(command: String): String {
        val words = command.split(Regex("\\s+"))
        val openIndex = words.indexOfFirst { it.contains("open") }

        if (openIndex != -1 && openIndex + 1 < words.size) {
            return words.subList(openIndex + 1, words.size).joinToString(" ")
        }

        return ""
    }

    /**
     * Perform basic calculations using a simple parser
     */
    private fun calculate(command: String): String? {
        try {
            val expression = command.replace(Regex(".*?(calculate|what is|equals)"), "")
                .replace(Regex("[^0-9+\\-*/(). ]"), "")
                .trim()
            if (expression.isNotEmpty()) {
                val result = evaluateExpression(expression)
                return if (result % 1 == 0.0) result.toInt().toString() else result.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Calculation failed", e)
        }
        return null
    }

    private fun evaluateExpression(expression: String): Double {
        var idx = 0
        return parseExpression(expression)
    }

    private fun parseExpression(expression: String): Double {
        var idx = 0
        var value = parseTerm(expression, idx)
        idx = value.second
        while (idx < expression.length) {
            when (expression[idx]) {
                '+' -> { idx++; value = Pair(value.first + parseTerm(expression, idx).first, parseTerm(expression, idx).second); idx = value.second }
                '-' -> { idx++; value = Pair(value.first - parseTerm(expression, idx).first, parseTerm(expression, idx).second); idx = value.second }
                else -> break
            }
        }
        return value.first
    }

    private fun parseTerm(expression: String, startIdx: Int): Pair<Double, Int> {
        var idx = startIdx
        var value = parseFactor(expression, idx)
        idx = value.second
        while (idx < expression.length) {
            when (expression[idx]) {
                '*' -> { idx++; value = Pair(value.first * parseFactor(expression, idx).first, parseFactor(expression, idx).second); idx = value.second }
                '/' -> { idx++; value = Pair(value.first / parseFactor(expression, idx).first, parseFactor(expression, idx).second); idx = value.second }
                else -> break
            }
        }
        return value
    }

    private fun parseFactor(expression: String, startIdx: Int): Pair<Double, Int> {
        var idx = startIdx
        var value: Double

        if (expression[idx] == '(') {
            idx++
            value = parseExpression(expression.substring(idx))
            idx += value.toString().length + 1 // Adjust for nested expression length
            if (idx < expression.length && expression[idx] == ')') idx++
        } else {
            val start = idx
            while (idx < expression.length && (expression[idx].isDigit() || expression[idx] == '.')) idx++
            value = expression.substring(start, idx).toDoubleOrNull() ?: 0.0
        }
        return Pair(value, idx)
    }

    /**
     * Get a greeting based on time of day
     */
    fun getTimeBasedGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..11 -> "Good morning! I'm Groot, your AI assistant. How may I help you?"
            in 12..16 -> "Good afternoon! I'm Groot, ready to assist you!"
            in 17..20 -> "Good evening! I'm Groot, how can I help you today?"
            else -> "Hello! I'm Groot, your AI assistant. What can I do for you?"
        }
    }

    /**
     * Check if the query can be handled offline
     */
    fun canHandleOffline(command: String): Boolean {
        val lowerCommand = command.lowercase().trim()

        val offlineKeywords = listOf(
            "name", "who are you", "how are you", "hello", "hi",
            "time", "date", "day", "call", "open", "wifi",
            "mobile data", "hotspot", "bluetooth", "settings",
            "thank", "joke", "help", "can you", "good morning",
            "good night", "namaste", "what can you do", "calculate",
            "capital", "message", "send", "text", "sms"
        )

        return offlineKeywords.any { lowerCommand.contains(it) }
    }
}