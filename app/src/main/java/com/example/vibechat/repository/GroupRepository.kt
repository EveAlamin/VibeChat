package com.example.vibechat.repository

import android.net.Uri
import com.example.vibechat.data.Conversation
import com.example.vibechat.data.Group
import com.example.vibechat.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.*

class GroupRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    suspend fun createGroup(
        name: String,
        imageUri: Uri?,
        members: List<User>
    ): Result<String> {
        return try {
            val currentUserUid = auth.currentUser?.uid
                ?: return Result.failure(Exception("Utilizador não autenticado."))

            // 1. Fazer upload da imagem do grupo (se houver)
            var groupPictureUrl: String? = null
            if (imageUri != null) {
                val storageRef = storage.reference.child("group_pictures/${UUID.randomUUID()}")
                groupPictureUrl = storageRef.putFile(imageUri).await()
                    .storage.downloadUrl.await().toString()
            }

            // 2. Preparar os dados do grupo
            val memberIds = (members.mapNotNull { it.uid } + currentUserUid).distinct()
            val newGroup = Group(
                name = name,
                groupPictureUrl = groupPictureUrl,
                memberIds = memberIds,
                adminIds = listOf(currentUserUid),
                lastMessage = "Grupo criado.",
                timestamp = com.google.firebase.Timestamp.now()
            )

            // 3. Criar o documento do grupo na coleção 'groups'
            val groupDocument = db.collection("groups").document()
            groupDocument.set(newGroup).await()
            val groupId = groupDocument.id

            // 4. Adicionar a conversa de grupo para cada membro
            val conversationForGroup = Conversation(
                partnerId = groupId, // Usamos o ID do grupo como partnerId
                partnerName = name,
                partnerProfilePictureUrl = groupPictureUrl,
                lastMessage = "Grupo criado.",
                timestamp = com.google.firebase.Timestamp.now(),
                isGroup = true
            )

            val batch = db.batch()
            memberIds.forEach { memberId ->
                val memberConversationRef = db.collection("users")
                    .document(memberId).collection("conversations").document(groupId)
                batch.set(memberConversationRef, conversationForGroup)
            }
            batch.commit().await()

            Result.success(groupId)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}