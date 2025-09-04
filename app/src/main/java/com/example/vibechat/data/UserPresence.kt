package com.example.vibechat.data

data class UserPresence(
    // MODIFICADO: Mudar 'val' para 'var' para que o Firestore consiga escrever
    var isOnline: Boolean = false,
    val lastSeen: Long = 0L
)