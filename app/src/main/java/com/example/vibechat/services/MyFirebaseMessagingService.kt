package com.example.vibechat.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.vibechat.MainActivity
import com.example.vibechat.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser != null) {
            saveTokenToFirestore(token, firebaseUser.uid)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM_DEBUG", "Mensagem recebida: $remoteMessage")
        Log.d("FCM_DEBUG", "Data: ${remoteMessage.data}")
        Log.d("FCM_DEBUG", "Notification: ${remoteMessage.notification}")

        // 🚨 Proteção contra duplicação
        if (remoteMessage.notification != null) {
            Log.w("FCM_DEBUG", "Payload contém 'notification'. Ignorando para evitar duplicação.")
            return
        }

        val data = remoteMessage.data
        val title = data["title"]
        val body = data["body"]
        val partnerUid = data["partnerUid"]
        val partnerName = data["partnerName"]
        val partnerPhone = data["partnerPhone"]

        showNotification(title, body, partnerUid, partnerName, partnerPhone)
    }

    fun showNotification(
        title: String?,
        body: String?,
        partnerUid: String?,
        partnerName: String?,
        partnerPhone: String?
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "vibechat_channel"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("partnerUid", partnerUid)
            putExtra("partnerName", partnerName)
            putExtra("partnerPhone", partnerPhone)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            partnerUid?.hashCode() ?: 0, // 🔑 ID único por conversa
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VibeChat Messages",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title ?: "VibeChat")
            .setContentText(body ?: "")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true) // ✅ evita alertar de novo se atualizar

        // 🔑 Notification ID estável por conversa
        val notificationId = partnerUid?.hashCode() ?: 0
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

     fun saveTokenToFirestore(token: String, userId: String) {
        if (userId.isNotEmpty()) {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(userId)
                .update("fcmToken", token)
        }
    }
}
