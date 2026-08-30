package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Alarm
import com.example.data.AlarmRepository
import com.example.data.AlarmRingState
import com.example.data.SecuritySetting
import com.example.receiver.AlarmScheduler
import com.example.service.AlarmService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(private val repository: AlarmRepository) : ViewModel() {

    // Alarms list
    val alarms: StateFlow<List<Alarm>> = repository.allAlarms
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Security passcode config (observes the DB)
    val securitySetting: StateFlow<SecuritySetting?> = repository.securitySetting
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Current digits pressed to unlock ringing alarm
    private val _enteredPasscode = MutableStateFlow("")
    val enteredPasscode: StateFlow<String> = _enteredPasscode.asStateFlow()

    // Keypad coordinates shuffling
    private val _keypadDigits = MutableStateFlow<List<Int>>(emptyList())
    val keypadDigits: StateFlow<List<Int>> = _keypadDigits.asStateFlow()

    // Unlock security state indicators
    private val _unlockStatusMessage = MutableStateFlow("")
    val unlockStatusMessage: StateFlow<String> = _unlockStatusMessage.asStateFlow()

    private val _isPasscodeIncorrect = MutableStateFlow(false)
    val isPasscodeIncorrect: StateFlow<Boolean> = _isPasscodeIncorrect.asStateFlow()

    private val _isDeactivatedSuccessfully = MutableStateFlow(false)
    val isDeactivatedSuccessfully: StateFlow<Boolean> = _isDeactivatedSuccessfully.asStateFlow()

    init {
        shuffleKeypad()
        // Make sure database is seeded with a default 10-digit passcode if empty
        viewModelScope.launch {
            val current = repository.getSecuritySettingDirect()
            // If empty, save the default 1234567890
            if (current.passcode == "1234567890" && current.id == 1) {
                repository.saveSecuritySetting(SecuritySetting())
            }
        }
    }

    fun shuffleKeypad() {
        _keypadDigits.value = (0..9).shuffled()
    }

    fun onDigitPressed(context: Context, digit: Int) {
        val current = _enteredPasscode.value
        if (current.length < 10) {
            val updated = current + digit
            _enteredPasscode.value = updated
            _isPasscodeIncorrect.value = false
            _unlockStatusMessage.value = "Entered: ${updated.length}/10 digits • Volume reduced by 60%"

            // Reduce alarm volume by 60% (to 40% playback level) as soon as the user enters the first digit and continue
            if (updated.isNotEmpty()) {
                AlarmRingState.setVolumeScale(0.4f)
            }

            // Keypad numbers must be shuffled AFTER EACH PRESS to ensure awake focus
            shuffleKeypad()

            if (updated.length == 10) {
                verifyAndDismiss(context, updated)
            }
        }
    }

    fun onBackspacePressed() {
        val current = _enteredPasscode.value
        if (current.isNotEmpty()) {
            val updated = current.dropLast(1)
            _enteredPasscode.value = updated
            _isPasscodeIncorrect.value = false
            _unlockStatusMessage.value = if (updated.isNotEmpty()) "Entered: ${updated.length}/10 digits • Volume reduced by 60%" else ""

            // If empty, restore to full volume, otherwise stay at 40%
            if (updated.isEmpty()) {
                AlarmRingState.setVolumeScale(1.0f)
            } else {
                AlarmRingState.setVolumeScale(0.4f)
            }

            shuffleKeypad() // Shuffle on backspace as well to increase wakefulness
        }
    }

    fun onClearPressed() {
        _enteredPasscode.value = ""
        _isPasscodeIncorrect.value = false
        _unlockStatusMessage.value = ""
        AlarmRingState.setVolumeScale(1.0f)
        shuffleKeypad()
    }

    private fun verifyAndDismiss(context: Context, code: String) {
        viewModelScope.launch {
            val targetSetting = repository.getSecuritySettingDirect()
            val correctCode = targetSetting.passcode
            if (code == correctCode) {
                // Correct passcode -> Show deactivation success animation state
                _unlockStatusMessage.value = "✔ Passcode Verified! Alarm Stopping..."
                _isPasscodeIncorrect.value = false
                _isDeactivatedSuccessfully.value = true
                
                // Allow the beautiful transition animation to run
                kotlinx.coroutines.delay(1800)
                
                AlarmService.stopAlarm(context)
                _isDeactivatedSuccessfully.value = false
                _enteredPasscode.value = ""
            } else {
                // Incorrect passcode -> Show vibrating error and reset
                _unlockStatusMessage.value = "❌ Incorrect Passcode! Try again."
                _isPasscodeIncorrect.value = true
                _enteredPasscode.value = ""
                AlarmRingState.setVolumeScale(1.0f)
            }
        }
    }

    // Core Alarm Scheduling Methods
    fun addAlarm(context: Context, hour: Int, minute: Int, label: String, repeatDays: String, toneUri: String?) {
        viewModelScope.launch {
            val newAlarm = Alarm(
                hour = hour,
                minute = minute,
                label = label.ifBlank { "Alarm" },
                isEnabled = true,
                repeatDays = repeatDays,
                toneUri = toneUri
            )
            val generatedId = repository.insertAlarm(newAlarm)
            val savedAlarm = newAlarm.copy(id = generatedId.toInt())

            // Schedule with Android AlarmManager
            AlarmScheduler.schedule(context, savedAlarm)
        }
    }

    fun editAlarm(context: Context, alarm: Alarm) {
        viewModelScope.launch {
            repository.updateAlarm(alarm)

            // Cancel any existing schedule for this ID first
            AlarmScheduler.cancel(context, alarm)

            // Re-schedule if enabled
            if (alarm.isEnabled) {
                AlarmScheduler.schedule(context, alarm)
            }
        }
    }

    fun toggleAlarm(context: Context, alarm: Alarm) {
        viewModelScope.launch {
            val updatedAlarm = alarm.copy(isEnabled = !alarm.isEnabled)
            repository.updateAlarm(updatedAlarm)

            if (updatedAlarm.isEnabled) {
                AlarmScheduler.schedule(context, updatedAlarm)
            } else {
                AlarmScheduler.cancel(context, updatedAlarm)
            }
        }
    }

    fun deleteAlarm(context: Context, alarm: Alarm) {
        viewModelScope.launch {
            AlarmScheduler.cancel(context, alarm)
            repository.deleteAlarm(alarm)
        }
    }

    // Passcode personalization update with mandatory previous PIN verification
    fun changePasscodeWithPreviousCheck(
        previousPasscode: String,
        newPasscode: String,
        confirmPasscode: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val currentActivePasscode = securitySetting.value?.passcode ?: "1234567890"

        if (previousPasscode.length != 10 || !previousPasscode.all { it.isDigit() }) {
            onComplete(false, "Previous PIN must be exactly 10 digits.")
            return
        }

        if (previousPasscode != currentActivePasscode) {
            onComplete(false, "Incorrect Previous PIN! You cannot change the PIN without entering the correct previous PIN.")
            return
        }

        if (newPasscode.length != 10 || !newPasscode.all { it.isDigit() }) {
            onComplete(false, "New PIN must be exactly 10 digits.")
            return
        }

        if (newPasscode != confirmPasscode) {
            onComplete(false, "New PIN and Confirmation PIN do not match.")
            return
        }

        if (newPasscode == currentActivePasscode) {
            onComplete(false, "New PIN must be different from the previous PIN.")
            return
        }

        viewModelScope.launch {
            try {
                repository.saveSecuritySetting(SecuritySetting(passcode = newPasscode))
                onComplete(true, "Security PIN changed successfully!")
            } catch (e: Exception) {
                onComplete(false, "Failed to persist PIN in database.")
            }
        }
    }

    fun updatePasscode(passcode: String, onComplete: (Boolean, String) -> Unit) {
        if (passcode.length != 10 || !passcode.all { it.isDigit() }) {
            onComplete(false, "Password must be exactly 10 digits.")
            return
        }

        viewModelScope.launch {
            try {
                repository.saveSecuritySetting(SecuritySetting(passcode = passcode))
                onComplete(true, "Password updated successfully!")
            } catch (e: Exception) {
                onComplete(false, "Failed to persist passcode in database.")
            }
        }
    }

    // Utility simulator to test triggers instantly in UI
    fun triggerInstantAlarmTest(context: Context, delaySeconds: Int) {
        viewModelScope.launch {
            val testAlarm = Alarm(
                id = -99, // Specific test ID
                hour = 0,
                minute = 0,
                label = "Immediate Secure Wake Test",
                isEnabled = true,
                repeatDays = "Once"
            )
            // Schedule matching time
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = android.content.Intent(context, com.example.receiver.AlarmReceiver::class.java).apply {
                putExtra("ALARM_ID", testAlarm.id)
                putExtra("ALARM_LABEL", testAlarm.label)
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                testAlarm.id,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val triggerTime = System.currentTimeMillis() + (delaySeconds * 1000)
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }
}

class AlarmViewModelFactory(private val repository: AlarmRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AlarmViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AlarmViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
