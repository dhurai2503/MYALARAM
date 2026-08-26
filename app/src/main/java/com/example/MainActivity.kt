package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.data.AlarmRingState
import com.example.data.AlarmRepository
import com.example.data.AppDatabase
import com.example.ui.screens.AlarmRingingScreen
import com.example.ui.screens.MainAlarmScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AlarmViewModel
import com.example.viewmodel.AlarmViewModelFactory

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { AlarmRepository(database.alarmDao(), database.securityDao()) }
    private val viewModel: AlarmViewModel by viewModels {
        AlarmViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Show over lockscreen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS permission dynamically on Android 13+
        requestNotificationPermission()

        setContent {
            MyApplicationTheme {
                val isRinging by AlarmRingState.isRinging.collectAsState()
                val activeLabel by AlarmRingState.activeAlarmLabel.collectAsState()

                // Keep screen on while ringing
                if (isRinging) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }

                if (isRinging) {
                    AlarmRingingScreen(
                        viewModel = viewModel,
                        label = activeLabel
                    )
                } else {
                    MainAlarmScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (AlarmRingState.isRinging.value) {
            val keyCode = event.keyCode
            if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
                return true // Consume volume keys to stop user from reducing the alarm volume
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}
