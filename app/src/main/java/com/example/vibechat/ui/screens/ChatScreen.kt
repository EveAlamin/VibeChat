package com.example.vibechat.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.vibechat.data.Conversation
import com.example.vibechat.data.Message
import com.example.vibechat.data.User
import com.example.vibechat.repository.UserRepository
// import com.example.vibechat.ui.theme.*
import com.example.vibechat.utils.formatTimestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

val BlueCheck = Color(0xFF34B7F1)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun ChatScreen(navController: NavController, name: String, receiverUid: String, receiverPhone: String) {
    var messageText by remember { mutableStateOf("") }
    var messageList by remember { mutableStateOf<List<Message>>(emptyList()) }
    var receiverUser by remember { mutableStateOf<User?>(null) }
    var isContact by remember { mutableStateOf(true) }
    var isBlocked by remember { mutableStateOf(false) }
    val senderUid = FirebaseAuth.getInstance().currentUser?.uid!!
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val userRepository = remember { UserRepository() }
    val coroutineScope = rememberCoroutineScope()

    val senderRoom = senderUid + receiverUid
    val receiverRoom = receiverUid + senderUid

    LaunchedEffect(Unit) {
        val conversationRef = db.collection("users").document(senderUid)
            .collection("conversations").document(receiverUid)

        conversationRef.get().addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {
                conversationRef.update("unreadCount", 0)
            }
        }

        db.collection("users").document(senderUid).collection("contacts").document(receiverUid).get()
            .addOnSuccessListener { document ->
                isContact = document.exists()
            }

        db.collection("users").document(senderUid).collection("blockedUsers").document(receiverUid)
            .addSnapshotListener { snapshot, _ ->
                isBlocked = snapshot != null && snapshot.exists()
            }

        db.collection("users").document(receiverUid).get().addOnSuccessListener {
            receiverUser = it.toObject(User::class.java)
        }

        db.collection("chats").document(senderRoom).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) { return@addSnapshotListener }
                if (snapshot != null) {
                    val newMessages = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Message::class.java)
                    }
                    messageList = newMessages

                    val batch = db.batch()
                    newMessages.forEach { message ->
                        if (message.senderId != senderUid && message.status != "READ") {
                            val messageRefSender = db.collection("chats").document(senderRoom).collection("messages").document(message.id)
                            val messageRefReceiver = db.collection("chats").document(receiverRoom).collection("messages").document(message.id)
                            batch.update(messageRefSender, "status", "READ")
                            batch.update(messageRefReceiver, "status", "READ")
                        }
                    }
                    batch.commit()
                }
            }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (receiverUser?.profilePictureUrl != null) {
                                GlideImage(
                                    model = receiverUser?.profilePictureUrl,
                                    contentDescription = "Foto de Perfil de $name",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Sem Foto de Perfil",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(name, color = Color.White)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Chamada de Vídeo", tint = Color.White)
                    }
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Call, contentDescription = "Chamada de Voz", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFECE5DD))
            ) {
                if (!isContact && !isBlocked) {
                    UnknownContactBanner(
                        onBlock = {
                            coroutineScope.launch {
                                val result = userRepository.blockContact(receiverUid)
                                result.onSuccess {
                                    Toast.makeText(context, "$name foi bloqueado.", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                }
                                result.onFailure {
                                    Toast.makeText(context, "Erro ao bloquear: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onAddContact = {
                            navController.navigate("addContact?phone=$receiverPhone")
                        }
                    )
                }

                LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    items(messageList) { message ->
                        if (message.senderId == senderUid) {
                            SentMessageBubble(message = message)
                        } else {
                            ReceivedMessageBubble(message = message)
                        }
                    }
                }

                if (isBlocked) {
                    BlockBanner(
                        onUnblock = {
                            coroutineScope.launch {
                                val result = userRepository.unblockContact(receiverUid)
                                result.onSuccess {
                                    Toast.makeText(context, "$name foi desbloqueado.", Toast.LENGTH_SHORT).show()
                                }
                                result.onFailure {
                                    Toast.makeText(context, "Erro ao desbloquear: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            if(isBlocked) Text("Você não pode enviar mensagens para este contacto.")
                            else Text("Digite uma mensagem...")
                        },
                        enabled = !isBlocked,
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.textFieldColors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (!isBlocked && messageText.isNotBlank()) {
                                val messageId = db.collection("chats").document().id
                                val messageObject = Message(id = messageId, message = messageText, senderId = senderUid, timestamp = Timestamp.now(), status = "SENT")

                                db.collection("chats").document(senderRoom).collection("messages").document(messageId).set(messageObject)
                                db.collection("chats").document(receiverRoom).collection("messages").document(messageId).set(messageObject)

                                updateConversation(db, senderUid, receiverUid, name, messageText, context)

                                messageText = ""
                            }
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Enviar", tint = Color.White)
                    }
                }
            }
        }
    )
}

@Composable
fun BlockBanner(onUnblock: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFF9C4))
            .clickable(onClick = onUnblock)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Você bloqueou este contacto. Toque para desbloquear.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = Color.Black
        )
    }
}

@Composable
fun UnknownContactBanner(onBlock: () -> Unit, onAddContact: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Este número não está na sua lista de contactos.",
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(onClick = onBlock, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Bloquear")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = onAddContact) {
                    Text("Adicionar")
                }
            }
        }
    }
}

private fun updateConversation(db: FirebaseFirestore, senderUid: String, receiverUid: String, receiverCustomName: String, lastMessage: String, context: Context) {
    val senderDocRef = db.collection("users").document(senderUid)
    val receiverDocRef = db.collection("users").document(receiverUid)

    receiverDocRef.get().addOnSuccessListener { receiverDoc ->
        val receiverUser = receiverDoc.toObject(User::class.java)
        if (receiverUser == null) {
            Toast.makeText(context, "Erro: Não foi possível encontrar o perfil do destinatário.", Toast.LENGTH_SHORT).show()
            return@addOnSuccessListener
        }

        senderDocRef.get().addOnSuccessListener { senderDoc ->
            val senderUser = senderDoc.toObject(User::class.java)
            if (senderUser == null) {
                Toast.makeText(context, "Erro: Não foi possível encontrar o seu perfil.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            // Lógica para a conversa do REMETENTE (quem envia)
            val senderConversation = Conversation(
                partnerId = receiverUid,
                partnerName = receiverCustomName,
                partnerProfilePictureUrl = receiverUser.profilePictureUrl,
                lastMessage = lastMessage,
                timestamp = Timestamp.now(),
                partnerPhoneNumber = receiverUser.phoneNumber ?: "",
                unreadCount = 0
            )
            senderDocRef.collection("conversations").document(receiverUid).set(senderConversation)

            // ***** INÍCIO DA CORREÇÃO *****
            // Lógica para a conversa do DESTINATÁRIO (quem recebe) - AGORA NO CLIENTE
            val receiverConversationRef = receiverDocRef.collection("conversations").document(senderUid)

            val receiverUpdateMap = mapOf(
                "partnerId" to senderUid,
                "partnerName" to (senderUser.name ?: "Utilizador"),
                "partnerProfilePictureUrl" to senderUser.profilePictureUrl,
                "lastMessage" to lastMessage,
                "timestamp" to Timestamp.now(),
                "partnerPhoneNumber" to (senderUser.phoneNumber ?: ""),
                "unreadCount" to FieldValue.increment(1) // <-- Contagem de volta no app
            )

            receiverConversationRef.set(receiverUpdateMap, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Falha ao atualizar a conversa do destinatário: ${e.message}", Toast.LENGTH_LONG).show()
                }
            // ***** FIM DA CORREÇÃO *****

        }.addOnFailureListener { e ->
            Toast.makeText(context, "Falha ao buscar o seu perfil: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }.addOnFailureListener { e ->
        Toast.makeText(context, "Falha ao buscar o perfil do destinatário: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun SentMessageBubble(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 0.dp))
                .background(Color(0xFFDCF8C6))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = message.message ?: "",
                    modifier = Modifier.padding(end = 8.dp),
                    color = Color.Black
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatTimestamp(message.timestamp),
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    MessageStatusIcon(status = message.status)
                }
            }
        }
    }
}

@Composable
fun ReceivedMessageBubble(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 12.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = message.message ?: "",
                    modifier = Modifier.padding(end = 8.dp),
                    color = Color.Black
                )
                Text(
                    text = formatTimestamp(message.timestamp),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun MessageStatusIcon(status: String) {
    // Lógica para mostrar o ícone correto com base no status da mensagem
    val (icon, color) = when (status) {
        "READ" -> Icons.Default.DoneAll to BlueCheck
        "DELIVERED" -> Icons.Default.DoneAll to Color.Gray
        else -> Icons.Default.Done to Color.Gray // "SENT" ou qualquer outro caso
    }

    Icon(
        imageVector = icon,
        contentDescription = "Status da Mensagem",
        modifier = Modifier.size(16.dp),
        tint = color
    )
}