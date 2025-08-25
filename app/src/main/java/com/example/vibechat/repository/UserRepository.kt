package com.example.vibechat.repository

import com.example.vibechat.data.Contact
import com.example.vibechat.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val usersCollection = db.collection("users")

    suspend fun addContact(phoneNumber: String, customName: String): Result<Unit> {
        return try {
            val currentUserUid = auth.currentUser?.uid ?: return Result.failure(Exception("Utilizador não autenticado."))

            val userQuery = usersCollection.whereEqualTo("phoneNumber", phoneNumber).limit(1).get().await()

            if (userQuery.isEmpty) {
                return Result.failure(Exception("Utilizador não encontrado com este número de telefone."))
            }

            val contactUser = userQuery.documents.first().toObject(User::class.java) ?: return Result.failure(Exception("Erro ao converter os dados do utilizador."))

            val newContact = Contact(
                uid = contactUser.uid!!,
                customName = customName,
                phoneNumber = phoneNumber,
                profilePictureUrl = contactUser.profilePictureUrl
            )

            usersCollection.document(currentUserUid).collection("contacts").document(contactUser.uid).set(newContact).await()

            val conversationRef = usersCollection.document(currentUserUid).collection("conversations").document(contactUser.uid)
            val conversationDoc = conversationRef.get().await()
            if (conversationDoc.exists()) {
                conversationRef.update("partnerName", customName).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteContact(contactUid: String): Result<Unit> {
        return try {
            val currentUserUid = auth.currentUser?.uid ?: return Result.failure(Exception("Utilizador não autenticado."))

            usersCollection.document(currentUserUid).collection("contacts").document(contactUid).delete().await()
            usersCollection.document(currentUserUid).collection("conversations").document(contactUid).delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun blockContact(contactUid: String): Result<Unit> {
        return try {
            val currentUserUid = auth.currentUser?.uid ?: return Result.failure(Exception("Utilizador não autenticado."))
            usersCollection.document(currentUserUid).collection("blockedUsers").document(contactUid).set(mapOf("blocked" to true)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unblockContact(contactUid: String): Result<Unit> {
        return try {
            val currentUserUid = auth.currentUser?.uid ?: return Result.failure(Exception("Utilizador não autenticado."))
            usersCollection.document(currentUserUid).collection("blockedUsers").document(contactUid).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
