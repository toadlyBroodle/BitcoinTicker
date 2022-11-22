package org.bitanon.bitcointicker

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import org.bitanon.bitcointicker.databinding.AppWidgetConfigureBinding

const val PREF_PREFIX = "org_bitanon_bitcointicker_"
const val PREF_CURRENCY = "pref_currency"
const val PREF_PRICE = "pref_price"
const val PREF_UPDATE_FREQ = "pref_update_freq"
const val WORK_MANAGER_NAME = "work_manager_name"

lateinit var apiClient: APIClient

class AppWidgetConfigureActivity : Activity() {
    private var context = this@AppWidgetConfigureActivity
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var binding: AppWidgetConfigureBinding

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        apiClient = APIClient().init(null, this)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = AppWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.addWidgetButton.setOnClickListener(onClickListener)

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
    }

    private var onClickListener = View.OnClickListener {

        // save config prefs before updating widget
        saveWidgetConfigPrefs(context, appWidgetId, binding)
        callForUpdate()

        // load prefCurrency and send query for price
        val prefs = loadWidgetPrefs(context, appWidgetId)
        val prefCurr = prefs.getString(PREF_CURRENCY, context.getString(R.string.usd))
        if (prefCurr != null) {
            apiClient.pingCoinGeckoCom(prefCurr)
        }
    }

    fun callForUpdate() {
        // It is the responsibility of the configuration activity to update the app widget
        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateAppWidget(context, appWidgetManager, appWidgetId)

        // Make sure we pass back the original appWidgetId
        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()

    }

    fun savePrefPriceUpdate(price: String) {
        val prefsKey = getPrefsName(appWidgetId)
        val prefs = context.getSharedPreferences(prefsKey, 0)
        val prefsEditor = prefs.edit()
        prefsEditor.putString(PREF_PRICE, price)
        prefsEditor.commit()
        println("saved $prefsKey :${prefs.all}")
        callForUpdate()
    }
}

//Write the prefixed SharedPreferences object for this widget
internal fun saveWidgetConfigPrefs(context: Context, appWidgetId: Int, binding: AppWidgetConfigureBinding) {
    val prefsKey = getPrefsName(appWidgetId)
    val prefs = context.getSharedPreferences(prefsKey, 0)
    val prefsEditor = prefs.edit()
    prefsEditor.putString(PREF_CURRENCY, binding.widgetCurrenciesList.selectedItem.toString())
    prefsEditor.putString(PREF_UPDATE_FREQ, binding.widgetUpdateFrequencyList.selectedItem.toString())
    prefsEditor.commit()
    println("saved $prefsKey :${prefs.all}")
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
