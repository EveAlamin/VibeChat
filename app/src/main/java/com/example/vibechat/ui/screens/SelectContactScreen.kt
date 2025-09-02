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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.vibechat.data.Contact
import com.example.vibechat.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectContactScreen(navController: NavController) {
    var contactList by remember { mutableStateOf<List<Contact>>(emptyList()) }
    val auth = FirebaseAuth.getInstance()
    val userRepository = remember { UserRepository() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showDeleteDialog by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<Contact?>(null) }

    DisposableEffect(auth.currentUser?.uid) {
        val currentUser = auth.currentUser
        if (currentUser == null) return@DisposableEffect onDispose {}

        val db = FirebaseFirestore.getInstance()
        val contactsRef = db.collection("users").document(currentUser.uid).collection("contacts")

        val listener = contactsRef.addSnapshotListener { contactsSnapshot, error ->
            if (error != null || contactsSnapshot == null) {
                contactList = emptyList()
                return@addSnapshotListener
            }
            contactList = contactsSnapshot.toObjects(Contact::class.java)
        }

        onDispose {
            listener.remove()
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Selecionar Contato") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                HeaderOption(icon = Icons.Filled.GroupAdd, text = "Novo grupo", onClick = { navController.navigate("createGroup") })
                HeaderOption(icon = Icons.Filled.PersonAdd, text = "Novo contato", onClick = { navController.navigate("addContact") })
            }

            items(contactList) { contact ->
                ContactItem(
                    contact = contact,
                    onClick = {
                        if (contact.uid.isNotEmpty() && contact.customName.isNotEmpty()) {
                            navController.navigate("chat/${contact.customName}/${contact.uid}?phone=${contact.phoneNumber}") {
                                popUpTo("home")
                            }
                        } else {
                            Toast.makeText(context, "Dados do contato incompletos.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDeleteClick = {
                        contactToDelete = contact
                        showDeleteDialog = true
                    }
                )
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Apagar Contato") },
                text = { Text("Tem a certeza de que quer apagar ${contactToDelete?.customName} da sua lista de contatos?") },
                confirmButton = {
                    Button(
                        onClick = {
                            contactToDelete?.let { contact ->
                                if (contact.uid.isNotEmpty()) {
                                    coroutineScope.launch {
                                        val result = userRepository.deleteContact(contact.uid)
                                        if (result.isSuccess) {
                                            Toast.makeText(context, "Contato apagado.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val error = result.exceptionOrNull()?.message
                                            Toast.makeText(context, "Erro ao apagar: $error", Toast.LENGTH_LONG).show()
                                        }
                                        showDeleteDialog = false
                                        contactToDelete = null
                                    }
                                } else {
                                    Toast.makeText(context, "Erro: ID do contato inválido.", Toast.LENGTH_SHORT).show()
                                    showDeleteDialog = false
                                    contactToDelete = null
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Apagar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun HeaderOption(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = text, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ContactItem(
    contact: Contact,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
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
            if (contact.profilePictureUrl != null && contact.profilePictureUrl.isNotEmpty()) {
                GlideImage(
                    model = contact.profilePictureUrl,
                    contentDescription = "Foto de Perfil de ${contact.customName}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Sem Foto de Perfil",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = contact.customName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))

        IconButton(onClick = onDeleteClick) {
            Icon(Icons.Filled.Delete, contentDescription = "Apagar Contato", tint = Color.Gray)
        }
    }
    Divider(modifier = Modifier.padding(start = 82.dp))
}