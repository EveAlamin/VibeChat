package com.example.vibechat.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Message(
    @DocumentId val id: String = "",
    val message: String? = null,
    val senderId: String? = null,
    val timestamp: Timestamp? = null,
    val status: String = "SENT",
    val wasDeleted: Boolean = false,
    val readBy: List<String> = emptyList(),
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    // CAMPOS ADICIONADOS PARA COMPATIBILIDADE COM A VERSÃO ANTERIOR
    val receiverId: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null
)