package org.bitanon.bitcointicker

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import org.bitanon.bitcointicker.databinding.SettingsActivityBinding


class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: SettingsActivityBinding

    private lateinit var prefCurrency: String
    private lateinit var prefBgColorRadioName: String

    private lateinit var arrayAdapter: ArrayAdapter<CharSequence>
    private lateinit var currencySpinner: Spinner
    private lateinit var colorRadioGroup: RadioGroup
    private lateinit var applyButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        currencySpinner = findViewById(R.id.main_settings_currencies_spinner)
        colorRadioGroup = findViewById(R.id.main_settings_radio_group)
        applyButton = findViewById(R.id.main_settings_apply_button)
        applyButton.setOnClickListener {
            NavUtils.navigateUpFromSameTask(this) } // call Toolbar Up Button
        //load main prefs
        val sharedPrefs = getSharedPreferences(MAIN_PREFS, 0)
        println("settingsActiv loaded prefs:${sharedPrefs.all}")
        prefCurrency = sharedPrefs.getString(MAIN_PREF_CURRENCY,
            getString(R.string.usd)).toString()
        prefBgColorRadioName = sharedPrefs.getString(
            MAIN_PREF_BG_COLOR_RADIO_ID, "").toString()

        //set views same as prefs
        arrayAdapter = ArrayAdapter.createFromResource(
            this, R.array.currency_codes, R.id.main_settings_currencies_spinner)
        currencySpinner.setSelection(arrayAdapter.getPosition(prefCurrency))
        val id = resources.getIdentifier(prefBgColorRadioName,
            "id", baseContext.packageName)
        binding.root.findViewById<RadioButton>(id).isChecked = true
    }

    override fun onPause() {
        super.onPause()

        // save settings to shared prefs
        val selCurr = currencySpinner.selectedItem.toString()
        val selPos = arrayAdapter.getPosition(selCurr)
        val curr = currencySpinner.getItemAtPosition(selPos).toString()
        val rbName = resources.getResourceEntryName(colorRadioGroup.checkedRadioButtonId)
        //val bgCol = baseContext.getString(getBgColor(rbID))
        savePrefs(baseContext, curr, null, null, rbName)
    }

}

fun getBgColor(rId: String?): Int {
    // strip id prefixes for main settings
    return when (rId?.replace("main_settings_", "")) {
        "radio_color_darkgrey" -> R.color.dark_grey
        "radio_color_lightgrey" -> R.color.light_grey
        "radio_color_white" -> R.color.white
        "radio_color_teal" -> R.color.teal_200
        "radio_color_lightblue" -> R.color.light_blue_600
        "radio_color_darkblue" -> R.color.light_blue_900
        "radio_color_purple" -> R.color.purple_700
        else -> R.color.black
    }
}