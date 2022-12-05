package org.bitanon.bitcointicker

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import org.bitanon.bitcointicker.databinding.SettingsActivityBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: SettingsActivityBinding

    private lateinit var prefCurrency: String

    private lateinit var arrayAdapter: ArrayAdapter<CharSequence>
    private lateinit var currencySpinner: Spinner
    private lateinit var applyButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        currencySpinner = findViewById(R.id.main_settings_currencies_spinner)
        applyButton = findViewById(R.id.main_settings_apply_button)
        applyButton.setOnClickListener {
            NavUtils.navigateUpFromSameTask(this) } // call Toolbar Up Button
        //load main prefs
        val sharedPrefs = getSharedPreferences(MAIN_PREFS, 0)
        println("settingsActiv loaded prefs:${sharedPrefs.all}")
        prefCurrency = sharedPrefs.getString(MAIN_PREF_CURRENCY, getString(R.string.usd)).toString()

        //set views same as prefs
        arrayAdapter = ArrayAdapter.createFromResource(
            this, R.array.currency_codes, R.id.main_settings_currencies_spinner)
        currencySpinner.setSelection(arrayAdapter.getPosition(prefCurrency))
    }

    override fun onPause() {
        super.onPause()

        // save settings to shared prefs
        val selCurr = currencySpinner.selectedItem.toString()
        val selPos = arrayAdapter.getPosition(selCurr)
        val curr = currencySpinner.getItemAtPosition(selPos).toString()
        val prefs = getSharedPreferences(MAIN_PREFS, 0)
        val prefsEditor = prefs.edit()
        prefsEditor.apply {
            putString(MAIN_PREF_CURRENCY, curr)
        }.commit()
    }
}