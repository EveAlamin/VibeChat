package com.example.vibechat.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.delay

@Composable
fun OtpVerificationScreen(
    navController: NavController,
    verificationId: String,
    phoneNumber: String
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    var otpCode by remember { mutableStateOf(List(6) { "" }) }
    val focusRequesters = remember { List(6) { FocusRequester() } }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resendTimer by remember { mutableStateOf(30) }

    LaunchedEffect(resendTimer) {
        if (resendTimer > 0) {
            delay(1000)
            resendTimer--
        }
    }

    LaunchedEffect(Unit) {
        delay(500)
        focusRequesters[0].requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Confirmar $phoneNumber",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Digite o código de 6 dígitos enviado por SMS",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            otpCode.forEachIndexed { index, value ->
                OutlinedTextField(
                    value = value,
                    onValueChange = { input ->
                        if (input.length <= 1 && input.all { it.isDigit() }) {
                            val newOtpCode = otpCode.toMutableList()
                            if (input.isEmpty()) {
                                newOtpCode[index] = ""
                                if (index > 0) {
                                    focusRequesters[index - 1].requestFocus()
                                }
                            } else {
                                newOtpCode[index] = input
                                if (index < 5) {
                                    focusRequesters[index + 1].requestFocus()
                                }
                            }
                            otpCode = newOtpCode
                        }
                    },
                    modifier = Modifier
                        .width(50.dp)
                        .height(60.dp)
                        .focusRequester(focusRequesters[index]),
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        focusedContainerColor = Color.White.copy(alpha = 0.2f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage ?: "",
                color = Color.Red,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                isLoading = true
                errorMessage = null
                val code = otpCode.joinToString("")
                if (code.length == 6) {
                    val credential = PhoneAuthProvider.getCredential(verificationId, code)
                    auth.signInWithCredential(credential)
                        .addOnCompleteListener { task ->
                            isLoading = false
                            if (task.isSuccessful) {
                                val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false
                                if (isNewUser) {
                                    navController.navigate("createProfile") {
                                        popUpTo("phoneLogin") { inclusive = true }
                                    }
                                } else {
                                    navController.navigate("home") {
                                        popUpTo("phoneLogin") { inclusive = true }
                                    }
                                }
                            } else {
                                errorMessage = "Código inválido. Tente novamente."
                            }
                        }
                } else {
                    errorMessage = "Digite os 6 dígitos."
                    isLoading = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = otpCode.all { it.isNotEmpty() } && !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Verificar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = {
                resendTimer = 30
                Toast.makeText(context, "Novo código enviado!", Toast.LENGTH_SHORT).show()
            },
            enabled = resendTimer == 0
        ) {
            Text(
                if (resendTimer > 0) "Reenviar em $resendTimer s"
                else "Reenviar código",
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}