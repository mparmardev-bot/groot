package com.example.groot

class MemoryManager {
    private val conversations = mutableListOf<String>()
    private val maxMemorySize = 100 // Maximum number of messages to keep

    fun addConversation(message: String) {
        conversations.add(message)
        if (conversations.size > maxMemorySize) {
            conversations.removeFirst()
        }
    }

    fun getRecentContext(count: Int = 5): List<String> {
        return if (conversations.size <= count) {
            conversations.toList()
        } else {
            conversations.takeLast(count)
        }
    }

    fun clearMemory() {
        conversations.clear()
    }

    fun getMemorySize(): Int = conversations.size
}