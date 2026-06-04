package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.Surface
import android.media.MediaPlayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DateHelper
import com.example.data.Task
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun TodoAppScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.safeAttribution()
    
    // Preferences persistence layer
    val sharedPrefs = remember { context.getSharedPreferences("todo_settings", Context.MODE_PRIVATE) }
    var isWallpaperEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("wallpaper_enabled", true)) }
    var selectedWallpaperStyle by remember { mutableStateOf(sharedPrefs.getString("wallpaper_style", "liquid_glass") ?: "liquid_glass") }
    var customWallpaperUrl by remember { mutableStateOf(sharedPrefs.getString("custom_wallpaper_url", "") ?: "") }
    var isVibrationEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("vibrations_enabled", true)) }
    var isSoundsEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("sounds_enabled", true)) }
    var isNotificationsEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("notifications_enabled", true)) }
 
    // Helpers to persist values instantly
    val updateWallpaperEnabled = { enabled: Boolean ->
        isWallpaperEnabled = enabled
        sharedPrefs.edit().putBoolean("wallpaper_enabled", enabled).apply()
    }
    val updateWallpaperStyle = { style: String ->
        selectedWallpaperStyle = style
        sharedPrefs.edit().putString("wallpaper_style", style).apply()
    }
    val updateCustomWallpaperUrl = { url: String ->
        customWallpaperUrl = url
        sharedPrefs.edit().putString("custom_wallpaper_url", url).apply()
    }
    val updateVibrationEnabled = { enabled: Boolean ->
        isVibrationEnabled = enabled
        sharedPrefs.edit().putBoolean("vibrations_enabled", enabled).apply()
    }
    val updateSoundsEnabled = { enabled: Boolean ->
        isSoundsEnabled = enabled
        sharedPrefs.edit().putBoolean("sounds_enabled", enabled).apply()
    }
    val updateNotificationsEnabled = { enabled: Boolean ->
        isNotificationsEnabled = enabled
        sharedPrefs.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    // Built-in low-latency sound system Beeps and Chimes
    val playSystemTick = {
        if (isSoundsEnabled) {
            try {
                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 45)
                tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 80)
            } catch (e: Exception) {}
        }
    }

    val playSuccessChime = {
        if (isSoundsEnabled) {
            try {
                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 75)
                tg.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 220)
            } catch (e: Exception) {}
        }
    }

    // High performance discrete haptic pulse
    val triggerVibePulse = {
        if (isVibrationEnabled) {
            try {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(25, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(25)
                    }
                }
            } catch (e: Exception) {}
        }
    }

    // State collection
    val currentDate by viewModel.currentDate.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val streakState by viewModel.streak.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf("home") } // "home" or "settings"

    // Intercept phone's physical back button to return to home screen instead of closing the app
    BackHandler(enabled = currentScreen == "settings") {
        currentScreen = "home"
    }

    var showFireStreakAnimation by remember { mutableStateOf(false) }
    var fireAnimationStreakValue by remember { mutableIntStateOf(0) }
    var showPermissionExplanation by remember { mutableStateOf(false) }

    // Dynamic Permission Handling for Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Notification alerts enabled successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notification alerts blocked. App will not beep on scheduled times.", Toast.LENGTH_LONG).show()
        }
    }

    // Check notification permission when entering screen
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                showPermissionExplanation = true
            }
        }
    }

    // Observe ViewModel events (Toast, success sound, registry results)
    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is UiEvent.SaveSuccess -> {
                    playSuccessChime()
                    triggerVibePulse()
                    fireAnimationStreakValue = event.newStreakCount
                    showFireStreakAnimation = true
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // High polish multi-style dynamic backdrop layers
        DynamicAppBackground(
            isWallpaperEnabled = isWallpaperEnabled,
            selectedWallpaperStyle = selectedWallpaperStyle,
            customWallpaperUrl = customWallpaperUrl
        )

        // Screen transition
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (targetState == "settings") {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                } else {
                    slideInHorizontally { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                }
            },
            label = "screenTransition"
        ) { screen ->
            if (screen == "settings") {
                SettingsScreenView(
                    currentDate = currentDate,
                    isWallpaperEnabled = isWallpaperEnabled,
                    onWallpaperEnabledChanged = updateWallpaperEnabled,
                    selectedWallpaperStyle = selectedWallpaperStyle,
                    onWallpaperStyleChanged = updateWallpaperStyle,
                    customWallpaperUrl = customWallpaperUrl,
                    onCustomWallpaperUrlChanged = updateCustomWallpaperUrl,
                    isVibrationEnabled = isVibrationEnabled,
                    onVibrationEnabledChanged = updateVibrationEnabled,
                    isSoundsEnabled = isSoundsEnabled,
                    onSoundsEnabledChanged = updateSoundsEnabled,
                    isNotificationsEnabled = isNotificationsEnabled,
                    onNotificationsEnabledChanged = updateNotificationsEnabled,
                    onBack = { currentScreen = "home" },
                    viewModel = viewModel
                )
            } else {
                // Primary Surface Scaffold
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    floatingActionButton = {
                        // Persistent saving trigger at bottom right
                        FloatingActionButton(
                            onClick = { viewModel.saveCurrentDay() },
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            modifier = Modifier
                                .testTag("save_day_fab")
                                .glassButton(isCircle = true)
                                .size(64.dp),
                            elevation = FloatingActionButtonDefaults.elevation(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save and Register Completed Day Checklist",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(top = 16.dp)
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Top Header Row with Date on Left, Settings on Right
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier.weight(1f)
                            ) {
                                GlassText(
                                    text = DateHelper.getDayAndDateWithMonth(currentDate),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            // Settings gear button at top right (simulation forward day button removed!)
                            IconButton(
                                onClick = { currentScreen = "settings" },
                                modifier = Modifier
                                    .glassButton(isCircle = true)
                                    .testTag("settings_button")
                                    .size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Open App Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Note: Date simulation has been removed to preserve strict scheduling.

                        // 1. STATS CAPTURE: Fire streak visual panel
                        val currentStreakCount = streakState?.streakCount ?: 0
                        val lastSavedDay = streakState?.lastCompletedDateString ?: "None"
                        val isTodaySaved = lastSavedDay == currentDate

                        StreakCard(
                            currentStreakCount = currentStreakCount,
                            isTodaySaved = isTodaySaved,
                            lastSavedDay = lastSavedDay,
                            onTap = {
                                if (isVibrationEnabled) {
                                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                    if (vibrator != null && vibrator.hasVibrator()) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(15, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                        } else {
                                            @Suppress("DEPRECATION")
                                            vibrator.vibrate(15)
                                        }
                                    }
                                }
                                playSystemTick()
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // HEADER ROW FOR TASK SECTION (Add Task Button completely removed!)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(
                                text = "TODAY'S TIMED TASKS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White.copy(alpha = 0.65f),
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 3. TASK LIST VIEW
                        AnimatedContent(
                            targetState = tasks.isEmpty(),
                            transitionSpec = { fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220)) },
                            label = "tasksListTransition"
                        ) { isEmpty ->
                            if (isEmpty) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .liquidGlass(cornerRadius = 20.dp, borderAlpha = 0.15f, bgAlpha = 0.03f)
                                        .padding(32.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "⏰",
                                        fontSize = 48.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    Text(
                                        text = "No Scheduled tasks",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            } else {
                                val slotOrder = listOf(
                                    "Slot 1: 07AM to 02PM -- Backlog clear",
                                    "Slot 2: 02PM to 04PM -- Revision",
                                    "Slot 3: 04PM to 09PM -- Classes",
                                    "Slot 4: 09PM to 12AM -- Questions and H.W",
                                    "Custom Tasks"
                                )
                                val groupedTasks = remember(tasks) {
                                    tasks.groupBy { it.slotCategory }
                                }

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 80.dp)
                                ) {
                                    slotOrder.forEach { slotName ->
                                        val slotTasks = groupedTasks[slotName]
                                        if (!slotTasks.isNullOrEmpty()) {
                                            item(key = "header_$slotName") {
                                                Text(
                                                    text = slotName.uppercase(java.util.Locale.getDefault()),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    letterSpacing = 1.sp,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
                                                )
                                            }
                                            items(slotTasks, key = { it.id }) { task ->
                                                TaskItemCard(
                                                    task = task,
                                                    onCompletedToggle = {
                                                        triggerVibePulse()
                                                        playSystemTick()
                                                        viewModel.toggleTaskCompletion(task)
                                                    },
                                                    onDelete = { viewModel.deleteTask(task) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fire streak animation overlay celebrating progress
        if (showFireStreakAnimation) {
            FireCelebrationOverlay(
                streakCount = fireAnimationStreakValue,
                onDismiss = { showFireStreakAnimation = false }
            )
        }

        // Permission request dialog
        if (showPermissionExplanation) {
            Dialog(onDismissRequest = { showPermissionExplanation = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .liquidGlass(cornerRadius = 24.dp, borderAlpha = 0.4f, bgAlpha = 0.16f)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔔",
                        fontSize = 36.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = "Enable Alerts",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "This app needs notification permission to send reminders for your scheduled tasks precisely on time.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showPermissionExplanation = false }) {
                            Text("Not Now", color = Color.White.copy(alpha = 0.6f))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                showPermissionExplanation = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            modifier = Modifier.glassButton(cornerRadius = 16.dp)
                        ) {
                            Text("Grant Permission", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Helper function that generates a waving, organic-looking Path for a rounded rect.
 * It works by sampling points along the boundary of the shape and pushing them outward
 * along the outward-facing normal vector using a sine wave function.
 */
fun createWavyRoundedRectPath(
    width: Float,
    height: Float,
    radius: Float,
    amplitude: Float,
    frequency: Float,
    phase: Float,
    touchPoint: Offset = Offset.Zero,
    touchProgress: Float = 0f
): Path {
    val path = Path()
    if (width <= 0f || height <= 0f) return path

    val points = mutableListOf<Offset>()
    val normals = mutableListOf<Offset>()

    // Clamp corner radius to prevent intersection issues
    val safeRadius = radius.coerceAtMost(width / 2f).coerceAtMost(height / 2f)

    // Top Edge
    val topCount = 16
    for (i in 0..topCount) {
        val t = i.toFloat() / topCount
        val x = safeRadius + t * (width - 2 * safeRadius)
        points.add(Offset(x, 0f))
        normals.add(Offset(0f, -1f))
    }

    // Top-Right Corner Arc
    val arcCount = 10
    for (i in 0..arcCount) {
        val t = i.toFloat() / arcCount
        val angle = -Math.PI / 2 + t * Math.PI / 2
        val x = width - safeRadius + cos(angle).toFloat() * safeRadius
        val y = safeRadius + sin(angle).toFloat() * safeRadius
        points.add(Offset(x, y))
        normals.add(Offset(cos(angle).toFloat(), sin(angle).toFloat()))
    }

    // Right Edge
    val rightCount = 16
    for (i in 0..rightCount) {
        val t = i.toFloat() / rightCount
        val y = safeRadius + t * (height - 2 * safeRadius)
        points.add(Offset(width, y))
        normals.add(Offset(1f, 0f))
    }

    // Bottom-Right Corner Arc
    for (i in 0..arcCount) {
        val t = i.toFloat() / arcCount
        val angle = t * Math.PI / 2
        val x = width - safeRadius + cos(angle).toFloat() * safeRadius
        val y = height - safeRadius + sin(angle).toFloat() * safeRadius
        points.add(Offset(x, y))
        normals.add(Offset(cos(angle).toFloat(), sin(angle).toFloat()))
    }

    // Bottom Edge
    val bottomCount = 16
    for (i in 0..bottomCount) {
        val t = i.toFloat() / bottomCount
        val x = width - safeRadius - t * (width - 2 * safeRadius)
        points.add(Offset(x, height))
        normals.add(Offset(0f, 1f))
    }

    // Bottom-Left Corner Arc
    for (i in 0..arcCount) {
        val t = i.toFloat() / arcCount
        val angle = Math.PI / 2 + t * Math.PI / 2
        val x = safeRadius + cos(angle).toFloat() * safeRadius
        val y = height - safeRadius + sin(angle).toFloat() * safeRadius
        points.add(Offset(x, y))
        normals.add(Offset(cos(angle).toFloat(), sin(angle).toFloat()))
    }

    // Left Edge
    val leftCount = 16
    for (i in 0..leftCount) {
        val t = i.toFloat() / leftCount
        val y = height - safeRadius - t * (height - 2 * safeRadius)
        points.add(Offset(0f, y))
        normals.add(Offset(-1f, 0f))
    }

    // Top-Left Corner Arc
    for (i in 0..arcCount) {
        val t = i.toFloat() / arcCount
        val angle = Math.PI + t * Math.PI / 2
        val x = safeRadius + cos(angle).toFloat() * safeRadius
        val y = safeRadius + sin(angle).toFloat() * safeRadius
        points.add(Offset(x, y))
        normals.add(Offset(cos(angle).toFloat(), sin(angle).toFloat()))
    }

    // Connect perturbed points
    val totalPointsCount = points.size
    val maxRadius = kotlin.math.hypot(width, height)
    val cx = width / 2f
    val cy = height / 2f
    val waveCount = 5

    for (i in 0 until totalPointsCount) {
        val pt = points[i]
        val normal = normals[i]
        
        // Calculate the angular position of this boundary point relative to the layout center.
        // Using this angle guarantees that the wave value is perfectly continuous and has zero jumps when the path closes!
        val angleFromCenter = kotlin.math.atan2(pt.y - cy, pt.x - cx)
        
        // Subtle base sloshing wave
        val ambientWave = sin(angleFromCenter * waveCount + phase) +
                0.2f * cos(angleFromCenter * waveCount * 2f - phase * 1.2f)
        
        // Physically propagated smooth decaying touch wave
        var touchWave = 0f
        if (touchProgress > 0f && touchProgress < 1f) {
            val dist = kotlin.math.hypot(pt.x - touchPoint.x, pt.y - touchPoint.y)
            val waveFront = touchProgress * maxRadius * 1.1f
            val distToFront = kotlin.math.abs(dist - waveFront)
            
            // Rich smooth Gaussian envelope around the wave front to avoid high-frequency edges
            val envelope = kotlin.math.exp(- (distToFront * distToFront) / (100f * 100f))
            
            // Decaying gentle ripple on the surface
            touchWave = envelope * sin(dist * 0.04f - touchProgress * 10f) * 0.5f * (1f - touchProgress)
        }
        
        val waveFactor = ambientWave + touchWave
        val perturbedPtX = pt.x + normal.x * waveFactor * amplitude
        val perturbedPtY = pt.y + normal.y * waveFactor * amplitude

        if (i == 0) {
            path.moveTo(perturbedPtX, perturbedPtY)
        } else {
            path.lineTo(perturbedPtX, perturbedPtY)
        }
    }
    path.close()
    return path
}

/**
 * Individual timed checklist task card styled as a crystal glass block with thin light gradients.
 * Features an interactive fluid water surface ripple when touched.
 */
@Composable
fun TaskItemCard(
    task: Task,
    onCompletedToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Constant slow breathing baseline phase (slowed down from 8000ms to 12000ms)
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_liquid_wave")
    val ambientPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambientPhase"
    )

    // Supports multiple concurrent slow ripples on rapid touch
    val activeWaves = remember { mutableStateListOf<TouchWave>() }
    var nextWaveId by remember { mutableStateOf(0L) }
    
    val density = androidx.compose.ui.platform.LocalDensity.current.density

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(task) {
                detectTapGestures(
                    onTap = { offset ->
                        // Add a tap wave!
                        val newId = nextWaveId++
                        val rippleAnim = androidx.compose.animation.core.Animatable(0f)
                        val borderAnim = androidx.compose.animation.core.Animatable(0f)
                        val wave = TouchWave(id = newId, point = offset, rippleAnim = rippleAnim, borderAnim = borderAnim)
                        activeWaves.add(wave)

                        // Slower wave speeds as requested!
                        coroutineScope.launch {
                            rippleAnim.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(2800, easing = androidx.compose.animation.core.CubicBezierEasing(0.08f, 0.45f, 0.15f, 1f))
                            )
                            activeWaves.remove(wave)
                        }
                        coroutineScope.launch {
                            borderAnim.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(3400, easing = androidx.compose.animation.core.CubicBezierEasing(0.08f, 0.35f, 0.15f, 1f))
                            )
                        }
                        onCompletedToggle()
                    }
                )
            }
            .drawBehind {
                val width = size.width
                val height = size.height
                val radiusPx = 16.dp.toPx()

                // Compute exact amplitude of wavy border
                val baseAmp = 0.5f * density // subtle organic baseline (0.5dp)
                
                var totalOscillatingTouchAmp = 0f
                var maxBorderProgress = 0f
                var combinedTouchPoint = Offset.Zero
                var totalWeight = 0f

                for (wave in activeWaves) {
                    val progress = wave.borderAnim.value
                    if (progress > 0f && progress < 1f) {
                        val decayFactor = (1f - progress)
                        val waveAmp = 2.0f * density * decayFactor * sin(progress * 3.1415927f * 2f)
                        totalOscillatingTouchAmp += waveAmp
                        if (progress > maxBorderProgress) {
                            maxBorderProgress = progress
                        }
                        combinedTouchPoint += wave.point * (1f - progress)
                        totalWeight += (1f - progress)
                    }
                }

                val finalTouchPoint = if (totalWeight > 0f) combinedTouchPoint / totalWeight else Offset.Zero
                val currentAmplitude = (baseAmp + totalOscillatingTouchAmp).coerceAtLeast(0f)
                val currentPhase = ambientPhase + (maxBorderProgress * 6f)

                val wavyPath = createWavyRoundedRectPath(
                    width = width,
                    height = height,
                    radius = radiusPx,
                    amplitude = currentAmplitude,
                    frequency = 0.25f,
                    phase = currentPhase,
                    touchPoint = finalTouchPoint,
                    touchProgress = maxBorderProgress
                )

                // 1. Draw Liquid Glass Wavy Background (Matching reference design premium vertical glass gradients)
                val bgAlpha = if (task.isCompleted) 0.02f else 0.08f
                val bgAlphaModifier = if (task.isCompleted) 0.35f else 1.0f
                val baseBgAlpha = bgAlpha * bgAlphaModifier
                val glassBgBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (baseBgAlpha * 2.8f).coerceAtMost(1.0f)),      // Specular top highlight glare
                        Color.White.copy(alpha = (baseBgAlpha * 0.9f).coerceAtMost(1.0f)),      // Sleek upper face transparency
                        Color(0xFF04060A).copy(alpha = 0.85f * bgAlphaModifier), // Extremely deep charcoal-black absorption
                        Color(0xFF0E1118).copy(alpha = 0.72f * bgAlphaModifier), // Translucent carbon base body
                        Color.White.copy(alpha = (baseBgAlpha * 1.4f).coerceAtMost(1.0f))       // Ground bounce upward reflect rim
                    ),
                    startY = 0f,
                    endY = height
                )
                drawPath(path = wavyPath, brush = glassBgBrush)

                // 2. Draw Liquid Glass Wavy Outer Border
                val borderAlpha = if (task.isCompleted) 0.15f else 0.25f
                val borderAlphaModifier = if (task.isCompleted) 0.5f else 1.0f
                val baseBorderAlpha = borderAlpha * borderAlphaModifier
                val borderBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (baseBorderAlpha * 3.2f).coerceAtMost(1.0f)),  // Primary bright light-facing sheen
                        Color.White.copy(alpha = (baseBorderAlpha * 0.35f).coerceAtMost(1.0f)), // Smooth edge transience bleed
                        Color.White.copy(alpha = (baseBorderAlpha * 1.6f).coerceAtMost(1.0f))   // Secondary bounce reflect glow
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(width, height)
                )
                drawPath(
                    path = wavyPath,
                    brush = borderBrush,
                    style = Stroke(width = 1.3f.dp.toPx()) // Beautiful crisp bevel edge width
                )

                // 3. Draw Water Circular Ripple Waves with 3D tactile texture (clipped inside wavyPath)
                clipPath(wavyPath) {
                    val maxRadius = kotlin.math.hypot(width, height)
                    for (wave in activeWaves) {
                        val ripVal = wave.rippleAnim.value
                        if (ripVal > 0f && ripVal < 1f) {
                            val tPoint = wave.point
                            
                            // Wave propagation Ring 1 (Highlight / Bulge Refraction / Shadow 3D effects)
                            val r1 = ripVal * maxRadius
                            val alpha1 = 0.45f * (1f - ripVal) * sin(ripVal * Math.PI.toFloat())
                            if (alpha1 > 0f) {
                                // Spherical liquid bulge (3D radial gradient refraction)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = alpha1 * 0.45f),
                                            Color(0xFF80DEEA).copy(alpha = alpha1 * 0.18f),
                                            Color(0xFF0288D1).copy(alpha = alpha1 * 0.04f),
                                            Color.Transparent
                                        ),
                                        center = tPoint,
                                        radius = r1
                                    ),
                                    radius = r1,
                                    center = tPoint
                                )

                                // Inner Specular highlight ring (offset slightly top-left simulating 3D light)
                                drawCircle(
                                    color = Color.White.copy(alpha = alpha1 * 0.6f),
                                    radius = kotlin.math.max(0f, r1 - 1.5f.dp.toPx()),
                                    center = tPoint - Offset(1.5f.dp.toPx(), 1.5f.dp.toPx()),
                                    style = Stroke(width = 1.dp.toPx())
                                )

                                // Outer shadow contour ring (offset slightly bottom-right simulating 3D physical occlusion)
                                drawCircle(
                                    color = Color.Black.copy(alpha = alpha1 * 0.12f),
                                    radius = r1 + 1.5f.dp.toPx(),
                                    center = tPoint + Offset(1.5f.dp.toPx(), 1.5f.dp.toPx()),
                                    style = Stroke(width = 1.5f.dp.toPx())
                                )

                                // Main light blue ripple wave front
                                drawCircle(
                                    color = Color(0xFF4FC3F7).copy(alpha = alpha1),
                                    radius = r1,
                                    center = tPoint,
                                    style = Stroke(width = 2.5f.dp.toPx())
                                )
                            }

                            // Wave propagation Ring 2 (Secondary 3D layered ring)
                            val r2 = (ripVal - 0.18f).coerceAtLeast(0f) * maxRadius
                            val alpha2 = 0.25f * (1f - ripVal) * sin((ripVal - 0.18f).coerceAtLeast(0f) * Math.PI.toFloat())
                            if (alpha2 > 0f) {
                                // Secondary spherical reflection
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = alpha2 * 0.25f),
                                            Color(0xFFE0F7FA).copy(alpha = alpha2 * 0.12f),
                                            Color.Transparent
                                        ),
                                        center = tPoint,
                                        radius = r2
                                    ),
                                    radius = r2,
                                    center = tPoint
                                )

                                // Secondary highlight
                                drawCircle(
                                    color = Color.White.copy(alpha = alpha2 * 0.4f),
                                    radius = kotlin.math.max(0f, r2 - 1f.dp.toPx()),
                                    center = tPoint - Offset(1f.dp.toPx(), 1f.dp.toPx()),
                                    style = Stroke(width = 0.8f.dp.toPx())
                                )

                                // Secondary subtle ripple wave front
                                drawCircle(
                                    color = Color(0xFFE0F7FA).copy(alpha = alpha2),
                                    radius = r2,
                                    center = tPoint,
                                    style = Stroke(width = 1.5f.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Tick Button (custom-designed premium 3D glass sphere checklist checkbox with the custom sleek white launcher tick style)
        Box(
            modifier = Modifier
                .size(34.dp)
                .glassButton(isCircle = true)
                .testTag("task_tick_${task.id}"),
            contentAlignment = Alignment.Center
        ) {
            if (task.isCompleted) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(16.dp)) {
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        // Drawing custom checkmark identical to the app logo (M 38 55 L 48 65 L 70 41 on a 108dp viewport)
                        moveTo(w * 0.35f, h * 0.51f)
                        lineTo(w * 0.44f, h * 0.61f)
                        lineTo(w * 0.65f, h * 0.38f)
                    }
                    drawPath(
                        path = path,
                        color = Color.White,
                        style = Stroke(
                            width = 2.4f.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Center Task details
        Column(modifier = Modifier.weight(1f)) {
            // Use gorgeous Liquid Glass text before and after the tick as requested!
            GlassText(
                text = task.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🕒",
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(
                    text = task.timeRange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (task.isCompleted) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "🔔",
                    fontSize = 10.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )
                val amPm = if (task.hour >= 12) "PM" else "AM"
                val displayHour = if (task.hour > 12) task.hour - 12 else if (task.hour == 0) 12 else task.hour
                Text(
                    text = String.format(java.util.Locale.getDefault(), "Alert at %02d:%02d %s", displayHour, task.minute, amPm),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (task.isCompleted) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.5f)
                )
            }
        }

        // Deletable ONLY if it is a Custom Task (immutable daily slots cannot be deleted ever!)
        if (task.slotCategory == "Custom Tasks") {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .glassButton(isCircle = true)
                    .size(36.dp)
                    .testTag("delete_task_${task.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove Task",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Full-Screen Settings View supporting background customization, alerts, and sandbox parameters.
 */
@Composable
fun SettingsScreenView(
    currentDate: String,
    isWallpaperEnabled: Boolean,
    onWallpaperEnabledChanged: (Boolean) -> Unit,
    selectedWallpaperStyle: String,
    onWallpaperStyleChanged: (String) -> Unit,
    customWallpaperUrl: String,
    onCustomWallpaperUrlChanged: (String) -> Unit,
    isVibrationEnabled: Boolean,
    onVibrationEnabledChanged: (Boolean) -> Unit,
    isSoundsEnabled: Boolean,
    onSoundsEnabledChanged: (Boolean) -> Unit,
    isNotificationsEnabled: Boolean,
    onNotificationsEnabledChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: TaskViewModel
) {
    val context = LocalContext.current.safeAttribution()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var isResolvingUrl by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf(customWallpaperUrl) }

    val imagePresets = listOf(
        "Campfire Glow" to "https://images.unsplash.com/photo-1542382156909-9ae37b3f56fa?auto=format&fit=crop&q=80&w=1200",
        "Cosmic Peak" to "https://images.unsplash.com/photo-1506318137071-a8e063b4bec0?auto=format&fit=crop&q=80&w=1200",
        "Golden Canopy" to "https://images.unsplash.com/photo-1448375240586-882707db888b?auto=format&fit=crop&q=80&w=1200",
        "Autumn Tranquility" to "https://images.unsplash.com/photo-1518531933037-91b2f5f229cc?auto=format&fit=crop&q=80&w=1200"
    )

    var showWipeConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .glassButton(isCircle = true)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Return to Home Checklist",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "SETTINGS",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(top = 16.dp)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // SECTION 1: VISUALS AND WALLPAPER
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(cornerRadius = 20.dp, borderAlpha = 0.3f, bgAlpha = 0.08f)
                    .padding(16.dp)
            ) {
                Text(
                    text = "VISUALS & CUSTOM WALLPAPERS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Toggle Custom Wallpaper Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Wallpaper Backdrops",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "When disabled, a solid high-contrast black background is loaded.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    GlassSwitch(
                        checked = isWallpaperEnabled,
                        onCheckedChange = onWallpaperEnabledChanged,
                        modifier = Modifier.testTag("wallpaper_toggle")
                    )
                }

                if (isWallpaperEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "SELECT WALLPAPER STYLE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Styles cards
                    val stylesList = listOf(
                        "liquid_glass" to "💧 Liquid Glass Live",
                        "custom_photo" to "🎨 Custom Live Wallpaper"
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        stylesList.forEach { (styleKey, label) ->
                            val isSelected = selectedWallpaperStyle == styleKey
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) Color.White.copy(alpha = 0.15f)
                                        else Color.White.copy(alpha = 0.03f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFFFF9100).copy(alpha = 0.6f)
                                        else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onWallpaperStyleChanged(styleKey) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active style",
                                        tint = Color(0xFFFF9100),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // URL Input field and Apply Button if custom_photo is active
                    if (selectedWallpaperStyle == "custom_photo") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "CUSTOM LIVE WALLPAPER PATH",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = {
                                inputUrl = it
                            },
                            placeholder = { Text("Paste HTTP image URL here...", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFF9100).copy(alpha = 0.7f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_wallpaper_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Or tap a tranquil scenic preset configuration:",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        // Presets Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            imagePresets.forEach { (name, url) ->
                                val isActive = inputUrl == url
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isActive) Color(0xFFFF9100).copy(alpha = 0.25f)
                                            else Color.White.copy(alpha = 0.05f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isActive) Color(0xFFFF9100) else Color.White.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            inputUrl = url
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name.split(" ")[0], // short string
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isActive) Color.White else Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Styled Apply Button for Custom Live Wallpaper
                        Button(
                            enabled = !isResolvingUrl,
                            onClick = {
                                if (inputUrl.isNotBlank() && (inputUrl.startsWith("http://") || inputUrl.startsWith("https://"))) {
                                    isResolvingUrl = true
                                    Toast.makeText(context, "Resolving custom wallpaper link...", Toast.LENGTH_SHORT).show()
                                    coroutineScope.launch {
                                        val resolvedUrl = resolveDirectImageUrl(inputUrl)
                                        if (resolvedUrl != inputUrl) {
                                            inputUrl = resolvedUrl
                                        }
                                        onCustomWallpaperUrlChanged(resolvedUrl)
                                        onWallpaperStyleChanged("custom_photo")
                                        isResolvingUrl = false
                                        Toast.makeText(context, "Custom live wallpaper updated successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    onCustomWallpaperUrlChanged(inputUrl)
                                    onWallpaperStyleChanged("custom_photo")
                                    Toast.makeText(context, "Custom live wallpaper updated successfully!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassButton(cornerRadius = 14.dp)
                        ) {
                            if (isResolvingUrl) {
                                CircularProgressIndicator(
                                    color = Color(0xFFFF9100),
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Resolving Link...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Apply Custom Wallpaper", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // SECTION 1.5: ALERTS & REMINDERS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(cornerRadius = 20.dp, borderAlpha = 0.3f, bgAlpha = 0.08f)
                    .padding(16.dp)
            ) {
                Text(
                    text = "ALERTS & SYSTEM REMINDERS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Toggle Notifications Switch
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Alarm Reminders",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Receive notifications on scheduled task times.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    GlassSwitch(
                        checked = isNotificationsEnabled,
                        onCheckedChange = onNotificationsEnabledChanged,
                        modifier = Modifier.testTag("notifications_toggle")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Toggle Vibrations Switch
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Haptic Tactile Vibrations",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Vibrate on task ticking and checklist saves.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    GlassSwitch(
                        checked = isVibrationEnabled,
                        onCheckedChange = onVibrationEnabledChanged,
                        modifier = Modifier.testTag("vibration_toggle")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Toggle Sounds Switch
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "System Action Sounds",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Play clean low-latency audio beeps upon clicks.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    GlassSwitch(
                        checked = isSoundsEnabled,
                        onCheckedChange = onSoundsEnabledChanged,
                        modifier = Modifier.testTag("sounds_toggle")
                    )
                }
            }

            // RECOVERY ZONE
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(cornerRadius = 20.dp, borderAlpha = 0.3f, bgAlpha = 0.08f)
                    .padding(16.dp)
            ) {
                Text(
                    text = "SYSTEM PROGRESS & RECOVERY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "Use this recovery tool to clear the database and refresh the application state back to factory defaults.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Database Wipe button
                Button(
                    onClick = { showWipeConfirmation = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassButton(cornerRadius = 14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Wipe Progress & Database", color = Color(0xFFEF5350), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Wipe confirmation pop up tab
            if (showWipeConfirmation) {
                Dialog(onDismissRequest = { showWipeConfirmation = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .liquidGlass(cornerRadius = 24.dp, borderAlpha = 0.4f, bgAlpha = 0.2f)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "⚠️",
                            fontSize = 36.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "Confirm Database Wipe",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Are you sure you want to reset and wipe your calendar progress? This will delete all completed checklist items and clear your daily streak score. This action is irreversible.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 20.dp),
                            lineHeight = 18.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showWipeConfirmation = false }) {
                                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    showWipeConfirmation = false
                                    viewModel.clearAllData()
                                    Toast.makeText(context, "All progress database successfully wiped!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                modifier = Modifier.glassButton(cornerRadius = 16.dp)
                            ) {
                                Text("Wipe Everything", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Animated background controller loading the chosen aesthetic styling or a solid black canvas dynamically.
 */
@Composable
fun DynamicAppBackground(
    isWallpaperEnabled: Boolean,
    selectedWallpaperStyle: String,
    customWallpaperUrl: String,
    modifier: Modifier = Modifier
) {
    if (!isWallpaperEnabled) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        )
    } else {
        when (selectedWallpaperStyle) {
            "liquid_glass" -> {
                com.example.ui.LiquidGlassBackground(modifier = modifier)
            }
            "live_embers" -> {
                LiveEmbersBackground(modifier = modifier)
            }
            "golden_aurora" -> {
                GoldenAuroraBackground(modifier = modifier)
            }
            "custom_photo" -> {
                CustomPhotoBackground(imageUrl = customWallpaperUrl, modifier = modifier)
            }
            else -> {
                com.example.ui.LiquidGlassBackground(modifier = modifier)
            }
        }
    }
}

/**
 * Live photo styled background representing fire sparks / warm starry embers floating and twinkling upwards.
 */
@Composable
fun LiveEmbersBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "EmbersAnimation")
    
    // Smooth infinite upward movement parameter
    val animationTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(40000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "embersTime"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF03050C),
                        Color(0xFF0D0D1A),
                        Color(0xFF05050A)
                    )
                )
            )
    ) {
        // High performance Canvas rendering sparks
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().blur(2.dp)) {
            val width = size.width
            val height = size.height
            if (width > 0 && height > 0) {
                for (i in 0 until 35) {
                    val seedX = (i * 73) % 1000 / 1000f
                    val seedY = (i * 37) % 1000 / 1000f
                    val speed = 0.05f + (i % 5) * 0.02f
                    val sizeFactor = 3.dp.toPx() + (i % 4) * 2.dp.toPx()

                    // Calculate position with drift and wrap-around
                    val angle1 = (animationTime * 0.012f + i.toFloat()).toDouble()
                    val x = (seedX * width + kotlin.math.sin(angle1).toFloat() * 30.dp.toPx()) % width
                    val y = (height - (seedY * height + animationTime * speed * height) % height) % height

                    // Floating twinkle overlay
                    val angle2 = (animationTime * 0.06f + i.toFloat()).toDouble()
                    val opacity = (0.15f + 0.5f * kotlin.math.sin(angle2).toFloat()).coerceIn(0f, 1f)

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFB74D).copy(alpha = opacity),
                                Color(0xFFFF7043).copy(alpha = opacity * 0.3f),
                                Color.Transparent
                            ),
                            center = Offset(x, y),
                            radius = sizeFactor * 3
                        ),
                        radius = sizeFactor,
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}

/**
 * Animated dynamic gradient background representing a golden shifting sunrise glow.
 */
@Composable
fun GoldenAuroraBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "AuroraAnimation")
    
    val shift1 by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shift1"
    )
    val shift2 by infiniteTransition.animateFloat(
        initialValue = -250f,
        targetValue = 250f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shift2"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF06040A))
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().blur(80.dp)) {
            val width = size.width
            val height = size.height
            if (width > 0 && height > 0) {
                // Drawing shifting amber sunshine aura
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFB300).copy(alpha = 0.22f),
                            Color(0xFFE65100).copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.5f + shift1, height * 0.82f + shift2),
                        radius = width * 0.75f
                    ),
                    radius = width * 0.75f,
                    center = Offset(width * 0.5f + shift1, height * 0.82f + shift2)
                )

                // Shifting elegant purple indigo deep aura
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF4A148C).copy(alpha = 0.2f),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.4f - shift2, height * 0.18f + shift1),
                        radius = width * 0.85f
                    ),
                    radius = width * 0.85f,
                    center = Offset(width * 0.4f - shift2, height * 0.18f + shift1)
                )
            }
        }
    }
}

/**
 * Detects if the given URL corresponds to a video format.
 */
private fun isVideoUrl(url: String): Boolean {
    val cleanUrl = url.lowercase().split("?")[0].split("#")[0]
    return cleanUrl.endsWith(".mp4") ||
           cleanUrl.endsWith(".webm") ||
           cleanUrl.endsWith(".mkv") ||
           cleanUrl.endsWith(".mov") ||
           cleanUrl.endsWith(".3gp") ||
           url.contains("video", ignoreCase = true) ||
           url.contains("mp4", ignoreCase = true) ||
           url.contains(".mp4", ignoreCase = true)
}

/**
 * Scenic photo or dynamic video custom wallpaper loaded gracefully.
 */
@Composable
fun CustomPhotoBackground(imageUrl: String, modifier: Modifier = Modifier) {
    val fallbackUrl = "https://images.unsplash.com/photo-1518531933037-91b2f5f229cc?auto=format&fit=crop&q=80&w=1200"
    val finalUrl = imageUrl.ifBlank { fallbackUrl }

    if (isVideoUrl(finalUrl)) {
        CustomVideoBackground(videoUrl = finalUrl, modifier = modifier)
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            coil.compose.AsyncImage(
                model = finalUrl,
                contentDescription = "Custom Scenic Wallpaper",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Add robust dark overlay to keep all white text and glass containers highly readable
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )
        }
    }
}

/**
 * Runs a highly-efficient real-time background video wallpaper using a WebView loaded with an HTML5 <video> element.
 */
@Composable
fun CustomVideoBackground(videoUrl: String, modifier: Modifier = Modifier) {
    val htmlContent = remember(videoUrl) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <style>
                html, body {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background-color: transparent;
                }
                video {
                    position: absolute;
                    top: 50%;
                    left: 50%;
                    transform: translate(-50%, -50%);
                    min-width: 100%;
                    min-height: 100%;
                    width: auto;
                    height: auto;
                    object-fit: cover;
                    background-color: transparent;
                }
            </style>
        </head>
        <body>
            <video autoplay muted loop playsinline id="bg-video">
                <source src="$videoUrl" type="video/mp4">
                <source src="$videoUrl" type="video/webm">
                <source src="$videoUrl" type="video/ogg">
            </video>
            <script>
                document.addEventListener('DOMContentLoaded', function() {
                    var v = document.getElementById('bg-video');
                    v.play().catch(function(e) {
                        console.log("Autoplay check:", e);
                    });
                });
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    setBackgroundColor(0) // transparent background
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    webChromeClient = android.webkit.WebChromeClient()
                    webViewClient = android.webkit.WebViewClient()
                    
                    // Load HTML content
                    loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        // Add premium darkened overlay matching custom photo wallpaper
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
        )
    }
}

/**
 * Full page custom fire sparks celebratory overlay sheet.
 */
@Composable
fun FireCelebrationOverlay(
    streakCount: Int,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "CelebrationEmbers")
    val animationTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "celebrationSparks"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(enabled = true, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        // Floating Celebratory Sparks rising up across full screen
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width > 0 && height > 0) {
                for (i in 0 until 40) {
                    val seedX = (i * 97) % 1000 / 1000f
                    val seedY = (i * 43) % 1000 / 1000f
                    val speed = 0.08f + (i % 6) * 0.03f
                    val sizeFactor = 4.dp.toPx() + (i % 5) * 2.dp.toPx()

                    val angle1 = (animationTime * 0.02f + i.toFloat()).toDouble()
                    val x = (seedX * width + kotlin.math.sin(angle1).toFloat() * 60.dp.toPx()) % width
                    val y = (height - (seedY * height + animationTime * speed * height) % height) % height
                    val angle2 = (animationTime * 0.07f + i.toFloat()).toDouble()
                    val opacity = (0.3f + 0.62f * kotlin.math.sin(angle2).toFloat()).coerceIn(0f, 1f)

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFD54F).copy(alpha = opacity),
                                Color(0xFFFF7043).copy(alpha = opacity * 0.25f),
                                Color.Transparent
                            ),
                            center = Offset(x, y),
                            radius = sizeFactor * 4
                        ),
                        radius = sizeFactor,
                        center = Offset(x, y)
                    )
                }
            }
        }

        // Inner celebratory crystal card
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .liquidGlass(cornerRadius = 28.dp, borderAlpha = 0.45f, bgAlpha = 0.16f)
                .padding(28.dp)
                .clickable(enabled = false) {}, // Intercept clicks inside card
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "STREAK BLAZING!",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFFF9100),
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Giant Flame Drawing
            FireFlameAnimation(modifier = Modifier.size(140.dp))

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "$streakCount Days!",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("celebration_streak_text")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your daily streak has compounded! Today's checklist is officially saved and locked under the stars.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dismiss_celebration_button")
                    .glassButton(cornerRadius = 16.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text(
                    text = "Keep the Fire Burning",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * Wavy canvas-based flame rendering celebrating streak increases.
 */
@Composable
fun FireFlameAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "FlameAnimation")
    
    val heightScaleOuter by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outerY"
    )
    val widthScaleOuter by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outerX"
    )

    val heightScaleInner by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "innerY"
    )
    
    val flameSway by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val cx = width * 0.5f
        val cy = height * 0.85f // bottom base
        
        // Draw fire base glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF5722).copy(alpha = 0.4f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = width * 0.6f
            ),
            radius = width * 0.6f,
            center = Offset(cx, cy)
        )

        // Outer Flame (Red/Crimson)
        val pathOuter = Path().apply {
            moveTo(cx - 30.dp.toPx() * widthScaleOuter, cy)
            quadraticTo(
                cx - 15.dp.toPx() + flameSway.dp.toPx(),
                cy - 50.dp.toPx() * heightScaleOuter,
                cx + flameSway.dp.toPx() * 1.5f,
                cy - 90.dp.toPx() * heightScaleOuter
            )
            quadraticTo(
                cx + 15.dp.toPx() + flameSway.dp.toPx(),
                cy - 50.dp.toPx() * heightScaleOuter,
                cx + 30.dp.toPx() * widthScaleOuter,
                cy
            )
            close()
        }
        drawPath(
            path = pathOuter,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFF3D00).copy(alpha = 0.95f),
                    Color(0xFFD50000).copy(alpha = 0.4f)
                )
            )
        )

        // Middle Flame (Bright Orange)
        val pathMiddle = Path().apply {
            moveTo(cx - 20.dp.toPx() * widthScaleOuter, cy)
            quadraticTo(
                cx - 10.dp.toPx() + flameSway.dp.toPx() * 0.8f,
                cy - 35.dp.toPx() * heightScaleInner,
                cx + flameSway.dp.toPx() * 0.8f,
                cy - 65.dp.toPx() * heightScaleInner
            )
            quadraticTo(
                cx + 10.dp.toPx() + flameSway.dp.toPx() * 0.8f,
                cy - 35.dp.toPx() * heightScaleInner,
                cx + 20.dp.toPx() * widthScaleOuter,
                cy
            )
            close()
        }
        drawPath(
            path = pathMiddle,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFF9100).copy(alpha = 0.9f),
                    Color(0xFFFF3D00).copy(alpha = 0.5f)
                )
            )
        )

        // Inner Core Flame (Warm Amber Gold)
        val pathInner = Path().apply {
            moveTo(cx - 12.dp.toPx(), cy)
            quadraticTo(
                cx - 5.dp.toPx() + flameSway.dp.toPx() * 0.4f,
                cy - 20.dp.toPx() * heightScaleInner,
                cx,
                cy - 40.dp.toPx() * heightScaleInner
            )
            quadraticTo(
                cx + 5.dp.toPx() + flameSway.dp.toPx() * 0.4f,
                cy - 20.dp.toPx() * heightScaleInner,
                cx + 12.dp.toPx(),
                cy
            )
            close()
        }
        drawPath(
            path = pathInner,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFEA00),
                    Color(0xFFFF9100).copy(alpha = 0.75f)
                )
            )
        )
    }
}

/**
 * Data class representing a dynamically spawned ripple wave for multi-touch organic feedback.
 */
data class TouchWave(
    val id: Long,
    val point: Offset,
    val rippleAnim: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    val borderAnim: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>
)

/**
 * Resolves shared page links (e.g., Unsplash, Pinterest) to their direct raw image sources using OpenGraph tags.
 * Runs on standard background coroutine dispatchers using HttpURLConnection.
 */
suspend fun resolveDirectImageUrl(inputUrl: String): String {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val trimmed = inputUrl.trim()
        try {
            // First treat typical Unsplash sharing patterns cleanly without network requests if possible
            if (trimmed.contains("unsplash.com/photos/")) {
                val lastSegment = trimmed.split("/").lastOrNull()?.split("?")?.firstOrNull() ?: ""
                if (lastSegment.isNotEmpty()) {
                    val id = if (lastSegment.contains("-")) lastSegment.substringAfterLast("-") else lastSegment
                    if (id.length >= 4) {
                        return@withContext "https://images.unsplash.com/photo-$id?auto=format&fit=crop&q=80&w=1200"
                    }
                }
            }

            val urlConnection = java.net.URL(trimmed).openConnection() as java.net.HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.connectTimeout = 8000
            urlConnection.readTimeout = 8000
            urlConnection.instanceFollowRedirects = true
            urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            
            val status = urlConnection.responseCode
            if (status == java.net.HttpURLConnection.HTTP_MOVED_TEMP || status == java.net.HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                val newUrl = urlConnection.getHeaderField("Location")
                if (!newUrl.isNullOrEmpty()) {
                    return@withContext resolveDirectImageUrl(newUrl)
                }
            }
            
            val contentType = urlConnection.contentType ?: ""
            if (contentType.startsWith("image/", ignoreCase = true)) {
                return@withContext trimmed
            }
            
            val html = urlConnection.inputStream.bufferedReader().use { it.readText() }
            
            val ogImageRegex = """<meta\s+[^>]*property=["']og:image["']\s+[^>]*content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
            val ogImageMatch = ogImageRegex.find(html)
            if (ogImageMatch != null) {
                return@withContext ogImageMatch.groupValues[1]
            }
            
            val ogImageAltRegex = """<meta\s+[^>]*content=["']([^"']+)["']\s+[^>]*property=["']og:image["']""".toRegex(RegexOption.IGNORE_CASE)
            val ogImageAltMatch = ogImageAltRegex.find(html)
            if (ogImageAltMatch != null) {
                return@withContext ogImageAltMatch.groupValues[1]
            }
            
            val twitterImageRegex = """<meta\s+[^>]*name=["']twitter:image["']\s+[^>]*content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
            val twitterMatch = twitterImageRegex.find(html)
            if (twitterMatch != null) {
                return@withContext twitterMatch.groupValues[1]
            }
            
            trimmed
        } catch (e: Exception) {
            e.printStackTrace()
            trimmed
        }
    }
}

/**
 * Premium Liquid Glass Custom Streak visual block displaying the current daily count.
 * Reacts with multi-wave tactile liquid ripples on touch.
 */
@Composable
fun StreakCard(
    currentStreakCount: Int,
    isTodaySaved: Boolean,
    lastSavedDay: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_streak_wave")
    val ambientPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambientPhase"
    )
    val coroutineScope = rememberCoroutineScope()
    val activeWaves = remember { mutableStateListOf<TouchWave>() }
    var nextWaveId by remember { mutableStateOf(0L) }
    
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val newId = nextWaveId++
                        val rippleAnim = androidx.compose.animation.core.Animatable(0f)
                        val borderAnim = androidx.compose.animation.core.Animatable(0f)
                        val wave = TouchWave(id = newId, point = offset, rippleAnim = rippleAnim, borderAnim = borderAnim)
                        activeWaves.add(wave)

                        coroutineScope.launch {
                            rippleAnim.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(2800, easing = androidx.compose.animation.core.CubicBezierEasing(0.08f, 0.45f, 0.15f, 1f))
                            )
                            activeWaves.remove(wave)
                        }
                        coroutineScope.launch {
                            borderAnim.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(3400, easing = androidx.compose.animation.core.CubicBezierEasing(0.08f, 0.35f, 0.15f, 1f))
                            )
                        }
                        onTap()
                    }
                )
            }
            .drawBehind {
                val width = size.width
                val height = size.height
                val radiusPx = 20.dp.toPx()

                val baseAmp = 0.5f * density
                
                var totalOscillatingTouchAmp = 0f
                var maxBorderProgress = 0f
                var combinedTouchPoint = Offset.Zero
                var totalWeight = 0f

                for (wave in activeWaves) {
                    val progress = wave.borderAnim.value
                    if (progress > 0f && progress < 1f) {
                        val decayFactor = (1f - progress)
                        val waveAmp = 2.0f * density * decayFactor * sin(progress * 3.1415927f * 2f)
                        totalOscillatingTouchAmp += waveAmp
                        if (progress > maxBorderProgress) {
                            maxBorderProgress = progress
                        }
                        combinedTouchPoint += wave.point * (1f - progress)
                        totalWeight += (1f - progress)
                    }
                }

                val finalTouchPoint = if (totalWeight > 0f) combinedTouchPoint / totalWeight else Offset.Zero
                val currentAmplitude = (baseAmp + totalOscillatingTouchAmp).coerceAtLeast(0f)
                val currentPhase = ambientPhase + (maxBorderProgress * 6f)

                val wavyPath = createWavyRoundedRectPath(
                    width = width,
                    height = height,
                    radius = radiusPx,
                    amplitude = currentAmplitude,
                    frequency = 0.20f,
                    phase = currentPhase,
                    touchPoint = finalTouchPoint,
                    touchProgress = maxBorderProgress
                )

                val bgAlpha = 0.08f
                val glassBgBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = bgAlpha * 2.8f),
                        Color.White.copy(alpha = bgAlpha * 0.9f),
                        Color(0xFF04060A).copy(alpha = 0.85f),
                        Color(0xFF0E1118).copy(alpha = 0.72f),
                        Color.White.copy(alpha = bgAlpha * 1.4f)
                    ),
                    startY = 0f,
                    endY = height
                )
                drawPath(path = wavyPath, brush = glassBgBrush)

                val borderAlpha = 0.3f
                val borderBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = borderAlpha * 3.2f),
                        Color.White.copy(alpha = borderAlpha * 0.35f),
                        Color.White.copy(alpha = borderAlpha * 1.6f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(width, height)
                )
                drawPath(
                    path = wavyPath,
                    brush = borderBrush,
                    style = Stroke(width = 1.3f.dp.toPx())
                )

                clipPath(wavyPath) {
                    val maxRadius = kotlin.math.hypot(width, height)
                    for (wave in activeWaves) {
                        val ripVal = wave.rippleAnim.value
                        if (ripVal > 0f && ripVal < 1f) {
                            val tPoint = wave.point
                            
                            val r1 = ripVal * maxRadius
                            val alpha1 = 0.45f * (1f - ripVal) * sin(ripVal * Math.PI.toFloat())
                            if (alpha1 > 0f) {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = alpha1 * 0.45f),
                                            Color(0xFF80DEEA).copy(alpha = alpha1 * 0.18f),
                                            Color(0xFF0288D1).copy(alpha = alpha1 * 0.04f),
                                            Color.Transparent
                                        ),
                                        center = tPoint,
                                        radius = r1
                                    ),
                                    radius = r1,
                                    center = tPoint
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = alpha1 * 0.6f),
                                    radius = kotlin.math.max(0f, r1 - 1.5f.dp.toPx()),
                                    center = tPoint - Offset(1.5f.dp.toPx(), 1.5f.dp.toPx()),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                                drawCircle(
                                    color = Color.Black.copy(alpha = alpha1 * 0.12f),
                                    radius = r1 + 1.5f.dp.toPx(),
                                    center = tPoint + Offset(1.5f.dp.toPx(), 1.5f.dp.toPx()),
                                    style = Stroke(width = 1.5f.dp.toPx())
                                )
                                drawCircle(
                                    color = Color(0xFF4FC3F7).copy(alpha = alpha1),
                                    radius = r1,
                                    center = tPoint,
                                    style = Stroke(width = 2.5f.dp.toPx())
                                )
                            }
                            
                            val r2 = (ripVal - 0.18f).coerceAtLeast(0f) * maxRadius
                            val alpha2 = 0.25f * (1f - ripVal) * sin((ripVal - 0.18f).coerceAtLeast(0f) * Math.PI.toFloat())
                            if (alpha2 > 0f) {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = alpha2 * 0.25f),
                                            Color(0xFFE0F7FA).copy(alpha = alpha2 * 0.12f),
                                            Color.Transparent
                                        ),
                                        center = tPoint,
                                        radius = r2
                                    ),
                                    radius = r2,
                                    center = tPoint
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = alpha2 * 0.4f),
                                    radius = kotlin.math.max(0f, r2 - 1f.dp.toPx()),
                                    center = tPoint - Offset(1f.dp.toPx(), 1f.dp.toPx()),
                                    style = Stroke(width = 0.8f.dp.toPx())
                                )
                                drawCircle(
                                    color = Color(0xFFE0F7FA).copy(alpha = alpha2),
                                    radius = r2,
                                    center = tPoint,
                                    style = Stroke(width = 1.5f.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "YOUR STREAK SCORE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🔥",
                    fontSize = 32.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                GlassText(
                    text = "$currentStreakCount Days",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.testTag("streak_count_text")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isTodaySaved) "Today's registry completed! Streak secured ✅" 
                       else "Checklist incomplete or unsaved. Secure it before end of day!",
                fontSize = 11.sp,
                color = if (isTodaySaved) Color(0xFF81C784) else Color.White.copy(alpha = 0.65f),
                fontWeight = FontWeight.Medium
            )

            if (lastSavedDay.isNotEmpty() && lastSavedDay != "None") {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Last Completed Save: ${DateHelper.getFormattedDisplayDate(lastSavedDay)}",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        }
    }
}
