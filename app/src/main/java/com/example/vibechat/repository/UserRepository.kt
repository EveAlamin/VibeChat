package com.example.vibechat.repository

import com.example.vibechat.VibeChatApp
import com.example.vibechat.data.Contact
import com.example.vibechat.data.Conversation
import com.example.vibechat.data.User
import com.example.vibechat.data.local.entities.toContactEntity
import com.example.vibechat.data.local.entities.toConversationEntity
import com.example.vibechat.data.local.entities.toUserEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import com.example.vibechat.data.local.entities.UserEntity
import kotlinx.coroutines.flow.first

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val usersCollection = db.collection("users")

    suspend fun addContact(phoneNumber: String, customName: String): Result<Unit> {
        return try {
            val currentUserUid = auth.currentUser?.uid ?: return Result.failure(Exception("Usuário não autenticado."))

            val userQuery = usersCollection.whereEqualTo("phoneNumber", phoneNumber).limit(1).get().await()

            if (userQuery.isEmpty) {
                return Result.failure(Exception("Usuário não encontrado com este número de telefone."))
            }

            val contactUser = userQuery.documents.first().toObject(User::class.java) ?: return Result.failure(Exception("Erro ao converter os dados do usuário."))
            val contactUid = contactUser.uid!!

            val newContact = Contact(
                uid = contactUid,
                customName = customName,
                phoneNumber = phoneNumber,
                profilePictureUrl = contactUser.profilePictureUrl
            )

            // MODIFICADO: Primeiro, salva o novo contato e o usuário no Room para sincronização offline.
            VibeChatApp.database.contactDao().insertContact(newContact.toContactEntity())
            VibeChatApp.database.userDao().insertUser(contactUser.toUserEntity())

            // Depois, salva no Firebase (lógica original).
            usersCollection.document(currentUserUid).collection("contacts").document(contactUid).set(newContact).await()

            val conversationRef = usersCollection.document(currentUserUid).collection("conversations").document(contactUid)
            val conversationData = Conversation(
                partnerId = contactUid,
                partnerName = customName,
                partnerProfilePictureUrl = contactUser.profilePictureUrl,
                partnerPhoneNumber = contactUser.phoneNumber ?: "",
                isGroup = false,
                timestamp = com.google.firebase.Timestamp.now()
            )
            conversationRef.set(conversationData, SetOptions.merge()).await()

            // MODIFICADO: Salva a conversa no Room também
            VibeChatApp.database.conversationDao().insertConversation(conversationData.toConversationEntity())

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteContact(contactUid: String): Result<Unit> {
        return try {
            val currentUserUid = auth.currentUser?.uid ?: return Result.failure(Exception("Utilizador não autenticado."))

            // MODIFICADO: Primeiro, apaga do Room.
            VibeChatApp.database.contactDao().deleteContactByUid(contactUid)

            // Depois, apaga do Firebase (lógica original).
            usersCollection.document(currentUserUid).collection("contacts").document(contactUid).delete().await()
            usersCollection.document(currentUserUid).collection("conversations").document(contactUid).delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ... (as outras funções permanecem as mesmas)
    suspend fun isContactBlocked(contactUid: String): Boolean {
        return try {
            val currentUserUid = auth.currentUser?.uid ?: return false
            val doc = usersCollection.document(currentUserUid)
                .collection("blockedUsers").document(contactUid).get().await()
            doc.exists()
        } catch (e: Exception) {
            false
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

    suspend fun updateUserPictureInAllConversations(userId: String, newPhotoUrl: String): Result<Unit> {
        return try {
            val batch = db.batch()
            val conversationsToUpdateSnapshot = db.collectionGroup("conversations")
                .whereEqualTo("partnerId", userId)
                .get()
                .await()
            for (document in conversationsToUpdateSnapshot.documents) {
                batch.update(document.reference, "partnerProfilePictureUrl", newPhotoUrl)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserNameInAllConversations(userId: String, newName: String): Result<Unit> {
        return try {
            val batch = db.batch()
            val conversationsToUpdateSnapshot = db.collectionGroup("conversations")
                .whereEqualTo("partnerId", userId)
                .get()
                .await()
            for (document in conversationsToUpdateSnapshot.documents) {
                batch.update(document.reference, "partnerName", newName)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // NOVAS FUNÇÕES DE ATUALIZAÇÃO PARA O PERFIL DO UTILIZADOR

    suspend fun updateUserProfilePicture(userId: String, newPhotoUrl: String): Result<Unit> {
        return try {
            // 1. Atualiza no Room
            val userEntity = VibeChatApp.database.userDao().getUserById(userId).first()
            userEntity?.let {
                val updatedUser = it.copy(profilePictureUrl = newPhotoUrl)
                VibeChatApp.database.userDao().insertUser(updatedUser)
            }

            // 2. Atualiza no Firebase
            usersCollection.document(userId).update("profilePictureUrl", newPhotoUrl).await()

            // 3. Atualiza a foto em todas as conversas
            updateUserPictureInAllConversations(userId, newPhotoUrl)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserName(userId: String, newName: String): Result<Unit> {
        return try {
            // 1. Atualiza no Room
            val userEntity = VibeChatApp.database.userDao().getUserById(userId).first()
            userEntity?.let {
                val updatedUser = it.copy(name = newName)
                VibeChatApp.database.userDao().insertUser(updatedUser)
            }

            // 2. Atualiza no Firebase
            usersCollection.document(userId).update("name", newName).await()

            // 3. Atualiza o nome em todas as conversas
            updateUserNameInAllConversations(userId, newName)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserStatus(userId: String, newStatus: String): Result<Unit> {
        return try {
            // 1. Atualiza no Room
            val userEntity = VibeChatApp.database.userDao().getUserById(userId).first()
            userEntity?.let {
                val updatedUser = it.copy(status = newStatus)
                VibeChatApp.database.userDao().insertUser(updatedUser)
            }

            // 2. Atualiza no Firebase
            usersCollection.document(userId).update("status", newStatus).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}