package com.example.vibechat.data.local.entities

import com.example.vibechat.data.Conversation
import com.google.firebase.Timestamp

fun Conversation.toConversationEntity(): ConversationEntity {
    return ConversationEntity(
        partnerId = this.partnerId,
        partnerName = this.partnerName,
        partnerProfilePictureUrl = this.partnerProfilePictureUrl,
        lastMessage = this.lastMessage,
        timestamp = this.timestamp.seconds,
        partnerPhoneNumber = this.partnerPhoneNumber,
        unreadCount = this.unreadCount,
        isGroup = this.isGroup
    )
}

fun ConversationEntity.toDataConversation(): Conversation {
    return Conversation(
        partnerId = this.partnerId,
        partnerName = this.partnerName,
        partnerProfilePictureUrl = this.partnerProfilePictureUrl,
        lastMessage = this.lastMessage ?: "",
        timestamp = this.timestamp?.let { Timestamp(it, 0) } ?: Timestamp.now(),
        partnerPhoneNumber = this.partnerPhoneNumber,
        unreadCount = this.unreadCount,
        isGroup = this.isGroup
    )
}