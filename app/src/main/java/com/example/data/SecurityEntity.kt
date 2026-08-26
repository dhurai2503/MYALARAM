package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "security_settings")
data class SecuritySetting(
    @PrimaryKey val id: Int = 1,
    val passcode: String = "1234567890" // Default 10-digit passcode
)
