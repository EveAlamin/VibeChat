package com.example.vibechat.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.vibechat.data.Group
import com.example.vibechat.data.Message
import com.example.vibechat.data.User
import com.example.vibechat.repository.MediaRepository
import com.example.vibechat.ui.theme.BlueCheck
import com.example.vibechat.utils.formatTimestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun ChatScreen(
    navController: NavController,
    name: String,
    chatId: String,
    receiverPhone: String,
    isGroup: Boolean
) {
    var messageText by remember { mutableStateOf("") }
    var messageList by remember { mutableStateOf<List<Message>>(emptyList()) }
    var chatPartner by remember { mutableStateOf<Any?>(null) }
    val membersNames = remember { mutableStateMapOf<String, String>() }
    val senderUid = FirebaseAuth.getInstance().currentUser?.uid!!
    val db = FirebaseFirestore.getInstance()
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val mediaRepository = remember { MediaRepository() }
    val context = LocalContext.current

    val mainCollection = if (isGroup) "groups" else "chats"
    val chatDocumentId = if (isGroup) chatId else senderUid + chatId

    // ✨ Launcher para selecionar uma imagem da galeria
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                coroutineScope.launch {
                    val result = mediaRepository.uploadMedia(it, "image")
                    result.onSuccess { downloadUrl ->
                        sendMessage(
                            message = null, // Mensagem de texto nula
                            mediaUrl = downloadUrl,
                            senderId = senderUid,
                            chatDocumentId = chatDocumentId,
                            isGroup = isGroup,
                            receiverUid = chatId,
                            db = db
                        )
                    }.onFailure { e ->
                        Toast.makeText(context, "Erro ao enviar imagem: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    LaunchedEffect(isGroup, chatId) {
        if (isGroup) {
            db.collection("groups").document(chatId).get().addOnSuccessListener { groupDoc ->
                val memberIds = groupDoc.toObject(Group::class.java)?.memberIds ?: emptyList()
                if (memberIds.isNotEmpty()) {
                    db.collection("users").whereIn("uid", memberIds).get().addOnSuccessListener { usersDoc ->
                        usersDoc.forEach { userDoc ->
                            val user = userDoc.toObject(User::class.java)
                            if (user.uid != null && user.name != null) {
                                membersNames[user.uid] = user.name
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(chatId) {
        db.collection("users").document(senderUid)
            .collection("conversations").document(chatId)
            .update("unreadCount", 0)

        val detailsListener = if (isGroup) {
            db.collection("groups").document(chatId).addSnapshotListener { snapshot, _ ->
                chatPartner = snapshot?.toObject(Group::class.java)
            }
        } else {
            db.collection("users").document(chatId).addSnapshotListener { snapshot, _ ->
                chatPartner = snapshot?.toObject(User::class.java)
            }
        }

        val messagesListener = db.collection(mainCollection).document(chatDocumentId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                messageList = snapshot.documents.mapNotNull { it.toObject(Message::class.java) }
            }

        onDispose {
            detailsListener.remove()
            messagesListener.remove()
        }
    }

    Scaffold(
        topBar = {
            CustomChatTopBar(
                isGroup = isGroup,
                chatPartner = chatPartner,
                name = name,
                onBackClick = { navController.popBackStack() },
                onDetailsClick = {
                    if (isGroup) {
                        navController.navigate("groupDetails/$chatId")
                    }
                }
            )
        },
        content = { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFECE5DD))) {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    state = listState,
                    reverseLayout = true
                ) {
                    items(messageList.reversed()) { message ->
                        val senderName = if (isGroup && message.senderId != senderUid) membersNames[message.senderId] else null
                        if (message.senderId == senderUid) {
                            SentMessageBubble(message = message)
                        } else {
                            ReceivedMessageBubble(message = message, senderName = senderName)
                        }
                    }
                }

                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Icon(Icons.Default.Photo, contentDescription = "Anexar Imagem", tint = MaterialTheme.colorScheme.primary)
                    }

                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Digite uma mensagem...") },
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.textFieldColors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                sendMessage(
                                    message = messageText,
                                    mediaUrl = null,
                                    senderId = senderUid,
                                    chatDocumentId = chatDocumentId,
                                    isGroup = isGroup,
                                    receiverUid = chatId,
                                    db = db
                                )
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

// ✨ Nova função para enviar a mensagem (texto ou mídia)
private fun sendMessage(
    message: String?,
    mediaUrl: String?,
    senderId: String,
    chatDocumentId: String,
    isGroup: Boolean,
    receiverUid: String,
    db: FirebaseFirestore
) {
    val messageId = db.collection("chats").document().id
    val messageObject = Message(
        id = messageId,
        message = message,
        mediaUrl = mediaUrl,
        senderId = senderId,
        timestamp = Timestamp.now()
    )

    db.collection(if (isGroup) "groups" else "chats")
        .document(chatDocumentId)
        .collection("messages")
        .document(messageId)
        .set(messageObject)

    if (!isGroup) {
        val receiverRoom = receiverUid + senderId
        db.collection("chats").document(receiverRoom)
            .collection("messages").document(messageId).set(messageObject)
    }

    updateLastMessage(db, receiverUid, message ?: "Nova Imagem", isGroup)
}

// ... (O resto do código permanece o mesmo)

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun CustomChatTopBar(
    isGroup: Boolean,
    chatPartner: Any?,
    name: String,
    onBackClick: () -> Unit,
    onDetailsClick: () -> Unit // Parâmetro renomeado
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Color.White
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val profilePictureUrl = if (isGroup) (chatPartner as? Group)?.groupPictureUrl else (chatPartner as? User)?.profilePictureUrl
                val defaultIcon = if (isGroup) Icons.Default.Groups else Icons.Default.Person

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (profilePictureUrl != null) {
                        GlideImage(
                            model = profilePictureUrl,
                            contentDescription = "Foto de $name",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = defaultIcon,
                            contentDescription = "Sem Foto",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = name,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isGroup) {
                IconButton(onClick = onDetailsClick) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Detalhes do grupo",
                        tint = Color.White
                    )
                }
            }
        }
    }
}


private fun updateLastMessage(db: FirebaseFirestore, chatId: String, lastMessage: String, isGroup: Boolean) {
    val timestamp = Timestamp.now()
    val senderUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

    if (isGroup) {
        db.collection("groups").document(chatId).get().addOnSuccessListener { groupDoc ->
            val memberIds = groupDoc.toObject(Group::class.java)?.memberIds ?: return@addOnSuccessListener
            val batch = db.batch()
            memberIds.forEach { memberId ->
                val conversationRef = db.collection("users").document(memberId)
                    .collection("conversations").document(chatId)
                val updateMap = mutableMapOf<String, Any>(
                    "lastMessage" to lastMessage,
                    "timestamp" to timestamp
                )
                if (memberId != senderUid) {
                    updateMap["unreadCount"] = FieldValue.increment(1)
                }
                batch.update(conversationRef, updateMap)
            }
            batch.commit()
        }
    } else {
        val receiverUid = chatId
        val conversationRefSender = db.collection("users").document(senderUid)
            .collection("conversations").document(receiverUid)
        val conversationRefReceiver = db.collection("users").document(receiverUid)
            .collection("conversations").document(senderUid)
        conversationRefSender.update("lastMessage", lastMessage, "timestamp", timestamp)
        conversationRefReceiver.update(
            "lastMessage", lastMessage,
            "timestamp", timestamp,
            "unreadCount", FieldValue.increment(1)
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ReceivedMessageBubble(message: Message, senderName: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = 0.dp,
                    bottomEnd = 12.dp
                ))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                if (senderName != null) {
                    Text(
                        text = senderName,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                if (!message.mediaUrl.isNullOrEmpty()) {
                    GlideImage(
                        model = message.mediaUrl,
                        contentDescription = "Imagem enviada",
                        modifier = Modifier
                            .size(150.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                if (!message.message.isNullOrBlank()) {
                    Text(
                        text = message.message,
                        color = Color.Black
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(message.timestamp),
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SentMessageBubble(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = 12.dp,
                    bottomEnd = 0.dp
                ))
                .background(Color(0xFFDCF8C6))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                if (!message.mediaUrl.isNullOrEmpty()) {
                    GlideImage(
                        model = message.mediaUrl,
                        contentDescription = "Imagem enviada",
                        modifier = Modifier
                            .size(150.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                if (!message.message.isNullOrBlank()) {
                    Text(
                        text = message.message,
                        color = Color.Black
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
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
fun MessageStatusIcon(status: String) {
    val (icon, color) = when (status) {
        "READ" -> Icons.Default.DoneAll to BlueCheck
        "DELIVERED" -> Icons.Default.DoneAll to Color.Gray
        else -> Icons.Default.Done to Color.Gray
    }
    Icon(
        imageVector = icon,
        contentDescription = "Status da Mensagem",
        modifier = Modifier.size(16.dp),
        tint = color
    )
}