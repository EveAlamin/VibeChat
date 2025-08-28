package com.example.vibechat.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.vibechat.data.Conversation
import com.example.vibechat.utils.formatTimestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    var showMenu by remember { mutableStateOf(false) }
    val auth = FirebaseAuth.getInstance()
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vibe Chat", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { /* TODO: Camera logic */ }) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Câmara", tint = Color.White)
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Mais opções", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Perfil") },
                            onClick = {
                                navController.navigate("profile")
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sair") },
                            onClick = {
                                auth.signOut()
                                navController.navigate("phoneLogin") {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                }
                                showMenu = false
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("selectContact") },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Chat, contentDescription = "Nova Conversa", tint = Color.White)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SearchAndFilterSection(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it }
            )
            ConversationsScreen(navController, searchQuery)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAndFilterSection(searchQuery: String, onSearchQueryChange: (String) -> Unit) {
    var selectedFilter by remember { mutableStateOf("Todas") }
    val filters = listOf("Todas", "Não lidas", "Grupos")

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Pesquisar conversas...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Pesquisar") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = TextFieldDefaults.textFieldColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter) }
                )
            }
        }
    }
}

@Composable
fun ConversationsScreen(navController: NavController, searchQuery: String) {
    var conversationList by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    val filteredList = remember(searchQuery, conversationList) {
        if (searchQuery.isBlank()) {
            conversationList
        } else {
            conversationList.filter {
                it.partnerName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        val currentUserUid = auth.currentUser?.uid
        if (currentUserUid != null) {
            db.collection("users").document(currentUserUid).collection("conversations")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) { return@addSnapshotListener }
                    if (snapshot != null) {
                        conversationList = snapshot.documents.mapNotNull { it.toObject(Conversation::class.java) }
                    }
                }
        }
    }

    LazyColumn {
        items(filteredList) { conversation ->
            ConversationItem(conversation = conversation, onClick = {
                navController.navigate("chat/${conversation.partnerName}/${conversation.partnerId}?phone=${conversation.partnerPhoneNumber}")
            })
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ConversationItem(conversation: Conversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (conversation.partnerProfilePictureUrl != null) {
                GlideImage(
                    model = conversation.partnerProfilePictureUrl,
                    contentDescription = "Foto de Perfil de ${conversation.partnerName}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Sem Foto de Perfil",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = conversation.partnerName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(text = conversation.lastMessage, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatTimestamp(conversation.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (conversation.unreadCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF25D366)), // Verde do WhatsApp
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = conversation.unreadCount.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    Divider(modifier = Modifier.padding(start = 82.dp))
}
