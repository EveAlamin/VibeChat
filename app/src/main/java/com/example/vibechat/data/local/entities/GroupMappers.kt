package com.example.vibechat.data.local.entities

import com.example.vibechat.data.Group
import com.google.firebase.Timestamp

fun Group.toGroupEntity(): GroupEntity {
    return GroupEntity(
        id = this.id,
        name = this.name,
        memberIds = this.memberIds,
        adminIds = this.adminIds,
        groupPictureUrl = this.groupPictureUrl,
        lastMessage = this.lastMessage,
        timestamp = this.timestamp?.seconds
    )
}

fun GroupEntity.toDataGroup(): Group {
    return Group(
        id = this.id,
        name = this.name,
        memberIds = this.memberIds,
        adminIds = this.adminIds,
        groupPictureUrl = this.groupPictureUrl,
        lastMessage = this.lastMessage,
        timestamp = this.timestamp?.let { Timestamp(it, 0) }
    )
}