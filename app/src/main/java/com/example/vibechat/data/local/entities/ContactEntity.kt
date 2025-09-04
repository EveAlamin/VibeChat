package com.example.vibechat.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val uid: String,
    val customName: String,
    val phoneNumber: String,
    val profilePictureUrl: String?
)