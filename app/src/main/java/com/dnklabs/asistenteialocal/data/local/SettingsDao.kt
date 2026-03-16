package com.dnklabs.asistenteialocal.data.local

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface SettingsDao {
    
    @Query("SELECT * FROM settings WHERE id = 1")
    fun getSettings(): LiveData<SettingsEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSettings(settings: SettingsEntity)
}
