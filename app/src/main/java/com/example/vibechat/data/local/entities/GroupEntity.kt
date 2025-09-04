package com.example.vibechat.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val memberIds: List<String>,
    val adminIds: List<String>,
    val groupPictureUrl: String?,
    val lastMessage: String,
    val timestamp: Long?
)