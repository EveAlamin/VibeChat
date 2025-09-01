package com.example.vibechat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.vibechat.data.Group
import com.example.vibechat.data.Message
import com.example.vibechat.data.User
import com.example.vibechat.ui.theme.BlueCheck
import com.example.vibechat.utils.formatTimestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
@OptIn(ExperimentalMaterial3Api::class)
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

    val filteredMessages by remember(searchQuery, messageList) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                messageList
            } else {
                messageList.filter {
                    it.message?.contains(searchQuery, ignoreCase = true) == true
                }
            }
        }
    }

    val mainCollection = if (isGroup) "groups" else "chats"
    val chatDocumentId = if (isGroup) chatId else senderUid + chatId

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
                },
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSearchClick = { isSearchActive = !isSearchActive }
            )
        },
        content = { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFECE5DD))) {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    state = listState,
                    reverseLayout = true
                ) {
                    items(filteredMessages.reversed()) { message ->
                        val senderName = if (isGroup && message.senderId != senderUid) membersNames[message.senderId] else null
                        if (message.senderId == senderUid) {
                            SentMessageBubble(message = message, searchQuery = searchQuery)
                        } else {
                            ReceivedMessageBubble(message = message, senderName = senderName, searchQuery = searchQuery)
                        }
                    }
                }

                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                val messageId = db.collection("chats").document().id
                                val messageObject = Message(
                                    id = messageId,
                                    message = messageText,
                                    senderId = senderUid,
                                    timestamp = Timestamp.now()
                                )

                                coroutineScope.launch {
                                    db.collection(mainCollection).document(chatDocumentId)
                                        .collection("messages").document(messageId).set(messageObject)
                                    if (!isGroup) {
                                        val receiverRoom = chatId + senderUid
                                        db.collection("chats").document(receiverRoom)
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
                    onSearchQueryChange("") // Limpa a busca ao fechar
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
                    // --- CORREÇÃO APLICADA AQUI ---
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
                    HighlightText(
                        text = message.message ?: "",
                        query = searchQuery,
                        color = Color.Yellow
                    )
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
                HighlightText(
                    text = message.message ?: "",
                    query = searchQuery,
                    color = Color.Yellow
                )
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