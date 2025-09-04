package com.example.vibechat.data.local.entities

import com.example.vibechat.data.User

fun User.toUserEntity(): UserEntity {
    return UserEntity(
        uid = this.uid ?: "",
        name = this.name,
        phoneNumber = this.phoneNumber,
        email = this.email,
        profilePictureUrl = this.profilePictureUrl,
        status = this.status
    )
}

fun UserEntity.toDataUser(): User {
    return User(
        uid = this.uid,
        name = this.name,
        phoneNumber = this.phoneNumber,
        email = this.email,
        profilePictureUrl = this.profilePictureUrl,
        status = this.status
    )
}