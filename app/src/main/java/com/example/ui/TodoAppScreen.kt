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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
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
    val inAppNotifications by viewModel.notifications.collectAsStateWithLifecycle()
    val slotOrder by viewModel.slotCategories.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf("home") } // "home" or "settings"

    // Intercept phone's physical back button to return to home screen instead of closing the app
    BackHandler(enabled = currentScreen == "settings") {
        currentScreen = "home"
    }

    var showFireStreakAnimation by remember { mutableStateOf(false) }
    var fireAnimationStreakValue by remember { mutableIntStateOf(0) }
    var showPermissionExplanation by remember { mutableStateOf(false) }
    var showNotificationDrawer by remember { mutableStateOf(false) }

    // Intercept phone's physical back button when notification drawer is open
    BackHandler(enabled = showNotificationDrawer) {
        showNotificationDrawer = false
    }
    var isNotificationStackExpanded by remember { mutableStateOf(false) }

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

    val backgroundBlurRadius = if (showNotificationDrawer) 16.dp else 0.dp

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(backgroundBlurRadius)
        ) {
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

                            // Notification bell button left of settings in liquid-glass style
                            IconButton(
                                onClick = { showNotificationDrawer = true },
                                modifier = Modifier
                                    .glassButton(isCircle = true)
                                    .testTag("notification_button")
                                    .size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notifications Drawer",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    
                                    val notificationListCount = remember(showNotificationDrawer) {
                                        inAppNotifications.size
                                    }
                                    if (notificationListCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .background(Color(0xFFFFB74D), CircleShape)
                                                .align(Alignment.TopEnd)
                                                .offset(x = 1.dp, y = (-1).dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

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
                                val dynamicSlotOrder = if (slotOrder.isEmpty()) {
                                    listOf(
                                        "Slot 1: 07AM to 02PM -- Backlog clear",
                                        "Slot 2: 02PM to 04PM -- Revision",
                                        "Slot 3: 04PM to 09PM -- Classes",
                                        "Slot 4: 09PM to 12AM -- Questions and H.W",
                                        "Custom Tasks"
                                    )
                                } else {
                                    slotOrder.map { DateHelper.normalizeSlotName(it) }
                                }
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
                                    dynamicSlotOrder.forEach { slotName ->
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
                        .background(Color(0xF50F1320), shape = RoundedCornerShape(24.dp))
                        .liquidGlass(cornerRadius = 24.dp, borderAlpha = 0.6f, bgAlpha = 0.42f)
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
        } // End of outer blur wrapper Box containing background and screen content

        // ------------------------------------------------------------------
        // RECTANGLE GLASS SYSTEM NOTIFICATION OVERLAY (Slideable & Stackable)
        // ------------------------------------------------------------------
        if (showNotificationDrawer) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                // 1. Transparent liquid frosted backdrop that doesn't overlap text
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF07090E).copy(alpha = 0.32f)) // Translucent, very light frosted feel, not very dark or opaque
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { showNotificationDrawer = false }
                )

                // 2. Clear foreground container with padding that holds the custom rectangle notification tabs
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .padding(top = 24.dp, bottom = 12.dp)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { /* prevent backdrop selection click-through */ }
                ) {
                    // Header mimicking iOS Notification Center title
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Notification Center",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Daily status & alarm logs",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    viewModel.clearAllNotifications()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFFB74D))
                            ) {
                                Text(
                                    text = "Clear All",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = { showNotificationDrawer = false },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close overlay",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    val notificationList = inAppNotifications

                    if (notificationList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.15f),
                                    modifier = Modifier.size(60.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No Notifications",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else {
                        // Display beautiful items in a single continuous scrollable deck with dynamic fanning stacks
                        val lazyListState = rememberLazyListState()

                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .navigationBarsPadding(),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(notificationList, key = { _, item -> item.id }) { index, item ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(100f - index.toFloat())
                                        .graphicsLayer {
                                            // Depend on scroll offsets to trigger draw-phase continuous updates
                                            val scrollTrigger = lazyListState.firstVisibleItemIndex + lazyListState.firstVisibleItemScrollOffset
                                            
                                            var transY = 0f
                                            var sX = 1f
                                            var sY = 1f
                                            var alp = 1f

                                            val layoutInfo = lazyListState.layoutInfo
                                            val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { visItem -> visItem.index == index }
                                            if (visibleItem != null) {
                                                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                                                val bottomPaddingPx = 36.dp.toPx()
                                                val anchorY = viewportHeight - bottomPaddingPx
                                                
                                                val itemBottom = visibleItem.offset + visibleItem.size
                                                if (itemBottom > anchorY) {
                                                    val extra = itemBottom - anchorY
                                                    val maxDepthPx = 300.dp.toPx()
                                                    val depth = (extra / maxDepthPx).coerceIn(0f, 1f)
                                                    
                                                    transY = -extra * 0.82f
                                                    sX = 1f - (depth * 0.16f)
                                                    sY = 1f - (depth * 0.16f)
                                                    alp = 1f - (depth * 0.70f)
                                                }
                                            }

                                            translationY = transY
                                            scaleX = sX
                                            scaleY = sY
                                            alpha = alp
                                        }
                                ) {
                                    SlideableNotificationCard(
                                        item = item,
                                        onDismiss = {
                                            viewModel.deleteNotification(item)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
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
                        Color(0xFF04060A).copy(alpha = (baseBgAlpha * 1.5f).coerceAtMost(1.0f)), // Smooth obsidian core absorption
                        Color(0xFF0E1118).copy(alpha = (baseBgAlpha * 1.2f).coerceAtMost(1.0f)), // Semi-translucent base
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

    val rawSlots by viewModel.slotCategories.collectAsStateWithLifecycle()
    val dynamicSlotOrder = if (rawSlots.isEmpty()) {
        listOf(
            "Slot 1: 07AM to 02PM -- Backlog clear",
            "Slot 2: 02PM to 04PM -- Revision",
            "Slot 3: 04PM to 09PM -- Classes",
            "Slot 4: 09PM to 12AM -- Questions and H.W",
            "Custom Tasks"
        )
    } else {
        rawSlots.map { DateHelper.normalizeSlotName(it) }
    }

    val tasksState by viewModel.tasks.collectAsStateWithLifecycle()

    var showSlotManageSection by remember { mutableStateOf(false) }
    var showTaskManageSection by remember { mutableStateOf(false) }

    // Dialog state for adding a slot
    var showAddSlotDialog by remember { mutableStateOf(false) }
    var newSlotNameInput by remember { mutableStateOf("") }

    // Dialog state for editing a slot
    var editingSlotNameOld by remember { mutableStateOf<String?>(null) }
    var editingSlotNameNew by remember { mutableStateOf("") }

    // Dialog state for editing a task
    var editingTask by remember { mutableStateOf<com.example.data.Task?>(null) }
    var editingTaskTitle by remember { mutableStateOf("") }
    var editingTaskHour by remember { mutableIntStateOf(12) }
    var editingTaskMinute by remember { mutableIntStateOf(0) }
    var editingTaskSlot by remember { mutableStateOf("") }

    // Dialog state for adding a task
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var addTaskTitleInput by remember { mutableStateOf("") }
    var addTaskHourInput by remember { mutableIntStateOf(12) }
    var addTaskMinuteInput by remember { mutableIntStateOf(0) }
    var addTaskSlotInput by remember { mutableStateOf("") }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isResolvingUrl = true
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    if (stream != null) {
                        val mimeType = context.contentResolver.getType(uri) ?: ""
                        val isVideo = mimeType.startsWith("video") || uri.toString().contains("video", ignoreCase = true)
                        val ext = if (isVideo) "mp4" else "jpg"
                        val destinationFile = java.io.File(context.filesDir, "custom_wallpaper.$ext")
                        
                        if (destinationFile.exists()) {
                            destinationFile.delete()
                        }
                        
                        destinationFile.outputStream().use { outStream ->
                            stream.copyTo(outStream)
                        }
                        
                        val path = destinationFile.absolutePath
                        inputUrl = path
                        onCustomWallpaperUrlChanged(path)
                        onWallpaperStyleChanged("custom_photo")
                        Toast.makeText(context, "Local ${if (isVideo) "video" else "image"} wallpaper loaded!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to import file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isResolvingUrl = false
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    viewModel.exportData(
                        context = context,
                        outputStream = outputStream,
                        onSuccess = {
                            Toast.makeText(context, "All data successfully exported!", Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            Toast.makeText(context, "Export failed: $error", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Unable to save file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    viewModel.importData(
                        context = context,
                        inputStream = inputStream,
                        onSettingsLoaded = { wallEnabled, wallStyle, wallUrl, vibEnabled, soundEnabled, notifEnabled ->
                            onWallpaperEnabledChanged(wallEnabled)
                            onWallpaperStyleChanged(wallStyle)
                            onCustomWallpaperUrlChanged(wallUrl)
                            onVibrationEnabledChanged(vibEnabled)
                            onSoundsEnabledChanged(soundEnabled)
                            onNotificationsEnabledChanged(notifEnabled)
                        },
                        onSuccess = {
                            Toast.makeText(context, "Import successful! Restored wallpaper, streak, and checklist.", Toast.LENGTH_LONG).show()
                        },
                        onError = { error ->
                            Toast.makeText(context, "Import failed: $error", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Unable to read backup file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

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

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { pickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassButton(cornerRadius = 14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Choose local video or image",
                                tint = Color(0xFFFF9100),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "📁 Choose Local Video / Image",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

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

            // SYSTEM ACCESS PANEL
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(cornerRadius = 20.dp, borderAlpha = 0.35f, bgAlpha = 0.08f)
                    .padding(16.dp)
            ) {
                Text(
                    text = "ADVANCED SYSTEM ACCESS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "Manage system slot categories, rename boundaries, and explicitly edit/add structured tasks including title, time, and precise alert schedules.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 16.dp),
                    lineHeight = 15.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Toggle Custom Slots
                    Button(
                        onClick = {
                            showSlotManageSection = !showSlotManageSection
                            showTaskManageSection = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (showSlotManageSection) Color(0xFFFF9100).copy(alpha = 0.2f) else Color.Transparent),
                        modifier = Modifier
                            .weight(1f)
                            .glassButton(cornerRadius = 14.dp)
                    ) {
                        Text(
                            text = if (showSlotManageSection) "🎯 Hide Slots" else "🛠️ Manage Slots",
                            color = if (showSlotManageSection) Color(0xFFFF9100) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Toggle Custom Tasks
                    Button(
                        onClick = {
                            showTaskManageSection = !showTaskManageSection
                            showSlotManageSection = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (showTaskManageSection) Color(0xFFFF9100).copy(alpha = 0.2f) else Color.Transparent),
                        modifier = Modifier
                            .weight(1f)
                            .glassButton(cornerRadius = 14.dp)
                    ) {
                        Text(
                            text = if (showTaskManageSection) "📋 Hide Tasks" else "📝 Manage Tasks",
                            color = if (showTaskManageSection) Color(0xFFFF9100) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Expandable Slots Section
                if (showSlotManageSection) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Add Slot Button
                    Button(
                        onClick = {
                            newSlotNameInput = ""
                            showAddSlotDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassButton(cornerRadius = 12.dp)
                    ) {
                        Text(text = "➕ Add Custom Slot", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dynamicSlotOrder.forEach { slot ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.04f), shape = RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = slot,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                if (slot != "Custom Tasks") {
                                    Row {
                                        IconButton(
                                            onClick = {
                                                editingSlotNameOld = slot
                                                editingSlotNameNew = slot
                                            }
                                        ) {
                                            Text(text = "✏️", fontSize = 14.sp)
                                        }
                                        IconButton(
                                            onClick = {
                                                viewModel.removeSlotCategory(slot)
                                            }
                                        ) {
                                            Text(text = "🗑️", fontSize = 14.sp)
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "Locked",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Expandable Tasks Section
                if (showTaskManageSection) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Add Task Button
                    Button(
                        onClick = {
                            addTaskTitleInput = ""
                            addTaskHourInput = 12
                            addTaskMinuteInput = 0
                            addTaskSlotInput = dynamicSlotOrder.firstOrNull() ?: "Custom Tasks"
                            showAddTaskDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassButton(cornerRadius = 12.dp)
                    ) {
                        Text(text = "➕ Add Custom Task", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))

                    if (tasksState.isEmpty()) {
                        Text(
                            text = "No tasks found for today. Add a custom task to begin!",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tasksState.forEach { task ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.04f), shape = RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${task.slotCategory} • ⏰ ${String.format(java.util.Locale.getDefault(), "%02d:%02d", task.hour, task.minute)}",
                                            fontSize = 10.sp,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            editingTask = task
                                            editingTaskTitle = task.title
                                            editingTaskHour = task.hour
                                            editingTaskMinute = task.minute
                                            editingTaskSlot = task.slotCategory
                                        }
                                    ) {
                                        Text(text = "✏️", fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // BACKUP & DEVICE TRANSFER ZONE
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(cornerRadius = 20.dp, borderAlpha = 0.3f, bgAlpha = 0.08f)
                    .padding(16.dp)
            ) {
                Text(
                    text = "BACKUP & DEVICE TRANSFER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "Export your app data (including wallpaper, streak count, checklist ticks, and alert settings) to a file on this device. Move this file to a new device and import it to clone your entire setup. Note: Importing will erase any existing settings on the target device.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 16.dp),
                    lineHeight = 15.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Export button
                    Button(
                        onClick = {
                            try {
                                exportLauncher.launch("liquid_glass_todo_backup.json")
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to start export: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .weight(1.5f)
                            .glassButton(cornerRadius = 14.dp)
                    ) {
                        Text(text = "📤 Export Data", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Import button
                    Button(
                        onClick = {
                            try {
                                importLauncher.launch(arrayOf("application/json", "*/*"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to start import: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .weight(1.5f)
                            .glassButton(cornerRadius = 14.dp)
                    ) {
                        Text(text = "📥 Import Data", color = Color(0xFFFF9100), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
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
                            .background(Color(0xF50F1320), shape = RoundedCornerShape(24.dp))
                            .liquidGlass(cornerRadius = 24.dp, borderAlpha = 0.6f, bgAlpha = 0.42f)
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

        // Add Slot Dialog
        if (showAddSlotDialog) {
            Dialog(onDismissRequest = { showAddSlotDialog = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(Color(0xF50F1320), shape = RoundedCornerShape(24.dp))
                        .liquidGlass(cornerRadius = 24.dp, borderAlpha = 0.6f, bgAlpha = 0.42f)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "➕ Add Custom Slot Category",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = newSlotNameInput,
                        onValueChange = { newSlotNameInput = it },
                        placeholder = { Text("e.g. Slot 5: Evening Focus", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF9100).copy(alpha = 0.7f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddSlotDialog = false }) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newSlotNameInput.isNotBlank()) {
                                    viewModel.addSlotCategory(newSlotNameInput)
                                    showAddSlotDialog = false
                                    newSlotNameInput = ""
                                } else {
                                    Toast.makeText(context, "Slot Name cannot be empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100))
                        ) {
                            Text("Add", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Edit Slot Dialog
        if (editingSlotNameOld != null) {
            Dialog(onDismissRequest = { editingSlotNameOld = null }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(Color(0xF50F1320), shape = RoundedCornerShape(24.dp))
                        .liquidGlass(cornerRadius = 24.dp, borderAlpha = 0.6f, bgAlpha = 0.42f)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📝 Rename Slot Category",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = editingSlotNameNew,
                        onValueChange = { editingSlotNameNew = it },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF9100).copy(alpha = 0.7f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { editingSlotNameOld = null }) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val oldName = editingSlotNameOld
                                if (oldName != null && editingSlotNameNew.isNotBlank()) {
                                    viewModel.renameSlotCategory(oldName, editingSlotNameNew)
                                    editingSlotNameOld = null
                                    editingSlotNameNew = ""
                                } else {
                                    Toast.makeText(context, "Slot Name cannot be empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100))
                        ) {
                            Text("Rename", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Add Task Dialog
        if (showAddTaskDialog) {
            Dialog(onDismissRequest = { showAddTaskDialog = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .background(Color(0xF50F1320), shape = RoundedCornerShape(24.dp))
                        .liquidGlass(cornerRadius = 24.dp, borderAlpha = 0.6f, bgAlpha = 0.42f)
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🆕 Add Custom Task Details",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = addTaskTitleInput,
                        onValueChange = { addTaskTitleInput = it },
                        label = { Text("Task Title", color = Color.White.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF9100).copy(alpha = 0.7f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = addTaskHourInput.toString(),
                            onValueChange = { addTaskHourInput = it.toIntOrNull()?.coerceIn(0, 23) ?: 12 },
                            label = { Text("Hour (0-23)", color = Color.White.copy(alpha = 0.6f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFF9100).copy(alpha = 0.7f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = addTaskMinuteInput.toString(),
                            onValueChange = { addTaskMinuteInput = it.toIntOrNull()?.coerceIn(0, 59) ?: 0 },
                            label = { Text("Minute (0-59)", color = Color.White.copy(alpha = 0.6f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFF9100).copy(alpha = 0.7f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = "SELECT SLOT CATEGORY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.Start).padding(bottom = 6.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        dynamicSlotOrder.forEach { slot ->
                            val isSelected = addTaskSlotInput == slot
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { addTaskSlotInput = slot }
                                    .background(
                                        if (isSelected) Color(0xFFFF9100).copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isSelected) "🟢 $slot" else "⚪ $slot",
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color(0xFFFF9100) else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showAddTaskDialog = false }) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (addTaskTitleInput.isNotBlank()) {
                                    viewModel.addTaskWithSlot(
                                        addTaskTitleInput,
                                        addTaskHourInput,
                                        addTaskMinuteInput,
                                        addTaskSlotInput
                                    )
                                    showAddTaskDialog = false
                                    addTaskTitleInput = ""
                                } else {
                                    Toast.makeText(context, "Title cannot be blank", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100))
                        ) {
                            Text("Add Task", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Edit Task Dialog
        if (editingTask != null) {
            Dialog(onDismissRequest = { editingTask = null }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .background(Color(0xF50F1320), shape = RoundedCornerShape(24.dp))
                        .liquidGlass(cornerRadius = 24.dp, borderAlpha = 0.6f, bgAlpha = 0.42f)
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "✏️ Edit Task Details",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = editingTaskTitle,
                        onValueChange = { editingTaskTitle = it },
                        label = { Text("Task Title", color = Color.White.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF9100).copy(alpha = 0.7f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = editingTaskHour.toString(),
                            onValueChange = { editingTaskHour = it.toIntOrNull()?.coerceIn(0, 23) ?: 12 },
                            label = { Text("Hour (0-23)", color = Color.White.copy(alpha = 0.6f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFF9100).copy(alpha = 0.7f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = editingTaskMinute.toString(),
                            onValueChange = { editingTaskMinute = it.toIntOrNull()?.coerceIn(0, 59) ?: 0 },
                            label = { Text("Minute (0-59)", color = Color.White.copy(alpha = 0.6f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFF9100).copy(alpha = 0.7f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = "ASSIGN TO SLOT CATEGORY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.Start).padding(bottom = 6.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        dynamicSlotOrder.forEach { slot ->
                            val isSelected = editingTaskSlot == slot
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingTaskSlot = slot }
                                    .background(
                                        if (isSelected) Color(0xFFFF9100).copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isSelected) "🟢 $slot" else "⚪ $slot",
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color(0xFFFF9100) else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                editingTask?.let { task ->
                                    viewModel.deleteTask(task)
                                    editingTask = null
                                }
                            }
                        ) {
                            Text(text = "🗑️", fontSize = 22.sp)
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(onClick = { editingTask = null }) {
                                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                            }

                            Button(
                                onClick = {
                                    editingTask?.let { task ->
                                        if (editingTaskTitle.isNotBlank()) {
                                            viewModel.updateTaskDetails(
                                                task,
                                                editingTaskTitle,
                                                editingTaskHour,
                                                editingTaskMinute,
                                                editingTaskSlot
                                            )
                                            editingTask = null
                                        } else {
                                            Toast.makeText(context, "Title cannot be blank", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100))
                            ) {
                                Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
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
 * Runs a highly-efficient real-time background video wallpaper using a native MediaPlayer inside a TextureView.
 * Supports both local files (offline device storage imports) and network streaming video URLs.
 */
@Composable
fun CustomVideoBackground(videoUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val textureView = TextureView(ctx)
                textureView.apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            val surface = Surface(surfaceTexture)
                            try {
                                mediaPlayerRef.value?.release()
                                val mp = MediaPlayer().apply {
                                    setSurface(surface)
                                    if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://") || videoUrl.startsWith("content://")) {
                                        setDataSource(ctx, android.net.Uri.parse(videoUrl))
                                    } else {
                                        setDataSource(videoUrl)
                                    }
                                    isLooping = true
                                    setVolume(0f, 0f) // mute background music
                                    setOnPreparedListener { player ->
                                        // Center Crop / Aspect Ratio cover
                                        val videoWidth = player.videoWidth.toFloat()
                                        val videoHeight = player.videoHeight.toFloat()
                                        if (videoWidth > 0f && videoHeight > 0f) {
                                            val viewWidth = textureView.width.toFloat()
                                            val viewHeight = textureView.height.toFloat()
                                            val sx = viewWidth / videoWidth
                                            val sy = viewHeight / videoHeight
                                            val maxScale = kotlin.math.max(sx, sy)
                                            val matrix = Matrix()
                                            matrix.setScale(maxScale / sx, maxScale / sy, viewWidth / 2f, viewHeight / 2f)
                                            textureView.setTransform(matrix)
                                        }
                                        player.start()
                                    }
                                    prepareAsync()
                                }
                                mediaPlayerRef.value = mp
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                            val mp = mediaPlayerRef.value ?: return
                            try {
                                val videoWidth = mp.videoWidth.toFloat()
                                val videoHeight = mp.videoHeight.toFloat()
                                if (videoWidth > 0f && videoHeight > 0f) {
                                    val viewWidth = width.toFloat()
                                    val viewHeight = height.toFloat()
                                    val sx = viewWidth / videoWidth
                                    val sy = viewHeight / videoHeight
                                    val maxScale = kotlin.math.max(sx, sy)
                                    val matrix = Matrix()
                                    matrix.setScale(maxScale / sx, maxScale / sy, viewWidth / 2f, viewHeight / 2f)
                                    textureView.setTransform(matrix)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            mediaPlayerRef.value?.release()
                            mediaPlayerRef.value = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = {
                mediaPlayerRef.value?.release()
                mediaPlayerRef.value = null
            }
        )

        // Add premium darkened overlay matching custom photo wallpaper
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
        )
    }

    DisposableEffect(videoUrl) {
        onDispose {
            mediaPlayerRef.value?.release()
            mediaPlayerRef.value = null
        }
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(enabled = true, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
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
            FireFlameAnimation(modifier = Modifier.size(150.dp))

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
 * Styled as a gorgeous 2D cartoon flame with layered fire tongues and floating detached ember sparks as requested.
 */
@Composable
fun FireFlameAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "FlameAnimation")
    
    // Wave animation phase
    val waveTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveTime"
    )

    // Breathing height scales
    val heightScaleOuter by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outerHeight"
    )
    
    val heightScaleInner by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "innerHeight"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val cx = width * 0.5f
        val cy = height * 0.82f // bottom base
        
        // Dynamic bottom glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF1744).copy(alpha = 0.4f),
                    Color(0xFFFF9100).copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = width * 0.55f
            ),
            radius = width * 0.55f,
            center = Offset(cx, cy)
        )

        // Floating detached fire drops/ember caps rising from the tip
        for (i in 0..2) {
            val phaseOffset = i * (6.28318f / 3f)
            val dropProgress = ((waveTime + phaseOffset) % 6.28318f) / 6.28318f // 0f to 1f
            
            val startY = cy - height * 0.7f
            val endY = cy - height * 1.35f
            val curY = startY + (endY - startY) * dropProgress
            
            // Side drift air physics
            val driftX = cx + kotlin.math.sin((waveTime * 1.8f + i * 1.5f).toDouble()).toFloat() * width * 0.08f
            
            // Starts small, grows, and shrinks completely
            val rawSize = 6.dp.toPx() + (i % 2) * 2.dp.toPx()
            val dropSize = if (dropProgress < 0.2f) {
                rawSize * (dropProgress / 0.2f)
            } else {
                rawSize * (1f - dropProgress)
            }
            
            val alpha = (1f - dropProgress).coerceIn(0f, 1f)
            
            if (dropSize > 0.5f) {
                drawCircle(
                    color = Color(0xFFFF3D00).copy(alpha = alpha * 0.75f),
                    center = Offset(driftX, curY),
                    radius = dropSize * 1.5f
                )
                drawCircle(
                    color = Color(0xFFFFEB3B).copy(alpha = alpha * 0.95f),
                    center = Offset(driftX, curY),
                    radius = dropSize * 0.8f
                )
            }
        }

        // OUTER LICKING FLAME (Red-Crimson)
        val outerPath = Path().apply {
            val startX = cx - width * 0.28f
            moveTo(startX, cy)
            
            // Left tongue curves
            val lCtrl1X = cx - width * 0.45f + kotlin.math.sin(waveTime.toDouble() + 0.0).toFloat() * width * 0.08f
            val lCtrl1Y = cy - height * 0.25f
            val lPt1X = cx - width * 0.15f + kotlin.math.sin(waveTime.toDouble() + 0.8).toFloat() * width * 0.05f
            val lPt1Y = cy - height * 0.42f
            
            val lCtrl2X = cx - width * 0.26f + kotlin.math.sin(waveTime.toDouble() + 1.6).toFloat() * width * 0.06f
            val lCtrl2Y = cy - height * 0.62f
            val tipX = cx + kotlin.math.sin(waveTime.toDouble() + 2.4).toFloat() * width * 0.12f
            val tipY = cy - height * 0.82f * heightScaleOuter
            
            cubicTo(lCtrl1X, lCtrl1Y, lPt1X, lPt1Y, tipX, tipY)
            
            // Right curve back down (asymmetric for organic cartoon motion)
            val rCtrl1X = cx + width * 0.18f + kotlin.math.sin(waveTime.toDouble() + 3.2).toFloat() * width * 0.06f
            val rCtrl1Y = cy - height * 0.58f
            val rPt1X = cx + width * 0.22f + kotlin.math.sin(waveTime.toDouble() + 4.0).toFloat() * width * 0.05f
            val rPt1Y = cy - height * 0.38f
            
            val rCtrl2X = cx + width * 0.42f + kotlin.math.sin(waveTime.toDouble() + 1.2).toFloat() * width * 0.08f
            val rCtrl2Y = cy - height * 0.18f
            val endX = cx + width * 0.28f
            
            cubicTo(rCtrl1X, rCtrl1Y, rPt1X, rPt1Y, endX, cy)
            
            // Base connection
            quadraticTo(cx, cy + height * 0.06f, startX, cy)
            close()
        }
        drawPath(
            path = outerPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFE53935),  // Vivid crimson
                    Color(0xFFB71C1C)   // Deep solid red
                )
            )
        )

        // MIDDLE LICKING FLAME (Vivid Orange)
        val middlePath = Path().apply {
            val startX = cx - width * 0.2f
            moveTo(startX, cy)
            
            val lCtrl1X = cx - width * 0.32f + kotlin.math.sin(waveTime.toDouble() + 0.4).toFloat() * width * 0.06f
            val lCtrl1Y = cy - height * 0.22f
            val lPt1X = cx - width * 0.12f + kotlin.math.sin(waveTime.toDouble() + 1.2).toFloat() * width * 0.04f
            val lPt1Y = cy - height * 0.36f
            
            val lCtrl2X = cx - width * 0.18f + kotlin.math.sin(waveTime.toDouble() + 2.0).toFloat() * width * 0.04f
            val lCtrl2Y = cy - height * 0.52f
            val tipX = cx + kotlin.math.sin(waveTime.toDouble() + 2.8).toFloat() * width * 0.08f
            val tipY = cy - height * 0.68f * heightScaleInner
            
            cubicTo(lCtrl1X, lCtrl1Y, lPt1X, lPt1Y, tipX, tipY)
            
            val rCtrl1X = cx + width * 0.13f + kotlin.math.sin(waveTime.toDouble() + 3.6).toFloat() * width * 0.04f
            val rCtrl1Y = cy - height * 0.48f
            val rPt1X = cx + width * 0.16f + kotlin.math.sin(waveTime.toDouble() + 4.4).toFloat() * width * 0.03f
            val rPt1Y = cy - height * 0.32f
            
            val rCtrl2X = cx + width * 0.32f + kotlin.math.sin(waveTime.toDouble() + 1.6).toFloat() * width * 0.06f
            val rCtrl2Y = cy - height * 0.15f
            val endX = cx + width * 0.2f
            
            cubicTo(rCtrl1X, rCtrl1Y, rPt1X, rPt1Y, endX, cy)
            
            quadraticTo(cx, cy + height * 0.04f, startX, cy)
            close()
        }
        drawPath(
            path = middlePath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFB8C00),  // Bright orange
                    Color(0xFFE65100)   // Burnt orange
                )
            )
        )

        // INNER GOLDEN CORE (Solar Yellow-White)
        val innerPath = Path().apply {
            val startX = cx - width * 0.12f
            moveTo(startX, cy)
            
            val lCtrl1X = cx - width * 0.20f + kotlin.math.sin(waveTime.toDouble() + 0.8).toFloat() * width * 0.03f
            val lCtrl1Y = cy - height * 0.18f
            val lPt1X = cx - width * 0.08f + kotlin.math.sin(waveTime.toDouble() + 1.6).toFloat() * width * 0.02f
            val lPt1Y = cy - height * 0.28f
            
            val lCtrl2X = cx - width * 0.10f + kotlin.math.sin(waveTime.toDouble() + 2.4).toFloat() * width * 0.02f
            val lCtrl2Y = cy - height * 0.4f
            val tipX = cx + kotlin.math.sin(waveTime.toDouble() + 3.2).toFloat() * width * 0.05f
            val tipY = cy - height * 0.5f * heightScaleInner
            
            cubicTo(lCtrl1X, lCtrl1Y, lPt1X, lPt1Y, tipX, tipY)
            
            val rCtrl1X = cx + width * 0.08f + kotlin.math.sin(waveTime.toDouble() + 4.0).toFloat() * width * 0.02f
            val rCtrl1Y = cy - height * 0.38f
            val rPt1X = cx + width * 0.10f + kotlin.math.sin(waveTime.toDouble() + 4.8).toFloat() * width * 0.02f
            val rPt1Y = cy - height * 0.25f
            
            val rCtrl2X = cx + width * 0.20f + kotlin.math.sin(waveTime.toDouble() + 2.0).toFloat() * width * 0.03f
            val rCtrl2Y = cy - height * 0.12f
            val endX = cx + width * 0.12f
            
            cubicTo(rCtrl1X, rCtrl1Y, rPt1X, rPt1Y, endX, cy)
            
            quadraticTo(cx, cy + height * 0.02f, startX, cy)
            close()
        }
        drawPath(
            path = innerPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFFFE0),  // Bright yellow-white
                    Color(0xFFFFD54F)   // Golden amber
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
                        Color(0xFF04060A).copy(alpha = bgAlpha * 1.5f),
                        Color(0xFF0E1118).copy(alpha = bgAlpha * 1.2f),
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

@Composable
fun SlideableNotificationCard(
    item: com.example.data.InAppNotificationEntity,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    alpha: Float = 1f
) {
    var offsetX by remember(item) { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = if (isDragging) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "SwipeOffset"
    )
    
    val maxDismissThreshold = 140f
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = animatedOffsetX
                this.alpha = alpha * (1f - (kotlin.math.abs(animatedOffsetX) / 600f)).coerceIn(0f, 1f)
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(item) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        if (kotlin.math.abs(offsetX) > maxDismissThreshold) {
                            onDismiss()
                        } else {
                            offsetX = 0f
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        offsetX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount * 1.25f
                    }
                )
            }
    ) {
        NotificationCardContent(item = item)
    }
}

@Composable
fun NotificationCardContent(
    item: com.example.data.InAppNotificationEntity,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(cornerRadius = 18.dp, borderAlpha = 0.28f, bgAlpha = 0.08f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val icon = when {
                item.title.contains("Ticked", ignoreCase = true) -> Icons.Default.Warning
                item.title.contains("Secure", ignoreCase = true) -> Icons.Default.Lock
                item.title.contains("Task", ignoreCase = true) -> Icons.Default.CheckCircle
                else -> Icons.Default.Notifications
            }
            
            val iconTint = when {
                item.title.contains("Ticked", ignoreCase = true) -> Color(0xFFFFB74D)
                item.title.contains("Secure", ignoreCase = true) -> Color(0xFFFF8A80)
                item.title.contains("Task", ignoreCase = true) -> Color(0xFF81C784)
                else -> Color.White.copy(alpha = 0.9f)
            }
            
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.time,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.45f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.85f),
                lineHeight = 15.sp
            )
        }
    }
}
