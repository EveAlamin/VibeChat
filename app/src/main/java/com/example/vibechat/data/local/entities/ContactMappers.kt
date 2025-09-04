package com.example.vibechat.data.local.entities

import com.example.vibechat.data.Contact

fun Contact.toContactEntity(): ContactEntity {
    return ContactEntity(
        uid = this.uid,
        customName = this.customName,
        phoneNumber = this.phoneNumber,
        profilePictureUrl = this.profilePictureUrl
    )
}

fun ContactEntity.toDataContact(): Contact {
    return Contact(
        uid = this.uid,
        customName = this.customName,
        phoneNumber = this.phoneNumber,
        profilePictureUrl = this.profilePictureUrl
    )
}