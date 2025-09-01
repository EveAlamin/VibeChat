package com.example.vibechat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.vibechat.data.Group
import com.example.vibechat.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun GroupDetailsScreen(navController: NavController, groupId: String) {
    var group by remember { mutableStateOf<Group?>(null) }
    var members by remember { mutableStateOf<List<User>>(emptyList()) }
    val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid

    // Efeito para buscar detalhes do grupo e dos membros
    LaunchedEffect(groupId) {
        val db = FirebaseFirestore.getInstance()
        db.collection("groups").document(groupId).get().addOnSuccessListener { groupSnapshot ->
            val groupData = groupSnapshot.toObject(Group::class.java)
            group = groupData
            if (groupData != null && groupData.memberIds.isNotEmpty()) {
                db.collection("users").whereIn("uid", groupData.memberIds).get()
                    .addOnSuccessListener { usersSnapshot ->
                        members = usersSnapshot.toObjects(User::class.java)
                    }
            }
        }
    }

    val isCurrentUserAdmin = group?.adminIds?.contains(currentUserUid) == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dados do grupo") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            // Seção com imagem e nome do grupo
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (group?.groupPictureUrl != null) {
                            GlideImage(
                                model = group?.groupPictureUrl,
                                contentDescription = "Foto do grupo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = "Ícone de grupo",
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = group?.name ?: "",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Divider()
            }

            // Seção de participantes
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${members.size} participantes",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Opção para adicionar participantes (visível apenas para admins)
                    if (isCurrentUserAdmin) {
                        ActionItem(
                            icon = Icons.Default.PersonAdd,
                            text = "Adicionar participantes",
                            onClick = { /* TODO: Navegar para tela de adicionar */ }
                        )
                    }
                }
            }

            // Lista de participantes
            items(members) { member ->
                MemberItem(
                    member = member,
                    isAdmin = group?.adminIds?.contains(member.uid) == true,
                    showAdminOptions = isCurrentUserAdmin && member.uid != currentUserUid,
                    onRemoveClick = { /* TODO: Lógica para remover */ }
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MemberItem(
    member: User,
    isAdmin: Boolean,
    showAdminOptions: Boolean,
    onRemoveClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = showAdminOptions, onClick = onRemoveClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- CORREÇÃO APLICADA AQUI ---
        if (member.profilePictureUrl.isNullOrEmpty()) {
            // Se a URL da foto for nula ou vazia, mostramos o ícone padrão
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Sem foto",
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray)
                    .padding(8.dp)
            )
        } else {
            // Se houver uma URL, usamos o GlideImage para carregar a foto
            GlideImage(
                model = member.profilePictureUrl,
                contentDescription = "Foto de ${member.name}",
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        // --- FIM DA CORREÇÃO ---

        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(member.name ?: "Usuário", style = MaterialTheme.typography.bodyLarge)
            Text(member.status ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        if (isAdmin) {
            Text(
                text = "Admin",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun ActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = MaterialTheme.colorScheme.primary)
    }
}