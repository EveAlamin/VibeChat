package com.example.vibechat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import com.example.vibechat.data.Conversation
import com.example.vibechat.data.Group
import com.example.vibechat.data.Message
import com.example.vibechat.data.User
import com.example.vibechat.ui.theme.BlueCheck
import com.example.vibechat.utils.formatTimestamp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch

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
    var messageList by remember { mutableStateOf<List<Message>>(emptyList()) }
    var chatPartner by remember { mutableStateOf<Any?>(null) }
    val membersNames = remember { mutableStateMapOf<String, String>() }
    val senderUid = FirebaseAuth.getInstance().currentUser?.uid!!
    val db = FirebaseFirestore.getInstance()
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var pinnedMessage by remember { mutableStateOf<Message?>(null) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<Message?>(null) }

    var locallyDeletedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val mainCollection = if (isGroup) "groups" else "chats"
    val chatDocumentId = if (isGroup) chatId else getChatRoomId(senderUid, chatId)
    val chatDocRef = db.collection(mainCollection).document(chatDocumentId)

    val visibleMessages by remember(messageList, locallyDeletedIds) {
        derivedStateOf {
            messageList.filter { it.id !in locallyDeletedIds }
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
        if (messageList.lastOrNull()?.id == message.id) {
            updateLastMessage(db, chatId, "🚫 Mensagem apagada", isGroup)
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

        val messagesListener = chatDocRef.collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                messageList = snapshot.documents.mapNotNull { it.toObject(Message::class.java) }
            }

        onDispose {
            conversationListener.remove()
            detailsListener.remove()
            messagesListener.remove()
            deletedMessagesListener.remove()
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
                    PinnedMessageBar(
                        message = pinnedMessage,
                        senderName = membersNames[pinnedMessage?.senderId] ?: "Eu",
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

                        Box {
                            val messageBubble: @Composable () -> Unit = {
                                if (message.senderId == senderUid) {
                                    SentMessageBubble(message = message, searchQuery = searchQuery)
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

                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            if (messageText.isNotBlank()) {
                                val messageId = db.collection("chats").document().id
                                val messageObject = Message(
                                    id = messageId,
                                    message = messageText,
                                    senderId = senderUid,
                                    timestamp = Timestamp.now()
                                )

                                coroutineScope.launch {
                                    chatDocRef.collection("messages").document(messageId).set(messageObject)
                                    if (!isGroup) {
                                        val receiverRoomId = getChatRoomId(chatId, senderUid)
                                        db.collection("chats").document(receiverRoomId)
                                            .collection("messages").document(messageId).set(messageObject)
                                    }
                                    updateLastMessage(db, chatId, messageText, isGroup)
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

@Composable
fun ReceivedMessageBubble(message: Message, senderName: String?, searchQuery: String) {
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
                Row(verticalAlignment = Alignment.Bottom) {
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatTimestamp(message.timestamp),
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            }
        }
    }
}

@Composable
fun SentMessageBubble(message: Message, searchQuery: String) {
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
            Row(verticalAlignment = Alignment.Bottom) {
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
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alignByBaseline()
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