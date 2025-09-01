package com.example.vibechat.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.vibechat.repository.UserRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(navController: NavController, prefilledPhone: String?) {
    var phoneNumber by remember { mutableStateOf(prefilledPhone ?: "") }
    var customName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val userRepository = remember { UserRepository() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adicionar Novo Contato") },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Número de Telefone do Contato") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                label = { Text("Nome para o Contato") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (phoneNumber.isNotBlank() && customName.isNotBlank()) {
                        isLoading = true
                        coroutineScope.launch {
                            val result = userRepository.addContact(phoneNumber, customName)
                            result.onSuccess {
                                Toast.makeText(context, "Contato adicionado!", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                            result.onFailure {
                                Toast.makeText(context, "Erro: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Text("Salvar Contato", color = Color.White)
                }
            }
        }
    }
}