package com.danielag_nutritrack.app.utils

/**
 * Utility object for calculating calories burned during exercise
 * Uses MET (Metabolic Equivalent of Task) values
 * Formula: Calories = MET × weight(kg) × duration(hours)
 */
object ExerciseCalorieCalculator {

    /**
     * MET values for different exercise types
     * Source: Compendium of Physical Activities
     */
    private val metValues = mapOf(
        "Running" to 9.8,            // Average running pace (9-10 min/mile)
        "Cycling" to 8.0,            // Moderate cycling (12-14 mph)
        "Swimming" to 8.0,           // Moderate freestyle swimming
        "Walking" to 3.5,            // Moderate walking pace (3-4 mph)
        "Weight Training" to 5.0,    // General weight training
        "Yoga" to 3.0,               // Hatha yoga
        "Football" to 8.0,           // Recreational football/soccer
        "Badminton" to 5.5,          // Recreational badminton
        "Other" to 5.0               // General moderate activity
    )

    /**
     * Calculate calories burned for an exercise
     * @param exerciseType Type of exercise (must match one of the predefined types)
     * @param durationMinutes Duration in minutes
     * @param weightKg User's weight in kilograms
     * @return Estimated calories burned (rounded to nearest integer)
     */
    fun calculateCalories(
        exerciseType: String,
        durationMinutes: Int,
        weightKg: Double
    ): Int {
        val met = metValues[exerciseType] ?: metValues["Other"]!!
        val durationHours = durationMinutes / 60.0
        val calories = met * weightKg * durationHours
        return calories.toInt()
    }

    /**
     * Get all supported exercise types
     */
    fun getSupportedExerciseTypes(): List<String> {
        return listOf(
            "Running",
            "Cycling",
            "Swimming",
            "Walking",
            "Weight Training",
            "Yoga",
            "Football",
            "Badminton",
            "Other"
        )
    }

    /**
     * Get MET value for a specific exercise type
     */
    fun getMetValue(exerciseType: String): Double {
        return metValues[exerciseType] ?: metValues["Other"]!!
    }
}