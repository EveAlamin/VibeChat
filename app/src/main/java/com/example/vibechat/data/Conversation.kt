package com.example.vibechat.data

import com.google.firebase.Timestamp
import com.google.firebase.database.PropertyName

data class Conversation(
    val partnerId: String = "",
    val partnerName: String = "",
    val partnerProfilePictureUrl: String? = null,
    val lastMessage: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val partnerPhoneNumber: String = "",
    val unreadCount: Int = 0,
    @get:PropertyName("isGroup") @set:PropertyName("isGroup")
    var isGroup: Boolean = false,
    val pinnedMessageId: String? = null
)