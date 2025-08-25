package com.example.vibechat.repository

import com.example.vibechat.data.Contact
import com.example.vibechat.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ContactRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun addContact(phoneNumber: String, customName: String): Result<Unit> {
        return try {
            val currentUserUid = auth.currentUser?.uid ?: return Result.failure(Exception("Utilizador não autenticado."))

            // 1. Encontrar o utilizador pelo número de telefone
            val userQuery = db.collection("users")
                .whereEqualTo("phoneNumber", phoneNumber)
                .limit(1)
                .get()
                .await()

            if (userQuery.isEmpty) {
                return Result.failure(Exception("Utilizador não encontrado com este número de telefone."))
            }

            val contactUser = userQuery.documents.first().toObject(User::class.java)
                ?: return Result.failure(Exception("Erro ao converter os dados do utilizador."))

            val contactUid = contactUser.uid!!

            // 2. Criar o objeto de contacto
            val newContact = Contact(
                uid = contactUid,
                customName = customName,
                phoneNumber = phoneNumber,
                profilePictureUrl = contactUser.profilePictureUrl
            )

            // 3. Guardar o contacto na subcoleção do utilizador atual
            db.collection("users").document(currentUserUid)
                .collection("contacts").document(contactUid)
                .set(newContact)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}