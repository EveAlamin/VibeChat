package com.example.vibechat.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val message: String?,
    val senderId: String?,
    val timestamp: Long?, // Usaremos Long para o Room
    val imageUrl: String?,
    val videoUrl: String?
)