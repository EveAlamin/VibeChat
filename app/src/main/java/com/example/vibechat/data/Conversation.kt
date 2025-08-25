package com.example.vibechat.data

import com.google.firebase.Timestamp

data class Conversation(
    val partnerId: String = "",
    val partnerName: String = "",
    val partnerProfilePictureUrl: String? = null,
    val lastMessage: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val partnerPhoneNumber: String = ""
)