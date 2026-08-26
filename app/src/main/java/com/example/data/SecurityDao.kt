package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityDao {
    @Query("SELECT * FROM security_settings WHERE id = 1")
    fun getSecuritySetting(): Flow<SecuritySetting?>

    @Query("SELECT * FROM security_settings WHERE id = 1")
    suspend fun getSecuritySettingDirect(): SecuritySetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSecuritySetting(setting: SecuritySetting)
}
