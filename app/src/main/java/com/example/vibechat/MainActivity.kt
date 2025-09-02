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
import com.example.vibechat.utils.PresenceManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.firestoreSettings
import com.google.firebase.ktx.Firebase

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

        // Ativa a persistência do Firestore (a do Realtime Database está na classe VibeChatApp)
        try {
            val firestore = Firebase.firestore
            val settings = firestoreSettings {
                isPersistenceEnabled = true
            }
            firestore.firestoreSettings = settings
        } catch (e: Exception) {
            // Ignorar erro se já estiver ativado
        }

        // A inicialização do PresenceManager é importante para preparar o onDisconnect
        PresenceManager.initialize()

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



                        composable(
                            "groupDetails/{groupId}",
                            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
                            GroupDetailsScreen(navController = navController, groupId = groupId)
                        }

                        composable("createGroup") {
                            CreateGroupScreen(navController = navController)
                        }

                        composable(
                            "addGroupMembers/{groupId}",
                            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
                            AddGroupMembersScreen(navController = navController, groupId = groupId)
                        }

                        composable("profile") {
                            ProfileScreen(navController = navController)
                        }

                        composable(
                            "contactProfile/{userId}",
                            arguments = listOf(navArgument("userId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId") ?: ""
                            ContactProfileScreen(navController = navController, userId = userId)
                        }

                        composable(
                            "chat/{name}/{uid}?phone={phone}&isGroup={isGroup}",
                            arguments = listOf(
                                navArgument("phone") {
                                    type = NavType.StringType
                                    nullable = true
                                },
                                navArgument("isGroup") {
                                    type = NavType.BoolType
                                    defaultValue = false
                                }
                            )
                        ) { backStackEntry ->
                            val name = backStackEntry.arguments?.getString("name") ?: ""
                            val uid = backStackEntry.arguments?.getString("uid") ?: ""
                            val phone = backStackEntry.arguments?.getString("phone")
                            val isGroup = backStackEntry.arguments?.getBoolean("isGroup") ?: false

                            ChatScreen(
                                navController = navController,
                                name = name,
                                chatId = uid,
                                receiverPhone = phone ?: "",
                                isGroup = isGroup
                            )
                        }
                    }

                    val partnerUid = intent.getStringExtra("partnerUid")
                    val partnerName = intent.getStringExtra("partnerName")
                    val partnerPhone = intent.getStringExtra("partnerPhone")

                    if (partnerUid != null && partnerName != null && partnerPhone != null) {
                        LaunchedEffect(Unit) {
                            navController.navigate("chat/$partnerName/$partnerUid?phone=$partnerPhone")
                            intent.removeExtra("partnerUid")
                        }
                    }
                }
            }
        }
    }

}