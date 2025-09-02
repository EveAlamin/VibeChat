package com.example.vibechat.utils

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

fun formatTimestamp(timestamp: Timestamp?): String {
    if (timestamp == null) return ""
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(timestamp.toDate())
}

// *** FUNÇÃO ATUALIZADA E CORRIGIDA ***
fun formatLastSeen(lastSeen: Long): String {
    if (lastSeen == 0L) return ""

    val currentTime = System.currentTimeMillis()
    val calendarLastSeen = Calendar.getInstance().apply { timeInMillis = lastSeen }
    val calendarNow = Calendar.getInstance().apply { timeInMillis = currentTime }

    // Esta função agora apenas formata a data/hora, sem tentar adivinhar se o utilizador está online.
    return when {
        // Se foi hoje
        calendarLastSeen.get(Calendar.DAY_OF_YEAR) == calendarNow.get(Calendar.DAY_OF_YEAR) &&
                calendarLastSeen.get(Calendar.YEAR) == calendarNow.get(Calendar.YEAR) -> {
            "visto por último hoje às ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastSeen))}"
        }

        // Se foi ontem
        calendarLastSeen.get(Calendar.DAY_OF_YEAR) == calendarNow.get(Calendar.DAY_OF_YEAR) - 1 &&
                calendarLastSeen.get(Calendar.YEAR) == calendarNow.get(Calendar.YEAR) -> {
            "visto por último ontem às ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastSeen))}"
        }

        // Caso contrário, mostra a data completa
        else -> {
            "visto por último em ${SimpleDateFormat("dd/MM/yy 'às' HH:mm", Locale.getDefault()).format(Date(lastSeen))}"
        }
    }
}