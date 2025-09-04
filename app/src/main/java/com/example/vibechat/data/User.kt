package com.example.vibechat.data

data class User(
    val name: String? = null,
    val uid: String? = null,
    val phoneNumber: String? = null,
    val email: String? = null,
    val profilePictureUrl: String? = null,
    val status: String? = "Disponível",
    // NOVO: Adiciona o campo para o token de notificação FCM
    val fcmToken: String? = null
)