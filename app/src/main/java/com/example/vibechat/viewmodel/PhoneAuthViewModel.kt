package com.example.vibechat.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.example.vibechat.repository.AuthRepository
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PhoneAuthViewModel : ViewModel() {
    private val authRepository = AuthRepository()

    private val _uiState = MutableStateFlow<PhoneAuthUiState>(PhoneAuthUiState.Idle)
    val uiState = _uiState.asStateFlow()

    // 👇 REMOVIDO o parâmetro navController daqui
    fun startPhoneNumberVerification(activity: Activity, phoneNumber: String) {
        _uiState.value = PhoneAuthUiState.Loading

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Lógica para login automático se desejar
                _uiState.value = PhoneAuthUiState.Idle
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _uiState.value = PhoneAuthUiState.Error(e.message ?: "Ocorreu um erro desconhecido.")
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                // 👇 AQUI ESTÁ A MUDANÇA PRINCIPAL
                // Em vez de navegar, atualize o estado com os dados necessários
                _uiState.value = PhoneAuthUiState.CodeSent(verificationId, phoneNumber)
            }
        }
        // A 'activity' ainda é necessária aqui por causa do SDK do Firebase
        authRepository.verifyPhoneNumber(activity, phoneNumber, callbacks)
    }

    fun resetState() {
        _uiState.value = PhoneAuthUiState.Idle
    }
}


sealed class PhoneAuthUiState {
    object Idle : PhoneAuthUiState()
    object Loading : PhoneAuthUiState()
    data class Error(val message: String) : PhoneAuthUiState()
    data class CodeSent(val verificationId: String, val phoneNumber: String) : PhoneAuthUiState()
}
