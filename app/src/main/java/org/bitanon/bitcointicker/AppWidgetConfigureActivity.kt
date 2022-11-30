package org.bitanon.bitcointicker

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.bitanon.bitcointicker.databinding.AppWidgetConfigureBinding
import java.util.concurrent.TimeUnit

fun getWorkerName(id: Int): String { return "worker$id"}

class AppWidgetConfigureActivity : Activity() {
    private var context = this@AppWidgetConfigureActivity
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var binding: AppWidgetConfigureBinding
    //private val receiver: br = br()

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = AppWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

/*        // on radio button checked, print it's resID and name
        lateinit var checkedRadioID: String
        val rg = findViewById<RadioGroup>(R.id.radio_group)
        rg.setOnCheckedChangeListener { group, checkedId ->
            println("radioGroupChecked->${group.checkedRadioButtonId}")
            checkedRadioID = resources.getResourceEntryName(checkedId)
            println("checkedRadioID=$checkedRadioID")
        }*/

        binding.addWidgetButton.setOnClickListener(addWidgetOnClickListener)

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
    }

    private var addWidgetOnClickListener = View.OnClickListener {

        // save config prefs before updating widget
        saveWidgetConfigPrefs(context, appWidgetId, binding)

        // It is the responsibility of the widget manager to update the app widget
        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateAppWidget(context, appWidgetManager, appWidgetId)

        // Make sure we pass back the original appWidgetId
        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()

        // load prefCurrency
        val prefs = loadWidgetPrefs(context, appWidgetId)
        val prefCurr = prefs?.getString(WIDGET_PREF_CURRENCY, context.getString(R.string.usd))
        val prefUpdFreq = prefs?.getInt(WIDGET_PREF_UPDATE_FREQ, 1800000)

        // construct recurring price query
        val queryPriceWork = prefUpdFreq?.let { it1 ->
            PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                it1.toLong(), TimeUnit.MILLISECONDS // minimum time is 15m
            )
        }
        //Add parameter in Data class. just like bundle. You can also add Boolean and Number in parameter.
        val data = Data.Builder()
        data.putString("pref_curr", prefCurr)
        data.putInt("widget_id", appWidgetId)
        queryPriceWork?.setInputData(data.build())

        // add recurring price query worker
        if (queryPriceWork != null) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                getWorkerName(appWidgetId),
                ExistingPeriodicWorkPolicy.REPLACE,
                queryPriceWork.build()
            )
        }
    }
}

//Write the prefixed SharedPreferences object for this widget
internal fun saveWidgetConfigPrefs(context: Context, appWidgetId: Int, binding: AppWidgetConfigureBinding) {
    val freqSelected = binding.widgetUpdateFrequencyList.selectedItem.toString()
    val index = context.resources.getStringArray(R.array.update_freq).indexOf(freqSelected)
    val freqValue = context.resources.getStringArray(R.array.update_freq_values)[index].toString()

    val prefsKey = getWidgetPackageName(appWidgetId)
    val prefs = context.getSharedPreferences(prefsKey, 0)
    val prefsEditor = prefs.edit()
    prefsEditor.putString(WIDGET_PREF_CURRENCY, binding.widgetCurrenciesList.selectedItem.toString())
    prefsEditor.putInt(WIDGET_PREF_UPDATE_FREQ, stringToInt(freqValue))
    prefsEditor.putFloat(WIDGET_PREF_BG_TRANSPARENCY, binding.transparencySlider.value)
    prefsEditor.putString(WIDGET_PREF_BG_COLOR_CHECKED_RADIO_ID,
        context.resources.getResourceEntryName(binding.radioGroup.checkedRadioButtonId))
    prefsEditor.commit()
    println("saved $prefsKey :${prefs.all}")
}
