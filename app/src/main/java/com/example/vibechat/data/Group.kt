package com.example.vibechat.data

import com.google.firebase.firestore.DocumentId

data class Group(
    @DocumentId val id: String = "",
    val name: String = "",
    val memberIds: List<String> = emptyList(),
    val adminIds: List<String> = emptyList(),
    val groupPictureUrl: String? = null,
    val lastMessage: String = "",
    val timestamp: com.google.firebase.Timestamp? = null
)