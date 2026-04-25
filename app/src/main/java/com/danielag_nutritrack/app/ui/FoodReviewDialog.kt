package com.danielag_nutritrack.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Dialog to review AI-analyzed food before saving
 * Shows confidence score, macros, and description
 */
@Composable
fun FoodReviewDialog(
    name: String,
    calories: Double,
    protein: Double,
    carbs: Double,
    fats: Double,
    notes: String?,
    isRefining: Boolean = false,
    onRefine: (String) -> Unit = {},
    onSaveAsFavorite: () -> Unit = {},
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var correctionText by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f) // Max 90% of screen height
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Review Meal",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        IconButton(onClick = onSaveAsFavorite) {
                            Icon(Icons.Default.Bookmark, "Save as Favorite", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Meal name
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Calories - big and prominent
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${calories.toInt()}",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "calories",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Macros
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MacroColumn("Protein", protein, "🥩")
                    MacroColumn("Carbs", carbs, "🍞")
                    MacroColumn("Fats", fats, "🥑")
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                // Confidence and description
                notes?.let { notesText ->
                    // Extract confidence from notes
                    val confidenceRegex = "Confidence: (\\d+)/10".toRegex()
                    val confidenceMatch = confidenceRegex.find(notesText)

                    if (confidenceMatch != null) {
                        val confidence = confidenceMatch.groupValues[1].toIntOrNull() ?: 5

                        // Confidence display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "AI Confidence:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val confidenceColor = when (confidence) {
                                    in 1..3 -> MaterialTheme.colorScheme.error
                                    in 4..6 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }

                                Text(
                                    "$confidence/10",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = confidenceColor,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                val stars = when (confidence) {
                                    in 1..3 -> "⭐"
                                    in 4..6 -> "⭐⭐"
                                    else -> "⭐⭐⭐"
                                }
                                Text(
                                    stars,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Confidence message
                        val confidenceMessage = when (confidence) {
                            in 1..3 -> "⚠️ Low confidence - please verify the details"
                            in 4..6 -> "⚡ Moderate confidence - details look reasonable"
                            else -> "✅ High confidence - details look accurate"
                        }

                        Text(
                            confidenceMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Description
                        val description = notesText.substringAfter("\n\n", "").trim()
                        if (description.isNotEmpty()) {
                            Text(
                                "Description:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(12.dp))

                // Correction chat input
                OutlinedTextField(
                    value = correctionText,
                    onValueChange = { correctionText = it },
                    label = { Text("Correct the analysis...") },
                    placeholder = { Text("e.g. it's 200g not 100g") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRefining,
                    singleLine = false,
                    maxLines = 3,
                    trailingIcon = {
                        if (isRefining) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    if (correctionText.isNotBlank()) {
                                        onRefine(correctionText)
                                        correctionText = ""
                                    }
                                },
                                enabled = correctionText.isNotBlank()
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send correction")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Meal")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "You can edit this meal later by tapping on it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun MacroColumn(name: String, amount: Double, emoji: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            emoji,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "${amount.toInt()}g",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}