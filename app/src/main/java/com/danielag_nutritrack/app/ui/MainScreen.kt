package com.danielag_nutritrack.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.danielag_nutritrack.app.ui.ComponentEditScreen
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlin.math.abs
import com.danielag_nutritrack.app.data.*
import com.danielag_nutritrack.app.R
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val weightHistory by viewModel.weightHistory.collectAsState()
    val exerciseLogs by viewModel.exerciseLogs.collectAsState()
    val remainingApiCalls by viewModel.remainingApiCalls.collectAsState()
    val weeklySummary by viewModel.weeklySummary.collectAsState()
    val monthlySummary by viewModel.monthlySummary.collectAsState()
    val summaryLoading by viewModel.summaryLoading.collectAsState()
    val pendingAnalyzedFood by viewModel.pendingAnalyzedFood.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val isRefining by viewModel.isRefining.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncMessage()
        }
    }

    var showProfileDialog by remember { mutableStateOf(false) }
    var showAddFoodDialog by remember { mutableStateOf(false) }
    var showExerciseDialog by remember { mutableStateOf(false) }
    var showStepsWeightDialog by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var showImagePicker by remember { mutableStateOf(false) }
    var editingFoodComponents by remember { mutableStateOf<FoodLog?>(null) }

    // Edit states
    var editingFoodLog by remember { mutableStateOf<FoodLog?>(null) }
    var editingExercise by remember { mutableStateOf<ExerciseLog?>(null) }

    // Auto-sync from intervals.icu on every app open / resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && viewModel.isIntervalsConfigured) {
                viewModel.syncFromIntervals()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Only check for profile once on first composition
    LaunchedEffect(Unit) {
        // Wait a moment for the profile to load from database
        kotlinx.coroutines.delay(100)
        if (uiState.userProfile == null) {
            showProfileDialog = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("NutriTrack")
                        Text(
                            text = formatDate(viewModel.selectedDate.collectAsState().value),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    if (viewModel.isIntervalsConfigured) {
                        IconButton(
                            onClick = { viewModel.syncFromIntervals() },
                            enabled = !isSyncing
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Sync, "Sync fra intervals.icu")
                            }
                        }
                    }
                    IconButton(onClick = { showSummaryDialog = true }) {
                        Icon(Icons.Default.Summarize, "Summary")
                    }
                    IconButton(onClick = { showProfileDialog = true }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!showCamera && !showImagePicker) {
                Column {
                    FloatingActionButton(
                        onClick = { showAddFoodDialog = true },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.Restaurant, "Add Food")
                    }
                    FloatingActionButton(
                        onClick = { showExerciseDialog = true }
                    ) {
                        Icon(Icons.Default.FitnessCenter, "Log Exercise")
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Background image - REPLACE R.drawable.background with your image name
            // Comment out the next 7 lines if you haven't added the background image yet
            Image(
                painter = painterResource(id = R.drawable.background),
                contentDescription = "Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = if (isSystemInDarkTheme()) 0.24f else 0.45f
            )

            // Content on top of background
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .swipeToNavigateDates(
                            onSwipeLeft = {
                                // Swipe left = next day
                                val nextDay = getNextDay(viewModel.selectedDate.value)
                                if (!isToday(viewModel.selectedDate.value)) { // Only if not already on today
                                    viewModel.changeSelectedDate(nextDay)
                                }
                            },
                            onSwipeRight = {
                                // Swipe right = previous day
                                viewModel.changeSelectedDate(getPreviousDay(viewModel.selectedDate.value))
                            }
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Date navigation buttons
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                viewModel.changeSelectedDate(getPreviousDay(viewModel.selectedDate.value))
                            }) {
                                Icon(Icons.Default.ArrowBack, "Previous Day")
                            }

                            Text(
                                text = formatDateFull(viewModel.selectedDate.collectAsState().value),
                                style = MaterialTheme.typography.titleMedium
                            )

                            IconButton(
                                onClick = {
                                    viewModel.changeSelectedDate(getNextDay(viewModel.selectedDate.value))
                                },
                                enabled = !isToday(viewModel.selectedDate.collectAsState().value)
                            ) {
                                Icon(Icons.Default.ArrowForward, "Next Day")
                            }
                        }
                    }

                    // Stats Cards
                    item {
                        DailyStatsCard(
                            uiState = uiState,
                            exerciseLogs = exerciseLogs,
                            weightHistory = weightHistory,
                            onStepsWeightClick = { showStepsWeightDialog = true }
                        )
                    }

                    item {
                        MacrosCard(uiState)
                    }

                    // Water Intake Card
                    item {
                        WaterIntakeCard(
                            currentIntake = uiState.dailyActivity?.waterIntake ?: 0,
                            dailyGoal = uiState.userProfile?.waterGoal ?: 2000,
                            onAddWater = { amount -> viewModel.addWater(amount) }
                        )
                    }

                    // Mini weight chart
                    if (weightHistory.isNotEmpty()) {
                        item {
                            MiniWeightChartWithTarget(
                                weightHistory = weightHistory,
                                currentDayActivity = uiState.dailyActivity,
                                targetWeight = uiState.userProfile?.targetWeight
                            )
                        }
                    }

                    // Food Logs
                    item {
                        Text(
                            "Today's Meals",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }

                    items(uiState.foodLogs.reversed()) { log ->
                        FoodLogCard(
                            log = log,
                            onEdit = { editingFoodLog = it },
                            onDelete = { viewModel.deleteLog(it) }
                        )
                    }

                    // Exercise Logs
                    if (exerciseLogs.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Today's Exercises",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }

                        items(exerciseLogs.reversed()) { exercise ->
                            ExerciseLogCard(
                                exercise = exercise,
                                onEdit = { editingExercise = it },
                                onDelete = { viewModel.deleteExercise(it) }
                            )
                        }
                    }
                }
            }
        }

        // Dialogs
        if (showProfileDialog) {
            ProfileDialog(
                profile = uiState.userProfile,
                onDismiss = { showProfileDialog = false },
                onSave = {
                    viewModel.saveUserProfile(it)
                    showProfileDialog = false
                }
            )
        }

        if (showAddFoodDialog) {
            AddFoodDialog(
                remainingCalls = remainingApiCalls,
                onDismiss = { showAddFoodDialog = false },
                onAnalyzeText = { text ->
                    viewModel.analyzeTextFood(text)
                    showAddFoodDialog = false
                },
                onAnalyzeImage = { base64 ->
                    // This won't be called directly anymore
                },
                onManualEntry = { name, calories, protein, carbs, fats, category, notes ->
                    viewModel.addManualFood(name, calories, protein, carbs, fats, category, notes)
                    showAddFoodDialog = false
                },
                onCameraClick = {
                    showAddFoodDialog = false
                    showCamera = true
                },
                onGalleryClick = {
                    showAddFoodDialog = false
                    showImagePicker = true
                }
            )
        }

        if (showCamera) {
            CameraScreen(
                onImageCaptured = { base64 ->
                    if (remainingApiCalls > 0) {
                        viewModel.analyzeImageFood(base64)
                    }
                    showCamera = false
                },
                onDismiss = { showCamera = false }
            )
        }

        if (showImagePicker) {
            ImagePickerScreen(
                onImageSelected = { base64 ->
                    if (remainingApiCalls > 0) {
                        viewModel.analyzeImageFood(base64)
                    }
                    showImagePicker = false
                },
                onDismiss = { showImagePicker = false }
            )
        }

        pendingAnalyzedFood?.let { analyzed ->
            FoodReviewDialog(
                name = analyzed.name,
                calories = analyzed.calories,
                protein = analyzed.protein,
                carbs = analyzed.carbs,
                fats = analyzed.fats,
                notes = analyzed.notes,
                isRefining = isRefining,
                onRefine = { viewModel.refineFood(it) },
                onConfirm = { viewModel.savePendingFood() },
                onDismiss = { viewModel.clearPendingFood() }
            )
        }

        val userWeight = uiState.userProfile?.weight ?: 70.0

        if (showExerciseDialog) {
            ExerciseDialog(
                userWeight = userWeight,
                onSave = { type, calories, duration, note ->
                    viewModel.logExercise(type, calories, duration, note)
                    showExerciseDialog = false
                },
                onDismiss = { showExerciseDialog = false }
            )
        }

        // Edit Food Dialog
        editingFoodLog?.let { foodLog ->
            EditFoodDialog(
                foodLog = foodLog,
                onDismiss = { editingFoodLog = null },
                onSave = { updatedLog ->
                    viewModel.updateLog(updatedLog)
                    editingFoodLog = null
                },
                onEditComponents = {
                    // Close edit dialog and open component editor
                    editingFoodLog = null
                    editingFoodComponents = foodLog
                }
            )
        }

        // Edit Exercise Dialog
        editingExercise?.let { exercise ->
            EditExerciseDialog(
                userWeight = userWeight,
                exercise = exercise,
                onDismiss = { editingExercise = null },
                onSave = { updatedExercise ->
                    viewModel.updateExercise(updatedExercise)
                    editingExercise = null
                }
            )
        }

        // Component Edit Screen
        editingFoodComponents?.let { foodLog ->
            ComponentEditScreen(
                foodLog = foodLog,
                onSave = { updatedLog ->
                    viewModel.updateLog(updatedLog)
                    editingFoodComponents = null
                },
                onBack = { editingFoodComponents = null }
            )
        }

        if (showStepsWeightDialog) {
            StepsWeightDialog(
                activity = uiState.dailyActivity,
                onDismiss = { showStepsWeightDialog = false },
                onSave = { steps, weight, water ->
                    viewModel.updateStepsWeightAndWater(steps, weight, water)
                    showStepsWeightDialog = false
                }
            )
        }

        if (showSummaryDialog) {
            SummaryDialog(
                onDismiss = { showSummaryDialog = false },
                onLoadWeekly = { viewModel.loadWeeklySummary() },
                onLoadMonthly = { viewModel.loadMonthlySummary() },
                weeklySummary = weeklySummary,
                monthlySummary = monthlySummary,
                isLoading = summaryLoading
            )
        }

        // Error Snackbar
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}

/**
 * Modifier that detects horizontal swipes for date navigation
 * Swipe right = previous day
 * Swipe left = next day
 */
fun Modifier.swipeToNavigateDates(
    onSwipeLeft: () -> Unit,  // Next day
    onSwipeRight: () -> Unit, // Previous day
    enabled: Boolean = true
): Modifier = this.then(
    if (enabled) {
        Modifier.pointerInput(Unit) {
            var totalDragX = 0f
            val threshold = 100f // Minimum swipe distance in pixels

            detectHorizontalDragGestures(
                onDragEnd = {
                    // When user releases, check if swipe was long enough
                    if (abs(totalDragX) >= threshold) {
                        if (totalDragX > 0) {
                            onSwipeRight() // Swiped right - go to previous day
                        } else {
                            onSwipeLeft() // Swiped left - go to next day
                        }
                    }
                    totalDragX = 0f
                },
                onDragCancel = {
                    totalDragX = 0f
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    totalDragX += dragAmount
                }
            )
        }
    } else {
        Modifier
    }
)

@Composable
fun DailyStatsCard(
    uiState: UiState,
    exerciseLogs: List<ExerciseLog>,
    weightHistory: List<DailyActivity>,
    onStepsWeightClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Daily Overview",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Consumed", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${uiState.totalCalories.toInt()} cal",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column {
                    Text("Target", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${uiState.targetCalories.toInt()} cal",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Column {
                    val remaining = uiState.targetCalories - uiState.totalCalories
                    Text("Remaining", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${if (remaining > 0) "+" else ""}${remaining.toInt()} cal",
                        style = MaterialTheme.typography.headlineMedium,
                        color = when {
                            kotlin.math.abs(remaining) <= 200 -> Color(0xFF4CAF50)
                            remaining < -200 -> Color(0xFFF44336)
                            else -> Color(0xFFFFEB3B)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Progress toward target
            val progress = if (uiState.targetCalories > 0) {
                (uiState.totalCalories / uiState.targetCalories).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }

            val remaining = uiState.targetCalories - uiState.totalCalories
            val progressBarColor = when {
                kotlin.math.abs(remaining) <= 200 -> Color(0xFF4CAF50) // Green
                remaining < -200 -> Color(0xFFF44336) // Red
                else -> Color(0xFFFFEB3B) // Yellow
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                color = progressBarColor,
                trackColor = progressBarColor.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // TDEE breakdown
            TdeeBreakdownRow("Burned (TDEE)", uiState.caloriesBurned)
            TdeeBreakdownRow("BMR", uiState.bmr)
            TdeeBreakdownRow("Steps (NEAT)", uiState.neat)
            TdeeBreakdownRow("Exercise (EAT)", uiState.eat)
            TdeeBreakdownRow("Digestion (TEF)", uiState.tef)

            Spacer(modifier = Modifier.height(8.dp))

            val displayWeight = uiState.dailyActivity?.weight
                ?: weightHistory.lastOrNull()?.weight
            val isWeightFromToday = uiState.dailyActivity?.weight != null

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (displayWeight != null) {
                    Column {
                        Text(
                            "Weight: ${"%.1f".format(displayWeight)} kg",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (!isWeightFromToday) {
                            Text(
                                "(from previous day)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Text(
                    "Steps: ${uiState.dailyActivity?.steps ?: 0}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onStepsWeightClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Update Steps, Weight & Water")
            }
        }
    }
}

@Composable
private fun TdeeBreakdownRow(label: String, value: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${value.toInt()} kcal", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun MacrosCard(uiState: UiState) {
    val currentWeight = uiState.dailyActivity?.weight ?: uiState.userProfile?.weight ?: 0.0
    val proteinGoal = if (currentWeight > 0) currentWeight * 1.6 else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Macros", style = MaterialTheme.typography.titleLarge)

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MacroItem("Protein", uiState.totalProtein, "🥩", goal = proteinGoal)
                MacroItem("Carbs", uiState.totalCarbs, "🍞")
                MacroItem("Fats", uiState.totalFats, "🥑")
            }
        }
    }
}

@Composable
fun WaterIntakeCard(currentIntake: Int, dailyGoal: Int, onAddWater: (Int) -> Unit) {
    val progress = (currentIntake.toFloat() / dailyGoal).coerceIn(0f, 1f)
    val glasses = currentIntake / 250

    var showCustomDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Water Intake 💧", style = MaterialTheme.typography.titleLarge)

                IconButton(onClick = { showCustomDialog = true }) {
                    Icon(Icons.Default.Edit, "Custom Amount")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        "$currentIntake ml",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "~$glasses glasses",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    "Goal: $dailyGoal ml",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = if (currentIntake >= dailyGoal)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { onAddWater(250) },
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                ) {
                    Text("+250ml")
                }
                Button(
                    onClick = { onAddWater(500) },
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                ) {
                    Text("+500ml")
                }
                Button(
                    onClick = { onAddWater(750) },
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                ) {
                    Text("+750ml")
                }
            }
        }
    }

    if (showCustomDialog) {
        CustomWaterDialog(
            onDismiss = { showCustomDialog = false },
            onAdd = { amount ->
                onAddWater(amount)
                showCustomDialog = false
            }
        )
    }
}

@Composable
fun CustomWaterDialog(
    onDismiss: () -> Unit,
    onAdd: (Int) -> Unit
) {
    var amount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Water") },
        text = {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount (ml)") },
                placeholder = { Text("e.g., 350") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    amount.toIntOrNull()?.let { onAdd(it) }
                },
                enabled = amount.toIntOrNull() != null && amount.toIntOrNull()!! > 0
            ) {
                Text("Add")
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
fun MacroItem(name: String, amount: Double, emoji: String, goal: Double? = null) {
    val progress = if (goal != null && goal > 0) (amount / goal).toFloat().coerceIn(0f, 1f) else null
    val goalMet = progress != null && progress >= 1f
    val ringColor = if (goalMet) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            if (progress != null) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(88.dp),
                    strokeWidth = 5.dp,
                    color = ringColor,
                    trackColor = ringColor.copy(alpha = 0.15f)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(if (progress != null) 8.dp else 0.dp)
            ) {
                Text(emoji, style = MaterialTheme.typography.headlineMedium)
                Text("${amount.toInt()}g", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.bodySmall)
        if (goal != null) {
            Text(
                "${amount.toInt()}/${goal.toInt()}g",
                style = MaterialTheme.typography.bodySmall,
                color = if (goalMet) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FoodLogCard(
    log: FoodLog,
    onEdit: (FoodLog) -> Unit,
    onDelete: (FoodLog) -> Unit,
) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row with name and action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        log.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "${log.category.name} • ${dateFormat.format(log.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = { onEdit(log) }) {
                        Icon(Icons.Default.Edit, "Edit")
                    }
                    IconButton(onClick = { onDelete(log) }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Nutritional info
            Text(
                "${log.calories.toInt()} cal | P: ${log.protein.toInt()}g C: ${log.carbs.toInt()}g F: ${log.fats.toInt()}g",
                style = MaterialTheme.typography.bodyMedium
            )

            // Expandable section for description and confidence
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Confidence score and description (if available in notes)
                    log.notes?.let { notes ->
                        // Try to extract confidence from notes
                        val confidenceRegex = "Confidence: (\\d+)/10".toRegex()
                        val confidenceMatch = confidenceRegex.find(notes)

                        if (confidenceMatch != null) {
                            val confidence = confidenceMatch.groupValues[1].toIntOrNull() ?: 5

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Text(
                                    "Confidence: ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Visual confidence indicator with color coding
                                val confidenceColor = when (confidence) {
                                    in 1..3 -> MaterialTheme.colorScheme.error
                                    in 4..6 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }

                                Text(
                                    "$confidence/10",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = confidenceColor,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                // Star rating visualization
                                val stars = when (confidence) {
                                    in 1..3 -> "⭐"
                                    in 4..6 -> "⭐⭐"
                                    else -> "⭐⭐⭐"
                                }
                                Text(stars, style = MaterialTheme.typography.bodyMedium)
                            }

                            // Extract and show description (everything after the confidence line)
                            val description = notes.substringAfter("\n\n", "").trim()
                            if (description.isNotEmpty()) {
                                Text(
                                    "Description:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 20.sp
                                )
                            }
                        } else {
                            // If no confidence format found, just show the notes
                            Text(
                                notes,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    // Tap hint
                    Text(
                        "Tap to collapse",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }

            // Show expand hint when collapsed
            if (!isExpanded && log.notes != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Tap to see details",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("▼", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    /*if (log.components != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onEditComponents(log) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Edit Components")
        }
    }*/
}

@Composable
fun MiniWeightChartWithTarget(
    weightHistory: List<DailyActivity>,
    currentDayActivity: DailyActivity?,
    targetWeight: Double?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Weight Trend", style = MaterialTheme.typography.titleMedium)

                if (weightHistory.size >= 2) {
                    val latest = weightHistory.last().weight ?: 0.0
                    val previous = weightHistory[weightHistory.size - 2].weight ?: latest
                    val change = latest - previous
                    val changeText = if (change > 0) "+${String.format("%.1f", change)}"
                    else String.format("%.1f", change)

                    Text(
                        "Since last weigh in: $changeText kg",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (change > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (weightHistory.isNotEmpty()) {
                val currentWeight = currentDayActivity?.weight
                    ?: weightHistory.lastOrNull()?.weight

                val isFromToday = currentDayActivity?.weight != null

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            "Current: ${currentWeight?.let { String.format("%.1f kg", it) } ?: "N/A"}",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        if (!isFromToday && currentWeight != null) {
                            Text(
                                "(from previous day)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    if (targetWeight != null && currentWeight != null) {
                        val rangeFromTarget = currentWeight - targetWeight
                        val rangeText = if (rangeFromTarget > 0)
                            "+${String.format("%.1f", rangeFromTarget)}"
                        else
                            String.format("%.1f", rangeFromTarget)

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Target: ${String.format("%.1f kg", targetWeight)}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                "$rangeText kg",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (kotlin.math.abs(rangeFromTarget) <= 2.0)
                                    Color(0xFF4CAF50)
                                else if (rangeFromTarget > 0)
                                    MaterialTheme.colorScheme.error
                                else
                                    Color(0xFF2196F3)
                            )
                        }
                    }
                }
            }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    currentDate: Date,
    onDateSelected: (Date) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentDate.time

    )

    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    onDateSelected(Date(millis))
                }
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

fun formatDate(date: Date): String {
    val calendar = java.util.Calendar.getInstance()
    calendar.time = date

    val today = java.util.Calendar.getInstance()
    val yesterday = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, -1)
    }

    return when {
        calendar.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                calendar.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR) -> {
            "Today"
        }
        calendar.get(java.util.Calendar.YEAR) == yesterday.get(java.util.Calendar.YEAR) &&
                calendar.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR) -> {
            "Yesterday"
        }
        else -> {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
        }
    }
}

fun formatDateFull(date: Date): String {
    return when {
        isToday(date) -> "Today, ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)}"
        isYesterday(date) -> "Yesterday, ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)}"
        else -> SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault()).format(date)
    }
}

fun isToday(date: Date): Boolean {
    val calendar = java.util.Calendar.getInstance()
    calendar.time = date

    val today = java.util.Calendar.getInstance()

    return calendar.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
            calendar.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
}

fun isYesterday(date: Date): Boolean {
    val calendar = java.util.Calendar.getInstance()
    calendar.time = date

    val yesterday = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, -1)
    }

    return calendar.get(java.util.Calendar.YEAR) == yesterday.get(java.util.Calendar.YEAR) &&
            calendar.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR)
}

fun getPreviousDay(date: Date): Date {
    val calendar = java.util.Calendar.getInstance()
    calendar.time = date
    calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
    return calendar.time
}

fun getNextDay(date: Date): Date {
    val calendar = java.util.Calendar.getInstance()
    calendar.time = date
    calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
    return calendar.time
}

@Composable
fun ExerciseLogCard(exercise: ExerciseLog, onEdit: (ExerciseLog) -> Unit, onDelete: (ExerciseLog) -> Unit) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    exercise.exerciseType,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "${exercise.caloriesBurned} cal burned • ${dateFormat.format(exercise.timestamp)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                exercise.duration?.let {
                    Text(
                        "$it minutes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                exercise.notes?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Row {
                IconButton(onClick = { onEdit(exercise) }) {
                    Icon(
                        Icons.Default.Edit,
                        "Edit",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                IconButton(onClick = { onDelete(exercise) }) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}