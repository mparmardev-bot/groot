package com.example.groot

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

class MemoryManager {

    companion object {
        private const val TAG = "MemoryManager"
        private const val MAX_CONVERSATIONS = 100
    }

    private val conversations = mutableListOf<ConversationEntry>()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    data class ConversationEntry(
        val message: String,
        val timestamp: String,
        val type: ConversationType
    )

    enum class ConversationType {
        USER_INPUT,
        ASSISTANT_RESPONSE,
        SYSTEM_MESSAGE
    }

    /**
     * Add a conversation message to memory
     * Automatically detects the type based on message prefix
     */
    fun addConversation(message: String) {
        val timestamp = dateFormatter.format(Date())
        val type = when {
            message.startsWith("User:") -> ConversationType.USER_INPUT
            message.startsWith("Assistant:") -> ConversationType.ASSISTANT_RESPONSE
            message.startsWith("System:") -> ConversationType.SYSTEM_MESSAGE
            else -> ConversationType.SYSTEM_MESSAGE
        }

        val entry = ConversationEntry(
            message = message,
            timestamp = timestamp,
            type = type
        )

        conversations.add(entry)

        // Keep only the most recent conversations
        if (conversations.size > MAX_CONVERSATIONS) {
            conversations.removeAt(0)
        }

        Log.d(TAG, "Added conversation: $message")
    }

    /**
     * Get recent context as a list of message strings (for LLM context)
     * Returns the last count*2 messages (to include both user and assistant messages)
     */
    fun getRecentContext(count: Int = 5): List<String> {
        return conversations
            .takeLast(count * 2)
            .map { it.message }
    }

    /**
     * Get recent conversations with full details (for UI display)
     */
    fun getRecentConversations(count: Int = 10): List<ConversationEntry> {
        return conversations.takeLast(count)
    }

    /**
     * Get all conversations (for full history display)
     */
    fun getAllConversations(): List<ConversationEntry> {
        return conversations.toList()
    }

    /**
     * Get the total number of conversations
     */
    fun getConversationCount(): Int {
        return conversations.size
    }

    /**
     * Get memory size (total number of messages)
     */
    fun getMemorySize(): Int = conversations.size

    /**
     * Get the last user message (without prefix)
     */
    fun getLastUserMessage(): String? {
        return conversations
            .findLast { it.type == ConversationType.USER_INPUT }
            ?.message
            ?.removePrefix("User: ")
    }

    /**
     * Get the last assistant response (without prefix)
     */
    fun getLastAssistantResponse(): String? {
        return conversations
            .findLast { it.type == ConversationType.ASSISTANT_RESPONSE }
            ?.message
            ?.removePrefix("Assistant: ")
    }

    /**
     * Search conversations by query string
     */
    fun searchConversations(query: String): List<ConversationEntry> {
        return conversations.filter {
            it.message.contains(query, ignoreCase = true)
        }
    }

    /**
     * Export all conversations as formatted text
     */
    fun exportConversations(): String {
        return conversations.joinToString("\n") {
            "[${it.timestamp}] ${it.type}: ${it.message}"
        }
    }

    /**
     * Clear all conversations from memory
     */
    fun clearMemory() {
        conversations.clear()
        Log.d(TAG, "Memory cleared")
    }

    /**
     * Get conversation statistics
     */
    fun getStatistics(): ConversationStats {
        val userMessages = conversations.count { it.type == ConversationType.USER_INPUT }
        val assistantMessages = conversations.count { it.type == ConversationType.ASSISTANT_RESPONSE }
        val systemMessages = conversations.count { it.type == ConversationType.SYSTEM_MESSAGE }

        return ConversationStats(
            total = conversations.size,
            userMessages = userMessages,
            assistantMessages = assistantMessages,
            systemMessages = systemMessages
        )
    }

    data class ConversationStats(
        val total: Int,
        val userMessages: Int,
        val assistantMessages: Int,
        val systemMessages: Int
    )
}