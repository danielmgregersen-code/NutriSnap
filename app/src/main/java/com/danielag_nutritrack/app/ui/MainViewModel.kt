package com.danielag_nutritrack.app.ui

import android.app.Application
import androidx.lifecycle.*
import com.danielag_nutritrack.app.api.OpenAIService
import com.danielag_nutritrack.app.data.*
import com.danielag_nutritrack.app.repository.NutritionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import com.danielag_nutritrack.app.BuildConfig

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)

    private val repository = NutritionRepository(
        foodLogDao = database.foodLogDao(),
        userProfileDao = database.userProfileDao(),
        dailyActivityDao = database.dailyActivityDao(),
        exerciseLogDao = database.exerciseLogDao(),
        apiUsageDao = database.apiUsageDao(),
        openAIService = OpenAIService.create(),
        apiKey = BuildConfig.OPENAI_API_KEY
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedDate = MutableStateFlow(Date())
    val selectedDate: StateFlow<Date> = _selectedDate.asStateFlow()

    private val _weightHistory = MutableStateFlow<List<DailyActivity>>(emptyList())
    val weightHistory: StateFlow<List<DailyActivity>> = _weightHistory.asStateFlow()

    private val _calorieHistory = MutableStateFlow<List<CalorieDataPoint>>(emptyList())
    val calorieHistory: StateFlow<List<CalorieDataPoint>> = _calorieHistory.asStateFlow()

    private val _exerciseLogs = MutableStateFlow<List<ExerciseLog>>(emptyList())
    val exerciseLogs: StateFlow<List<ExerciseLog>> = _exerciseLogs.asStateFlow()

    private val _remainingApiCalls = MutableStateFlow(0)
    val remainingApiCalls: StateFlow<Int> = _remainingApiCalls.asStateFlow()

    private val _weeklySummary = MutableStateFlow<PeriodSummary?>(null)
    val weeklySummary: StateFlow<PeriodSummary?> = _weeklySummary.asStateFlow()

    private val _monthlySummary = MutableStateFlow<PeriodSummary?>(null)
    val monthlySummary: StateFlow<PeriodSummary?> = _monthlySummary.asStateFlow()

    private val _summaryLoading = MutableStateFlow(false)
    val summaryLoading: StateFlow<Boolean> = _summaryLoading.asStateFlow()

    private val _pendingAnalyzedFood = MutableStateFlow<AnalyzedFood?>(null)
    val pendingAnalyzedFood: StateFlow<AnalyzedFood?> = _pendingAnalyzedFood

    private var lastCheckedDate: Date? = null

    init {
        loadData()
        loadChartData()
        loadApiUsage()
        lastCheckedDate = getTodayDate()
    }

    private fun loadApiUsage() {
        viewModelScope.launch {
            _remainingApiCalls.value = repository.getRemainingApiCalls()
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            // Load user profile
            repository.getUserProfile().collect { profile ->
                _uiState.update { it.copy(userProfile = profile) }
                if (profile != null) {
                    calculateDailyStats()
                }
            }
        }

        viewModelScope.launch {
            // Load food logs - REACTIVE to date changes
            _selectedDate.flatMapLatest { date ->
                android.util.Log.d("NutriTrack", "Loading food logs for date: $date")
                repository.getLogsForDate(date)
            }.collect { logs ->
                android.util.Log.d("NutriTrack", "Received ${logs.size} food logs")
                _uiState.update { it.copy(foodLogs = logs) }
                calculateDailyStats()
            }
        }

        viewModelScope.launch {
            // Load activity - REACTIVE to date changes
            _selectedDate.flatMapLatest { date ->
                android.util.Log.d("NutriTrack", "Loading activity for date: $date")
                repository.getActivityForDate(date)
            }.collect { activity ->
                android.util.Log.d("NutriTrack", "Received activity: $activity")
                _uiState.update { it.copy(dailyActivity = activity) }
                calculateDailyStats()
            }
        }

        viewModelScope.launch {
            // Load exercises - REACTIVE to date changes
            _selectedDate.flatMapLatest { date ->
                android.util.Log.d("NutriTrack", "Loading exercises for date: $date")
                repository.getExercisesForDate(date)
            }.collect { exercises ->
                android.util.Log.d("NutriTrack", "Received ${exercises.size} exercises")
                _exerciseLogs.value = exercises
                calculateDailyStats()
            }
        }
    }


    fun checkAndUpdateDateIfNeeded() {
        val today = getTodayDate()

        // If selected date was "today" when we last checked, update it to the new today
        lastCheckedDate?.let { lastChecked ->
            val selectedDate = _selectedDate.value

            // Check if the calendar day changed
            if (!isSameDay(today, lastChecked) && isSameDay(selectedDate, lastChecked)) {
                // The day changed and user was viewing "today", so update to new today
                android.util.Log.d("NutriTrack", "Date changed overnight, updating to new today")
                changeSelectedDate(today)
            }
        }

        lastCheckedDate = today
    }

    // Helper function to get today's date (normalized to start of day)
    private fun getTodayDate(): Date {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.time
    }

    // Helper function to check if two dates are the same day
    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { time = date1 }
        val cal2 = java.util.Calendar.getInstance().apply { time = date2 }

        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
                cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun loadChartData() {
        viewModelScope.launch {
            // Load weight history
            repository.getWeightHistory().collect { activities ->
                _weightHistory.value = activities.sortedBy { it.date }
            }
        }

        viewModelScope.launch {
            // Load calorie history (last 30 days)
            repository.getRecentActivities().collect { activities ->
                val calorieData = mutableListOf<CalorieDataPoint>()

                activities.sortedByDescending { it.date }.take(30).reversed().forEach { activity ->
                    // Get food logs for this date
                    val logs = repository.getLogsForDate(activity.date).first()
                    val consumed = logs.sumOf { it.calories }

                    calorieData.add(
                        CalorieDataPoint(
                            date = activity.date,
                            consumed = consumed,
                            burned = calculateCaloriesBurned(activity)
                        )
                    )
                }

                _calorieHistory.value = calorieData
            }
        }
    }

    private fun calculateCaloriesBurned(activity: DailyActivity): Double {
        val profile = _uiState.value.userProfile ?: return 0.0
        val tdee = repository.calculateTDEE(profile, activity.steps)
        return tdee + activity.exerciseCalories
    }

    private fun calculateDailyStats() {
        val state = _uiState.value
        val profile = state.userProfile ?: return

        val totalCaloriesConsumed = state.foodLogs.sumOf { it.calories }
        val totalProtein = state.foodLogs.sumOf { it.protein }
        val totalCarbs = state.foodLogs.sumOf { it.carbs }
        val totalFats = state.foodLogs.sumOf { it.fats }

        val steps = state.dailyActivity?.steps ?: 0
        val exerciseCalories = _exerciseLogs.value.sumOf { it.caloriesBurned }

        val currentWeight = state.dailyActivity?.weight
            ?: _weightHistory.value.lastOrNull()?.weight
            ?: profile.weight

        val bmr = calculateBMRWithWeight(profile, currentWeight)
        val tdee = calculateTDEEWithWeight(profile, currentWeight, steps)
        val baseTargetCalories = calculateTargetCaloriesWithWeight(profile, currentWeight, steps)
        val targetCalories = baseTargetCalories + exerciseCalories  // ADD EXERCISE!
        val caloriesBurned = tdee + exerciseCalories

        val netCalories = totalCaloriesConsumed - caloriesBurned

        _uiState.update {
            it.copy(
                totalCalories = totalCaloriesConsumed,
                totalProtein = totalProtein,
                totalCarbs = totalCarbs,
                totalFats = totalFats,
                bmr = bmr,
                tdee = tdee,
                targetCalories = targetCalories,
                caloriesBurned = caloriesBurned,
                netCalories = netCalories
            )
        }
    }

    private fun calculateBMRWithWeight(profile: UserProfile, weight: Double): Double {
        val heightCm = profile.height
        val age = profile.age

        return when (profile.gender) {
            Gender.MALE -> (10 * weight) + (6.25 * heightCm) - (5 * age) + 5
            Gender.FEMALE -> (10 * weight) + (6.25 * heightCm) - (5 * age) - 161
        }
    }

    private fun calculateTDEEWithWeight(profile: UserProfile, weight: Double, steps: Int = 0): Double {
        val bmr = calculateBMRWithWeight(profile, weight)
        val activityMultiplier = when (profile.activityLevel) {
            ActivityLevel.OFFICE_JOB -> 1.0
            ActivityLevel.PHYSICAL_JOB -> 1.5
        }

        val stepCalories = steps * 0.042

        return (bmr * activityMultiplier) + stepCalories
    }

    private fun calculateTargetCaloriesWithWeight(profile: UserProfile, weight: Double, steps: Int = 0): Double {
        val tdee = calculateTDEEWithWeight(profile, weight, steps)

        return when (profile.goal) {
            Goal.MAINTAIN -> tdee
            Goal.LOSE_WEIGHT -> {
                val deficit = when (profile.weightChangeRate) {
                    WeightChangeRate.RATE_025 -> 275
                    WeightChangeRate.RATE_050 -> 550
                    WeightChangeRate.RATE_075 -> 825
                    WeightChangeRate.RATE_100 -> 1100
                }
                tdee - deficit
            }
            Goal.GAIN_MUSCLE -> {
                val surplus = when (profile.weightChangeRate) {
                    WeightChangeRate.RATE_025 -> 275
                    WeightChangeRate.RATE_050 -> 550
                    WeightChangeRate.RATE_075 -> 825
                    WeightChangeRate.RATE_100 -> 1100
                }
                tdee + surplus
            }
        }
    }

    fun clearPendingFood() {
        _pendingAnalyzedFood.value = null
    }

    fun analyzeTextFood(description: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                android.util.Log.d("NutriTrack", "Starting analysis for: $description")

                repository.analyzeTextFood(description)
                    .onSuccess { nutritionInfo ->
                        android.util.Log.d("NutriTrack", "Got response: ${nutritionInfo.name} with ${nutritionInfo.components?.size ?: 0} components")

                        // Parse category - handle multiple categories separated by |
                        val categoryStr = nutritionInfo.category.split("|").firstOrNull()?.trim() ?: "SNACK"
                        val category = try {
                            MealCategory.valueOf(categoryStr)
                        } catch (e: IllegalArgumentException) {
                            android.util.Log.e("NutriTrack", "Invalid category: $categoryStr, using SNACK")
                            MealCategory.SNACK // Default fallback
                        }

                        // Convert components to JSON string
                        val componentsJson = nutritionInfo.components?.let {
                            com.google.gson.Gson().toJson(it)
                        }

                        // Instead of inserting immediately, set as pending for review
                        _pendingAnalyzedFood.value = AnalyzedFood(
                            name = nutritionInfo.name,
                            calories = nutritionInfo.calories,
                            protein = nutritionInfo.protein,
                            carbs = nutritionInfo.carbs,
                            fats = nutritionInfo.fats,
                            category = category,
                            notes = "Confidence: ${nutritionInfo.confidence}/10\n\n${nutritionInfo.description}",
                            components = componentsJson  // NEW: Save components JSON
                        )

                        android.util.Log.d("NutriTrack", "Analysis complete, showing review dialog")
                        _uiState.update { it.copy(isLoading = false) }
                        loadApiUsage() // Refresh API call count
                    }
                    .onFailure { error ->
                        android.util.Log.e("NutriTrack", "Analysis failed: ${error.message}", error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Error analyzing food: ${error.message ?: "Unknown error"}"
                            )
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.e("NutriTrack", "Exception during analysis: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error analyzing food: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun analyzeImageFood(base64Image: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                android.util.Log.d("NutriTrack", "Starting image analysis")

                repository.analyzeImageFood(base64Image)
                    .onSuccess { nutritionInfo ->
                        android.util.Log.d("NutriTrack", "Got image response: ${nutritionInfo.name} with ${nutritionInfo.components?.size ?: 0} components")

                        // Parse category - handle multiple categories separated by |
                        val categoryStr = nutritionInfo.category.split("|").firstOrNull()?.trim() ?: "SNACK"
                        val category = try {
                            MealCategory.valueOf(categoryStr)
                        } catch (e: IllegalArgumentException) {
                            android.util.Log.e("NutriTrack", "Invalid category: $categoryStr, using SNACK")
                            MealCategory.SNACK // Default fallback
                        }

                        // Convert components to JSON string
                        val componentsJson = nutritionInfo.components?.let {
                            com.google.gson.Gson().toJson(it)
                        }

                        // Instead of inserting immediately, set as pending for review
                        _pendingAnalyzedFood.value = AnalyzedFood(
                            name = nutritionInfo.name,
                            calories = nutritionInfo.calories,
                            protein = nutritionInfo.protein,
                            carbs = nutritionInfo.carbs,
                            fats = nutritionInfo.fats,
                            category = category,
                            notes = "Confidence: ${nutritionInfo.confidence}/10\n\n${nutritionInfo.description}",
                            components = componentsJson  // NEW: Save components JSON
                        )

                        android.util.Log.d("NutriTrack", "Image analysis complete, showing review dialog")
                        _uiState.update { it.copy(isLoading = false) }
                        loadApiUsage() // Refresh API call count
                    }
                    .onFailure { error ->
                        android.util.Log.e("NutriTrack", "Image analysis failed: ${error.message}", error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Error analyzing image: ${error.message ?: "Unknown error"}"
                            )
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.e("NutriTrack", "Exception during image analysis: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error analyzing image: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun savePendingFood() {
        viewModelScope.launch {
            _pendingAnalyzedFood.value?.let { analyzed ->
                // Normalize date to start of day
                val calendar = java.util.Calendar.getInstance()
                calendar.time = _selectedDate.value
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)

                val foodLog = FoodLog(
                    name = analyzed.name,
                    calories = analyzed.calories,
                    protein = analyzed.protein,
                    carbs = analyzed.carbs,
                    fats = analyzed.fats,
                    category = analyzed.category,
                    timestamp = calendar.time,
                    notes = analyzed.notes,
                    components = analyzed.components  // NEW: Save components
                )

                android.util.Log.d("NutriTrack", "Saving reviewed food log: ${foodLog.name} with components")
                repository.insertLog(foodLog)
                android.util.Log.d("NutriTrack", "Food log saved successfully")

                // Clear pending food after saving
                _pendingAnalyzedFood.value = null
            }
        }
    }

    fun addManualFood(
        name: String,
        calories: Double,
        protein: Double?,
        carbs: Double?,
        fats: Double?,
        category: MealCategory,
        notes: String?
    ) {
        viewModelScope.launch {
            try {
                android.util.Log.d("NutriTrack", "Adding manual food: $name, $calories cal")

                // Normalize date to start of day
                val calendar = java.util.Calendar.getInstance()
                calendar.time = _selectedDate.value
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)

                val foodLog = FoodLog(
                    name = name,
                    calories = calories,
                    protein = protein ?: 0.0,
                    carbs = carbs ?: 0.0,
                    fats = fats ?: 0.0,
                    category = category,
                    timestamp = calendar.time,
                    notes = notes
                )

                repository.insertLog(foodLog)
                android.util.Log.d("NutriTrack", "Manual food log added successfully")
            } catch (e: Exception) {
                android.util.Log.e("NutriTrack", "Error adding manual food: ${e.message}", e)
                _uiState.update {
                    it.copy(error = "Error adding food: ${e.message}")
                }
            }
        }
    }

    fun saveUserProfile(profile: UserProfile) {
        viewModelScope.launch {
            try {
                android.util.Log.d("NutriTrack", "Saving profile: $profile")
                repository.saveUserProfile(profile)
                android.util.Log.d("NutriTrack", "Profile saved successfully")
                // Force reload to update UI
                loadData()
            } catch (e: Exception) {
                android.util.Log.e("NutriTrack", "Error saving profile: ${e.message}", e)
                _uiState.update {
                    it.copy(error = "Error saving profile: ${e.message}")
                }
            }
        }
    }

    fun updateDailyActivity(steps: Int, exerciseCalories: Int, weight: Double? = null) {
        viewModelScope.launch {
            try {
                android.util.Log.d("NutriTrack", "Updating activity - Steps: $steps, Exercise: $exerciseCalories, Weight: $weight")

                // Normalize date to start of day for proper primary key matching
                val calendar = java.util.Calendar.getInstance()
                calendar.time = _selectedDate.value
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)

                val activity = DailyActivity(
                    date = calendar.time,
                    steps = steps,
                    exerciseCalories = exerciseCalories,
                    weight = weight,
                    waterIntake = _uiState.value.dailyActivity?.waterIntake ?: 0 // Preserve water intake
                )

                repository.saveActivity(activity)
                android.util.Log.d("NutriTrack", "Activity saved successfully for date: ${calendar.time}")

                // Force reload to update UI
                loadData()
                loadChartData()
            } catch (e: Exception) {
                android.util.Log.e("NutriTrack", "Error saving activity: ${e.message}", e)
                _uiState.update {
                    it.copy(error = "Error saving activity: ${e.message}")
                }
            }
        }
    }

    fun deleteLog(log: FoodLog) {
        viewModelScope.launch {
            android.util.Log.d("NutriTrack", "Deleting food log: ${log.name}")
            repository.deleteLog(log)
            android.util.Log.d("NutriTrack", "Food log deleted and data refreshed")
        }
    }

    fun updateLog(log: FoodLog) {
        viewModelScope.launch {
            try {
                android.util.Log.d("NutriTrack", "Updating food log: ${log.name}")
                repository.updateLog(log)
                android.util.Log.d("NutriTrack", "Food log updated successfully")
            } catch (e: Exception) {
                android.util.Log.e("NutriTrack", "Error updating food log: ${e.message}", e)
                _uiState.update {
                    it.copy(error = "Error updating food log: ${e.message}")
                }
            }
        }
    }

    fun logExercise(exerciseType: String, caloriesBurned: Int, duration: Int?, notes: String?) {
        viewModelScope.launch {
            try {
                android.util.Log.d("NutriTrack", "Logging exercise: $exerciseType, $caloriesBurned cal")

                val calendar = java.util.Calendar.getInstance()
                calendar.time = _selectedDate.value
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)

                val exercise = ExerciseLog(
                    exerciseType = exerciseType,
                    caloriesBurned = caloriesBurned,
                    timestamp = calendar.time,
                    duration = duration,
                    notes = notes
                )

                repository.insertExercise(exercise)
                android.util.Log.d("NutriTrack", "Exercise logged successfully")
            } catch (e: Exception) {
                android.util.Log.e("NutriTrack", "Error logging exercise: ${e.message}", e)
                _uiState.update {
                    it.copy(error = "Error logging exercise: ${e.message}")
                }
            }
        }
    }

    fun deleteExercise(exercise: ExerciseLog) {
        viewModelScope.launch {
            android.util.Log.d("NutriTrack", "Deleting exercise: ${exercise.exerciseType}")
            repository.deleteExercise(exercise)
            android.util.Log.d("NutriTrack", "Exercise deleted and data refreshed")
        }
    }

    fun updateExercise(exercise: ExerciseLog) {
        viewModelScope.launch {
            try {
                android.util.Log.d("NutriTrack", "Updating exercise: ${exercise.exerciseType}")
                repository.updateExercise(exercise)
                android.util.Log.d("NutriTrack", "Exercise updated successfully")
            } catch (e: Exception) {
                android.util.Log.e("NutriTrack", "Error updating exercise: ${e.message}", e)
                _uiState.update {
                    it.copy(error = "Error updating exercise: ${e.message}")
                }
            }
        }
    }

    fun updateStepsAndWeight(steps: Int, weight: Double?) {
        viewModelScope.launch {
            try {
                android.util.Log.d("NutriTrack", "Updating steps and weight - Steps: $steps, Weight: $weight")

                // Normalize date to start of day
                val calendar = java.util.Calendar.getInstance()
                calendar.time = _selectedDate.value
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)

                val activity = DailyActivity(
                    date = calendar.time,
                    steps = steps,
                    exerciseCalories = 0, // No longer used, exercises logged separately
                    weight = weight,
                    waterIntake = _uiState.value.dailyActivity?.waterIntake ?: 0 // Preserve water intake
                )

                repository.saveActivity(activity)
                android.util.Log.d("NutriTrack", "Steps and weight updated successfully")

                // Force reload to update UI
                loadData()
                loadChartData()
            } catch (e: Exception) {
                android.util.Log.e("NutriTrack", "Error updating steps/weight: ${e.message}", e)
                _uiState.update {
                    it.copy(error = "Error updating steps/weight: ${e.message}")
                }
            }
        }
    }

    fun updateStepsWeightAndWater(steps: Int, weight: Double?, waterIntake: Int) {
        viewModelScope.launch {
            try {
                android.util.Log.d("NutriTrack", "Updating steps, weight, and water - Steps: $steps, Weight: $weight, Water: $waterIntake ml")

                // Normalize date to start of day
                val calendar = java.util.Calendar.getInstance()
                calendar.time = _selectedDate.value
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)

                val activity = DailyActivity(
                    date = calendar.time,
                    steps = steps,
                    exerciseCalories = 0, // No longer used, exercises logged separately
                    weight = weight,
                    waterIntake = waterIntake
                )

                repository.saveActivity(activity)
                android.util.Log.d("NutriTrack", "Steps, weight, and water updated successfully")

                // Force reload to update UI
                loadData()
                loadChartData()
            } catch (e: Exception) {
                android.util.Log.e("NutriTrack", "Error updating activity: ${e.message}", e)
                _uiState.update {
                    it.copy(error = "Error updating activity: ${e.message}")
                }
            }
        }
    }

    fun addWater(amount: Int) {
        viewModelScope.launch {
            try {
                android.util.Log.d("NutriTrack", "Adding water: $amount ml")

                // Normalize date to start of day
                val calendar = java.util.Calendar.getInstance()
                calendar.time = _selectedDate.value
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)

                val currentActivity = _uiState.value.dailyActivity
                val newWaterIntake = (currentActivity?.waterIntake ?: 0) + amount

                val activity = DailyActivity(
                    date = calendar.time,
                    steps = currentActivity?.steps ?: 0,
                    exerciseCalories = 0,
                    weight = currentActivity?.weight,
                    waterIntake = newWaterIntake
                )

                repository.saveActivity(activity)
                android.util.Log.d("NutriTrack", "Water intake updated: $newWaterIntake ml")

                // Force reload to update UI
                loadData()
            } catch (e: Exception) {
                android.util.Log.e("NutriTrack", "Error updating water intake: ${e.message}", e)
                _uiState.update {
                    it.copy(error = "Error updating water intake: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun loadWeeklySummary() {
        viewModelScope.launch {
            _summaryLoading.value = true
            try {
                val endDate = Date()
                val calendar = java.util.Calendar.getInstance()
                calendar.time = endDate
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -6) // Last 7 days
                val startDate = calendar.time

                val summary = calculatePeriodSummary(startDate, endDate)
                _weeklySummary.value = summary
            } catch (e: Exception) {
                android.util.Log.e("NutriTrack", "Error loading weekly summary: ${e.message}", e)
            } finally {
                _summaryLoading.value = false
            }
        }
    }

    fun loadMonthlySummary() {
        viewModelScope.launch {
            _summaryLoading.value = true
            try {
                val endDate = Date()
                val calendar = java.util.Calendar.getInstance()
                calendar.time = endDate
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -29) // Last 30 days
                val startDate = calendar.time

                val summary = calculatePeriodSummary(startDate, endDate)
                _monthlySummary.value = summary
            } catch (e: Exception) {
                android.util.Log.e("NutriTrack", "Error loading monthly summary: ${e.message}", e)
            } finally {
                _summaryLoading.value = false
            }
        }
    }

    private suspend fun calculatePeriodSummary(startDate: Date, endDate: Date): PeriodSummary {
        val profile = _uiState.value.userProfile

        // If no profile, return empty summary
        if (profile == null) {
            return PeriodSummary(
                startDate = startDate,
                endDate = endDate,
                avgCaloriesConsumed = 0.0,
                avgCaloriesBurned = 0.0,
                avgNetCalories = 0.0,
                avgProtein = 0.0,
                avgCarbs = 0.0,
                avgFats = 0.0,
                totalExercises = 0,
                totalSteps = 0,
                avgWaterIntake = 0.0,
                startWeight = null,
                endWeight = null,
                daysLogged = 0,
                daysOnTarget = 0,
                totalDays = 0
            )
        }

        // Normalize dates to start of day
        val calendar = java.util.Calendar.getInstance()
        calendar.time = startDate
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val normalizedStart = calendar.time

        calendar.time = endDate
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val normalizedEnd = calendar.time

        // Calculate total days in period
        val totalDays = ((normalizedEnd.time - normalizedStart.time) / (1000 * 60 * 60 * 24)).toInt() + 1

        // Collect data for each day
        var totalCaloriesConsumed = 0.0
        var totalCaloriesBurned = 0.0
        var totalProtein = 0.0
        var totalCarbs = 0.0
        var totalFats = 0.0
        var totalExercises = 0
        var totalSteps = 0
        var totalWaterIntake = 0
        var daysLogged = 0
        var daysOnTarget = 0

        var startWeight: Double? = null
        var endWeight: Double? = null
        var lastKnownWeight = profile.weight // Start with profile weight

        // Iterate through each day
        calendar.time = normalizedStart
        while (calendar.time <= normalizedEnd) {
            val currentDate = calendar.time

            // Get food logs for this date
            val foodLogs = repository.getLogsForDate(currentDate).first()
            if (foodLogs.isNotEmpty()) {
                daysLogged++
                val dayCalories = foodLogs.sumOf { it.calories }
                totalCaloriesConsumed += dayCalories
                totalProtein += foodLogs.sumOf { it.protein }
                totalCarbs += foodLogs.sumOf { it.carbs }
                totalFats += foodLogs.sumOf { it.fats }

                // Get activity and exercises for this date
                val activity = repository.getActivityForDate(currentDate).first()
                val steps = activity?.steps ?: 0
                val exerciseCalories = repository.getExercisesForDate(currentDate).first().sumOf { it.caloriesBurned }

                // Update last known weight if available
                activity?.weight?.let { lastKnownWeight = it }

                // Calculate target using the weight for this day (or last known weight)
                val baseTarget = calculateTargetCaloriesWithWeight(profile, lastKnownWeight, steps)
                // Add exercise calories to target (like daily screen does)
                val targetCalories = baseTarget + exerciseCalories

                val tdee = calculateTDEEWithWeight(profile, lastKnownWeight, steps)
                val burned = tdee + exerciseCalories

                totalCaloriesBurned += burned

                // Check if within ±200 cal of the exercise-adjusted target
                if (kotlin.math.abs(dayCalories - targetCalories) <= 200) {
                    daysOnTarget++
                }
            }

            // Get exercises for this date
            val exercises = repository.getExercisesForDate(currentDate).first()
            totalExercises += exercises.size

            // Get activity for this date
            val activity = repository.getActivityForDate(currentDate).first()
            activity?.let {
                totalSteps += it.steps
                totalWaterIntake += it.waterIntake

                // Track weight changes
                it.weight?.let { w ->
                    if (startWeight == null) startWeight = w
                    endWeight = w
                }
            }

            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }

        // Calculate averages
        val avgCaloriesConsumed = if (daysLogged > 0) totalCaloriesConsumed / daysLogged else 0.0
        val avgCaloriesBurned = if (daysLogged > 0) totalCaloriesBurned / daysLogged else 0.0
        val avgNetCalories = avgCaloriesConsumed - avgCaloriesBurned
        val avgProtein = if (daysLogged > 0) totalProtein / daysLogged else 0.0
        val avgCarbs = if (daysLogged > 0) totalCarbs / daysLogged else 0.0
        val avgFats = if (daysLogged > 0) totalFats / daysLogged else 0.0
        val avgWaterIntake = if (totalDays > 0) totalWaterIntake.toDouble() / totalDays else 0.0

        return PeriodSummary(
            startDate = normalizedStart,
            endDate = normalizedEnd,
            avgCaloriesConsumed = avgCaloriesConsumed,
            avgCaloriesBurned = avgCaloriesBurned,
            avgNetCalories = avgNetCalories,
            avgProtein = avgProtein,
            avgCarbs = avgCarbs,
            avgFats = avgFats,
            totalExercises = totalExercises,
            totalSteps = totalSteps,
            avgWaterIntake = avgWaterIntake,
            startWeight = startWeight,
            endWeight = endWeight,
            daysLogged = daysLogged,
            daysOnTarget = daysOnTarget,
            totalDays = totalDays
        )
    }

    fun changeSelectedDate(newDate: Date) {
        viewModelScope.launch {
            android.util.Log.d("NutriTrack", "=================================")
            android.util.Log.d("NutriTrack", "CHANGE DATE CALLED")
            android.util.Log.d("NutriTrack", "Current date: ${_selectedDate.value}")
            android.util.Log.d("NutriTrack", "New date: $newDate")
            android.util.Log.d("NutriTrack", "Current thread: ${Thread.currentThread().name}")

            // Print stack trace to see WHO is calling this
            val stackTrace = Thread.currentThread().stackTrace
            android.util.Log.d("NutriTrack", "Called from:")
            stackTrace.take(10).forEachIndexed { index, element ->
                android.util.Log.d("NutriTrack", "  $index: $element")
            }
            android.util.Log.d("NutriTrack", "=================================")

            // Normalize date to start of day
            val calendar = java.util.Calendar.getInstance()
            calendar.time = newDate
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)

            _selectedDate.value = calendar.time
        }
    }
}

data class CalorieDataPoint(
    val date: Date,
    val consumed: Double,
    val burned: Double
)

data class AnalyzedFood(
    val name: String,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
    val category: MealCategory,
    val notes: String?,
    val components: String? = null  // NEW: JSON string of components
)

data class UiState(
    val foodLogs: List<FoodLog> = emptyList(),
    val userProfile: UserProfile? = null,
    val dailyActivity: DailyActivity? = null,
    val totalCalories: Double = 0.0,
    val totalProtein: Double = 0.0,
    val totalCarbs: Double = 0.0,
    val totalFats: Double = 0.0,
    val bmr: Double = 0.0,
    val tdee: Double = 0.0,
    val targetCalories: Double = 0.0,
    val caloriesBurned: Double = 0.0,
    val netCalories: Double = 0.0,
    val isLoading: Boolean = false,
    val error: String? = null
)