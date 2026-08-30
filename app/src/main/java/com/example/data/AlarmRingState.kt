package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AlarmRingState {
    private val _isRinging = MutableStateFlow(false)
    val isRinging: StateFlow<Boolean> = _isRinging

    private val _activeAlarmId = MutableStateFlow<Int?>(null)
    val activeAlarmId: StateFlow<Int?> = _activeAlarmId

    private val _activeAlarmLabel = MutableStateFlow("")
    val activeAlarmLabel: StateFlow<String> = _activeAlarmLabel

    // Volume level multiplier: 1.0f (100%) by default, reduces to 0.4f (reduced by 60%) when entering passcode
    private val _volumeScale = MutableStateFlow(1.0f)
    val volumeScale: StateFlow<Float> = _volumeScale

    fun setVolumeScale(scale: Float) {
        _volumeScale.value = scale
    }

    fun startRinging(alarmId: Int, label: String) {
        _activeAlarmId.value = alarmId
        _activeAlarmLabel.value = label
        _volumeScale.value = 1.0f
        _isRinging.value = true
    }

    fun stopRinging() {
        _isRinging.value = false
        _activeAlarmId.value = null
        _activeAlarmLabel.value = ""
        _volumeScale.value = 1.0f
    }
}
