package com.example.vibechat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.vibechat.ui.screens.*
import com.example.vibechat.ui.theme.VibeChatTheme
import com.example.vibechat.utils.PresenceManager // <-- ADICIONADO
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Lógica opcional se a permissão for negada
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askNotificationPermission()

        // Inicializa o gerenciador de presença assim que o app abre
        PresenceManager.initialize() // <-- ADICIONADO

        setContent {
            VibeChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val auth = FirebaseAuth.getInstance()

                    val startDestination = if (auth.currentUser != null) "home" else "phoneLogin"

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("phoneLogin") {
                            PhoneLoginScreen(navController = navController)
                        }
                        composable("enterPhoneNumber") {
                            EnterPhoneNumberScreen(navController = navController)
                        }
                        composable(
                            "otpVerification/{verificationId}/{phoneNumber}",
                            arguments = listOf(
                                navArgument("verificationId") { type = NavType.StringType },
                                navArgument("phoneNumber") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val verificationId = backStackEntry.arguments?.getString("verificationId") ?: ""
                            val phoneNumber = backStackEntry.arguments?.getString("phoneNumber") ?: ""
                            OtpVerificationScreen(
                                navController = navController,
                                verificationId = verificationId,
                                phoneNumber = phoneNumber
                            )
                        }
                        composable("createProfile") {
                            CreateProfileScreen(navController = navController)
                        }
                        composable("home") {
                            HomeScreen(navController = navController)
                        }
                        composable(
                            "addContact?phone={phone}",
                            arguments = listOf(navArgument("phone") {
                                type = NavType.StringType
                                nullable = true
                            })
                        ) { backStackEntry ->
                            AddContactScreen(
                                navController = navController,
                                prefilledPhone = backStackEntry.arguments?.getString("phone")
                            )
                        }
                        composable("selectContact") {
                            SelectContactScreen(navController = navController)
                        }
                        composable("profile") {
                            ProfileScreen(navController = navController)
                        }
                        composable(
                            "chat/{name}/{uid}?phone={phone}",
                            arguments = listOf(navArgument("phone") {
                                type = NavType.StringType
                                nullable = true
                            })
                        ) { backStackEntry ->
                            val name = backStackEntry.arguments?.getString("name") ?: ""
                            val uid = backStackEntry.arguments?.getString("uid") ?: ""
                            val phone = backStackEntry.arguments?.getString("phone")
                            ChatScreen(navController = navController, name = name, receiverUid = uid, receiverPhone = phone ?: "")
                        }
                    }

                    // Lógica para lidar com a abertura da app a partir de uma notificação
                    val partnerUid = intent.getStringExtra("partnerUid")
                    val partnerName = intent.getStringExtra("partnerName")
                    val partnerPhone = intent.getStringExtra("partnerPhone")

                    if (partnerUid != null && partnerName != null && partnerPhone != null) {
                        LaunchedEffect(Unit) {
                            navController.navigate("chat/$partnerName/$partnerUid?phone=$partnerPhone")
                            // Limpa os extras para não navegar novamente em caso de recriação da Activity
                            intent.removeExtra("partnerUid")
                        }
                    }
                }
            }
        }
    }

    // Define o usuário como offline quando o app é completamente fechado
    override fun onDestroy() { // <-- ADICIONADO
        super.onDestroy()
        PresenceManager.goOffline()
    }
}