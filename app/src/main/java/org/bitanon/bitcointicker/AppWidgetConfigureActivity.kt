package org.bitanon.bitcointicker

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import org.bitanon.bitcointicker.databinding.AppWidgetConfigureBinding

//private var pref_currency: String? = null
//private var pref_update_freq: Int? = null

class AppWidgetConfigureActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var binding: AppWidgetConfigureBinding

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

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

        // load preferences from shared preferences
        loadWidgetConfigPrefs(this, appWidgetId)
    }

    private var onClickListener = View.OnClickListener {
        val context = this@AppWidgetConfigureActivity

        // save config prefs before updating widget
        saveWidgetConfigPrefs(this, appWidgetId, binding)

        // It is the responsibility of the configuration activity to update the app widget
        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateAppWidget(context, appWidgetManager, appWidgetId)

        // Make sure we pass back the original appWidgetId
        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()

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
internal fun loadWidgetConfigPrefs(context: Context, appWidgetId: Int): SharedPreferences? {
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
