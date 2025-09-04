package com.example.vibechat.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.vibechat.data.Contact
import com.example.vibechat.repository.GroupRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

// NOVAS IMPORTAÇÕES NECESSÁRIAS PARA O ROOM
import com.example.vibechat.VibeChatApp
import com.example.vibechat.data.local.entities.toDataContact
import com.example.vibechat.data.local.entities.toContactEntity


@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun CreateGroupScreen(navController: NavController) {
    var groupName by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    // MODIFICADO: A lista de contatos é agora lida do Room
    val contactList by VibeChatApp.database.contactDao().getAllContacts().collectAsState(initial = emptyList())
    var selectedContacts by remember { mutableStateOf<Set<Contact>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val groupRepository = remember { GroupRepository() }

    // MODIFICADO: O DisposableEffect agora sincroniza dados do Firebase para o Room
    DisposableEffect(auth.currentUser?.uid) {
        val currentUser = auth.currentUser ?: return@DisposableEffect onDispose {}

        val db = FirebaseFirestore.getInstance()
        val contactsRef = db.collection("users").document(currentUser.uid).collection("contacts")

        val listener = contactsRef.addSnapshotListener { contactsSnapshot, error ->
            if (error != null || contactsSnapshot == null) {
                return@addSnapshotListener
            }
            val contacts = contactsSnapshot.toObjects(Contact::class.java)
            coroutineScope.launch {
                VibeChatApp.database.contactDao().insertAllContacts(contacts.map { it.toContactEntity() })
            }
        }
        onDispose { listener.remove() }
    }


    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> imageUri = uri }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Novo Grupo") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (groupName.isBlank()) {
                        Toast.makeText(context, "O nome do grupo não pode estar vazio.", Toast.LENGTH_SHORT).show()
                        return@FloatingActionButton
                    }
                    if (selectedContacts.isEmpty()) {
                        Toast.makeText(context, "Selecione pelo menos um participante.", Toast.LENGTH_SHORT).show()
                        return@FloatingActionButton
                    }

                    isLoading = true
                    coroutineScope.launch {
                        val result = groupRepository.createGroup(
                            name = groupName,
                            imageUri = imageUri,
                            members = selectedContacts.toList()
                        )

                        result.onSuccess {
                            isLoading = false
                            Toast.makeText(context, "Grupo criado com sucesso!", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }

                        result.onFailure { error ->
                            isLoading = false
                            Toast.makeText(context, "Erro: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.Check, contentDescription = "Criar Grupo", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        GlideImage(
                            model = imageUri,
                            contentDescription = "Foto do Grupo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "Adicionar Foto", tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Nome do grupo") },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                )
            }

            Text(
                text = "Selecione os participantes",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn {
                // MODIFICADO: A lista de contatos vem do Room
                items(contactList) { contactEntity ->
                    val contact = contactEntity.toDataContact()
                    ContactSelectionItem(
                        contact = contact,
                        isSelected = selectedContacts.contains(contact),
                        onSelectionChanged = {
                            if (!isLoading) {
                                selectedContacts = if (selectedContacts.contains(contact)) {
                                    selectedContacts - contact
                                } else {
                                    selectedContacts + contact
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
// As funções ContactSelectionItem e outras permanecem as mesmas
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ContactSelectionItem(
    contact: Contact,
    isSelected: Boolean,
    onSelectionChanged: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelectionChanged)
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (!contact.profilePictureUrl.isNullOrEmpty()) {
                    GlideImage(
                        model = contact.profilePictureUrl,
                        contentDescription = "Foto de ${contact.customName}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Sem Foto",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selecionado",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).background(Color.White, CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = contact.customName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
    Divider(modifier = Modifier.padding(start = 82.dp))
}