package com.example.vibechat.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val partnerId: String,
    val partnerName: String,
    val partnerProfilePictureUrl: String?,
    val lastMessage: String?,
    val timestamp: Long?,
    val partnerPhoneNumber: String,
    val unreadCount: Int,
    val isGroup: Boolean
)