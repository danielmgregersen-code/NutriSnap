package com.danielag_nutritrack.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A 10 km run in 50 minutes is used throughout: per-foot cadence 82.5 (= 165 steps/min)
 * and a 1.22 m stride, which is roughly 8200 steps whichever way it is derived.
 */
class StepEstimatorTest {

    @Test
    fun `per-foot cadence is doubled into steps per minute`() {
        assertEquals(8250, StepEstimator.estimateSteps(
            type = "Run", movingTimeSeconds = 3000, averageCadence = 82.5))
    }

    @Test
    fun `cadence already in steps per minute is used as-is`() {
        assertEquals(8250, StepEstimator.estimateSteps(
            type = "Run", movingTimeSeconds = 3000, averageCadence = 165.0))
    }

    @Test
    fun `stride is used when cadence is missing`() {
        assertEquals(8197, StepEstimator.estimateSteps(
            type = "Run", movingTimeSeconds = 3000, distanceMeters = 10000.0, averageStride = 1.22))
    }

    @Test
    fun `a full-cycle stride is halved to a single step`() {
        assertEquals(StepEstimator.stepsFromStride(10000.0, 1.22), StepEstimator.stepsFromStride(10000.0, 2.44))
    }

    @Test
    fun `an implausible stride is rejected`() {
        assertNull(StepEstimator.stepsFromStride(10000.0, 0.05))
        assertNull(StepEstimator.stepsFromStride(10000.0, 0.0))
        assertNull(StepEstimator.stepsFromStride(0.0, 1.22))
    }

    @Test
    fun `duration alone falls back to a typical cadence`() {
        assertEquals(45 * 165, StepEstimator.estimateSteps(type = "Run", movingTimeSeconds = 45 * 60))
        assertEquals(60 * 110, StepEstimator.estimateSteps(type = "Walk", movingTimeSeconds = 60 * 60))
    }

    @Test
    fun `runs are recognised by activity type regardless of the activity name`() {
        // The reported bug: the activity name never contains "run", so nothing was subtracted.
        assertEquals(8250, StepEstimator.estimateSteps(
            type = "Run", name = "Tirsdag 4x1000", movingTimeSeconds = 3000, averageCadence = 82.5))
        assertTrue(StepEstimator.isRun("TrailRun", "Løbetur i skoven"))
    }

    @Test
    fun `activities that do not produce steps are ignored`() {
        assertEquals(0, StepEstimator.estimateSteps(
            type = "Ride", name = "Evening Ride", movingTimeSeconds = 7200,
            distanceMeters = 60000.0, averageCadence = 90.0))
        assertEquals(0, StepEstimator.estimateSteps(
            type = "Swim", name = "Pool", movingTimeSeconds = 1800, distanceMeters = 1500.0))
        assertEquals(0, StepEstimator.estimateSteps(
            type = "WeightTraining", movingTimeSeconds = 3600))
    }

    @Test
    fun `notes round-trip the step count and keep the intervals prefix`() {
        val notes = ExerciseNotes.forIntervalsActivity("i12345", "Run", 8250)

        // ExerciseLogDao deletes synced rows with notes LIKE 'intervals:%'
        assertTrue(notes.startsWith("intervals:"))
        assertEquals(8250, ExerciseNotes.steps(notes))
        assertEquals("Run", ExerciseNotes.activityType(notes))
    }

    @Test
    fun `notes without a step count parse as null`() {
        assertNull(ExerciseNotes.steps("intervals:99"))
        assertNull(ExerciseNotes.steps(null))
        assertNull(ExerciseNotes.activityType("intervals:99"))
    }
}
