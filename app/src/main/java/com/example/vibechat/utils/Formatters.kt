package com.example.vibechat.utils

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

fun formatTimestamp(timestamp: Timestamp?): String {
    if (timestamp == null) return ""
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(timestamp.toDate())
}