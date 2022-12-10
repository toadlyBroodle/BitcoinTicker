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

            updateAllWidgets(context, appWidgetManager, appWidgetId)

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
        filters.addAction(BROADCAST_MARKET_CHARTS_UPDATED)
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
            println("broadcast received by widget -> action:${intent?.action}")
            // ignore widget price updates
            val widgetId = intent?.getIntExtra(WIDGIT_ID, 0)
            if ( widgetId == -1) return

            val prefs = loadWidgetPrefs(context, widgetId)

            when (intent?.action) {
                BROADCAST_WIDGET_UPDATE_BUTTON_CLICK -> { //TODO not receiving
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
                BROADCAST_PRICE_UPDATED -> {
                    val price = intent.getStringExtra(CURR_PRICE)
                    val dayVolume = intent.getStringExtra(CURR_DAY_VOLUME).toString()
                    val marketCap = intent.getStringExtra(CURR_MARKET_CAP).toString()
                    val lastUpdate = intent.getStringExtra(CURR_LAST_UPDATE).toString()

                    // save widget prefs price
                    prefs?.edit()?.apply {
                        putString(CURR_PRICE, price)
                        putString(CURR_DAY_VOLUME, dayVolume)
                        putString(CURR_MARKET_CAP, marketCap)
                        putString(CURR_LAST_UPDATE, lastUpdate)
                        commit()
                    }
                    println("saved widget$widgetId prefs:${prefs?.all}")
                    updateWidget(context, widgetId)
                }
                BROADCAST_MARKET_CHARTS_UPDATED -> {
                    val priceDeltaDay = intent.getFloatExtra(PRICE_DELTA_DAY, 0f)
                    val priceDeltaWeek = intent.getFloatExtra(PRICE_DELTA_WEEK, 0f)
                    val priceDeltaMonth = intent.getFloatExtra(PRICE_DELTA_MONTH, 0f)
                    val volumeDeltaDay = intent.getFloatExtra(VOLUME_DELTA_DAY, 0f)
                    val volumeDeltaWeek = intent.getFloatExtra(VOLUME_DELTA_WEEK, 0f)
                    val volumeDeltaMonth = intent.getFloatExtra(VOLUME_DELTA_MONTH, 0f)
                    val marketCapDeltaDay = intent.getFloatExtra(MARKET_CAP_DELTA_DAY, 0f)
                    val marketCapDeltaWeek = intent.getFloatExtra(MARKET_CAP_DELTA_WEEK, 0f)
                    val marketCapDeltaMonth = intent.getFloatExtra(MARKET_CAP_DELTA_MONTH, 0f)

                    prefs?.edit()?.apply {
                        putFloat(PRICE_DELTA_DAY, priceDeltaDay)
                        putFloat(PRICE_DELTA_WEEK, priceDeltaWeek)
                        putFloat(PRICE_DELTA_MONTH, priceDeltaMonth)
                        putFloat(VOLUME_DELTA_DAY, volumeDeltaDay)
                        putFloat(VOLUME_DELTA_WEEK, volumeDeltaWeek)
                        putFloat(VOLUME_DELTA_MONTH, volumeDeltaMonth)
                        putFloat(MARKET_CAP_DELTA_DAY, marketCapDeltaDay)
                        putFloat(MARKET_CAP_DELTA_WEEK, marketCapDeltaWeek)
                        putFloat(MARKET_CAP_DELTA_MONTH, marketCapDeltaMonth)
                        commit()
                    }
                    println("saved widget$widgetId prefs:${prefs?.all}")
                    updateWidget(context, widgetId)
                }
            }
        }
    }
}

internal fun updateWidget(ctx: Context?, id: Int?) {
    // and update widgets
    val appWidgetManager = AppWidgetManager.getInstance(ctx)
    if (ctx != null && id != null) {
        updateAllWidgets(ctx, appWidgetManager, id)
    }
}

internal fun updateAllWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {

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
    views.setOnClickPendingIntent(R.id.widget_metric_update, piWidgetUpdateButtonClicked)

    // get prefs
    val prefs = loadWidgetPrefs(context, appWidgetId)
    val prefCurr = prefs?.getString(CURRENCY, context.getString(R.string.usd))
    val prefPrice = prefs?.getString(CURR_PRICE, context.getString(R.string.loading))
    val prefDayVolume = prefs?.getString(CURR_DAY_VOLUME, context.getString(R.string.loading))
    val prefMarketCap = prefs?.getString(CURR_MARKET_CAP, context.getString(R.string.loading))
    val prefLastUpdate = prefs?.getString(CURR_LAST_UPDATE, context.getString(R.string.loading))
    val prefPriceDeltaDay = prefs?.getFloat(PRICE_DELTA_DAY, 0f)
    val prefPriceDeltaWeek = prefs?.getFloat(PRICE_DELTA_WEEK, 0f)
    val prefPriceDeltaMonth = prefs?.getFloat(PRICE_DELTA_MONTH , 0f)
    val prefVolumeDeltaDay = prefs?.getFloat(VOLUME_DELTA_DAY, 0f)
    val prefVolumeDeltaWeek = prefs?.getFloat(VOLUME_DELTA_WEEK, 0f)
    val prefVolumeDeltaMonth = prefs?.getFloat(VOLUME_DELTA_MONTH, 0f)
    val prefMarketCapDeltaDay = prefs?.getFloat(MARKET_CAP_DELTA_DAY, 0f)
    val prefMarketCapDeltaWeek = prefs?.getFloat(MARKET_CAP_DELTA_WEEK , 0f)
    val prefMarketCapDeltaMonth = prefs?.getFloat(MARKET_CAP_DELTA_MONTH, 0f)

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
        setTextViewText(R.id.widget_textview_price, "$prefCurr/BTC")
        setTextViewText(R.id.widget_textview_price_value, numberToCurrency(prefPrice, prefCurr.toString()))
        setTextViewText(R.id.widget_textview_volume_value, prettyBigNumber(prefDayVolume.toString()))
        setTextViewText(R.id.widget_textview_market_cap_value, prettyBigNumber(prefMarketCap.toString()))
        setTextViewText(R.id.widget_last_update_time, getDateTime(prefLastUpdate.toString()))
        setTextViewText(R.id.widget_textview_price_delta_day_value, prefPriceDeltaDay?.let { formatChangePercent(it) })
        setTextViewText(R.id.widget_textview_price_delta_week_value, prefPriceDeltaWeek?.let { formatChangePercent(it) })
        setTextViewText(R.id.widget_textview_price_delta_month_value, prefPriceDeltaMonth?.let { formatChangePercent(it) })
        setTextViewText(R.id.widget_textview_market_cap_delta_day_value, prefMarketCapDeltaDay?.let { formatChangePercent(it) })
        setTextViewText(R.id.widget_textview_market_cap_delta_week_value, prefMarketCapDeltaWeek?.let { formatChangePercent(it) })
        setTextViewText(R.id.widget_textview_market_cap_delta_month_value, prefMarketCapDeltaMonth?.let { formatChangePercent(it) })
        setTextViewText(R.id.widget_textview_volume_delta_day_value, prefVolumeDeltaDay?.let { formatChangePercent(it) })
        setTextViewText(R.id.widget_textview_volume_delta_week_value, prefVolumeDeltaWeek?.let { formatChangePercent(it) })
        setTextViewText(R.id.widget_textview_volume_delta_month_value, prefVolumeDeltaMonth?.let { formatChangePercent(it) })
/*
    R.id.widget_textview_price_value
    R.id.widget_textview_volume_value
    R.id.widget_textview_market_cap_value
    R.id.widget_textview_price_delta_day_value
    R.id.widget_textview_price_delta_week_value
    R.id.widget_textview_price_delta_month_value
    R.id.widget_textview_market_cap_delta_day_value
    R.id.widget_textview_market_cap_delta_week_value
    R.id.widget_textview_market_cap_delta_month_value
    R.id.widget_textview_volume_delta_day_value
    R.id.widget_textview_volume_delta_week_value
    R.id.widget_textview_volume_delta_month_value
    */

        // change color of delta metrics
        if (prefPriceDeltaDay != null) {
            val deltaColor: Int = if (prefPriceDeltaDay > 0)
                context.getColor(R.color.green)
            else
                context.getColor(R.color.red)
            setTextColor(R.id.widget_textview_price_delta_day_value, deltaColor)
            setTextColor(R.id.widget_textview_price_value, deltaColor)
        }
        if (prefMarketCapDeltaDay != null) {
            val deltaColor: Int = if (prefMarketCapDeltaDay > 0)
                context.getColor(R.color.green)
            else
                context.getColor(R.color.red)
            setTextColor(R.id.widget_textview_market_cap_delta_day_value, deltaColor)
            setTextColor(R.id.widget_textview_market_cap_value, deltaColor)
        }
        if (prefVolumeDeltaDay != null) {
            val deltaColor: Int = if (prefVolumeDeltaDay > 0)
                context.getColor(R.color.green)
            else
                context.getColor(R.color.red)
            setTextColor(R.id.widget_textview_volume_delta_day_value, deltaColor)
            setTextColor(R.id.widget_textview_volume_value, deltaColor)
        }
        if (prefPriceDeltaWeek != null) {
            val deltaColor: Int = if (prefPriceDeltaWeek > 0)
                context.getColor(R.color.green)
            else
                context.getColor(R.color.red)
            setTextColor(R.id.widget_textview_price_delta_week_value, deltaColor)
        }
        if (prefPriceDeltaMonth != null) {
            val deltaColor: Int = if (prefPriceDeltaMonth > 0)
                context.getColor(R.color.green)
            else
                context.getColor(R.color.red)
            setTextColor(R.id.widget_textview_price_delta_month_value, deltaColor)
        }
        if (prefMarketCapDeltaWeek != null) {
            val deltaColor: Int = if (prefMarketCapDeltaWeek > 0)
                context.getColor(R.color.green)
            else
                context.getColor(R.color.red)
            setTextColor(R.id.widget_textview_market_cap_delta_week_value, deltaColor)
        }
        if (prefMarketCapDeltaMonth != null) {
            val deltaColor: Int = if (prefMarketCapDeltaMonth > 0)
                context.getColor(R.color.green)
            else
                context.getColor(R.color.red)
            setTextColor(R.id.widget_textview_market_cap_delta_month_value, deltaColor)
        }
        if (prefVolumeDeltaWeek != null) {
            val deltaColor: Int = if (prefVolumeDeltaWeek > 0)
                context.getColor(R.color.green)
            else
                context.getColor(R.color.red)
            setTextColor(R.id.widget_textview_volume_delta_week_value, deltaColor)
        }
        if (prefVolumeDeltaMonth != null) {
            val deltaColor: Int = if (prefVolumeDeltaMonth > 0)
                context.getColor(R.color.green)
            else
                context.getColor(R.color.red)
            setTextColor(R.id.widget_textview_volume_delta_month_value, deltaColor)
        }

    }

    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

// Read the prefixed SharedPreferences object for this widget
internal fun loadWidgetPrefs(context: Context?, appWidgetId: Int?): SharedPreferences? {
    val prefsKey = appWidgetId?.let { getWidgetPackageName(it) }
    val prefs = context?.getSharedPreferences(prefsKey, 0)
/*    if (prefs != null) {
        println("loaded $prefsKey :${prefs.all}")
    }*/
    return prefs
}

internal fun deleteWidgetPrefs(context: Context, appWidgetId: Int) {
    val prefsKey = getWidgetPackageName(appWidgetId)
    val prefsEditor = context.getSharedPreferences(prefsKey, 0).edit()
    prefsEditor.remove(prefsKey)
    prefsEditor.apply()
    println("deleted $prefsKey")
}