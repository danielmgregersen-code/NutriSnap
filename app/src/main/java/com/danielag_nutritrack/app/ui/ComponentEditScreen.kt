package com.danielag_nutritrack.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danielag_nutritrack.app.data.FoodComponent
import com.danielag_nutritrack.app.data.FoodLog
import com.danielag_nutritrack.app.data.toComponentsMap
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentEditScreen(
    foodLog: FoodLog,
    onSave: (FoodLog) -> Unit,
    onBack: () -> Unit
) {
    // Parse components from JSON
    val initialComponents = foodLog.components?.toComponentsMap() ?: emptyMap()

    // Track multipliers for each component (default 1.0 = 100%)
    val componentMultipliers = remember {
        mutableStateMapOf<String, Float>().apply {
            initialComponents.keys.forEach { key ->
                this[key] = 1.0f
            }
        }
    }

    // Track which components to delete - using mutableStateListOf
    val deletedComponents = remember { mutableStateListOf<String>() }

    // Calculate current totals based on multipliers
    val currentTotals by remember {
        derivedStateOf {
            var calories = 0.0
            var protein = 0.0
            var carbs = 0.0
            var fats = 0.0
            var totalWeight = 0.0

            initialComponents.forEach { (name, component) ->
                if (!deletedComponents.contains(name)) {
                    val multiplier = componentMultipliers[name] ?: 1.0f
                    calories += component.calories * multiplier
                    protein += component.protein * multiplier
                    carbs += component.carbs * multiplier
                    fats += component.fats * multiplier
                    totalWeight += (component.weight ?: 0.0) * multiplier
                }
            }

            ComponentTotals(calories, protein, carbs, fats, totalWeight)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Components") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Summary card at top
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        foodLog.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Current Totals:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )

                    Text(
                        "${currentTotals.calories.roundToInt()} cal",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Text(
                        "P: ${currentTotals.protein.roundToInt()}g | C: ${currentTotals.carbs.roundToInt()}g | F: ${currentTotals.fats.roundToInt()}g",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    // Show total weight if available
                    if (currentTotals.totalWeight > 0) {
                        Text(
                            "Total weight: ${currentTotals.totalWeight.roundToInt()}g",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    // Show comparison to original
                    val calorieChange = currentTotals.calories - foodLog.calories
                    if (kotlin.math.abs(calorieChange) > 1) {
                        Text(
                            "${if (calorieChange > 0) "+" else ""}${calorieChange.roundToInt()} cal from original",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Component list with weight for scrolling
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Adjust Components:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(
                    items = initialComponents.filter { !deletedComponents.contains(it.key) }.toList(),
                    key = { it.first }
                ) { (componentName, component) ->
                    ComponentCard(
                        name = componentName,
                        component = component,
                        multiplier = componentMultipliers[componentName] ?: 1.0f,
                        onMultiplierChange = { newMultiplier ->
                            componentMultipliers[componentName] = newMultiplier
                        },
                        onDelete = {
                            deletedComponents.add(componentName)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Cancel and Save buttons at bottom
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            // Build updated components map
                            val updatedComponents = initialComponents
                                .filter { !deletedComponents.contains(it.key) }
                                .mapValues { (name, component) ->
                                    val multiplier = componentMultipliers[name] ?: 1.0f
                                    FoodComponent(
                                        calories = component.calories * multiplier,
                                        protein = component.protein * multiplier,
                                        carbs = component.carbs * multiplier,
                                        fats = component.fats * multiplier,
                                        weight = component.weight?.let { it * multiplier }
                                    )
                                }

                            // Save updated food log
                            val updatedLog = foodLog.copy(
                                calories = currentTotals.calories,
                                protein = currentTotals.protein,
                                carbs = currentTotals.carbs,
                                fats = currentTotals.fats,
                                components = com.google.gson.Gson().toJson(updatedComponents)
                            )

                            onSave(updatedLog)
                            onBack()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}

@Composable
fun ComponentCard(
    name: String,
    component: FoodComponent,
    multiplier: Float,
    onMultiplierChange: (Float) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Component name and delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Show original values with weight
                    val originalText = buildString {
                        append("Original: ${component.calories.roundToInt()} cal")
                        if (component.weight != null && component.weight > 0) {
                            append(" (${component.weight.roundToInt()}g)")
                        }
                        append(" | P: ${component.protein.roundToInt()}g | C: ${component.carbs.roundToInt()}g | F: ${component.fats.roundToInt()}g")
                    }
                    Text(
                        originalText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete component",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Multiplier slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Amount:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        "${(multiplier * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = multiplier,
                    onValueChange = onMultiplierChange,
                    valueRange = 0.00f..2.0f,
                    steps = 7, // Creates steps: 0.00, 0.25, 0.50, 0.75, 1.00, 1.25, 1.50, 1.75, 2.00
                    modifier = Modifier.fillMaxWidth()
                )

                // Slider labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0%", style = MaterialTheme.typography.labelSmall)
                    Text("100%", style = MaterialTheme.typography.labelSmall)
                    Text("200%", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Show adjusted values
            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            // Build adjusted text with weight
            val adjustedText = buildString {
                append("Adjusted: ${(component.calories * multiplier).roundToInt()} cal")
                if (component.weight != null && component.weight > 0) {
                    append(" (${(component.weight * multiplier).roundToInt()}g)")
                }
                append(" | P: ${(component.protein * multiplier).roundToInt()}g")
                append(" | C: ${(component.carbs * multiplier).roundToInt()}g")
                append(" | F: ${(component.fats * multiplier).roundToInt()}g")
            }

            Text(
                adjustedText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

data class ComponentTotals(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
    val totalWeight: Double = 0.0
)