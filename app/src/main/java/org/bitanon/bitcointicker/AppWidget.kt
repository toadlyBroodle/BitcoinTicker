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

const val WIDGET_PREF_PREFIX = "org.bitanon.bitcointicker.widget"
const val BROADCAST_WIDGET_UPDATE_BUTTON_CLICK = "org.bitanon.bitcointicker.BROADCAST_WIDGET_UPDATE_BUTTON_CLICK"
const val WIDGET_PREF_BG_TRANSPARENCY = "WIDGET_PREF_BG_TRANSPARENCY"
const val WIDGET_PREF_UPDATE_FREQ = "WIDGET_PREF_UPDATE_FREQ"

fun getWidgetPackageName(id: Int): String { return WIDGET_PREF_PREFIX + id }

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
        // TODO integrate BROADCAST_MARKET_CHARTS_UPDATED
        LocalBroadcastManager.getInstance(context.applicationContext).registerReceiver(br, filters)
    }

    override fun onDisabled(context: Context) {
        // unregister price and widget button update receivers
        LocalBroadcastManager.getInstance(context.applicationContext).unregisterReceiver(br)
    }

    private val br = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // ignore widget price updates
            val widgetId = intent?.getIntExtra(WIDGIT_ID, 0)
            if ( widgetId == -1) return

            val prefs = loadWidgetPrefs(context, widgetId)

            when (intent?.action) {
                BROADCAST_PRICE_UPDATED -> {
                    val price = intent.getStringExtra(CURR_PRICE)
                    val dayVolume = intent.getStringExtra(CURR_DAY_VOLUME).toString()
                    val marketCap = intent.getStringExtra(CURR_MARKET_CAP).toString()
                    val lastUpdate = intent.getStringExtra(CURR_LAST_UPDATE).toString()

                    // save widget prefs price
                    val prefsEditor = prefs?.edit()
                    if (prefsEditor != null) {
                        prefsEditor.putString(CURR_PRICE, price)
                        prefsEditor.putString(CURR_DAY_VOLUME, dayVolume)
                        prefsEditor.putString(CURR_MARKET_CAP, marketCap)
                        prefsEditor.putString(CURR_LAST_UPDATE, lastUpdate)
                        prefsEditor.commit()
                    }
                    println("saved widget$widgetId prefs:${prefs?.all}")

                    // and update widgets
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    if (context != null && widgetId != null) {
                        updateAppWidget(context, appWidgetManager, widgetId)
                    }
                }
                BROADCAST_WIDGET_UPDATE_BUTTON_CLICK -> {
                    // construct onetime price query
                    val priceReq = OneTimeWorkRequestBuilder<RequestUpdateWorker>()
                    val data = Data.Builder()
                    if (prefs != null) {
                        data.putString(CURRENCY, prefs.getString(CURRENCY, "USD"))
                    }
                    if (widgetId != null) {
                        data.putInt(WIDGIT_ID, widgetId)
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

    val updateWidgetIntent = Intent(BROADCAST_WIDGET_UPDATE_BUTTON_CLICK).setClass(
        context.applicationContext, AppWidget::class.java)
    // create intent to update widget when button clicked TODO this not working
    val piWidgetUpdateButtonClicked =
        PendingIntent.getBroadcast(context.applicationContext, appWidgetId,
            updateWidgetIntent, PendingIntent.FLAG_UPDATE_CURRENT
                    or PendingIntent.FLAG_IMMUTABLE
        )
    views.setOnClickPendingIntent(R.id.widget_metric_column, piWidgetUpdateButtonClicked)

    // get prefs
    val prefs = loadWidgetPrefs(context, appWidgetId)
    val prefCurr = prefs?.getString(CURRENCY, context.getString(R.string.usd))
    val prefPrice = prefs?.getString(CURR_PRICE, context.getString(R.string.loading))
    val prefDayVolume = prefs?.getString(CURR_DAY_VOLUME, context.getString(R.string.loading))
    val prefMarketCap = prefs?.getString(CURR_MARKET_CAP, context.getString(R.string.loading))
    val prefLastUpdate = prefs?.getString(CURR_LAST_UPDATE, context.getString(R.string.loading))?.let { getDateTime(it) }

    // get bg color selected
    val prefBgTransp = prefs?.getFloat(WIDGET_PREF_BG_TRANSPARENCY, 0.5f)
    // convert bg color float -> int -> hex
    var hexBgTransp = prefBgTransp?.times(255)?.let { Integer.toHexString(it.toInt()) }
    // pad 0 value with extra 0 for correct color hex formatting
    if (stringToInt(hexBgTransp) == 0)
        hexBgTransp = "00"

    //update widget views
    views.apply {
        // add transparency to black background
        setInt(R.id.widget_background_layout, "setBackgroundColor",
            Color.parseColor("#${hexBgTransp}000000"))
        setTextViewText(R.id.widget_textview_btcprice_units, "$prefCurr/BTC")
        if (prefCurr != null)
            setTextViewText(R.id.widget_textview_btcprice, numberToCurrency(prefPrice, prefCurr))
        if (prefDayVolume != null)
            setTextViewText(R.id.widget_textview_day_volume_value, prefDayVolume)
        if (prefMarketCap != null)
            setTextViewText(R.id.widget_textview_market_cap_value, prefMarketCap)
        if (prefLastUpdate != null)
            setTextViewText(R.id.widget_value_column, prefLastUpdate)
        // change metric colors based on 24h change
        /*if (prefDayChange != null) {
            val deltaColor: Int = if (prefDayChange.toFloat() > 0)
                context.getColor(R.color.green)
            else
                context.getColor(R.color.red)
            setTextColor(R.id.widget_textview_btcprice, deltaColor)
            setTextColor(R.id.widget_textview_day_change_value, deltaColor)
            setTextColor(R.id.widget_textview_market_cap_value, deltaColor)
        }*/
    }

    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

// Read the prefixed SharedPreferences object for this widget
internal fun loadWidgetPrefs(context: Context?, appWidgetId: Int?): SharedPreferences? {
    val prefsKey = appWidgetId?.let { getWidgetPackageName(it) }
    val prefs = context?.getSharedPreferences(prefsKey, 0)
    if (prefs != null) {
        println("loaded $prefsKey :${prefs.all}")
    }
    return prefs
}

internal fun deleteWidgetPrefs(context: Context, appWidgetId: Int) {
    val prefsKey = getWidgetPackageName(appWidgetId)
    val prefsEditor = context.getSharedPreferences(prefsKey, 0).edit()
    prefsEditor.remove(prefsKey)
    prefsEditor.apply()
    println("deleted $prefsKey")
}