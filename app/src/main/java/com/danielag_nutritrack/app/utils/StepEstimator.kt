package com.danielag_nutritrack.app.utils

import kotlin.math.roundToInt

/**
 * Estimates how many steps a foot-based activity contributed.
 *
 * intervals.icu has no step field on an activity — the API spec only exposes steps on
 * `Wellness` (the daily total coming from the watch). The `Activity` schema does carry
 * `average_cadence`, `moving_time`, `distance` and `average_stride`, and those give us
 * the activity's step count two independent ways:
 *
 *   steps = cadence (steps/min) x moving_time
 *   steps = distance (m) / average_stride (m)
 *
 * Those steps are already part of the daily wellness total, and the activity's energy is
 * counted separately as EAT, so they have to be taken out of the step count before NEAT is
 * calculated — otherwise a run is paid for twice.
 */
object StepEstimator {

    /** Typical recreational running cadence, used when an activity carries no cadence data. */
    const val FALLBACK_RUNNING_CADENCE_SPM = 165

    /** Typical walking cadence, used when an activity carries no cadence data. */
    const val FALLBACK_WALKING_CADENCE_SPM = 110

    /**
     * Cadence is reported either as total steps per minute (~150-190 running) or as revolutions
     * per minute for a single foot (~75-95). Anything below this limit is treated as per-foot.
     */
    private const val ONE_FOOT_CADENCE_LIMIT = 120.0

    /** Plausible bounds for the length of a single step, in metres. */
    private const val MIN_STRIDE_METERS = 0.4
    private const val MAX_STRIDE_METERS = 2.2

    private val RUN_KEYWORDS = listOf("run", "jog", "løb", "lob")
    private val WALK_KEYWORDS = listOf("walk", "hike", "gåtur", "gaatur", "vandring")

    /** True when any of the labels (activity type, activity name, exercise type) looks like a run. */
    fun isRun(vararg labels: String?): Boolean = matches(RUN_KEYWORDS, labels)

    /** True when any of the labels looks like a walk or hike. */
    fun isWalk(vararg labels: String?): Boolean = matches(WALK_KEYWORDS, labels)

    /** True for activities that are performed on foot and therefore register steps. */
    fun isStepBased(vararg labels: String?): Boolean = isRun(*labels) || isWalk(*labels)

    private fun matches(keywords: List<String>, labels: Array<out String?>): Boolean {
        val haystack = labels.filterNotNull().joinToString(" ").lowercase()
        return keywords.any { haystack.contains(it) }
    }

    /**
     * Best-effort step count for one activity. Falls back through the available data:
     * cadence x time, then distance / stride, then a typical cadence for the activity type.
     * Returns 0 for activities that do not produce steps (rides, swims, strength work).
     */
    fun estimateSteps(
        type: String? = null,
        name: String? = null,
        movingTimeSeconds: Int? = null,
        distanceMeters: Double? = null,
        averageCadence: Double? = null,
        averageStride: Double? = null
    ): Int {
        if (!isStepBased(type, name)) return 0

        stepsFromCadence(averageCadence, movingTimeSeconds)?.let { return it }
        stepsFromStride(distanceMeters, averageStride)?.let { return it }

        val seconds = movingTimeSeconds?.takeIf { it > 0 } ?: return 0
        val fallbackCadence =
            if (isRun(type, name)) FALLBACK_RUNNING_CADENCE_SPM else FALLBACK_WALKING_CADENCE_SPM
        return (fallbackCadence * seconds / 60.0).roundToInt()
    }

    /** steps = cadence x minutes, normalising per-foot cadence to total steps per minute. */
    fun stepsFromCadence(averageCadence: Double?, movingTimeSeconds: Int?): Int? {
        val cadence = averageCadence?.takeIf { it > 0 } ?: return null
        val seconds = movingTimeSeconds?.takeIf { it > 0 } ?: return null
        val stepsPerMinute = if (cadence < ONE_FOOT_CADENCE_LIMIT) cadence * 2 else cadence
        return (stepsPerMinute * seconds / 60.0).roundToInt()
    }

    /**
     * steps = distance / stride length. Some sources report the full stride (two steps)
     * rather than a single step, so an implausibly long stride is halved before use.
     */
    fun stepsFromStride(distanceMeters: Double?, averageStride: Double?): Int? {
        val distance = distanceMeters?.takeIf { it > 0 } ?: return null
        val reported = averageStride?.takeIf { it > 0 } ?: return null
        val perStep = if (reported > MAX_STRIDE_METERS) reported / 2 else reported
        if (perStep < MIN_STRIDE_METERS || perStep > MAX_STRIDE_METERS) return null
        return (distance / perStep).roundToInt()
    }
}
