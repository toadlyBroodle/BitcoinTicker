package org.bitanon.bitcointicker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.*
import java.util.concurrent.TimeUnit


lateinit var widgetIds: IntArray

fun getPrefsName(id: Int): String {
    return PREF_PREFIX + id
}

class AppWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        widgetIds = appWidgetIds

        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {

            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // When the user deletes the widget, delete the preference associated with it.
        for (appWidgetId in appWidgetIds) {
            deleteWidgetPrefs(context, appWidgetId)
        }
        // and remove workmanager updater


    }
    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_MANAGER_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                30, TimeUnit.MINUTES
            ).build()
        )
        println("$WORK_MANAGER_NAME enabled")
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
        WorkManager.getInstance(context).cancelUniqueWork(WORK_MANAGER_NAME)
        println("$WORK_MANAGER_NAME canceled")
    }
}

internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {

    // Construct the RemoteViews object
    val views = RemoteViews(context.packageName, R.layout.app_widget)

    // Create an Intent to launch MainActivity.
    val pendingIntent: PendingIntent = PendingIntent.getActivity(
        /* context = */ context,
        /* requestCode = */  0,
        /* intent = */ Intent(context, MainActivity::class.java),
        /* flags = */ PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val widgetPrefs = loadWidgetConfigPrefs(context, appWidgetId)
    val prefCurr = widgetPrefs!!.getString(PREF_CURRENCY, "USD")

    // Attach an on-click listener to the widget and set currency units from prefs
    views.apply {
        setOnClickPendingIntent(R.id.widget_linear_layout, pendingIntent)
        setTextViewText(R.id.widget_textview_btcprice_units, "$prefCurr/BTC")
        //TODO setTextViewText(R.id.widget_textview_btcprice, "")
    }

    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}


class WidgetUpdateWorker(private val appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {

    override fun doWork(): Result {

        val intent = Intent(
            AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, appContext,
            AppWidget::class.java
        )
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
        appContext.sendBroadcast(intent)

        return Result.success()
    }
}