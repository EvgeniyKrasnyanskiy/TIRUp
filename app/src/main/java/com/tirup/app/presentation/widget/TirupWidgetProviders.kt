package com.tirup.app.presentation.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 4x1 ↔ 5x1 Glycemic Strip Widget Provider
 */
class TirupStripWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        CoroutineScope(Dispatchers.IO).launch {
            TirupWidgetUpdater.updateAllWidgets(context)
        }
    }
}

/**
 * 4x2 ↔ 5x2 Bento Dashboard Widget Provider with Sparkline Chart
 */
class TirupDashboardWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        CoroutineScope(Dispatchers.IO).launch {
            TirupWidgetUpdater.updateAllWidgets(context)
        }
    }
}

/**
 * 2x2 Compact Focus Widget Provider
 */
class TirupCompactWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        CoroutineScope(Dispatchers.IO).launch {
            TirupWidgetUpdater.updateAllWidgets(context)
        }
    }
}
