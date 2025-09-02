package com.example.vibechat.data

data class UserPresence(
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L
)