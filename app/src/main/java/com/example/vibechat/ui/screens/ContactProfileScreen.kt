package com.example.vibechat.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import com.example.vibechat.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun ContactProfileScreen(navController: NavController, userId: String) {
    var user by remember { mutableStateOf<User?>(null) }
    var commonGroups by remember { mutableStateOf<List<Group>>(emptyList()) }
    val db = FirebaseFirestore.getInstance()
    val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Lógica de bloqueio
    val userRepository = remember { UserRepository() }
    var isBlocked by remember { mutableStateOf(false) }
    var isLoadingBlockAction by remember { mutableStateOf(true) }

    // Busca os dados do utilizador E o status de bloqueio
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            db.collection("users").document(userId).addSnapshotListener { snapshot, _ ->
                user = snapshot?.toObject(User::class.java)
            }
            // Verifica o status de bloqueio inicial
            isBlocked = userRepository.isContactBlocked(userId)
            isLoadingBlockAction = false
        }
    }

    // Busca os grupos em comum
    LaunchedEffect(userId, currentUserUid) {
        if (userId.isNotEmpty() && currentUserUid != null) {
            db.collection("groups")
                .whereArrayContains("memberIds", currentUserUid)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val allMyGroups = querySnapshot.toObjects(Group::class.java)
                    commonGroups = allMyGroups.filter { it.memberIds.contains(userId) }
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dados do Contato") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (user == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        // Secção de informações do perfil
                        Column {
                            Spacer(modifier = Modifier.height(24.dp))
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(150.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (user?.profilePictureUrl != null) {
                                        GlideImage(
                                            model = user?.profilePictureUrl,
                                            contentDescription = "Foto de Perfil",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Sem Foto de Perfil",
                                            modifier = Modifier.size(80.dp),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            ReadOnlyProfileInfoRow(
                                icon = Icons.Default.Person,
                                title = "Nome",
                                subtitle = user?.name ?: "Sem nome"
                            )
                            Divider(modifier = Modifier.padding(start = 56.dp))
                            ReadOnlyProfileInfoRow(
                                icon = Icons.Default.Info,
                                title = "Recado",
                                subtitle = user?.status ?: "Disponível"
                            )
                            Divider(modifier = Modifier.padding(start = 56.dp))
                            ReadOnlyProfileInfoRow(
                                icon = Icons.Default.Phone,
                                title = "Telefone",
                                subtitle = user?.phoneNumber ?: "Sem número"
                            )
                        }
                    }

                    // Secção de Grupos em Comum
                    if (commonGroups.isNotEmpty()) {
                        item {
                            Divider()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Grupos em Comum (${commonGroups.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(commonGroups) { group ->
                            GroupInCommonItem(group = group) {
                                navController.navigate("chat/${group.name}/${group.id}?isGroup=true")
                            }
                        }
                    }
                }

                // Botão de Bloqueio
                Divider()
                TextButton(
                    onClick = {
                        isLoadingBlockAction = true
                        coroutineScope.launch {
                            val result = if (isBlocked) {
                                userRepository.unblockContact(userId)
                            } else {
                                userRepository.blockContact(userId)
                            }
                            result.onSuccess {
                                isBlocked = !isBlocked // Atualiza o estado local
                                val feedback = if (isBlocked) "Contato bloqueado" else "Contato desbloqueado"
                                Toast.makeText(context, feedback, Toast.LENGTH_SHORT).show()
                            }
                            result.onFailure {
                                Toast.makeText(context, "Erro: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                            isLoadingBlockAction = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    enabled = !isLoadingBlockAction
                ) {
                    val buttonText = if (isBlocked) "Desbloquear Contato" else "Bloquear Contato"
                    val buttonColor = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    Icon(
                        imageVector = if (isBlocked) Icons.Default.LockOpen else Icons.Default.Block,
                        contentDescription = buttonText,
                        tint = buttonColor,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(buttonText, color = buttonColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ReadOnlyProfileInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text(text = subtitle, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun GroupInCommonItem(group: Group, onClick: () -> Unit) {
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
            if (group.groupPictureUrl != null) {
                GlideImage(
                    model = group.groupPictureUrl,
                    contentDescription = "Foto do Grupo ${group.name}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = "Ícone de Grupo",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = group.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}