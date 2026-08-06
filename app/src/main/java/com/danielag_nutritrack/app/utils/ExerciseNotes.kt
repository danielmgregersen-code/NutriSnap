package com.danielag_nutritrack.app.utils

/**
 * The `notes` field of an ExerciseLog doubles as metadata storage for synced activities.
 * Format: `intervals:<activityId>;type:<intervalsType>;steps:<stepCount>`.
 *
 * The `intervals:` prefix has to stay first — ExerciseLogDao deletes synced entries with
 * `notes LIKE 'intervals:%'`.
 */
object ExerciseNotes {

    private const val SEPARATOR = ";"
    private const val ID_KEY = "intervals"
    private const val TYPE_KEY = "type"
    private const val STEPS_KEY = "steps"

    fun forIntervalsActivity(activityId: String?, type: String?, steps: Int): String {
        val fields = mutableListOf("$ID_KEY:${activityId.orEmpty()}")
        if (!type.isNullOrBlank()) fields += "$TYPE_KEY:$type"
        if (steps > 0) fields += "$STEPS_KEY:$steps"
        return fields.joinToString(SEPARATOR)
    }

    fun field(notes: String?, key: String): String? =
        notes?.split(SEPARATOR)
            ?.firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(":")
            ?.takeIf { it.isNotBlank() }

    /** Step count recorded for a synced activity, or null when it was never stored. */
    fun steps(notes: String?): Int? = field(notes, STEPS_KEY)?.toIntOrNull()

    /** The intervals.icu activity type (e.g. "Run", "Ride"), or null for manual entries. */
    fun activityType(notes: String?): String? = field(notes, TYPE_KEY)
}
