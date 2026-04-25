package com.danielag_nutritrack.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import com.danielag_nutritrack.app.data.*
import com.danielag_nutritrack.app.utils.ExerciseCalorieCalculator
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDialog(
    profile: UserProfile?,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var age by remember { mutableStateOf(profile?.age?.toString() ?: "") }          // Standard setting when making new profile
    var weight by remember { mutableStateOf(profile?.weight?.toString() ?: "") }    // Standard setting when making new profile
    var height by remember { mutableStateOf(profile?.height?.toString() ?: "") }    // Standard setting when making new profile
    var gender by remember { mutableStateOf(profile?.gender ?: Gender.MALE) }
    var activityLevel by remember { mutableStateOf(profile?.activityLevel ?: ActivityLevel.OFFICE_JOB) }
    var goal by remember { mutableStateOf(profile?.goal ?: Goal.MAINTAIN) }
    var weightChangeRate by remember { mutableStateOf(profile?.weightChangeRate ?: WeightChangeRate.RATE_050) }
    var waterGoal by remember { mutableStateOf(profile?.waterGoal?.toString() ?: "2000") }
    var targetWeight by remember { mutableStateOf(profile?.targetWeight?.toString() ?: "") }
    var intervalsAthleteId by remember { mutableStateOf(profile?.intervalsAthleteId ?: "") }
    var intervalsApiKey by remember { mutableStateOf(profile?.intervalsApiKey ?: "") }

    var genderExpanded by remember { mutableStateOf(false) }
    var activityExpanded by remember { mutableStateOf(false) }
    var goalExpanded by remember { mutableStateOf(false) }
    var rateExpanded by remember { mutableStateOf(false) }

    // Show rate selector only if goal is LOSE_WEIGHT or GAIN_MUSCLE
    val showRateSelector = goal == Goal.LOSE_WEIGHT || goal == Goal.GAIN_MUSCLE

    AlertDialog(
        onDismissRequest = {
            if (profile != null) {
                onDismiss()
            }
        },
        title = { Text(if (profile == null) "Create Your Profile" else "Edit Profile") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState()), // ADD THIS LINE
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it },
                    label = { Text("Age") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Current Weight (kg)") },  // CHANGED from "Weight (kg)"
                    modifier = Modifier.fillMaxWidth()
                )

                // NEW: Target Weight field
                OutlinedTextField(
                    value = targetWeight,
                    onValueChange = { targetWeight = it },
                    label = { Text("Target Weight (kg) - Optional") },
                    placeholder = { Text("e.g., 75") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it },
                    label = { Text("Height (cm)") },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = genderExpanded,
                    onExpandedChange = { genderExpanded = !genderExpanded }
                ) {
                    OutlinedTextField(
                        value = getGenderLabel(gender),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Gender") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = genderExpanded,
                        onDismissRequest = { genderExpanded = false }
                    ) {
                        Gender.values().forEach { g ->
                            DropdownMenuItem(
                                text = { Text(getGenderLabel(g)) },
                                onClick = {
                                    gender = g
                                    genderExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = activityExpanded,
                    onExpandedChange = { activityExpanded = !activityExpanded }
                ) {
                    OutlinedTextField(
                        value = getActivityLevelLabel(activityLevel),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Job Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = activityExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = activityExpanded,
                        onDismissRequest = { activityExpanded = false }
                    ) {
                        ActivityLevel.values().forEach { al ->
                            DropdownMenuItem(
                                text = { Text(getActivityLevelLabel(al)) },
                                onClick = {
                                    activityLevel = al
                                    activityExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = goalExpanded,
                    onExpandedChange = { goalExpanded = !goalExpanded }
                ) {
                    OutlinedTextField(
                        value = getGoalLabel(goal),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Goal") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = goalExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = goalExpanded,
                        onDismissRequest = { goalExpanded = false }
                    ) {
                        Goal.values().forEach { g ->
                            DropdownMenuItem(
                                text = { Text(getGoalLabel(g)) },
                                onClick = {
                                    goal = g
                                    goalExpanded = false
                                }
                            )
                        }
                    }
                }

                // Weight change rate selector (only shown for weight loss/gain goals)
                if (showRateSelector) {
                    ExposedDropdownMenuBox(
                        expanded = rateExpanded,
                        onExpandedChange = { rateExpanded = !rateExpanded }
                    ) {
                        OutlinedTextField(
                            value = getWeightChangeRateLabel(weightChangeRate),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Rate") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rateExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = rateExpanded,
                            onDismissRequest = { rateExpanded = false }
                        ) {
                            WeightChangeRate.values().forEach { rate ->
                                DropdownMenuItem(
                                    text = { Text(getWeightChangeRateLabel(rate)) },
                                    onClick = {
                                        weightChangeRate = rate
                                        rateExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Warning for aggressive rates
                    if (weightChangeRate == WeightChangeRate.RATE_075 || weightChangeRate == WeightChangeRate.RATE_100) {
                        Text(
                            "⚠️ This rate may affect your performance and energy levels",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                // Water goal field
                OutlinedTextField(
                    value = waterGoal,
                    onValueChange = { waterGoal = it },
                    label = { Text("Daily Water Goal (ml)") },
                    placeholder = { Text("e.g., 2000 (Recommended: 2000-3000)") },
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    "Intervals.icu (optional)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = intervalsAthleteId,
                    onValueChange = { intervalsAthleteId = it },
                    label = { Text("Athlete ID") },
                    placeholder = { Text("e.g., i12345") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = intervalsApiKey,
                    onValueChange = { intervalsApiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    UserProfile(
                        age = age.toIntOrNull() ?: 30,
                        weight = weight.toDoubleOrNull() ?: 70.0,
                        height = height.toDoubleOrNull() ?: 170.0,
                        gender = gender,
                        activityLevel = activityLevel,
                        goal = goal,
                        weightChangeRate = weightChangeRate,
                        waterGoal = waterGoal.toIntOrNull() ?: 2000,
                        targetWeight = targetWeight.toDoubleOrNull(),
                        intervalsAthleteId = intervalsAthleteId.trim().takeIf { it.isNotBlank() },
                        intervalsApiKey = intervalsApiKey.trim().takeIf { it.isNotBlank() }
                    )
                )
            }) {
                Text(if (profile == null) "Create Profile" else "Save")
            }
        },
        dismissButton = {
            if (profile != null) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

fun getActivityLevelLabel(level: ActivityLevel): String {
    return when (level) {
        ActivityLevel.OFFICE_JOB -> "Office Job"
        ActivityLevel.PHYSICAL_JOB -> "Physical Job"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodDialog(
    remainingCalls: Int,
    favorites: List<com.danielag_nutritrack.app.data.FavoriteMeal> = emptyList(),
    onDismiss: () -> Unit,
    onAnalyzeText: (String) -> Unit,
    onAnalyzeImage: (String) -> Unit,  // Keep this for compatibility but won't be used
    onManualEntry: (String, Double, Double?, Double?, Double?, MealCategory, String?) -> Unit,
    onAddFromFavorite: (com.danielag_nutritrack.app.data.FavoriteMeal) -> Unit = {},
    onDeleteFavorite: (com.danielag_nutritrack.app.data.FavoriteMeal) -> Unit = {},
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var textInput by remember { mutableStateOf("") }

    // Manual entry states
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fats by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(MealCategory.SNACK) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Add Food",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // API calls remaining indicator
                Text(
                    "AI Analysis: $remainingCalls/50 calls remaining today",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (remainingCalls > 5) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Text") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Image") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Manual") })
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Saved") })
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> {
                        // Text input tab - UNCHANGED
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Describe your food") },
                            placeholder = { Text("e.g., chicken breast with rice and vegetables") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                    1 -> {
                        // Image input tab - UPDATE THESE BUTTONS
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Choose how to add your food photo:",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            // Camera button - UPDATED
                            Button(
                                onClick = onCameraClick,  // Call the callback
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Take Photo")
                            }

                            // Gallery button - UPDATED
                            OutlinedButton(
                                onClick = onGalleryClick,  // Call the callback
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose from Gallery")
                            }
                        }
                    }
                    3 -> {
                        // Favorites tab
                        if (favorites.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Bookmark,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "No favorites yet",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Tap ☆ on any meal to save it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(favorites, key = { it.id }) { fav ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(fav.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    "${fav.calories.toInt()} cal  •  P:${fav.protein.toInt()}g  C:${fav.carbs.toInt()}g  F:${fav.fats.toInt()}g",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(onClick = { onAddFromFavorite(fav); onDismiss() }) {
                                                Icon(Icons.Default.Bookmark, "Add", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { onDeleteFavorite(fav) }) {
                                                Icon(Icons.Default.Delete, "Remove favorite", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // Manual entry tab
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.5f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Food Name *") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = calories,
                                onValueChange = { calories = it },
                                label = { Text("Calories *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = protein,
                                onValueChange = { protein = it },
                                label = { Text("Protein (g)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = carbs,
                                onValueChange = { carbs = it },
                                label = { Text("Carbs (g)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = fats,
                                onValueChange = { fats = it },
                                label = { Text("Fats (g)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            ExposedDropdownMenuBox(
                                expanded = categoryExpanded,
                                onExpandedChange = { categoryExpanded = !categoryExpanded }
                            ) {
                                OutlinedTextField(
                                    value = getMealCategoryLabel(selectedCategory),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Category") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = categoryExpanded,
                                    onDismissRequest = { categoryExpanded = false }
                                ) {
                                    MealCategory.values().forEach { category ->
                                        DropdownMenuItem(
                                            text = { Text(getMealCategoryLabel(category)) },
                                            onClick = {
                                                selectedCategory = category
                                                categoryExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("Notes") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            when (selectedTab) {
                                0 -> {
                                    if (textInput.isNotBlank() && remainingCalls > 0) {
                                        onAnalyzeText(textInput)
                                    }
                                }
                                2 -> {
                                    val cal = calories.toDoubleOrNull()
                                    if (name.isNotBlank() && cal != null) {
                                        onManualEntry(
                                            name,
                                            cal,
                                            protein.toDoubleOrNull(),
                                            carbs.toDoubleOrNull(),
                                            fats.toDoubleOrNull(),
                                            selectedCategory,
                                            notes.takeIf { it.isNotBlank() }
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = when (selectedTab) {
                            0 -> textInput.isNotBlank() && remainingCalls > 0
                            2 -> name.isNotBlank() && calories.toDoubleOrNull() != null
                            else -> false
                        }
                    ) {
                        Text(when (selectedTab) {
                            0 -> "Analyze"
                            2 -> "Add"
                            else -> "Add"
                        })
                    }
                }
            }
        }
    }
}

fun getMealCategoryLabel(category: MealCategory): String {
    return when (category) {
        MealCategory.BREAKFAST -> "Breakfast"
        MealCategory.LUNCH -> "Lunch"
        MealCategory.DINNER -> "Dinner"
        MealCategory.SNACK -> "Snack"
        MealCategory.FRUIT -> "Fruit"
        MealCategory.TRAINING -> "Training"
    }
}

@Composable
fun ImagePickerScreen(
    onImageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                // Convert URI to base64
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                // Compress and convert to base64
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
                val byteArray = outputStream.toByteArray()
                val base64 = Base64.encodeToString(byteArray, Base64.DEFAULT)

                onImageSelected(base64)
            } catch (e: Exception) {
                android.util.Log.e("ImagePicker", "Error processing image: ${e.message}")
                onDismiss()
            }
        } else {
            onDismiss()
        }
    }

    // Launch picker immediately when this composable is shown
    LaunchedEffect(Unit) {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // Show a loading dialog while picker is open
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Select a photo from your gallery")
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFoodDialog(
    foodLog: FoodLog,
    onDismiss: () -> Unit,
    onSave: (FoodLog) -> Unit,
    onEditComponents: () -> Unit
) {
    var name by remember { mutableStateOf(foodLog.name) }
    var calories by remember { mutableStateOf(foodLog.calories.toString()) }
    var protein by remember { mutableStateOf(foodLog.protein.toString()) }
    var carbs by remember { mutableStateOf(foodLog.carbs.toString()) }
    var fats by remember { mutableStateOf(foodLog.fats.toString()) }
    var category by remember { mutableStateOf(foodLog.category) }
    var notes by remember { mutableStateOf(foodLog.notes ?: "") }

    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Food") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it },
                    label = { Text("Calories") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it },
                        label = { Text("Protein (g)") },
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it },
                        label = { Text("Carbs (g)") },
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = fats,
                        onValueChange = { fats = it },
                        label = { Text("Fats (g)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = category.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        MealCategory.values().forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes - Optional") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            // NEW: Row with Components button (if applicable) and Save button
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Show "Edit Components" button only if components exist
                if (foodLog.components != null) {
                    OutlinedButton(
                        onClick = onEditComponents
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Components")
                    }
                }

                Button(
                    onClick = {
                        if (name.isNotBlank() && calories.isNotBlank()) {
                            onSave(
                                foodLog.copy(
                                    name = name,
                                    calories = calories.toDoubleOrNull() ?: foodLog.calories,
                                    protein = protein.toDoubleOrNull() ?: foodLog.protein,
                                    carbs = carbs.toDoubleOrNull() ?: foodLog.carbs,
                                    fats = fats.toDoubleOrNull() ?: foodLog.fats,
                                    category = category,
                                    notes = notes.ifBlank { null }
                                )
                            )
                        }
                    },
                    enabled = name.isNotBlank() && calories.isNotBlank()
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDialog(
    userWeight: Double,
    onSave: (exerciseType: String, calories: Int, duration: Int, note: String) -> Unit,  // SWAPPED ORDER
    onDismiss: () -> Unit
) {
    var exerciseType by remember { mutableStateOf(ExerciseCalorieCalculator.getSupportedExerciseTypes()[0]) }
    var expanded by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf("") }
    var caloriesInput by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val suggestedCalories = remember(exerciseType, duration, userWeight) {
        val durationInt = duration.toIntOrNull() ?: 0
        if (durationInt > 0 && userWeight > 0) {
            ExerciseCalorieCalculator.calculateCalories(exerciseType, durationInt, userWeight)
        } else {
            0
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Exercise") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = exerciseType,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Exercise Type") },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, "Dropdown")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        ExerciseCalorieCalculator.getSupportedExerciseTypes().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    exerciseType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Duration (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = caloriesInput,
                    onValueChange = { caloriesInput = it },
                    label = { Text("Calories (optional)") },
                    placeholder = {
                        if (suggestedCalories > 0) {
                            Text("Suggested: $suggestedCalories cal")
                        }
                    },
                    supportingText = {
                        if (caloriesInput.isEmpty() && suggestedCalories > 0) {
                            Text("Leave empty to use $suggestedCalories cal")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val durationInt = duration.toIntOrNull() ?: 0

                    val finalCalories = if (caloriesInput.isNotEmpty()) {
                        caloriesInput.toIntOrNull() ?: 0
                    } else {
                        suggestedCalories
                    }

                    if (durationInt > 0 && finalCalories > 0) {
                        onSave(exerciseType, finalCalories, durationInt, note)  // SWAPPED: calories BEFORE duration
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun StepsWeightDialog(
    activity: DailyActivity?,
    onDismiss: () -> Unit,
    onSave: (Int, Double?, Int) -> Unit
) {
    var steps by remember { mutableStateOf(activity?.steps?.toString() ?: "0") }
    var weight by remember { mutableStateOf(activity?.weight?.toString() ?: "") }
    var water by remember { mutableStateOf(activity?.waterIntake?.toString() ?: "0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily Activity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = steps,
                    onValueChange = { steps = it },
                    label = { Text("Steps") },
                    placeholder = { Text("e.g., 10000") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight (kg) - Optional") },
                    placeholder = { Text("e.g., 75.5") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = water,
                    onValueChange = { water = it },
                    label = { Text("Water Intake (ml)") },
                    placeholder = { Text("e.g., 2000") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Track your daily steps, weight, and water intake.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    steps.toIntOrNull() ?: 0,
                    weight.toDoubleOrNull(),
                    water.toIntOrNull() ?: 0
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExerciseDialog(
    exercise: ExerciseLog,
    userWeight: Double,
    onSave: (ExerciseLog) -> Unit,
    onDismiss: () -> Unit
) {
    var exerciseType by remember { mutableStateOf(exercise.exerciseType) }
    var expanded by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(exercise.duration?.toString() ?: "") }
    var caloriesInput by remember { mutableStateOf(exercise.caloriesBurned.toString()) }
    var note by remember { mutableStateOf(exercise.notes ?: "") }

    val suggestedCalories = remember(exerciseType, duration, userWeight) {
        val durationInt = duration.toIntOrNull() ?: 0
        if (durationInt > 0 && userWeight > 0) {
            ExerciseCalorieCalculator.calculateCalories(exerciseType, durationInt, userWeight)
        } else {
            0
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Exercise") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = exerciseType,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Exercise Type") },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, "Dropdown")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        ExerciseCalorieCalculator.getSupportedExerciseTypes().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    exerciseType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Duration (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = caloriesInput,
                    onValueChange = { caloriesInput = it },
                    label = { Text("Calories") },
                    placeholder = {
                        if (suggestedCalories > 0 && caloriesInput != suggestedCalories.toString()) {
                            Text("Suggested: $suggestedCalories cal")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val durationInt = duration.toIntOrNull()
                    val finalCalories = caloriesInput.toIntOrNull() ?: 0

                    if (finalCalories > 0) {
                        onSave(
                            exercise.copy(
                                exerciseType = exerciseType,
                                caloriesBurned = finalCalories,
                                duration = durationInt,
                                notes = note.ifBlank { null }
                            )
                        )
                    }
                },
                enabled = caloriesInput.toIntOrNull() != null && caloriesInput.toIntOrNull()!! > 0
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Helper Functions
fun getGenderLabel(gender: Gender): String {
    return when (gender) {
        Gender.MALE -> "Male"
        Gender.FEMALE -> "Female"
    }
}

fun getGoalLabel(goal: Goal): String {
    return when (goal) {
        Goal.LOSE_WEIGHT -> "Lose Weight"
        Goal.MAINTAIN -> "Maintain Weight"
        Goal.GAIN_MUSCLE -> "Gain Muscle"
    }
}

fun getWeightChangeRateLabel(rate: WeightChangeRate): String {
    return when (rate) {
        WeightChangeRate.RATE_025 -> "0.25 kg/week"
        WeightChangeRate.RATE_050 -> "0.50 kg/week"
        WeightChangeRate.RATE_075 -> "0.75 kg/week ⚠️"
        WeightChangeRate.RATE_100 -> "1.00 kg/week ⚠️"
    }
}