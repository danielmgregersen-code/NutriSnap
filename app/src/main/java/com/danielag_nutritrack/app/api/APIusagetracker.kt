package com.danielag_nutritrack.app.data

import androidx.room.*
import java.util.Date

@Entity(tableName = "api_usage")
data class ApiUsage(
    @PrimaryKey val date: Date, // Date without time (normalized to midnight)
    val callCount: Int = 0
)

@Dao
interface ApiUsageDao {
    @Query("SELECT * FROM api_usage WHERE DATE(date/1000, 'unixepoch', 'localtime') = DATE(:date/1000, 'unixepoch', 'localtime') LIMIT 1")
    suspend fun getUsageForDate(date: Long): ApiUsage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(usage: ApiUsage)

    @Query("DELETE FROM api_usage WHERE date < :cutoffDate")
    suspend fun deleteOldRecords(cutoffDate: Long)
}