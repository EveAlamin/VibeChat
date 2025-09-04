package com.example.vibechat.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.vibechat.data.*
import com.example.vibechat.repository.UserRepository
import com.example.vibechat.ui.theme.BlueCheck
import com.example.vibechat.utils.formatLastSeen
import com.example.vibechat.utils.formatTimestamp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.widthIn
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.util.UUID
import com.example.vibechat.VibeChatApp
import com.example.vibechat.data.local.entities.MessageEntity
import com.example.vibechat.data.local.entities.toDataMessage
import com.example.vibechat.data.local.entities.toMessageEntity
import androidx.compose.ui.platform.LocalContext
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    navController: NavController,
    name: String,
    chatId: String,
    receiverPhone: String,
    isGroup: Boolean
) {
    var messageText by remember { mutableStateOf("") }
    // A lista de mensagens agora vem do Room como um Flow
    val messageListRoom by VibeChatApp.database.messageDao().getMessagesForChat(chatId).collectAsState(initial = emptyList())
    var chatPartner by remember { mutableStateOf<Any?>(null) }
    val membersNames = remember { mutableStateMapOf<String, String>() }
    val senderUid = FirebaseAuth.getInstance().currentUser?.uid!!
    val db = FirebaseFirestore.getInstance()
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf<User?>(null) }
    var pinnedMessage by remember { mutableStateOf<Message?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<Message?>(null) }
    var locallyDeletedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isPartnerBlocked by remember { mutableStateOf(false) }
    val userRepository = remember { UserRepository() }
    var partnerPresence by remember { mutableStateOf<UserPresence?>(null) }
    val storage = remember { FirebaseStorage.getInstance() }
    var isLoadingMedia by remember { mutableStateOf(false) }
    val context = LocalContext.current // Adiciona esta linha

    DisposableEffect(chatId) {
        var presenceListener: ValueEventListener? = null
        if (!isGroup) {
            val presenceRef = FirebaseDatabase.getInstance().getReference("/status/$chatId")
            presenceListener = presenceRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    partnerPresence = snapshot.getValue(UserPresence::class.java)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
        onDispose {
            presenceListener?.let {
                FirebaseDatabase.getInstance().getReference("/status/$chatId").removeEventListener(it)
            }
        }
    }

    LaunchedEffect(chatId) {
        if (!isGroup) {
            isPartnerBlocked = userRepository.isContactBlocked(chatId)
        }
    }

    val mainCollection = if (isGroup) "groups" else "chats"
    val chatDocumentId = if (isGroup) chatId else getChatRoomId(senderUid, chatId)
    val chatDocRef = db.collection(mainCollection).document(chatDocumentId)

    val visibleMessages by remember(messageListRoom, locallyDeletedIds) {
        derivedStateOf {
            messageListRoom.filter { it.id !in locallyDeletedIds }.map { it.toDataMessage() }
        }
    }

    val filteredMessages by remember(searchQuery, visibleMessages) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                visibleMessages
            } else {
                visibleMessages.filter {
                    it.message?.contains(searchQuery, ignoreCase = true) == true
                }
            }
        }
    }

    LaunchedEffect(senderUid) {
        db.collection("users").document(senderUid).get().addOnSuccessListener {
            currentUser = it.toObject(User::class.java)
        }
    }

    LaunchedEffect(visibleMessages, listState) {
        val unreadMessages = visibleMessages
            .filter { it.senderId != senderUid && !it.readBy.contains(senderUid) }

        if (unreadMessages.isNotEmpty()) {
            val batch = db.batch()
            unreadMessages.forEach { message ->
                val messageRef = chatDocRef.collection("messages").document(message.id)
                batch.update(messageRef, "readBy", FieldValue.arrayUnion(senderUid))

                if (!isGroup) {
                    val receiverRoomId = getChatRoomId(chatId, senderUid)
                    val receiverMessageRef = db.collection("chats").document(receiverRoomId)
                        .collection("messages").document(message.id)
                    batch.update(receiverMessageRef, "readBy", FieldValue.arrayUnion(senderUid))
                }
            }
            batch.commit()
        }
    }

    fun updatePinnedMessage(messageId: String?) {
        val conversationRefSender = db.collection("users").document(senderUid).collection("conversations").document(chatId)

        if (isGroup) {
            db.collection("groups").document(chatId).get().addOnSuccessListener { groupDoc ->
                val memberIds = groupDoc.toObject(Group::class.java)?.memberIds ?: emptyList()
                val batch = db.batch()
                memberIds.forEach { memberId ->
                    val conversationRef = db.collection("users").document(memberId).collection("conversations").document(chatId)
                    batch.update(conversationRef, "pinnedMessageId", messageId)
                }
                batch.commit()
            }
        } else {
            val conversationRefReceiver = db.collection("users").document(chatId).collection("conversations").document(senderUid)
            conversationRefSender.update("pinnedMessageId", messageId)
            conversationRefReceiver.update("pinnedMessageId", messageId)
        }
    }

    fun deleteMessageForEveryone(message: Message) {
        val updatedData = mapOf(
            "message" to "🚫 Mensagem apagada",
            "wasDeleted" to true
        )
        chatDocRef.collection("messages").document(message.id).update(updatedData)
        if (!isGroup) {
            val receiverRoomId = getChatRoomId(chatId, senderUid)
            db.collection("chats").document(receiverRoomId)
                .collection("messages").document(message.id).update(updatedData)
        }
        if (visibleMessages.lastOrNull()?.id == message.id) {
            updateLastMessage(db, chatId, "🚫 Mensagem apagada", isGroup, currentUser)
        }
    }

    fun deleteMessageForMe(messageId: String) {
        val deletedDocRef = db.collection("users").document(senderUid)
            .collection("deletedMessages").document(chatDocumentId)

        deletedDocRef.set(mapOf("ids" to FieldValue.arrayUnion(messageId)), SetOptions.merge())
    }

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
        val conversationListener = db.collection("users").document(senderUid)
            .collection("conversations").document(chatId)
            .addSnapshotListener { snapshot, _ ->
                val conversation = snapshot?.toObject(Conversation::class.java)
                val pinnedId = conversation?.pinnedMessageId
                if (pinnedId != null && pinnedId.isNotEmpty()) {
                    chatDocRef.collection("messages").document(pinnedId).get()
                        .addOnSuccessListener { messageDoc ->
                            pinnedMessage = messageDoc.toObject(Message::class.java)
                        }
                } else {
                    pinnedMessage = null
                }
            }

        val deletedMessagesListener = db.collection("users").document(senderUid)
            .collection("deletedMessages").document(chatDocumentId)
            .addSnapshotListener { snapshot, _ ->
                val ids = snapshot?.get("ids") as? List<String>
                locallyDeletedIds = ids?.toSet() ?: emptySet()
            }

        val detailsListener = if (isGroup) {
            db.collection("groups").document(chatId).addSnapshotListener { snapshot, _ ->
                chatPartner = snapshot?.toObject(Group::class.java)
            }
        } else {
            db.collection("users").document(chatId).addSnapshotListener { snapshot, _ ->
                chatPartner = snapshot?.toObject(User::class.java)
            }
        }

        // NOVO: Listener do Firebase para sincronizar com o Room
        val messagesListener = chatDocRef.collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                val messages = snapshot.documents.mapNotNull { it.toObject(Message::class.java) }

                coroutineScope.launch {
                    val messageEntities = messages.map { it.toMessageEntity(chatId) }
                    VibeChatApp.database.messageDao().insertAllMessages(messageEntities)

                    db.collection("users").document(senderUid)
                        .collection("conversations").document(chatId)
                        .update("unreadCount", 0)
                }
            }

        onDispose {
            conversationListener.remove()
            detailsListener.remove()
            messagesListener.remove()
            deletedMessagesListener.remove()
        }
    }

    // NOVO: Lançador de atividade para selecionar mídia
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isLoadingMedia = true
            coroutineScope.launch {
                val storageRef = storage.reference.child("chat_media/${UUID.randomUUID()}")
                try {
                    val uploadTask = storageRef.putFile(it).await()
                    val downloadUrl = uploadTask.storage.downloadUrl.await().toString()

                    val messageObject = Message(
                        id = db.collection("chats").document().id,
                        senderId = senderUid,
                        timestamp = Timestamp.now(),
                        readBy = listOf(senderUid),
                        imageUrl = downloadUrl,
                        message = "Foto"
                    )

                    // Primeiro salva no Room
                    VibeChatApp.database.messageDao().insertMessage(messageObject.toMessageEntity(chatId))

                    // Depois envia para o Firebase
                    chatDocRef.collection("messages").document(messageObject.id).set(messageObject)
                    if (!isGroup) {
                        val receiverRoomId = getChatRoomId(chatId, senderUid)
                        db.collection("chats").document(receiverRoomId)
                            .collection("messages").document(messageObject.id).set(messageObject)
                    }
                    updateLastMessage(db, chatId, "Foto", isGroup, currentUser)
                } catch (e: Exception) {
                    Toast.makeText(context, "Erro ao enviar mídia.", Toast.LENGTH_SHORT).show()
                } finally {
                    isLoadingMedia = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CustomChatTopBar(
                isGroup = isGroup,
                chatPartner = chatPartner,
                name = name,
                presence = partnerPresence,
                onBackClick = { navController.popBackStack() },
                onDetailsClick = {
                    if (isGroup) {
                        navController.navigate("groupDetails/$chatId")
                    } else {
                        navController.navigate("contactProfile/$chatId")
                    }
                },
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSearchClick = { isSearchActive = !isSearchActive }
            )
        },
        content = { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFECE5DD))) {
                AnimatedVisibility(visible = pinnedMessage != null, enter = fadeIn(), exit = fadeOut()) {
                    val pinnedMessageSenderName = when {
                        pinnedMessage?.senderId == senderUid -> "Eu"
                        isGroup -> membersNames[pinnedMessage?.senderId] ?: "Alguém"
                        else -> name
                    }
                    PinnedMessageBar(
                        message = pinnedMessage,
                        senderName = pinnedMessageSenderName,
                        onUnpin = { updatePinnedMessage(null) }
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    state = listState,
                    reverseLayout = true
                ) {
                    items(filteredMessages.reversed()) { message ->
                        var showMessageMenu by remember { mutableStateOf(false) }
                        val senderName = if (isGroup && message.senderId != senderUid) membersNames[message.senderId] else null
                        val memberCount = (chatPartner as? Group)?.memberIds?.size ?: 0

                        Box {
                            val messageBubble: @Composable () -> Unit = {
                                if (message.senderId == senderUid) {
                                    SentMessageBubble(
                                        message = message,
                                        searchQuery = searchQuery,
                                        isGroup = isGroup,
                                        partnerId = chatId,
                                        memberCount = memberCount
                                    )
                                } else {
                                    ReceivedMessageBubble(message = message, senderName = senderName, searchQuery = searchQuery)
                                }
                            }

                            Box(modifier = Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    if (!message.wasDeleted) {
                                        showMessageMenu = true
                                    }
                                }
                            )) {
                                messageBubble()
                            }

                            DropdownMenu(
                                expanded = showMessageMenu,
                                onDismissRequest = { showMessageMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Fixar") },
                                    onClick = {
                                        updatePinnedMessage(message.id)
                                        showMessageMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Apagar") },
                                    onClick = {
                                        messageToDelete = message
                                        showMessageMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                if (showDeleteDialog) {
                    DeleteMessageDialog(
                        isSender = messageToDelete?.senderId == senderUid,
                        onDismiss = { showDeleteDialog = false },
                        onDeleteForMe = {
                            messageToDelete?.let { deleteMessageForMe(it.id) }
                            showDeleteDialog = false
                        },
                        onDeleteForEveryone = {
                            messageToDelete?.let { deleteMessageForEveryone(it) }
                            showDeleteDialog = false
                        }
                    )
                }

                if (isPartnerBlocked && !isGroup) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF9C4))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Você bloqueou este contato.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black
                        )
                    }
                } else {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            enabled = !isLoadingMedia
                        ) {
                            if (isLoadingMedia) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Attachment,
                                    contentDescription = "Anexar mídia",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        TextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Digite uma mensagem...") },
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FloatingActionButton(
                            onClick = {
                                if (messageText.isNotBlank() && currentUser != null) {
                                    val currentMessage = messageText
                                    val messageId = UUID.randomUUID().toString()
                                    val messageObject = Message(
                                        id = messageId,
                                        message = currentMessage,
                                        senderId = senderUid,
                                        timestamp = Timestamp.now(),
                                        readBy = listOf(senderUid)
                                    )

                                    coroutineScope.launch {
                                        // Primeiro salva no Room
                                        VibeChatApp.database.messageDao().insertMessage(messageObject.toMessageEntity(chatId))

                                        // Depois envia para o Firebase
                                        chatDocRef.collection("messages").document(messageId).set(messageObject)
                                        if (!isGroup) {
                                            val receiverRoomId = getChatRoomId(chatId, senderUid)
                                            db.collection("chats").document(receiverRoomId)
                                                .collection("messages").document(messageId).set(messageObject)
                                        }
                                        updateLastMessage(db, chatId, currentMessage, isGroup, currentUser)
                                    }
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
        }
    )
}

// Funções de conversão para o Room
fun Message.toMessageEntity(chatId: String): MessageEntity {
    return MessageEntity(
        id = this.id,
        chatId = chatId,
        message = this.message,
        senderId = this.senderId,
        timestamp = this.timestamp?.seconds,
        imageUrl = this.imageUrl,
        videoUrl = this.videoUrl
    )
}

fun MessageEntity.toDataMessage(): Message {
    return Message(
        id = this.id,
        message = this.message,
        senderId = this.senderId,
        timestamp = this.timestamp?.let { Timestamp(it, 0) },
        imageUrl = this.imageUrl,
        videoUrl = this.videoUrl
    )
}

@Composable
fun DeleteMessageDialog(
    isSender: Boolean,
    onDismiss: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    "Apagar mensagem?",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDeleteForMe,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apagar para mim")
                }

                if (isSender) {
                    TextButton(
                        onClick = onDeleteForEveryone,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Apagar para todos")
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancelar", color = Color.Gray)
                }
            }
        }
    }
}


private fun getChatRoomId(user1: String, user2: String): String {
    return if (user1 < user2) {
        "$user1$user2"
    } else {
        "$user2$user1"
    }
}

@Composable
fun PinnedMessageBar(message: Message?, senderName: String, onUnpin: () -> Unit) {
    if (message == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = senderName,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )
            Text(
                text = message.message ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = onUnpin) {
            Icon(Icons.Default.Close, contentDescription = "Desafixar mensagem", modifier = Modifier.size(18.dp))
        }
    }
}


@OptIn(ExperimentalGlideComposeApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CustomChatTopBar(
    isGroup: Boolean,
    chatPartner: Any?,
    name: String,
    presence: UserPresence?,
    onBackClick: () -> Unit,
    onDetailsClick: () -> Unit,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit
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
            if (isSearchActive) {
                IconButton(onClick = {
                    onSearchClick()
                    onSearchQueryChange("")
                }) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Fechar busca",
                        tint = Color.White
                    )
                }
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Buscar...", color = Color.White.copy(alpha = 0.7f)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            } else {
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
                        .padding(horizontal = 12.dp)
                        .clickable {
                            onDetailsClick()
                        },
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
                        if (!isGroup && presence != null) {
                            val statusText = if (presence.isOnline) {
                                "Online"
                            } else {
                                formatLastSeen(presence.lastSeen)
                            }
                            Text(
                                text = statusText,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                IconButton(onClick = onSearchClick) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = Color.White
                    )
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
}

@Composable
fun HighlightText(text: String, query: String, color: Color) {
    if (query.isBlank()) {
        Text(text = text, color = Color.Black)
        return
    }

    val annotatedString = buildAnnotatedString {
        var startIndex = 0
        while (startIndex < text.length) {
            val index = text.indexOf(query, startIndex, ignoreCase = true)
            if (index == -1) {
                append(text.substring(startIndex))
                break
            }
            append(text.substring(startIndex, index))
            withStyle(style = SpanStyle(background = color)) {
                append(text.substring(index, index + query.length))
            }
            startIndex = index + query.length
        }
    }
    Text(annotatedString)
}


private fun updateLastMessage(db: FirebaseFirestore, chatId: String, lastMessage: String, isGroup: Boolean, sender: User?) {
    if (sender == null) return
    val timestamp = Timestamp.now()
    val senderUid = sender.uid ?: return

    if (isGroup) {
        db.collection("groups").document(chatId).get().addOnSuccessListener { groupDoc ->
            val memberIds = groupDoc.toObject(Group::class.java)?.memberIds ?: return@addOnSuccessListener
            val batch = db.batch()
            memberIds.forEach { memberId ->
                val conversationRef = db.collection("users").document(memberId)
                    .collection("conversations").document(chatId)

                val messageForMember = if (memberId == senderUid) "Você: $lastMessage" else lastMessage

                val updateMap = mutableMapOf<String, Any>(
                    "lastMessage" to messageForMember,
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
        val conversationRefSender = db.collection("users").document(senderUid).collection("conversations").document(receiverUid)
        val conversationRefReceiver = db.collection("users").document(receiverUid).collection("conversations").document(senderUid)

        conversationRefSender.set(mapOf("lastMessage" to "Você: $lastMessage", "timestamp" to timestamp), SetOptions.merge())

        val receiverConversationData = mapOf(
            "partnerId" to senderUid,
            "partnerName" to (sender.name ?: ""),
            "partnerProfilePictureUrl" to sender.profilePictureUrl,
            "partnerPhoneNumber" to (sender.phoneNumber ?: ""),
            "lastMessage" to lastMessage,
            "timestamp" to timestamp,
            "unreadCount" to FieldValue.increment(1),
            "isGroup" to false
        )
        conversationRefReceiver.set(receiverConversationData, SetOptions.merge())
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ReceivedMessageBubble(message: Message, senderName: String?, searchQuery: String) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = screenWidth * 0.8f)
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

                if (message.imageUrl != null) {
                    GlideImage(
                        model = message.imageUrl,
                        contentDescription = "Imagem do chat",
                        modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        if (message.wasDeleted) {
                            Text(
                                text = message.message ?: "",
                                fontStyle = FontStyle.Italic,
                                color = Color.Gray
                            )
                        } else {
                            HighlightText(
                                text = message.message ?: "",
                                query = searchQuery,
                                color = Color.Yellow
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formatTimestamp(message.timestamp),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SentMessageBubble(
    message: Message,
    searchQuery: String,
    isGroup: Boolean,
    partnerId: String,
    memberCount: Int
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = screenWidth * 0.8f)
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
                if (message.imageUrl != null) {
                    GlideImage(
                        model = message.imageUrl,
                        contentDescription = "Imagem do chat",
                        modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    HighlightText(
                        text = message.message ?: "",
                        query = searchQuery,
                        color = Color.Yellow
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = formatTimestamp(message.timestamp),
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    MessageStatusIcon(
                        message = message,
                        isGroup = isGroup,
                        partnerId = partnerId,
                        memberCount = memberCount
                    )
                }
            }
        }
    }
}


@Composable
fun MessageStatusIcon(
    message: Message,
    isGroup: Boolean,
    partnerId: String,
    memberCount: Int
) {
    val isRead = if (isGroup) {
        message.readBy.size >= memberCount && memberCount > 0
    } else {
        message.readBy.contains(partnerId)
    }

    val (icon, color) = when {
        isRead -> Icons.Default.DoneAll to BlueCheck
        else -> Icons.Default.Done to Color.Gray
    }
    Icon(
        imageVector = icon,
        contentDescription = "Status da Mensagem",
        modifier = Modifier.size(16.dp),
        tint = color
    )
}