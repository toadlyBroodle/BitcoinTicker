package org.bitanon.bitcointicker

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.preference.PreferenceManager
import org.bitanon.bitcointicker.databinding.ActivityMainBinding
import org.w3c.dom.Text
import java.text.NumberFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var apiClient: APIClient

    var sharedPrefs: SharedPreferences? = null
    var prefCurrency: String? = null

    var btcPriceUnitsTextView: TextView? = null
    var btcPriceTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        prefCurrency = sharedPrefs!!.getString(getString(R.string.pref_list_currency), "USD")
        println("prefCurrency=$prefCurrency")

        apiClient = APIClient().init(this, sharedPrefs!!)

        btcPriceUnitsTextView = findViewById(R.id.textview_btcprice_units)
        btcPriceUnitsTextView?.text = "$prefCurrency/BTC"
        btcPriceTextView = findViewById(R.id.textview_btcprice)

        apiClient.pingCoinGeckoCom()
        apiClient.getBitcoinPrice(prefCurrency!!)

        //save last real btc price to preferences to avoid null pointer exception
        //if server cannot be reached on next server request
        sharedPrefs!!.edit().putString(getString(
            org.bitanon.bitcointicker.R.string.last_real_btc_price),
            apiClient.lastRealBtcPrice!!).apply()
     }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                val startActivity = Intent(this, SettingsActivity::class.java)
                startActivity(startActivity)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            apiClient.getBitcoinPrice(prefCurrency!!)
        }

        return true
    }

    fun updateUI(price: String?) {
        runOnUiThread {
            btcPriceTextView?.text = price
        }
    }

    fun showToast(message: Int) =
        runOnUiThread {
            Toast.makeText(this, getString(message), Toast.LENGTH_SHORT).show()
        }
}

