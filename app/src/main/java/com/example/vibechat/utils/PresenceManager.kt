package com.example.vibechat.utils // Verifique se o pacote está correto

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

object PresenceManager {

    private val currentUserUid: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    private var database: DatabaseReference? = null

    fun initialize() {
        // Inicialize a referência apenas se houver um usuário logado
        currentUserUid?.let { uid ->
            database = FirebaseDatabase.getInstance().getReference("/status/$uid")

            val onlineStatus = mapOf(
                "isOnline" to true,
                "lastSeen" to ServerValue.TIMESTAMP
            )

            val offlineStatus = mapOf(
                "isOnline" to false,
                "lastSeen" to ServerValue.TIMESTAMP
            )

            val connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected")
            connectedRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    if (connected) {
                        database?.setValue(onlineStatus)
                        database?.onDisconnect()?.setValue(offlineStatus)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w("PresenceManager", "Listener was cancelled at .info/connected", error.toException())
                }
            })
        }
    }

    fun goOffline() {
        currentUserUid?.let { uid ->
            val offlineStatus = mapOf(
                "isOnline" to false,
                "lastSeen" to ServerValue.TIMESTAMP
            )
            FirebaseDatabase.getInstance().getReference("/status/$uid").setValue(offlineStatus)
        }
    }
}