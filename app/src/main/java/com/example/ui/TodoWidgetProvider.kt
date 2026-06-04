package com.example.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Color
import android.graphics.RadialGradient
import android.graphics.PointF
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.DateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TodoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Run update in a coroutine to retrieve Room database values safely
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val taskDao = db.taskDao()
            
            // Get streak direct
            val streak = taskDao.getStreakDirect()
            val streakCount = streak?.streakCount ?: 0
            val streakText = "🔥 $streakCount"

            val todayStr = DateHelper.getTodayString()
            val todayTasks = taskDao.getTasksForDateList(todayStr)
            val hasTasks = todayTasks.isNotEmpty()

            // Pre-calculate/generate wavy glass bitmap on the background thread
            // Standard nice high-quality container dimensions (600x450px)
            val wavyBg = try {
                createWavyBitmap(context, 600, 450)
            } catch (e: Exception) {
                Log.e("TodoWidgetProvider", "Error generating wavy background", e)
                null
            }

            withContext(Dispatchers.Main) {
                for (widgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.todo_widget)

                    if (wavyBg != null) {
                        views.setImageViewBitmap(R.id.widget_background, wavyBg)
                    }

                    // 1. Set the Title & Streak status
                    views.setTextViewText(R.id.widget_streak, streakText)

                    // 2. Set Remote Adapter for scrollable ListView of tasks
                    val serviceIntent = Intent(context, TodoWidgetService::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        // This makes the intent unique to ensure list updates are bound correctly
                        data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                    }
                    views.setRemoteAdapter(R.id.widget_list, serviceIntent)
                    views.setEmptyView(R.id.widget_list, R.id.widget_empty_text)

                    // 3. Setup click template on ListView rows to allow completing tasks on item tap
                    val clickIntent = Intent(context, TodoWidgetProvider::class.java).apply {
                        action = ACTION_TOGGLE_TASK
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    }
                    val clickPendingIntent = PendingIntent.getBroadcast(
                        context,
                        widgetId,
                        clickIntent,
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    views.setPendingIntentTemplate(R.id.widget_list, clickPendingIntent)

                    // 4. Click target to launch Main App (to add/view/save tasks)
                    val appIntent = Intent(context, MainActivity::class.java)
                    val appPendingIntent = PendingIntent.getActivity(
                        context,
                        widgetId + 1000,
                        appIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    // Let header click or bottom save-button click open the app to add & save tasks
                    views.setOnClickPendingIntent(R.id.widget_title, appPendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_header, appPendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_save_button, appPendingIntent)

                    // Notify ListView manager to invalidate cache
                    appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_TASK) {
            val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
            val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            
            if (taskId != -1) {
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getDatabase(context)
                    val taskDao = db.taskDao()
                    
                    val targetTask = taskDao.getTaskByIdDirect(taskId)

                    if (targetTask != null) {
                        // Toggle state
                        val updatedTask = targetTask.copy(isCompleted = !targetTask.isCompleted)
                        taskDao.updateTask(updatedTask)
                        
                        // We also need to recalculate or trigger update check for streaks if necessary
                        // Re-trigger visual updates for all widgets
                        val widgetManager = AppWidgetManager.getInstance(context)
                        val component = ComponentName(context, TodoWidgetProvider::class.java)
                        val appWidgetIds = widgetManager.getAppWidgetIds(component)

                        withContext(Dispatchers.Main) {
                            widgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list)
                            // Call update again to update the streak if changed
                            onUpdate(context, widgetManager, appWidgetIds)
                        }
                    }
                }
            }
        }
    }

    private fun createWavyBitmap(context: Context, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val density = context.resources.displayMetrics.density
        val radius = 20f * density
        val borderStrokeWidth = 1.2f * density
        
        // Pad slightly to avoid clipping the stroke and wave displacements at the edges
        val padding = 4f * density
        val w = width - 2 * padding
        val h = height - 2 * padding
        
        // Target amplitude and frequency for the static wavy look
        val amplitude = 2.5f * density
        
        // Fixed phase slosh
        val phase = 1.2f 
        
        val path = Path()
        val points = mutableListOf<PointF>()
        val normals = mutableListOf<PointF>()
        
        // Clamp corner radius
        val safeRadius = kotlin.math.min(radius, kotlin.math.min(w / 2f, h / 2f))
        
        // Top Edge
        val topCount = 16
        for (i in 0..topCount) {
            val t = i.toFloat() / topCount
            val x = padding + safeRadius + t * (w - 2 * safeRadius)
            points.add(PointF(x, padding))
            normals.add(PointF(0f, -1f))
        }
        
        // Top-Right Corner Arc
        val arcCount = 10
        for (i in 0..arcCount) {
            val t = i.toFloat() / arcCount
            val angle = -Math.PI / 2 + t * Math.PI / 2
            val x = padding + w - safeRadius + kotlin.math.cos(angle).toFloat() * safeRadius
            val y = padding + safeRadius + kotlin.math.sin(angle).toFloat() * safeRadius
            points.add(PointF(x, y))
            normals.add(PointF(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat()))
        }
        
        // Right Edge
        val rightCount = 16
        for (i in 0..rightCount) {
            val t = i.toFloat() / rightCount
            val y = padding + safeRadius + t * (h - 2 * safeRadius)
            points.add(PointF(padding + w, y))
            normals.add(PointF(1f, 0f))
        }
        
        // Bottom-Right Corner Arc
        for (i in 0..arcCount) {
            val t = i.toFloat() / arcCount
            val angle = t * Math.PI / 2
            val x = padding + w - safeRadius + kotlin.math.cos(angle).toFloat() * safeRadius
            val y = padding + h - safeRadius + kotlin.math.sin(angle).toFloat() * safeRadius
            points.add(PointF(x, y))
            normals.add(PointF(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat()))
        }
        
        // Bottom Edge
        val bottomCount = 16
        for (i in 0..bottomCount) {
            val t = i.toFloat() / bottomCount
            val x = padding + w - safeRadius - t * (w - 2 * safeRadius)
            points.add(PointF(x, padding + h))
            normals.add(PointF(0f, 1f))
        }
        
        // Bottom-Left Corner Arc
        for (i in 0..arcCount) {
            val t = i.toFloat() / arcCount
            val angle = Math.PI / 2 + t * Math.PI / 2
            val x = padding + safeRadius + kotlin.math.cos(angle).toFloat() * safeRadius
            val y = padding + h - safeRadius + kotlin.math.sin(angle).toFloat() * safeRadius
            points.add(PointF(x, y))
            normals.add(PointF(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat()))
        }
        
        // Left Edge
        val leftCount = 16
        for (i in 0..leftCount) {
            val t = i.toFloat() / leftCount
            val y = padding + h - safeRadius - t * (h - 2 * safeRadius)
            points.add(PointF(padding, y))
            normals.add(PointF(-1f, 0f))
        }
        
        // Top-Left Corner Arc
        for (i in 0..arcCount) {
            val t = i.toFloat() / arcCount
            val angle = Math.PI + t * Math.PI / 2
            val x = padding + safeRadius + kotlin.math.cos(angle).toFloat() * safeRadius
            val y = padding + safeRadius + kotlin.math.sin(angle).toFloat() * safeRadius
            points.add(PointF(x, y))
            normals.add(PointF(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat()))
        }
        
        // Center winding angle calculations to close the wavy loop perfectly with zero discontinuity jaggies
        val cx = padding + w / 2f
        val cy = padding + h / 2f
        val waveCount = 5
        
        for (i in points.indices) {
            val pt = points[i]
            val normal = normals[i]
            
            // Angular position calculation matching the updated main app implementation perfectly
            val angleFromCenter = kotlin.math.atan2(pt.y - cy, pt.x - cx)
            val ambientWave = kotlin.math.sin(angleFromCenter * waveCount + phase) +
                    0.2f * kotlin.math.cos(angleFromCenter * waveCount * 2f - phase * 1.2f)
            
            val perturbedX = pt.x + normal.x * ambientWave * amplitude
            val perturbedY = pt.y + normal.y * ambientWave * amplitude
            
            if (i == 0) {
                path.moveTo(perturbedX, perturbedY)
            } else {
                path.lineTo(perturbedX, perturbedY)
            }
        }
        path.close()
        
        // Create background gradient matching the main app liquid pane background exactly
        val glassColors = intArrayOf(
            Color.parseColor("#50FFFFFF"), // Specular top sheen
            Color.parseColor("#17FFFFFF"), // Smooth face bleed
            Color.parseColor("#F504060A"), // Obsidian dark core reflection
            Color.parseColor("#C30E1118"), // Semi-translucent obsidian base
            Color.parseColor("#26FFFFFF")  // Bottom return glow
        )
        val glassPositions = floatArrayOf(0.0f, 0.15f, 0.45f, 0.82f, 1.0f)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                glassColors,
                glassPositions,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(path, bgPaint)
        
        // Render glowing abstract inner glass blobs/orbs for translucent refractions
        val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        
        fun drawVisualBlob(cxB: Float, cyB: Float, radB: Float, startCol: Int) {
            val radialGrad = RadialGradient(
                cxB, cyB, radB,
                startCol, Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            blobPaint.shader = radialGrad
            canvas.drawCircle(cxB, cyB, radB, blobPaint)
        }
        
        drawVisualBlob(cx - w * 0.15f, cy - h * 0.2f, w * 0.35f, Color.parseColor("#26FFFFFF"))
        drawVisualBlob(cx + w * 0.25f, cy + h * 0.15f, w * 0.3f, Color.parseColor("#1480DEEA"))
        drawVisualBlob(cx + w * 0.1f, cy - h * 0.3f, w * 0.25f, Color.parseColor("#194FC3F7"))
        
        // Wavy glass outer stroke border
        val strokeColors = intArrayOf(
            Color.parseColor("#E6FFFFFF"), // High-density top specular glow
            Color.parseColor("#1AFFFFFF"), // Light edge fade
            Color.parseColor("#73FFFFFF")  // Bottom-up environment bounce reflection
        )
        val strokePos = floatArrayOf(0.0f, 0.55f, 1.0f)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = borderStrokeWidth
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                strokeColors,
                strokePos,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(path, strokePaint)
        
        return bitmap
    }

    companion object {
        const val ACTION_TOGGLE_TASK = "com.example.action.TOGGLE_TASK"
        const val EXTRA_TASK_ID = "com.example.extra.TASK_ID"

        // Global helper to trigger update from the Main App (whenever tasks are added, modified or deleted)
        fun triggerUpdate(context: Context) {
            val intent = Intent(context, TodoWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val widgetManager = AppWidgetManager.getInstance(context)
            val ids = widgetManager.getAppWidgetIds(ComponentName(context, TodoWidgetProvider::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
