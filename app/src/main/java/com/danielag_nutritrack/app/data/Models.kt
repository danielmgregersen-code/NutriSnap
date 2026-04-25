package com.danielag_nutritrack.app.data

import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

@Entity(tableName = "food_logs")
data class FoodLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
    val category: MealCategory,
    val timestamp: Date,
    val imageUri: String? = null,
    val notes: String? = null,
    val components: String? = null  // JSON string of Map<String, FoodComponent>
)

// Data class for individual food components
data class FoodComponent(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
    val weight: Double? = null
)

// Helper functions for component serialization
fun Map<String, FoodComponent>.toJson(): String {
    return Gson().toJson(this)
}

fun String.toComponentsMap(): Map<String, FoodComponent>? {
    return try {
        val type = object : TypeToken<Map<String, FoodComponent>>() {}.type
        Gson().fromJson(this, type)
    } catch (e: Exception) {
        null
    }
}

enum class MealCategory {
    BREAKFAST, LUNCH, DINNER, SNACK, FRUIT, TRAINING
}

@Entity(tableName = "favorite_meals")
data class FavoriteMeal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
    val category: MealCategory,
    val notes: String? = null
)

@Entity(tableName = "exercise_logs")
data class ExerciseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseType: String,
    val caloriesBurned: Int,
    val timestamp: Date,
    val duration: Int? = null, // minutes
    val notes: String? = null
)

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val age: Int,
    val weight: Double,
    val height: Double,
    val gender: Gender,
    val activityLevel: ActivityLevel,
    val goal: Goal,
    val weightChangeRate: WeightChangeRate = WeightChangeRate.RATE_050, // Default 0.5 kg/week
    val waterGoal: Int = 2000, // Daily water goal in ml, default 2L
    val targetWeight: Double? = null, // Target weight in kg (optional)
    val intervalsApiKey: String? = null,
    val intervalsAthleteId: String? = null
)

enum class Gender { MALE, FEMALE }

enum class ActivityLevel {
    OFFICE_JOB,      // 1.0 - Sedentary, desk work
    PHYSICAL_JOB     // 1.5 - Active, physical labor
}

enum class Goal {
    LOSE_WEIGHT,
    MAINTAIN,
    GAIN_MUSCLE
}

enum class WeightChangeRate {
    RATE_025,  // 0.25 kg/week = 275 cal/day
    RATE_050,  // 0.50 kg/week = 550 cal/day
    RATE_075,  // 0.75 kg/week = 825 cal/day (warning: affects performance)
    RATE_100   // 1.00 kg/week = 1100 cal/day (warning: affects performance)
}

@Entity(tableName = "daily_activity")
data class DailyActivity(
    @PrimaryKey val date: Date,
    val steps: Int = 0,
    val exerciseCalories: Int = 0,
    val weight: Double? = null,
    val waterIntake: Int = 0, // ml
    val hrv: Double? = null,
    val restingHR: Int? = null
)