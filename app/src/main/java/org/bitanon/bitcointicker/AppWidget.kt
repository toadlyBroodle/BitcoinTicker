package org.bitanon.bitcointicker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.graphics.Color
import android.widget.RemoteViews
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager


const val PREF_PREFIX = "org.bitanon.bitcointicker.widget"
const val PREF_CURRENCY = "PREF_CURRENCY"
const val PREF_PRICE = "PREF_PRICE"
const val PREF_DAY_CHANGE = "PREF_DAY_CHANGE"
const val PREF_UPDATE_FREQ = "PREF_UPDATE_FREQUENCY"
const val PREF_BG_TRANSPARENCY = "PREF_BG_TRANSPARENCY"
const val PREF_BG_CHECKED_COLOR_RADIO_ID = "PREF_BG_CHECKED_COLOR_RADIO_ID"
const val BROADCAST_WIDGET_UPDATE_BUTTON_CLICK = "org.bitanon.bitcointicker.BROADCAST_WIDGET_UPDATE_BUTTON_CLICK"

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
        // register price and widget button update receivers
        val filters = IntentFilter()
        filters.addAction(BROADCAST_PRICE_UPDATED)
        filters.addAction(BROADCAST_WIDGET_UPDATE_BUTTON_CLICK)
        LocalBroadcastManager.getInstance(context.applicationContext).registerReceiver(br, filters)
    }

    override fun onDisabled(context: Context) {
        // unregister price and widget button update receivers
        LocalBroadcastManager.getInstance(context.applicationContext).unregisterReceiver(br)
    }

    private val br = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            println("broadcast received")
            // ignore widget price updates
            val widgetId = intent?.getIntExtra("widget_id", 0)
            if ( widgetId == -1) return

            val prefs = loadWidgetPrefs(context, widgetId)

            when (intent?.action) {
                BROADCAST_PRICE_UPDATED -> {
                    val price = intent.getStringExtra("price")
                    val dayChange = intent.getStringExtra("day_change")

                    // save widget prefs price
                    val prefsEditor = prefs?.edit()
                    if (prefsEditor != null) {
                        prefsEditor.putString(PREF_PRICE, price)
                        prefsEditor.putString(PREF_DAY_CHANGE, dayChange)
                        prefsEditor.commit()
                    }
                    println("saved widget$widgetId prefs:${prefs?.all}")

                    // and update widgets
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    if (context != null && widgetId != null) {
                        updateAppWidget(context, appWidgetManager, widgetId)
                    }
                }
                // TODO not receiving this broadcast
                BROADCAST_WIDGET_UPDATE_BUTTON_CLICK -> {
                    // construct onetime price query
                    val priceReq = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                    val data = Data.Builder()
                    if (prefs != null) {
                        data.putString("pref_curr", prefs.getString(PREF_CURRENCY, "USD"))
                    }
                    if (widgetId != null) {
                        data.putInt("widget_id", widgetId)
                    }
                    priceReq.setInputData(data.build())
                    priceReq.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)

                    if (context != null) {
                        WorkManager.getInstance(context).enqueue(priceReq.build())
                    }
                }
            }
        }
    }
}

internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {

    // Construct the RemoteViews object
    val views = RemoteViews(context.packageName, R.layout.app_widget)

    // Create an Intent to launch MainActivity when widget background touched
    val piLaunchMainActiv: PendingIntent = PendingIntent.getActivity(context,0,
        Intent(context.applicationContext, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    views.setOnClickPendingIntent(R.id.widget_background_layout, piLaunchMainActiv)

    // create intent to update widget when button clicked TODO this not working
    val piWidgetUpdateButtonClicked =
        PendingIntent.getBroadcast(context, appWidgetId,
            Intent(BROADCAST_WIDGET_UPDATE_BUTTON_CLICK), PendingIntent.FLAG_UPDATE_CURRENT
                    or PendingIntent.FLAG_IMMUTABLE
        )
    views.setOnClickPendingIntent(R.id.widget_update_button, piWidgetUpdateButtonClicked)

    // get prefs
    val prefs = loadWidgetPrefs(context, appWidgetId)
    val prefCurr = prefs?.getString(PREF_CURRENCY, context.getString(R.string.usd))
    val prefPrice = prefs?.getString(PREF_PRICE, context.getString(R.string.loading))
    val prefDayChange = prefs?.getString(PREF_DAY_CHANGE, null)?.toFloat()
    val prefBgTransp = prefs?.getFloat(PREF_BG_TRANSPARENCY, 0.5f)

    // get bg color selected
    val bgColorRadioId = prefs?.getString(PREF_BG_CHECKED_COLOR_RADIO_ID, "radio_color_black")
    val bgColor = when (bgColorRadioId) {
        "radio_color_darkgrey" -> R.color.dark_grey
        "radio_color_lightgrey" -> R.color.light_grey
        "radio_color_white" -> R.color.white
        "radio_color_teal" -> R.color.teal_200
        "radio_color_lightblue" -> R.color.light_blue_600
        "radio_color_darkblue" -> R.color.light_blue_900
        "radio_color_purple" -> R.color.purple_700
        else -> R.color.black
    }
    // convert bg color float -> int -> hex
    var hexBgTransp = prefBgTransp?.times(255)?.let { Integer.toHexString(it.toInt()) }
    // pad 0 value with extra 0 for correct color hex formatting
    if (stringToInt(hexBgTransp) == 0)
        hexBgTransp = "00"
    // add transparency to hex color
    val bgTranspColorVal = context.getString(bgColor).replace("ff", "$hexBgTransp")

    //update widget views
    views.apply {
        try { // try parsing transparent color
            setInt(R.id.widget_background_layout, "setBackgroundColor",
                Color.parseColor(bgTranspColorVal))
        } catch (e: Exception) {
            println("bgColorRadioId=$bgColorRadioId")
            println("bgColor=${context.getString(bgColor)}")
            println("hexBgTransp=$hexBgTransp")
            println("bgTranspColorVal=$bgTranspColorVal")
            println(e)
            // if fails, just use original non-transparent color value
            setInt(R.id.widget_background_layout, "setBackgroundColor",
                Color.parseColor(context.getString(bgColor)))
        }
        setTextViewText(R.id.widget_textview_btcprice_units, "$prefCurr/BTC")
        if (prefCurr != null)
            setTextViewText(R.id.widget_textview_btcprice, numberToCurrency(prefPrice, prefCurr))
        if (prefDayChange != null)
            setTextViewText(R.id.widget_textview_day_change_value, "%.2f".format(prefDayChange) + "%")
        // change color of price based on 24h change
        if (prefDayChange != null) {
            val deltaColor: Int = if (prefDayChange > 0)
                context.getColor(R.color.green)
            else
                context.getColor(R.color.red)
            setTextColor(R.id.widget_textview_btcprice, deltaColor)
            setTextColor(R.id.widget_textview_day_change_value, deltaColor)
        }
    }

    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

// Read the prefixed SharedPreferences object for this widget
internal fun loadWidgetPrefs(context: Context?, appWidgetId: Int?): SharedPreferences? {
    val prefsKey = appWidgetId?.let { getPrefsName(it) }
    val prefs = context?.getSharedPreferences(prefsKey, 0)
    if (prefs != null) {
        println("loaded $prefsKey :${prefs.all}")
    }
    return prefs
}

internal fun deleteWidgetPrefs(context: Context, appWidgetId: Int) {
    val prefsKey = getPrefsName(appWidgetId)
    val prefsEditor = context.getSharedPreferences(prefsKey, 0).edit()
    prefsEditor.remove(prefsKey)
    prefsEditor.apply()
    println("deleted $prefsKey")
}