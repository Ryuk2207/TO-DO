package com.example.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.R
import com.example.data.AppDatabase
import com.example.data.DateHelper
import com.example.data.Task
import kotlinx.coroutines.runBlocking

class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TodoViewsFactory(applicationContext, intent)
    }
}

class TodoViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var tasksList: List<Task> = emptyList()

    override fun onCreate() {
        // Initial setup
    }

    override fun onDataSetChanged() {
        // This is executed on a background binder thread. We can fetch data synchronously.
        val todayStr = DateHelper.getTodayString()
        runBlocking {
            try {
                val db = AppDatabase.getDatabase(context)
                tasksList = db.taskDao().getTasksForDateList(todayStr)
            } catch (e: Exception) {
                tasksList = emptyList()
            }
        }
    }

    override fun onDestroy() {
        tasksList = emptyList()
    }

    override fun getCount(): Int {
        return tasksList.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= tasksList.size) {
            return RemoteViews(context.packageName, R.layout.todo_widget_item)
        }

        val task = tasksList[position]
        val views = RemoteViews(context.packageName, R.layout.todo_widget_item)

        // Set Title text and checkmark style representation
        views.setTextViewText(R.id.widget_item_title, task.title)
        
        // Show status (checked or unchecked)
        if (task.isCompleted) {
            views.setImageViewResource(R.id.widget_item_status_icon, R.drawable.widget_circle_checked)
        } else {
            views.setImageViewResource(R.id.widget_item_status_icon, R.drawable.widget_circle_unchecked)
        }

        // Subtitle time range combined with slot info
        val timeLabel = String.format("%02d:%02d", task.hour, task.minute) + " -- " + task.timeRange
        views.setTextViewText(R.id.widget_item_time, timeLabel)

        // Set fill-in Intent to allow clicking the row to toggle task completion status
        val fillInIntent = Intent().apply {
            putExtra(TodoWidgetProvider.EXTRA_TASK_ID, task.id)
        }
        // Let clicking on anywhere within the item row toggle its status
        views.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)
        views.setOnClickFillInIntent(R.id.widget_item_status_icon, fillInIntent)
        views.setOnClickFillInIntent(R.id.widget_item_title, fillInIntent)
        views.setOnClickFillInIntent(R.id.widget_item_time, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        if (position >= 0 && position < tasksList.size) {
            return tasksList[position].id.toLong()
        }
        return position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return false
    }
}
