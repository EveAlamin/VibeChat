package com.example.vibechat.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.vibechat.data.Group
import com.example.vibechat.data.User
import com.example.vibechat.repository.GroupRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun GroupDetailsScreen(navController: NavController, groupId: String) {
    var group by remember { mutableStateOf<Group?>(null) }
    var members by remember { mutableStateOf<List<User>>(emptyList()) }
    val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
    val coroutineScope = rememberCoroutineScope()
    val groupRepository = remember { GroupRepository() }
    val context = LocalContext.current

    // Estados para o diálogo de renomear
    var showRenameDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    // Estados para o diálogo de confirmação de remoção
    var showRemoveDialog by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<User?>(null) }

    DisposableEffect(groupId) {
        val db = FirebaseFirestore.getInstance()
        val groupListener = db.collection("groups").document(groupId)
            .addSnapshotListener { groupSnapshot, _ ->
                val groupData = groupSnapshot?.toObject(Group::class.java)
                group = groupData
                if (groupData != null && groupData.memberIds.isNotEmpty()) {
                    db.collection("users").whereIn("uid", groupData.memberIds)
                        .addSnapshotListener { usersSnapshot, _ ->
                            members = usersSnapshot?.toObjects(User::class.java) ?: emptyList()
                        }
                } else {
                    members = emptyList()
                    if (groupData != null && groupData.memberIds.isEmpty()) {
                        navController.popBackStack()
                    }
                }
            }
        onDispose { groupListener.remove() }
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
                        // ... (código da imagem do grupo sem alterações)
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

                    // --- ÁREA DO NOME MODIFICADA ---
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = group?.name ?: "",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // Mostra o ícone de edição apenas para administradores
                        if (isCurrentUserAdmin) {
                            IconButton(onClick = {
                                newGroupName = group?.name ?: ""
                                showRenameDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Renomear grupo", tint = Color.Gray)
                            }
                        }
                    }
                }
                Divider()
            }

            // ... (resto do LazyColumn com participantes e ActionItem sem alterações)
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${members.size} participantes",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isCurrentUserAdmin) {
                        ActionItem(
                            icon = Icons.Default.PersonAdd,
                            text = "Adicionar participantes",
                            onClick = { navController.navigate("addGroupMembers/$groupId") }
                        )
                    }
                }
            }
            items(members) { member ->
                MemberItem(
                    member = member,
                    isAdmin = group?.adminIds?.contains(member.uid) == true,
                    showAdminOptions = isCurrentUserAdmin && member.uid != currentUserUid,
                    onRemoveClick = {
                        memberToRemove = member
                        showRemoveDialog = true
                    }
                )
            }
        }
    }

    // Diálogo de confirmação para remover membro
    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remover Participante") },
            text = { Text("Tem a certeza de que quer remover ${memberToRemove?.name} do grupo?") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val result = groupRepository.removeMemberFromGroup(groupId, memberToRemove!!.uid!!)
                            if (result.isSuccess) {
                                Toast.makeText(context, "${memberToRemove?.name} removido.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Erro ao remover.", Toast.LENGTH_SHORT).show()
                            }
                            showRemoveDialog = false
                            memberToRemove = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remover")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // --- NOVO DIÁLOGO PARA RENOMEAR O GRUPO ---
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Renomear Grupo") },
            text = {
                TextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    singleLine = true,
                    label = { Text("Nome do grupo") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val result = groupRepository.renameGroup(groupId, newGroupName)
                            if (result.isSuccess) {
                                Toast.makeText(context, "Grupo renomeado.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Erro: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// ... (Composables MemberItem e ActionItem permanecem os mesmos)
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
        if (member.profilePictureUrl.isNullOrEmpty()) {
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
            GlideImage(
                model = member.profilePictureUrl,
                contentDescription = "Foto de ${member.name}",
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
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