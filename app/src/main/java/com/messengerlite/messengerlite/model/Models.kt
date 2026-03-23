package com.messengerlite.model

data class Message(
    val id: Int,
    val fromUser: String,
    val toUser: String?,
    val groupId: Int?,
    val message: String,
    val type: String = "text",
    val timestamp: Long,
    val replyToId: Int? = null
)

data class Conversation(
    val name: String,
    val isGroup: Boolean,
    val groupId: Int? = null,
    val lastMessage: String = "",
    val lastTimestamp: Long = 0,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false
)

data class GroupInfo(
    val id: Int,
    val name: String,
    val type: String,
    val joinCode: String? = null,
    val memberCount: Int = 0
)
