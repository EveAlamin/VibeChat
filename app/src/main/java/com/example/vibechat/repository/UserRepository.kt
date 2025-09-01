package com.example.vibechat.repository

import com.example.vibechat.data.Contact
import com.example.vibechat.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

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

            // Salva o contato com o nome personalizado na sua subcoleção de contatos
            val newContact = Contact(
                uid = contactUid,
                customName = customName,
                phoneNumber = phoneNumber,
                profilePictureUrl = contactUser.profilePictureUrl
            )
            usersCollection.document(currentUserUid).collection("contacts").document(contactUid).set(newContact).await()

            // --- CORREÇÃO APLICADA AQUI ---
            // Cria ou atualiza a conversa para que ela apareça na HomeScreen com o nome certo
            val conversationRef = usersCollection.document(currentUserUid).collection("conversations").document(contactUid)
            val conversationData = com.example.vibechat.data.Conversation(
                partnerId = contactUid,
                partnerName = customName, // Usa o NOME PERSONALIZADO
                partnerProfilePictureUrl = contactUser.profilePictureUrl,
                partnerPhoneNumber = contactUser.phoneNumber ?: "",
                isGroup = false,
                timestamp = com.google.firebase.Timestamp.now()
            )
            // Usamos .set com SetOptions.merge() para criar a conversa se não existir, ou atualizar se já existir
            conversationRef.set(conversationData, com.google.firebase.firestore.SetOptions.merge()).await()

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


    suspend fun updateUserPictureInAllConversations(userId: String, newPhotoUrl: String): Result<Unit> {
        return try {
            val batch = db.batch()

            // Consulta todas as subcoleções "conversations" em busca de documentos
            // onde o usuário atual é o "partnerId".
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
            // Este erro geralmente acontece se o índice do Firestore não foi criado.
            Result.failure(e)
        }
    }

    /**
     * Atualiza o nome de um usuário em todas as conversas
     * onde ele aparece como parceiro.
     * Isso garante que a mudança de nome se reflita para todos os contatos.
     */
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
}
