package com.example.vibechat.repository

import android.net.Uri
import com.example.vibechat.VibeChatApp
import com.example.vibechat.data.Contact
import com.example.vibechat.data.Conversation
import com.example.vibechat.data.Group
import com.example.vibechat.data.local.entities.toConversationEntity
import com.example.vibechat.data.local.entities.toGroupEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.*
import com.example.vibechat.data.local.entities.toContactEntity
import com.example.vibechat.data.local.entities.toUserEntity
import kotlinx.coroutines.flow.first

class GroupRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    suspend fun createGroup(
        name: String,
        imageUri: Uri?,
        members: List<Contact>
    ): Result<String> {
        return try {
            val currentUserUid = auth.currentUser?.uid
                ?: return Result.failure(Exception("Usuário não autenticado."))

            var groupPictureUrl: String? = null
            if (imageUri != null) {
                val storageRef = storage.reference.child("group_pictures/${UUID.randomUUID()}")
                groupPictureUrl = storageRef.putFile(imageUri).await()
                    .storage.downloadUrl.await().toString()
            }

            val memberIds = (members.mapNotNull { it.uid } + currentUserUid).distinct()

            val newGroup = Group(
                name = name,
                groupPictureUrl = groupPictureUrl,
                memberIds = memberIds,
                adminIds = listOf(currentUserUid),
                lastMessage = "Grupo criado.",
                timestamp = com.google.firebase.Timestamp.now()
            )

            val groupDocument = db.collection("groups").document()
            groupDocument.set(newGroup).await()
            val groupId = groupDocument.id

            val conversationForGroup = Conversation(
                partnerId = groupId,
                partnerName = name,
                partnerProfilePictureUrl = groupPictureUrl,
                lastMessage = "Grupo criado.",
                timestamp = com.google.firebase.Timestamp.now(),
                isGroup = true
            )

            // MODIFICADO: Salva o grupo e as conversas no Room
            VibeChatApp.database.groupDao().insertGroup(newGroup.toGroupEntity().copy(id = groupId))
            val batch = db.batch()
            memberIds.forEach { memberId ->
                val memberConversationRef = db.collection("users")
                    .document(memberId).collection("conversations").document(groupId)
                batch.set(memberConversationRef, conversationForGroup)
            }
            batch.commit().await()

            // MODIFICADO: Salva a conversa para cada membro no Room
            memberIds.forEach { memberId ->
                VibeChatApp.database.conversationDao().insertConversation(conversationForGroup.toConversationEntity())
            }


            Result.success(groupId)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeMemberFromGroup(groupId: String, memberUidToRemove: String): Result<Unit> {
        return try {
            val groupRef = db.collection("groups").document(groupId)
            val memberConversationRef = db.collection("users").document(memberUidToRemove)
                .collection("conversations").document(groupId)

            // MODIFICADO: Remove do Room primeiro
            val group = VibeChatApp.database.groupDao().getGroupById(groupId).first()
            group?.let {
                val updatedMemberIds = it.memberIds.filter { id -> id != memberUidToRemove }
                val updatedAdminIds = it.adminIds.filter { id -> id != memberUidToRemove }
                val updatedGroup = it.copy(memberIds = updatedMemberIds, adminIds = updatedAdminIds)
                VibeChatApp.database.groupDao().insertGroup(updatedGroup)
            }
            VibeChatApp.database.conversationDao().deleteConversationByPartnerId(groupId)

            db.runTransaction { transaction ->
                transaction.update(groupRef, "memberIds", FieldValue.arrayRemove(memberUidToRemove))
                transaction.update(groupRef, "adminIds", FieldValue.arrayRemove(memberUidToRemove))
                transaction.delete(memberConversationRef)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addMembersToGroup(groupId: String, newMembers: List<Contact>): Result<Unit> {
        return try {
            val groupRef = db.collection("groups").document(groupId)
            val group = groupRef.get().await().toObject(Group::class.java)
                ?: return Result.failure(Exception("Grupo não encontrado."))

            val newMemberIds = newMembers.mapNotNull { it.uid }

            val conversationForGroup = Conversation(
                partnerId = groupId,
                partnerName = group.name,
                partnerProfilePictureUrl = group.groupPictureUrl,
                lastMessage = "Você foi adicionado(a).",
                timestamp = com.google.firebase.Timestamp.now(),
                isGroup = true
            )

            // MODIFICADO: Adiciona os membros no Room e no Firebase
            val currentGroup = VibeChatApp.database.groupDao().getGroupById(groupId).first()
            currentGroup?.let {
                val updatedMemberIds = it.memberIds + newMemberIds
                val updatedGroup = it.copy(memberIds = updatedMemberIds)
                VibeChatApp.database.groupDao().insertGroup(updatedGroup)
            }

            val batch = db.batch()
            batch.update(groupRef, "memberIds", FieldValue.arrayUnion(*newMemberIds.toTypedArray()))
            newMemberIds.forEach { memberId ->
                val memberConversationRef = db.collection("users")
                    .document(memberId).collection("conversations").document(groupId)
                batch.set(memberConversationRef, conversationForGroup)
            }

            batch.commit().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameGroup(groupId: String, newName: String): Result<Unit> {
        return try {
            if (newName.isBlank()) {
                return Result.failure(Exception("O nome não pode estar vazio."))
            }

            val groupRef = db.collection("groups").document(groupId)
            val group = groupRef.get().await().toObject(Group::class.java)
                ?: return Result.failure(Exception("Grupo não encontrado."))

            val memberIds = group.memberIds
            val batch = db.batch()

            batch.update(groupRef, "name", newName)

            // MODIFICADO: Atualiza o nome do grupo no Room
            val currentGroup = VibeChatApp.database.groupDao().getGroupById(groupId).first()
            currentGroup?.let {
                val updatedGroup = it.copy(name = newName)
                VibeChatApp.database.groupDao().insertGroup(updatedGroup)
            }

            memberIds.forEach { memberId ->
                val conversationRef = db.collection("users").document(memberId)
                    .collection("conversations").document(groupId)
                batch.update(conversationRef, "partnerName", newName)
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(groupId: String): Result<Unit> {
        return try {
            val currentUserUid = auth.currentUser?.uid
                ?: return Result.failure(Exception("Usuário não autenticado."))

            val groupRef = db.collection("groups").document(groupId)
            val userConversationRef = db.collection("users").document(currentUserUid)
                .collection("conversations").document(groupId)

            // MODIFICADO: Apaga a conversa do Room antes da transação
            VibeChatApp.database.conversationDao().deleteConversationByPartnerId(groupId)

            val group = VibeChatApp.database.groupDao().getGroupById(groupId).first()
            if (group != null && group.memberIds.size <= 1) {
                VibeChatApp.database.groupDao().deleteGroupById(groupId)
            } else {
                val updatedMemberIds = group?.memberIds?.filter { id -> id != currentUserUid } ?: emptyList()
                val updatedAdminIds = group?.adminIds?.filter { id -> id != currentUserUid } ?: emptyList()
                val updatedGroup = group?.copy(memberIds = updatedMemberIds, adminIds = updatedAdminIds)
                updatedGroup?.let { VibeChatApp.database.groupDao().insertGroup(it) }
            }


            db.runTransaction { transaction ->
                val groupSnapshot = transaction.get(groupRef)
                val groupData = groupSnapshot.toObject(Group::class.java)
                    ?: throw Exception("Grupo não encontrado.")

                // Se o utilizador for o último membro, apaga o grupo inteiro.
                if (groupData.memberIds.size == 1 && groupData.memberIds.contains(currentUserUid)) {
                    transaction.delete(groupRef)
                } else {
                    // Caso contrário, apenas remove o utilizador das listas.
                    transaction.update(
                        groupRef,
                        "memberIds", FieldValue.arrayRemove(currentUserUid),
                        "adminIds", FieldValue.arrayRemove(currentUserUid)
                    )
                }

                // Apaga sempre a conversa da lista do utilizador.
                transaction.delete(userConversationRef)

                null // Sucesso da transação
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}