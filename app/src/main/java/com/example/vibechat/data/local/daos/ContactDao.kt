package com.example.vibechat.data.local.daos

import androidx.room.*
import com.example.vibechat.data.local.entities.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllContacts(contacts: List<ContactEntity>)

    @Query("SELECT * FROM contacts ORDER BY customName ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("DELETE FROM contacts WHERE uid = :uid")
    suspend fun deleteContactByUid(uid: String)
}