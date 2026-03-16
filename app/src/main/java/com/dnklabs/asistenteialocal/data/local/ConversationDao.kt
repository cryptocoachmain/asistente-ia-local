package com.dnklabs.asistenteialocal.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAll(): LiveData<List<ConversationEntity>>
    
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<ConversationEntity>>
    
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity): Long
    
    @Delete
    suspend fun delete(conversation: ConversationEntity)
    
    @Query("UPDATE conversations SET title = :newTitle WHERE id = :id")
    suspend fun updateTitle(id: String, newTitle: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}
