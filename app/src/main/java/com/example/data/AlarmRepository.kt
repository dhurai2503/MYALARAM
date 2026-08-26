package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlarmRepository(
    private val alarmDao: AlarmDao,
    private val securityDao: SecurityDao
) {
    val allAlarms: Flow<List<Alarm>> = alarmDao.getAllAlarms()

    val securitySetting: Flow<SecuritySetting?> = securityDao.getSecuritySetting()

    suspend fun getAlarmById(id: Int): Alarm? {
        return alarmDao.getAlarmById(id)
    }

    suspend fun insertAlarm(alarm: Alarm): Long {
        return alarmDao.insertAlarm(alarm)
    }

    suspend fun updateAlarm(alarm: Alarm) {
        alarmDao.updateAlarm(alarm)
    }

    suspend fun deleteAlarm(alarm: Alarm) {
        alarmDao.deleteAlarm(alarm)
    }

    suspend fun getSecuritySettingDirect(): SecuritySetting {
        return securityDao.getSecuritySettingDirect() ?: SecuritySetting()
    }

    suspend fun saveSecuritySetting(setting: SecuritySetting) {
        securityDao.insertOrUpdateSecuritySetting(setting)
    }
}
