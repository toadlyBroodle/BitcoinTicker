package org.bitanon.bitcointicker

import android.content.*
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.preference.PreferenceManager
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.bitanon.bitcointicker.databinding.ActivityMainBinding
import java.util.concurrent.TimeUnit

const val PREF_LIST_CURRENCY = "pref_list_currency"
const val PREF_LAST_REAL_BTC_PRICE = "pref_last_real_btc_price"
const val BROADCAST_SHOW_TOAST = "org.bitanon.bitcointicker.BROADCAST_SHOW_TOAST"
const val BROADCAST_PRICE_UPDATED = "org.bitanon.bitcointicker.BROADCAST_PRICE_UPDATED"

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    lateinit var sharedPrefs: SharedPreferences
    private lateinit var prefCurrency: String
    lateinit var lastRealBtcPrice: String
    private var lastReqTime: Long = 0

    private lateinit var btcPriceUnitsTextView: TextView
    private lateinit var btcPriceTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // register broadcast reciever
        val filterToast = IntentFilter(BROADCAST_SHOW_TOAST)
        LocalBroadcastManager.getInstance(this).registerReceiver(br, filterToast)
        val filterPrice = IntentFilter(BROADCAST_PRICE_UPDATED)
        LocalBroadcastManager.getInstance(this).registerReceiver(br, filterPrice)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        prefCurrency = sharedPrefs.getString(PREF_LIST_CURRENCY, "USD").toString()
        lastRealBtcPrice = sharedPrefs.getInt(PREF_LAST_REAL_BTC_PRICE, -1).toString()
        println("loaded sharedPrefs: ${sharedPrefs.all}")

        btcPriceUnitsTextView = findViewById(R.id.textview_btcprice_units)
        btcPriceUnitsTextView.text = "$prefCurrency/BTC"
        btcPriceTextView = findViewById(R.id.textview_btcprice)
        btcPriceTextView.text = numberToCurrency(lastRealBtcPrice, prefCurrency)

        // construct recurring price query
        val queryPriceWork = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            1, TimeUnit.MINUTES
        )
        val data = Data.Builder()
        data.putString("pref_curr", prefCurrency)
        data.putInt("widget_id", -1)
        queryPriceWork.setInputData(data.build())

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            getWorkerName(-1),
            ExistingPeriodicWorkPolicy.REPLACE,
            queryPriceWork.build()
        )

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

    // update price when screen touched
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            //if last update less than 1m ago, update last real price with random price jitter
            if (System.currentTimeMillis() - lastReqTime <= 60000) {
                val rand = (-9..9).random()
                val jitteredPrice = lastRealBtcPrice.toInt().plus(rand).toString()
                println("jittered price: $jitteredPrice")

                updateUI(numberToCurrency(jitteredPrice, prefCurrency))
            }
        }

        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        // unregister broadcast receiver
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(br)
    }

    fun updateUI(price: String?) {
        //update price
        runOnUiThread {
            btcPriceTextView.text = price
        }
    }

    fun showToast(message: String) =
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

    private val br = object : BroadcastReceiver() {
        override fun onReceive(contxt: Context?, intent: Intent?) {
            // ignore widget price updates
            val widgetId = intent?.getIntExtra("widget_id", 0)
            if ( widgetId != -1) return

            val message = intent.getStringExtra("message")
            val price = intent.getStringExtra("price")
            when (intent.action) {
                BROADCAST_SHOW_TOAST -> message?.let { showToast(it) }
                BROADCAST_PRICE_UPDATED -> {
                    updateUI(price)
                    //save last real btc price to preferences to avoid null pointer exception
                    //if server cannot be reached on next server request
                    val priceInt = stringToInt(price)
                    sharedPrefs.edit()?.putInt(PREF_LAST_REAL_BTC_PRICE, priceInt)?.apply()
                    lastRealBtcPrice = priceInt.toString()
                    println("saved last real price pref:$priceInt")
                    lastReqTime = System.currentTimeMillis()
                }
            }
        }
    }

}

