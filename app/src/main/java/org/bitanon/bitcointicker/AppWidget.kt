package org.bitanon.bitcointicker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.widget.RemoteViews
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.WorkManager

const val PREF_PREFIX = "org.bitanon.bitcointicker."
const val PREF_CURRENCY = "PREF_CURRENCY"
const val PREF_PRICE = "PREF_PRICE"
const val PREF_DAY_CHANGE = "PREF_DAY_CHANGE"
const val PREF_UPDATE_FREQ = "PREF_UPDATE_FREQUENCY"

fun getPrefsName(id: Int): String { return PREF_PREFIX + id }

var widgetIds: IntArray? = null

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
            // and remove workmanager updater
            WorkManager.getInstance(context).cancelUniqueWork(getWorkerName(appWidgetId))
        }
    }
    override fun onEnabled(context: Context) {
        // register price update receiver
        val filterPrice = IntentFilter(BROADCAST_PRICE_UPDATED)
        LocalBroadcastManager.getInstance(context.applicationContext).registerReceiver(br, filterPrice)

    }

    override fun onDisabled(context: Context) {
        // unregister price update receiver
        LocalBroadcastManager.getInstance(context.applicationContext).unregisterReceiver(br)
    }

    private val br = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // ignore widget price updates
            val widgetId = intent?.getIntExtra("widget_id", 0)
            if ( widgetId == -1) return

            when (intent?.action) {
                BROADCAST_PRICE_UPDATED -> {
                    val price = intent.getStringExtra("price")
                    val dayChange = intent.getStringExtra("day_change")

                    // save widget prefs price
                    val prefsKey = widgetId?.let { getPrefsName(it) }
                    val prefs = context?.getSharedPreferences(prefsKey, 0)
                    val prefsEditor = prefs?.edit()
                    if (prefsEditor != null) {
                        prefsEditor.putString(PREF_PRICE, price)
                        prefsEditor.putString(PREF_DAY_CHANGE, dayChange)
                        prefsEditor.commit()
                    }
                    println("saved $prefsKey :${prefs?.all}")

                    // and update widgets
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    if (context != null && widgetId != null) {
                        updateAppWidget(context, appWidgetManager, widgetId)
                    }
                }
            }
        }
    }
}

internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {

    // Construct the RemoteViews object
    val views = RemoteViews(context.packageName, R.layout.app_widget)

    // Create an Intent to launch MainActivity when widget touched
    val pendingIntent: PendingIntent = PendingIntent.getActivity(
        /* context = */ context,
        /* requestCode = */  0,
        /* intent = */ Intent(context, MainActivity::class.java),
        /* flags = */ PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val prefs = loadWidgetPrefs(context, appWidgetId)
    val prefCurr = prefs.getString(PREF_CURRENCY, context.getString(R.string.usd))
    val prefPrice = prefs.getString(PREF_PRICE, context.getString(R.string.loading))
    val prefDayChange = prefs.getString(PREF_DAY_CHANGE, null)?.toFloat()
    // Attach an on-click listener to the widget and set currency units from prefs
    views.apply {
        setOnClickPendingIntent(R.id.widget_linear_layout, pendingIntent)
        setTextViewText(R.id.widget_textview_btcprice_units, "$prefCurr/BTC")
        if (prefCurr != null)
            setTextViewText(R.id.widget_textview_btcprice, numberToCurrency(prefPrice, prefCurr))
        if (prefDayChange != null)
            setTextViewText(R.id.widget_textview_day_change_value, "%.2f".format(prefDayChange) + "%")
        // change color of price based on 24h change
        if (prefDayChange != null) {
            val deltaColor: Int
            if (prefDayChange > 0)
                deltaColor = context.getColor(R.color.green)
            else
                deltaColor = context.getColor(R.color.red)
            setTextColor(R.id.widget_textview_btcprice, deltaColor)
            setTextColor(R.id.widget_textview_day_change_value, deltaColor)
        }
    }

    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

// Read the prefixed SharedPreferences object for this widget
internal fun loadWidgetPrefs(context: Context, appWidgetId: Int): SharedPreferences {
    val prefsKey = getPrefsName(appWidgetId)
    val prefs = context.getSharedPreferences(prefsKey, 0)
    println("loaded $prefsKey :${prefs.all}")
    return prefs
}

internal fun deleteWidgetPrefs(context: Context, appWidgetId: Int) {
    val prefsKey = getPrefsName(appWidgetId)
    val prefsEditor = context.getSharedPreferences(prefsKey, 0).edit()
    prefsEditor.remove(prefsKey)
    prefsEditor.apply()
    println("deleted $prefsKey")
}