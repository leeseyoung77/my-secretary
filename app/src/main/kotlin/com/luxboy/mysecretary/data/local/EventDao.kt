package com.luxboy.mysecretary.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query("SELECT * FROM events ORDER BY startEpochMillis ASC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query(
        """
        SELECT * FROM events
        WHERE rrule IS NULL
          AND startEpochMillis < :rangeEndExclusive
          AND endEpochMillis > :rangeStart
        ORDER BY startEpochMillis ASC
        """
    )
    fun observeNonRecurringInRange(rangeStart: Long, rangeEndExclusive: Long): Flow<List<EventEntity>>

    @Query(
        """
        SELECT * FROM events
        WHERE rrule IS NULL
          AND startEpochMillis < :rangeEndExclusive
          AND endEpochMillis > :rangeStart
        ORDER BY startEpochMillis ASC
        """
    )
    suspend fun getNonRecurringInRange(rangeStart: Long, rangeEndExclusive: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE rrule IS NOT NULL ORDER BY startEpochMillis ASC")
    fun observeAllRecurring(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun findById(id: Long): EventEntity?

    @Query("SELECT * FROM events WHERE startEpochMillis >= :nowMillis ORDER BY startEpochMillis ASC")
    suspend fun getUpcoming(nowMillis: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE rrule IS NOT NULL")
    suspend fun getAllRecurring(): List<EventEntity>

    @Query(
        """
        SELECT * FROM events
        WHERE title LIKE :query OR description LIKE :query
        ORDER BY startEpochMillis DESC
        LIMIT 200
        """
    )
    suspend fun search(query: String): List<EventEntity>

    @Query("SELECT * FROM events")
    suspend fun getAll(): List<EventEntity>

    @Query("SELECT id FROM events")
    suspend fun getAllIds(): List<Long>

    @Query("DELETE FROM events")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)
}
