package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val label: String,
    val isEnabled: Boolean,
    val repeatDays: String, // List of days like "Mon,Tue" or "Once"
    val toneUri: String? = null // Holds the Uri string of local sound file/ringtone
) {
    val formattedTime: String
        get() {
            val amPm = if (hour >= 12) "PM" else "AM"
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            return String.format("%02d:%02d %s", displayHour, minute, amPm)
        }
}
