package com.example.vibechat.data.local.entities

import com.example.vibechat.data.Message
import com.google.firebase.Timestamp

fun Message.toMessageEntity(chatId: String): MessageEntity {
    return MessageEntity(
        id = this.id,
        chatId = chatId,
        message = this.message,
        senderId = this.senderId,
        timestamp = this.timestamp?.seconds,
        imageUrl = this.imageUrl,
        videoUrl = this.videoUrl
    )
}

fun MessageEntity.toDataMessage(): Message {
    return Message(
        id = this.id,
        message = this.message,
        senderId = this.senderId,
        timestamp = this.timestamp?.let { Timestamp(it, 0) },
        imageUrl = this.imageUrl,
        videoUrl = this.videoUrl
    )
}