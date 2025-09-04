package com.example.vibechat.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String,
    val name: String?,
    val phoneNumber: String?,
    val email: String?,
    val profilePictureUrl: String?,
    val status: String?
)