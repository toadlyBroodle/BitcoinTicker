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

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var apiClient: APIClient

    lateinit var sharedPrefs: SharedPreferences
    private lateinit var prefCurrency: String
    var lastRealBtcPrice: String? = null

    private lateinit var btcPriceUnitsTextView: TextView
    private lateinit var btcPriceTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        prefCurrency = sharedPrefs.getString(getString(R.string.pref_list_currency), "USD").toString()
        lastRealBtcPrice = sharedPrefs.getString(
            R.string.last_real_btc_price.toString(), null)
        println("loaded sharedPrefs: ${sharedPrefs.all}")

        apiClient = APIClient().init(this, null)

        btcPriceUnitsTextView = findViewById(R.id.textview_btcprice_units)
        btcPriceUnitsTextView.text = "$prefCurrency/BTC"
        btcPriceTextView = findViewById(R.id.textview_btcprice)
        btcPriceTextView.text = lastRealBtcPrice?.let { numberToCurrency(it.toInt(), prefCurrency) }

        apiClient.pingCoinGeckoCom(prefCurrency)
        //apiClient.getBitcoinPrice(prefCurrency!!)
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
            apiClient.getBitcoinPrice(prefCurrency)
        }

        return true
    }

    fun updateUI(price: String?) {
        runOnUiThread {
            btcPriceTextView.text = price
        }
    }

    fun showToast(message: Int) =
        runOnUiThread {
            Toast.makeText(this, getString(message), Toast.LENGTH_SHORT).show()
        }
}

