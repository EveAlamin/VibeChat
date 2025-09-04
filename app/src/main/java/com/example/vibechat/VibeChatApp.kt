package com.example.vibechat

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.vibechat.utils.PresenceManager
import com.google.firebase.database.FirebaseDatabase

import androidx.room.Room
import com.example.vibechat.data.local.database.VibeChatDatabase

class VibeChatApp : Application() {
    companion object {
        lateinit var database: VibeChatDatabase
    }
    override fun onCreate() {
        super.onCreate()
        // Ativa a persistência offline para uma experiência mais rápida
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Ignorar erro se já estiver ativado
        }
        // Inicialização do Room
        database = Room.databaseBuilder(
            applicationContext,
            VibeChatDatabase::class.java,
            "vibechat_database"
        ).build()

        // Adiciona o nosso observador de ciclo de vida
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())
    }

    // Este observador reage quando a app inteira entra ou sai de primeiro plano
    private class AppLifecycleObserver : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            // App entrou em primeiro plano
            PresenceManager.goOnline()
        }

        override fun onStop(owner: LifecycleOwner) {
            // App entrou em segundo plano
            PresenceManager.goOffline()
        }
    }
}