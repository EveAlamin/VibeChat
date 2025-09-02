package com.example.vibechat.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Message(
    @DocumentId val id: String = "",
    val message: String? = null,
    val senderId: String? = null,
    val timestamp: Timestamp? = null,
    val status: String = "SENT",
    val mediaUrl: String? = null // ✨ Novo campo para armazenar a URL da mídia
)