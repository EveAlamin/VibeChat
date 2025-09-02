package com.example.vibechat.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

object PresenceManager {

    private val currentUserUid: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    private var database: DatabaseReference? = null

    fun initialize() {
        // Esta função apenas prepara a referência e a nossa "rede de segurança".
        if (currentUserUid != null && database == null) {
            database = FirebaseDatabase.getInstance().getReference("/status/$currentUserUid")

            // O onDisconnect é a instrução que o servidor Firebase executará
            // se a ligação da app cair abruptamente (crash, perda de rede, etc.).
            database?.onDisconnect()?.setValue(mapOf(
                "isOnline" to false,
                "lastSeen" to ServerValue.TIMESTAMP
            ))
        }
    }

    // Chamada explicitamente quando a app entra em primeiro plano.
    fun goOnline() {
        currentUserUid?.let {
            database?.setValue(mapOf(
                "isOnline" to true,
                "lastSeen" to ServerValue.TIMESTAMP
            ))
        }
    }

    // Chamada explicitamente quando a app entra em segundo plano.
    fun goOffline() {
        currentUserUid?.let {
            database?.setValue(mapOf(
                "isOnline" to false,
                "lastSeen" to ServerValue.TIMESTAMP
            ))
        }
    }
}