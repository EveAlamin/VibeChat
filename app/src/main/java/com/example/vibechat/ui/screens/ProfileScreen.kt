package com.example.vibechat.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.vibechat.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch

// NOVAS IMPORTAÇÕES NECESSÁRIAS PARA O ROOM
import com.example.vibechat.VibeChatApp
import com.example.vibechat.data.local.entities.toDataUser
import com.example.vibechat.data.local.entities.toUserEntity
import com.example.vibechat.repository.UserRepository
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun ProfileScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userRepository = remember { UserRepository() }

    val currentUserUid = auth.currentUser?.uid ?: ""

    // MODIFICADO: Lendo o utilizador a partir do Room
    val userEntity by VibeChatApp.database.userDao().getUserById(currentUserUid).collectAsState(initial = null)
    val user = userEntity?.let { it.toDataUser() }

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showEditStatusDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(user?.name ?: "") }
    var newStatus by remember { mutableStateOf(user?.status ?: "") }

    // MODIFICADO: Sincronização do Firebase para o Room
    LaunchedEffect(currentUserUid) {
        if (currentUserUid.isNotEmpty()) {
            db.collection("users").document(currentUserUid).addSnapshotListener { snapshot, _ ->
                val userData = snapshot?.toObject(User::class.java)
                userData?.let {
                    coroutineScope.launch {
                        VibeChatApp.database.userDao().insertUser(it.toUserEntity())
                    }
                }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val uid = auth.currentUser?.uid
            if (uid != null) {
                coroutineScope.launch {
                    try {
                        val storageRef = storage.reference.child("profile_pictures/$uid")
                        val uploadTask = storageRef.putFile(it).await()
                        val downloadUrl = uploadTask.storage.downloadUrl.await().toString()

                        // MODIFICADO: Chama o novo método do repositório
                        userRepository.updateUserProfilePicture(uid, downloadUrl)

                        Toast.makeText(context, "Foto de perfil atualizada.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Erro ao fazer o upload da imagem.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil") },
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
            Spacer(modifier = Modifier.height(24.dp))
            // Foto de Perfil
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { imagePickerLauncher.launch("image/*") },
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

            // Nome, Recado e Telefone
            ProfileInfoRow(
                icon = Icons.Default.Person,
                title = "Nome",
                subtitle = user?.name ?: "Sem nome",
                onClick = {
                    newName = user?.name ?: ""
                    showEditNameDialog = true
                }
            )
            Divider(modifier = Modifier.padding(start = 56.dp))
            ProfileInfoRow(
                icon = Icons.Default.Info,
                title = "Recado",
                subtitle = user?.status ?: "Disponível",
                onClick = {
                    newStatus = user?.status ?: ""
                    showEditStatusDialog = true
                }
            )
            Divider(modifier = Modifier.padding(start = 56.dp))
            ProfileInfoRow(
                icon = Icons.Default.Phone,
                title = "Telefone",
                subtitle = user?.phoneNumber ?: "Sem número",
                onClick = {}
            )
        }
    }

    if (showEditNameDialog) {
        EditInfoDialog(
            title = "Editar Nome",
            initialValue = newName,
            onDismiss = { showEditNameDialog = false },
            onConfirm = { updatedName ->
                auth.currentUser?.uid?.let { uid ->
                    coroutineScope.launch {
                        // MODIFICADO: Chama o repositório para sincronizar Room e Firebase
                        userRepository.updateUserName(uid, updatedName)
                        showEditNameDialog = false
                    }
                }
            }
        )
    }

    if (showEditStatusDialog) {
        EditInfoDialog(
            title = "Editar Recado",
            initialValue = newStatus,
            onDismiss = { showEditStatusDialog = false },
            onConfirm = { updatedStatus ->
                auth.currentUser?.uid?.let { uid ->
                    coroutineScope.launch {
                        // MODIFICADO: Chama o repositório para sincronizar Room e Firebase
                        userRepository.updateUserStatus(uid, updatedStatus)
                        showEditStatusDialog = false
                    }
                }
            }
        )
    }
}
// As funções ProfileInfoRow e EditInfoDialog permanecem as mesmas
@Composable
fun ProfileInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text(text = subtitle, style = MaterialTheme.typography.bodyLarge)
        }
        if (onClick != {}) {
            Icon(imageVector = Icons.Default.Edit, contentDescription = "Editar", tint = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditInfoDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it }
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}