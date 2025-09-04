package com.example.vibechat.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.vibechat.data.Contact
import com.example.vibechat.data.Group
import com.example.vibechat.repository.GroupRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

import com.example.vibechat.VibeChatApp
import com.example.vibechat.data.local.entities.toDataContact
import com.example.vibechat.data.local.entities.toContactEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroupMembersScreen(navController: NavController, groupId: String) {
    val allContactsFromRoom by VibeChatApp.database.contactDao().getAllContacts().collectAsState(initial = emptyList())
    var availableContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var selectedContacts by remember { mutableStateOf<Set<Contact>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val groupRepository = remember { GroupRepository() }

    // MODIFICADO: Lógica para sincronizar contatos e buscar membros do grupo.
    LaunchedEffect(key1 = groupId) {
        val db = FirebaseFirestore.getInstance()
        val currentUserUid = auth.currentUser?.uid ?: return@LaunchedEffect

        // Listener para sincronizar os contatos do Firebase para o Room
        db.collection("users").document(currentUserUid).collection("contacts")
            .addSnapshotListener { contactsSnapshot, _ ->
                val contacts = contactsSnapshot?.toObjects(Contact::class.java) ?: emptyList()
                coroutineScope.launch {
                    VibeChatApp.database.contactDao().insertAllContacts(contacts.map { it.toContactEntity() })
                }
            }

        // O `LaunchedEffect` que observa a lista do Room para filtrar
        snapshotFlow { allContactsFromRoom }.collect { contacts ->
            db.collection("groups").document(groupId).get().addOnSuccessListener { groupDoc ->
                val group = groupDoc.toObject(Group::class.java)
                val currentMemberIds = group?.memberIds ?: emptyList()
                // Filtra a lista de contatos do Room para remover quem já é membro
                availableContacts = contacts
                    .filter { it.uid !in currentMemberIds }
                    .map { it.toDataContact() }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adicionar Participantes") },
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
                    if (selectedContacts.isNotEmpty()) {
                        isLoading = true
                        coroutineScope.launch {
                            val result = groupRepository.addMembersToGroup(groupId, selectedContacts.toList())
                            isLoading = false
                            if (result.isSuccess) {
                                Toast.makeText(context, "Membros adicionados!", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            } else {
                                Toast.makeText(context, "Erro: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.Check, contentDescription = "Adicionar", tint = Color.White)
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(top = 16.dp)) {
            items(availableContacts) { contact ->
                // Agora estamos passando o tipo correto (Contact) para o Composable
                ContactSelectionItem(
                    contact = contact,
                    isSelected = selectedContacts.contains(contact),
                    onSelectionChanged = {
                        selectedContacts = if (selectedContacts.contains(contact)) {
                            selectedContacts - contact
                        } else {
                            selectedContacts + contact
                        }
                    }
                )
            }
        }
    }
}