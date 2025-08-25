package com.example.vibechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.vibechat.ui.screens.*
import com.example.vibechat.ui.theme.VibeChatTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                            EnterPhoneNumberScreen(activity = this@MainActivity, navController = navController)
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
                }
            }
        }
    }
}
