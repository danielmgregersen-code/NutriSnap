package com.danielag_nutritrack.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

data class PeriodSummary(
    val startDate: Date,
    val endDate: Date,
    val avgCaloriesConsumed: Double,
    val avgCaloriesBurned: Double,
    val avgNetCalories: Double,
    val avgProtein: Double,
    val avgCarbs: Double,
    val avgFats: Double,
    val totalExercises: Int,
    val totalSteps: Int,
    val avgWaterIntake: Double,
    val startWeight: Double?,
    val endWeight: Double?,
    val daysLogged: Int,
    val daysOnTarget: Int,
    val totalDays: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryDialog(
    onDismiss: () -> Unit,
    onLoadWeekly: () -> Unit,
    onLoadMonthly: () -> Unit,
    weeklySummary: PeriodSummary?,
    monthlySummary: PeriodSummary?,
    isLoading: Boolean
) {
    var selectedTab by remember { mutableStateOf(0) }

    // Load data when dialog opens
    LaunchedEffect(Unit) {
        onLoadWeekly()
        onLoadMonthly()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.large
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Summary", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                // Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Weekly") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Monthly") }
                    )
                }

                // Content
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    when (selectedTab) {
                        0 -> weeklySummary?.let { SummaryContent(it) }
                        1 -> monthlySummary?.let { SummaryContent(it) }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryContent(summary: PeriodSummary) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Date Range
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Period",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "${dateFormat.format(summary.startDate)} - ${dateFormat.format(summary.endDate)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${summary.daysLogged} of ${summary.totalDays} days logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Weight Change
        if (summary.startWeight != null && summary.endWeight != null) {
            val change = summary.endWeight - summary.startWeight
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Weight Change",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "${if (change > 0) "+" else ""}${String.format("%.1f", change)} kg",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (change > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${String.format("%.1f", summary.startWeight)} kg → ${String.format("%.1f", summary.endWeight)} kg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Calories Summary
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Average Daily Calories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryStatItem("Consumed", "${summary.avgCaloriesConsumed.toInt()}", "")
                    SummaryStatItem("Burned", "${summary.avgCaloriesBurned.toInt()}", "")
                    SummaryStatItem("Net", "${summary.avgNetCalories.toInt()}", "")
                }
            }
        }

        // Macros Summary
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Average Daily Macros",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryStatItem("Protein", "${summary.avgProtein.toInt()}", "g", "🥩")
                    SummaryStatItem("Carbs", "${summary.avgCarbs.toInt()}", "g", "🍞")
                    SummaryStatItem("Fats", "${summary.avgFats.toInt()}", "g", "🥑")
                }
            }
        }

        // Activity Summary
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                SummaryRow("Total Exercises", "${summary.totalExercises}")
                Spacer(modifier = Modifier.height(8.dp))
                SummaryRow("Average Steps/Day", "${(summary.totalSteps / summary.totalDays.coerceAtLeast(1))}")
                Spacer(modifier = Modifier.height(8.dp))
                SummaryRow("Total Steps", "${summary.totalSteps}")
                Spacer(modifier = Modifier.height(8.dp))
                SummaryRow("Average Water/Day 💧", "${summary.avgWaterIntake.toInt()} ml")
            }
        }

        // Adherence
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Target Adherence",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))

                val adherencePercent = if (summary.daysLogged > 0) {
                    (summary.daysOnTarget * 100.0 / summary.daysLogged).toInt()
                } else {
                    0
                }

                Text(
                    "$adherencePercent%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${summary.daysOnTarget} of ${summary.daysLogged} days within ±200 cal of target",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )

                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { adherencePercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun SummaryStatItem(label: String, value: String, unit: String, emoji: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        emoji?.let {
            Text(it, style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "$value $unit",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}