package com.danielag_nutritrack.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.danielag_nutritrack.app.data.DailyActivity
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollState
import com.patrykandpatrick.vico.compose.component.shapeComponent
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.compose.dimensions.dimensionsOf
import com.patrykandpatrick.vico.compose.legend.verticalLegend
import com.patrykandpatrick.vico.compose.legend.verticalLegendItem
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsDialog(
    weightHistory: List<DailyActivity>,
    calorieHistory: List<CalorieDataPoint>,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f)
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
                    Text("Progress Charts", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                // Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Weight") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Calories") }
                    )
                }

                // Chart Content
                when (selectedTab) {
                    0 -> WeightChartTab(weightHistory)
                    1 -> CalorieChartTab(calorieHistory)
                }
            }
        }
    }
}

@Composable
fun WeightChartTab(weightHistory: List<DailyActivity>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (weightHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No weight data yet.\nLog your weight in Daily Activity!",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            // Stats
            val weights = weightHistory.mapNotNull { it.weight }
            val latest = weights.lastOrNull() ?: 0.0
            val earliest = weights.firstOrNull() ?: latest
            val change = latest - earliest
            val highest = weights.maxOrNull() ?: 0.0
            val lowest = weights.minOrNull() ?: 0.0

            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("Current", "${String.format("%.1f", latest)} kg")
                    StatItem("Change", "${if (change > 0) "+" else ""}${String.format("%.1f", change)} kg")
                    StatItem("Range", "${String.format("%.1f", lowest)}-${String.format("%.1f", highest)}")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart
            Text("Weight Over Time", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            WeightLineChart(weightHistory)
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun WeightLineChart(weightHistory: List<DailyActivity>) {
    val entries = weightHistory
        .mapIndexedNotNull { index, activity ->
            activity.weight?.let { FloatEntry(index.toFloat(), it.toFloat()) }
        }

    if (entries.isEmpty()) return

    val chartEntryModelProducer = remember(entries) {
        ChartEntryModelProducer(listOf(entries))
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    // NEW: Calculate min and max for y-axis range with padding
    val weights = entries.map { it.y }
    val minWeight = weights.minOrNull() ?: 0f
    val maxWeight = weights.maxOrNull() ?: 100f
    val range = maxWeight - minWeight
    val padding = if (range > 0) range * 0.1f else 5f  // 10% padding or 5kg if flat line

    val axisMinValue = (minWeight - padding).coerceAtLeast(0f)  // Don't go below 0
    val axisMaxValue = maxWeight + padding

    ProvideChartStyle {
        Chart(
            chart = lineChart(),
            chartModelProducer = chartEntryModelProducer,
            startAxis = rememberStartAxis(
                title = "Weight (kg)",
                valueFormatter = { value, _ ->
                    String.format("%.0f", value)
                }
            ),
            bottomAxis = rememberBottomAxis(
                valueFormatter = { value, _ ->
                    val index = value.toInt()
                    if (index in weightHistory.indices) {
                        dateFormat.format(weightHistory[index].date)
                    } else ""
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            chartScrollState = rememberChartScrollState()
        )
    }
}

@Composable
fun CalorieChartTab(calorieHistory: List<CalorieDataPoint>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (calorieHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No calorie data yet.\nStart logging your meals!",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            // Stats
            val avgConsumed = calorieHistory.map { it.consumed }.average()
            val avgBurned = calorieHistory.map { it.burned }.average()
            val avgNet = avgConsumed - avgBurned

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("Avg Consumed", "${avgConsumed.toInt()} cal")
                    StatItem("Avg Burned", "${avgBurned.toInt()} cal")
                    StatItem("Avg Net", "${avgNet.toInt()} cal")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart
            Text("Calories Over Time", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            CalorieLineChart(calorieHistory)
        }
    }
}

@Composable
fun CalorieLineChart(calorieHistory: List<CalorieDataPoint>) {
    val consumedEntries = calorieHistory.mapIndexed { index, data ->
        FloatEntry(index.toFloat(), data.consumed.toFloat())
    }

    val burnedEntries = calorieHistory.mapIndexed { index, data ->
        FloatEntry(index.toFloat(), data.burned.toFloat())
    }

    val chartEntryModelProducer = remember(consumedEntries, burnedEntries) {
        ChartEntryModelProducer(listOf(consumedEntries, burnedEntries))
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    ProvideChartStyle {
        Chart(
            chart = lineChart(),
            chartModelProducer = chartEntryModelProducer,
            startAxis = rememberStartAxis(
                title = "Calories"
            ),
            bottomAxis = rememberBottomAxis(
                valueFormatter = { value, _ ->
                    val index = value.toInt()
                    if (index in calorieHistory.indices) {
                        dateFormat.format(calorieHistory[index].date)
                    } else ""
                }
            ),
            legend = rememberLegend(),
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            chartScrollState = rememberChartScrollState()
        )
    }
}

@Composable
private fun rememberLegend() = verticalLegend(
    items = listOf(
        verticalLegendItem(
            icon = shapeComponent(
                shape = androidx.compose.ui.graphics.RectangleShape,
                color = Color(0xFF4CAF50)
            ),
            label = textComponent(),
            labelText = "Consumed"
        ),
        verticalLegendItem(
            icon = shapeComponent(
                shape = androidx.compose.ui.graphics.RectangleShape,
                color = Color(0xFFFF9800)
            ),
            label = textComponent(),
            labelText = "Burned"
        )
    ),
    iconSize = 8.dp,
    iconPadding = 8.dp,
    spacing = 4.dp,
    padding = dimensionsOf(top = 8.dp)
)