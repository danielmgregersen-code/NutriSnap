package com.danielag_nutritrack.app.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.danielag_nutritrack.app.api.*
import com.danielag_nutritrack.app.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class IntervalsSync(
    val steps: Int?,
    val weight: Double?,
    val hrv: Double?,
    val restingHR: Int?,
    val activityCalories: Int
)

class NutritionRepository(
    private val foodLogDao: FoodLogDao,
    private val userProfileDao: UserProfileDao,
    private val dailyActivityDao: DailyActivityDao,
    private val exerciseLogDao: ExerciseLogDao,
    private val apiUsageDao: ApiUsageDao,
    private val openAIService: OpenAIService,
    private val apiKey: String,
    private val intervalsService: IntervalsService? = null,
    private val intervalsAthleteId: String = ""
) {
    companion object {
        const val DAILY_API_LIMIT = 50 // Configurable daily limit
        private const val TAG = "NutritionRepository"
    }

    // Food Logs
    fun getAllLogs(): Flow<List<FoodLog>> = foodLogDao.getAllLogs()

    fun getLogsForDate(date: Date): Flow<List<FoodLog>> =
        foodLogDao.getLogsForDate(date.time)

    suspend fun insertLog(log: FoodLog) = foodLogDao.insert(log)

    suspend fun updateLog(log: FoodLog) = foodLogDao.update(log)

    suspend fun deleteLog(log: FoodLog) = foodLogDao.delete(log)

    // Exercise Logs
    fun getAllExercises(): Flow<List<ExerciseLog>> = exerciseLogDao.getAllExercises()

    fun getExercisesForDate(date: Date): Flow<List<ExerciseLog>> =
        exerciseLogDao.getExercisesForDate(date.time)

    suspend fun insertExercise(exercise: ExerciseLog) = exerciseLogDao.insert(exercise)

    suspend fun updateExercise(exercise: ExerciseLog) = exerciseLogDao.update(exercise)

    suspend fun deleteExercise(exercise: ExerciseLog) = exerciseLogDao.delete(exercise)

    // User Profile
    fun getUserProfile(): Flow<UserProfile?> = userProfileDao.getProfile()

    suspend fun saveUserProfile(profile: UserProfile) =
        userProfileDao.insertOrUpdate(profile)

    // Daily Activity
    fun getActivityForDate(date: Date): Flow<DailyActivity?> =
        dailyActivityDao.getActivityForDate(date.time)

    suspend fun saveActivity(activity: DailyActivity) =
        dailyActivityDao.insertOrUpdate(activity)

    fun getRecentActivities(): Flow<List<DailyActivity>> =
        dailyActivityDao.getRecentActivities()

    fun getWeightHistory(): Flow<List<DailyActivity>> =
        dailyActivityDao.getWeightHistory()

    // API Usage Tracking
    private fun getTodayDate(): Date {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.time
    }

    suspend fun getRemainingApiCalls(): Int {
        val today = getTodayDate()
        val usage = apiUsageDao.getUsageForDate(today.time)
        val used = usage?.callCount ?: 0
        return (DAILY_API_LIMIT - used).coerceAtLeast(0)
    }

    suspend fun canMakeApiCall(): Boolean {
        return getRemainingApiCalls() > 0
    }

    private suspend fun incrementApiUsage() {
        val today = getTodayDate()
        val usage = apiUsageDao.getUsageForDate(today.time)
        val newCount = (usage?.callCount ?: 0) + 1
        apiUsageDao.insertOrUpdate(ApiUsage(date = today, callCount = newCount))

        // Clean up old records (older than 7 days)
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -7)
        apiUsageDao.deleteOldRecords(calendar.timeInMillis)
    }

    // Stores the last conversation so the ViewModel can continue the thread for corrections
    var lastConversationMessages: List<Message> = emptyList()
        private set

    // OpenAI Integration
    fun calculateBMR(profile: UserProfile): Double {
        val weightKg = profile.weight
        val heightCm = profile.height
        val age = profile.age

        return when (profile.gender) {
            Gender.MALE -> (10 * weightKg) + (6.25 * heightCm) - (5 * age) + 5
            Gender.FEMALE -> (10 * weightKg) + (6.25 * heightCm) - (5 * age) - 161
        }
    }

    // Calculate TDEE (Total Daily Energy Expenditure)
    // Garmin-style: BMR + fixed base active calories + step bonus above 5,000 steps threshold
    fun calculateTDEE(profile: UserProfile, steps: Int = 0): Double {
        val bmr = calculateBMR(profile)
        val baseActiveCalories = 350.0
        val stepCalories = if (steps > 5000) steps * 0.042 else 0.0
        return bmr + baseActiveCalories + stepCalories
    }

    // Calculate target calories based on goal and rate
    fun calculateTargetCalories(profile: UserProfile, steps: Int = 0): Double {
        val tdee = calculateTDEE(profile, steps)

        return when (profile.goal) {
            Goal.MAINTAIN -> tdee
            Goal.LOSE_WEIGHT -> {
                val deficit = when (profile.weightChangeRate) {
                    WeightChangeRate.RATE_025 -> 275  // 0.25 kg/week
                    WeightChangeRate.RATE_050 -> 550  // 0.50 kg/week
                    WeightChangeRate.RATE_075 -> 825  // 0.75 kg/week
                    WeightChangeRate.RATE_100 -> 1100 // 1.00 kg/week
                }
                tdee - deficit
            }
            Goal.GAIN_MUSCLE -> {
                val surplus = when (profile.weightChangeRate) {
                    WeightChangeRate.RATE_025 -> 275  // 0.25 kg/week
                    WeightChangeRate.RATE_050 -> 550  // 0.50 kg/week
                    WeightChangeRate.RATE_075 -> 825  // 0.75 kg/week
                    WeightChangeRate.RATE_100 -> 1100 // 1.00 kg/week
                }
                tdee + surplus
            }
        }
    }

    // OpenAI Integration - Text Analysis with Components
    suspend fun analyzeTextFood(description: String): Result<NutritionInfo> {
        return try {
            // Check API limit
            if (!canMakeApiCall()) {
                return Result.failure(Exception("Daily API limit reached ($DAILY_API_LIMIT calls). Resets at midnight."))
            }

            // Ensure proper UTF-8 encoding for Danish characters
            val encodedDescription = description.trim()

            val prompt = """
                Analyze this food description and provide nutritional information with component breakdown in JSON format:
                "$encodedDescription"
                
                Respond ONLY with valid JSON in this exact format:
                {
                  "name": "brief food name",
                  "description": "detailed description with portion estimates",
                  "calories": total_calories_number,
                  "protein": total_protein_grams,
                  "carbs": total_carbs_grams,
                  "fats": total_fats_grams,
                  "weight": total_weight_in_grams
                  "category": "BREAKFAST|LUNCH|DINNER|SNACK|FRUIT|TRAINING",
                  "confidence": confidence_score_1_to_10,
                  "components": {
                    "ingredient name (amount)": {
                      "calories": number,
                      "protein": number,
                      "carbs": number,
                      "fats": number,
                      "weight": weight_in_grams
                    }
                  }
                }
                
                CRITICAL REQUIREMENTS:
                - Include NUMERIC weight in grams for each component
                - Include serving size description AND weight (e.g., "tortilla (1 medium, 60g)")
                - The weight field should be the numeric value only (e.g., 60, not "60g") - Break down the meal into individual ingredients
                - Ensure component totals match the overall totals
                - Use realistic portion estimates
                - Confidence: 1-3=low, 4-7=medium, 8-10=high
                - Text responses in Danish
                
                For "components", break down the meal into its individual ingredients with their amounts and nutritional values. Include amount in the key like "ingredient (amount)".
                
                Use reasonable estimates for a typical serving size.
                Provide a detailed 1-2 sentence description of the meal, including estimated portion size and key nutritional highlights.              
            """.trimIndent()

            val request = OpenAIRequest(
                model = "gpt-5.4",
                messages = listOf(
                    Message(
                        role = "user",
                        content = prompt
                    )
                ),
                maxTokens = 10000
            )

            Log.d(TAG, "Sending request to OpenAI with description: $encodedDescription")

            val response = openAIService.analyzeFood(
                authorization = "Bearer $apiKey",
                request = request
            )

            val content = response.choices.firstOrNull()?.message?.content

            Log.d(TAG, "OpenAI raw response: $content")

            when {
                content == null -> {
                    Log.e(TAG, "No response content from OpenAI")
                    Result.failure(Exception("No response from OpenAI"))
                }
                content !is String -> {
                    Log.e(TAG, "Response content is not a String: ${content.javaClass}")
                    Result.failure(Exception("Invalid response format"))
                }
                else -> {
                    // Store conversation for follow-up corrections
                    lastConversationMessages = request.messages + Message(role = "assistant", content = content)
                    try {
                        // Try to extract JSON if it's wrapped in markdown code blocks
                        val jsonContent = content.trim()
                            .removePrefix("```json")
                            .removePrefix("```")
                            .removeSuffix("```")
                            .trim()

                        Log.d(TAG, "Attempting to parse JSON: $jsonContent")

                        val nutritionInfo = Gson().fromJson(jsonContent, NutritionInfo::class.java)

                        if (nutritionInfo == null) {
                            Log.e(TAG, "Parsed NutritionInfo is null")
                            Result.failure(Exception("Failed to parse nutrition information"))
                        } else {
                            Log.d(TAG, "Successfully parsed: ${nutritionInfo.name} with ${nutritionInfo.components?.size ?: 0} components")
                            // Increment usage counter on success
                            incrementApiUsage()
                            Result.success(nutritionInfo)
                        }
                    } catch (e: JsonSyntaxException) {
                        Log.e(TAG, "JSON parsing error: ${e.message}")
                        Log.e(TAG, "Content that failed to parse: $content")
                        Result.failure(Exception("Failed to parse response: ${e.message}\nResponse: $content"))
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected error during parsing: ${e.message}")
                        Result.failure(Exception("Failed to parse response: ${e.message}"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "API call failed: ${e.message}", e)
            Result.failure(Exception("API call failed: ${e.message}"))
        }
    }

    // Intervals.icu Integration
    suspend fun syncFromIntervals(date: Date): Result<IntervalsSync> {
        val service = intervalsService
            ?: return Result.failure(Exception("Intervals.icu not configured"))
        if (intervalsAthleteId.isBlank())
            return Result.failure(Exception("Intervals.icu athlete ID not configured"))

        return try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
            Log.d(TAG, "Syncing from intervals.icu for date: $dateStr athlete: $intervalsAthleteId")

            // Fetch wellness data (steps, weight, HRV, RHR)
            val wellness = service.getWellness(intervalsAthleteId, dateStr)
            Log.d(TAG, "Wellness: steps=${wellness.steps}, weight=${wellness.weight}, hrv=${wellness.hrv}, rhr=${wellness.restingHR}")

            // Fetch activities to sum calories
            val activities = service.getActivities(intervalsAthleteId, dateStr, dateStr)
            // Prefer work (kJ) when available — 1 kJ ≈ 1 kcal at ~25% cycling efficiency
            // Fall back to calories field for activities without a power meter
            val activityCalories = activities.sumOf { it.work?.toInt() ?: it.calories ?: 0 }
            Log.d(TAG, "Activities: ${activities.size} found, $activityCalories total calories")

            // Load existing record to preserve fields not being synced (e.g. waterIntake)
            val existing = dailyActivityDao.getActivityForDateSuspend(date.time)

            val updated = (existing ?: DailyActivity(date = date)).copy(
                steps = wellness.steps ?: existing?.steps ?: 0,
                weight = wellness.weight?.toDouble() ?: existing?.weight,
                exerciseCalories = if (activityCalories > 0) activityCalories else existing?.exerciseCalories ?: 0,
                waterIntake = existing?.waterIntake ?: 0,
                hrv = wellness.hrv?.toDouble() ?: existing?.hrv,
                restingHR = wellness.restingHR ?: existing?.restingHR
            )
            dailyActivityDao.insertOrUpdate(updated)

            Result.success(
                IntervalsSync(
                    steps = wellness.steps,
                    weight = wellness.weight?.toDouble(),
                    hrv = wellness.hrv?.toDouble(),
                    restingHR = wellness.restingHR,
                    activityCalories = activityCalories
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Intervals sync failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // OpenAI Integration - Image Analysis with Components
    suspend fun analyzeImageFood(base64Image: String): Result<NutritionInfo> {
        return try {
            // Check API limit
            if (!canMakeApiCall()) {
                return Result.failure(Exception("Daily API limit reached ($DAILY_API_LIMIT calls). Resets at midnight."))
            }

            val prompt = """
                Analyze this food image and provide nutritional information with component breakdown in JSON format.
                
                Respond ONLY with valid JSON in this exact format:
                {
                  "name": "brief food name",
                  "description": "detailed description with portion estimates",
                  "calories": total_calories_number,
                  "protein": total_protein_grams,
                  "carbs": total_carbs_grams,
                  "fats": total_fats_grams,
                  "weight": total_weight_in_grams
                  "category": "BREAKFAST|LUNCH|DINNER|SNACK|FRUIT|TRAINING",
                  "confidence": confidence_score_1_to_10,
                  "components": {
                    "ingredient name (amount)": {
                      "calories": number,
                      "protein": number,
                      "carbs": number,
                      "fats": number,
                      "weight": weight_in_grams
                    }
                  }
                }
                
                CRITICAL REQUIREMENTS:
                - Include NUMERIC weight in grams for each component
                - Include serving size description AND weight (e.g., "tortilla (1 medium, 60g)")
                - The weight field should be the numeric value only (e.g., 60, not "60g") - Break down the meal into individual ingredients
                - Ensure component totals match the overall totals
                - Use realistic portion estimates
                - Confidence: 1-3=low, 4-7=medium, 8-10=high
                - Text responses in Danish
                
                For "components", break down the meal into its individual ingredients with their amounts and nutritional values. Include amount in the key like "ingredient (amount)".
                
                Estimate nutrition based on what you see in the image.
                Provide a detailed 1-2 sentence description based on what you observe, including estimated portion size and any visible ingredients or preparation methods.
            """.trimIndent()

            val request = OpenAIRequest(
                model = "gpt-5.4",
                messages = listOf(
                    Message(
                        role = "user",
                        content = listOf(
                            Content(type = "text", text = prompt),
                            Content(
                                type = "image_url",
                                imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
                            )
                        )
                    )
                ),
                maxTokens = 10000
            )

            Log.d(TAG, "Sending image analysis request to OpenAI")

            val response = openAIService.analyzeFood(
                authorization = "Bearer $apiKey",
                request = request
            )

            val content = response.choices.firstOrNull()?.message?.content

            Log.d(TAG, "OpenAI image analysis response: $content")

            if (content is String) {
                // Store conversation for follow-up corrections
                lastConversationMessages = request.messages + Message(role = "assistant", content = content)
                try {
                    // Try to extract JSON if it's wrapped in markdown code blocks
                    val jsonContent = content.trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()

                    Log.d(TAG, "Attempting to parse JSON: $jsonContent")

                    val nutritionInfo = Gson().fromJson(jsonContent, NutritionInfo::class.java)

                    if (nutritionInfo == null) {
                        Log.e(TAG, "Parsed NutritionInfo is null")
                        Result.failure(Exception("Failed to parse nutrition information"))
                    } else {
                        Log.d(TAG, "Successfully parsed: ${nutritionInfo.name} with ${nutritionInfo.components?.size ?: 0} components")
                        // Increment usage counter on success
                        incrementApiUsage()
                        Result.success(nutritionInfo)
                    }
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "JSON parsing error: ${e.message}")
                    Log.e(TAG, "Content that failed to parse: $content")
                    Result.failure(Exception("Failed to parse response: ${e.message}\nResponse: $content"))
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error during parsing: ${e.message}")
                    Result.failure(Exception("Failed to parse response: ${e.message}"))
                }
            } else {
                Log.e(TAG, "Response content is not a String")
                Result.failure(Exception("Invalid response format"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image API call failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Continue a food analysis conversation with a user correction
    suspend fun refineFoodAnalysis(conversationMessages: List<Message>): Result<NutritionInfo> {
        return try {
            if (!canMakeApiCall()) {
                return Result.failure(Exception("Daily API limit reached ($DAILY_API_LIMIT calls). Resets at midnight."))
            }

            val request = OpenAIRequest(
                model = "gpt-5.4",
                messages = conversationMessages,
                maxTokens = 10000
            )

            val response = openAIService.analyzeFood(
                authorization = "Bearer $apiKey",
                request = request
            )

            val content = response.choices.firstOrNull()?.message?.content

            if (content is String) {
                lastConversationMessages = conversationMessages + Message(role = "assistant", content = content)

                val jsonContent = content.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val nutritionInfo = Gson().fromJson(jsonContent, NutritionInfo::class.java)
                if (nutritionInfo != null) {
                    incrementApiUsage()
                    Result.success(nutritionInfo)
                } else {
                    Result.failure(Exception("Failed to parse updated nutrition information"))
                }
            } else {
                Result.failure(Exception("Invalid response format"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Food refinement failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}