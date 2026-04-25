package com.danielag_nutritrack.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromMealCategory(value: MealCategory): String {
        return value.name
    }

    @TypeConverter
    fun toMealCategory(value: String): MealCategory {
        return MealCategory.valueOf(value)
    }

    @TypeConverter
    fun fromGender(value: Gender): String {
        return value.name
    }

    @TypeConverter
    fun toGender(value: String): Gender {
        return Gender.valueOf(value)
    }

    @TypeConverter
    fun fromActivityLevel(value: ActivityLevel): String {
        return value.name
    }

    @TypeConverter
    fun toActivityLevel(value: String): ActivityLevel {
        return ActivityLevel.valueOf(value)
    }

    @TypeConverter
    fun fromGoal(value: Goal): String {
        return value.name
    }

    @TypeConverter
    fun toGoal(value: String): Goal {
        return Goal.valueOf(value)
    }

    @TypeConverter
    fun fromWeightChangeRate(value: WeightChangeRate): String {
        return value.name
    }

    @TypeConverter
    fun toWeightChangeRate(value: String): WeightChangeRate {
        return try {
            WeightChangeRate.valueOf(value)
        } catch (e: IllegalArgumentException) {
            WeightChangeRate.RATE_050 // Default fallback
        }
    }
}

@Dao
interface FoodLogDao {
    @Query("SELECT * FROM food_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<FoodLog>>

    @Query("" +
            "SELECT * FROM food_logs " +
            "WHERE DATE(timestamp/1000, 'unixepoch', 'localtime') = DATE(:date/1000, 'unixepoch', 'localtime') " +
            "ORDER BY timestamp DESC")
    fun getLogsForDate(date: Long): Flow<List<FoodLog>>

    @Insert
    suspend fun insert(log: FoodLog): Long

    @Delete
    suspend fun delete(log: FoodLog)

    @Update
    suspend fun update(log: FoodLog)
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getProfile(): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: UserProfile)
}

@Dao
interface DailyActivityDao {
    @Query("" +
            "SELECT * FROM daily_activity " +
            "WHERE DATE(date/1000, 'unixepoch', 'localtime') = DATE(:date/1000, 'unixepoch', 'localtime') LIMIT 1")
    fun getActivityForDate(date: Long): Flow<DailyActivity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(activity: DailyActivity)

    @Query("" +
            "SELECT * FROM daily_activity " +
            "WHERE DATE(date/1000, 'unixepoch', 'localtime') = DATE(:date/1000, 'unixepoch', 'localtime') LIMIT 1")
    suspend fun getActivityForDateSuspend(date: Long): DailyActivity?

    @Query("SELECT * FROM daily_activity ORDER BY date DESC LIMIT 30")
    fun getRecentActivities(): Flow<List<DailyActivity>>

    @Query("SELECT * FROM daily_activity WHERE weight IS NOT NULL ORDER BY date DESC LIMIT 30")
    fun getWeightHistory(): Flow<List<DailyActivity>>

    @Query("SELECT * FROM daily_activity")
    fun getAllActivities(): Flow<List<DailyActivity>>
}

@Dao
interface ExerciseLogDao {
    @Query("SELECT * FROM exercise_logs ORDER BY timestamp DESC")
    fun getAllExercises(): Flow<List<ExerciseLog>>

    @Query("" +
            "SELECT * FROM exercise_logs " +
            "WHERE DATE(timestamp/1000, 'unixepoch', 'localtime') = DATE(:date/1000, 'unixepoch', 'localtime') " +
            "ORDER BY timestamp DESC")
    fun getExercisesForDate(date: Long): Flow<List<ExerciseLog>>

    @Insert
    suspend fun insert(exercise: ExerciseLog): Long

    @Delete
    suspend fun delete(exercise: ExerciseLog)

    @Update
    suspend fun update(exercise: ExerciseLog)

    @Query(
        "DELETE FROM exercise_logs " +
        "WHERE DATE(timestamp/1000, 'unixepoch', 'localtime') = DATE(:date/1000, 'unixepoch', 'localtime') " +
        "AND notes LIKE 'intervals:%'"
    )
    suspend fun deleteIntervalsExercisesForDate(date: Long)
}

// Migration from version 7 to 8: Add components column to food_logs
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE food_logs ADD COLUMN components TEXT")
    }
}

// Migration from version 8 to 9: Add hrv and restingHR columns to daily_activity
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE daily_activity ADD COLUMN hrv REAL")
        database.execSQL("ALTER TABLE daily_activity ADD COLUMN restingHR INTEGER")
    }
}

@Dao
interface FavoriteMealDao {
    @Query("SELECT * FROM favorite_meals ORDER BY name ASC")
    fun getAllFavorites(): Flow<List<FavoriteMeal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteMeal): Long

    @Delete
    suspend fun delete(favorite: FavoriteMeal)
}

// Migration from version 9 to 10: Add intervals.icu credentials to user_profile
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE user_profile ADD COLUMN intervalsApiKey TEXT")
        database.execSQL("ALTER TABLE user_profile ADD COLUMN intervalsAthleteId TEXT")
    }
}

// Migration from version 10 to 11: Add favorite_meals table
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS favorite_meals (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "name TEXT NOT NULL, " +
            "calories REAL NOT NULL, " +
            "protein REAL NOT NULL, " +
            "carbs REAL NOT NULL, " +
            "fats REAL NOT NULL, " +
            "category TEXT NOT NULL, " +
            "notes TEXT)"
        )
    }
}

@Database(
    entities = [FoodLog::class, UserProfile::class, DailyActivity::class, ExerciseLog::class, ApiUsage::class, FavoriteMeal::class],
    version = 11,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodLogDao(): FoodLogDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun dailyActivityDao(): DailyActivityDao
    abstract fun exerciseLogDao(): ExerciseLogDao
    abstract fun apiUsageDao(): ApiUsageDao
    abstract fun favoriteMealDao(): FavoriteMealDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nutritrack_database"
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}