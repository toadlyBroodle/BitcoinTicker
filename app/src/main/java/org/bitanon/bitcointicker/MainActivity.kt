package org.bitanon.bitcointicker

import android.content.*
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.work.*
import org.bitanon.bitcointicker.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

const val MAIN_PREFS = "main_prefs"
const val MAIN_PREF_PRICE = "main_pref_price"
const val MAIN_PREF_MARKET_CAP = "main_pref_market_cap"
const val MAIN_PREF_DAY_VOLUME = "main_pref_day_volume"
const val MAIN_PREF_LAST_UPDATE = "main_pref_last_update"
const val MAIN_PREF_PRICE_DELTA_DAY = "MAIN_PREF_PRICE_DELTA_DAY"
const val MAIN_PREF_PRICE_DELTA_WEEK = "MAIN_PREF_PRICE_DELTA_WEEK"
const val MAIN_PREF_PRICE_DELTA_MONTH = "MAIN_PREF_PRICE_DELTA_MONTH"
const val MAIN_PREF_VOLUME_DELTA_DAY = "MAIN_PREF_VOLUME_DELTA_DAY"
const val MAIN_PREF_VOLUME_DELTA_WEEK = "MAIN_PREF_VOLUME_DELTA_WEEK"
const val MAIN_PREF_VOLUME_DELTA_MONTH = "MAIN_PREF_VOLUME_DELTA_MONTH"
const val MAIN_PREF_MARKET_CAP_DELTA_DAY = "MAIN_PREF_MARKET_CAP_DELTA_DAY"
const val MAIN_PREF_MARKET_CAP_DELTA_WEEK = "MAIN_PREF_MARKET_CAP_DELTA_WEEK"
const val MAIN_PREF_MARKET_CAP_DELTA_MONTH = "MAIN_PREF_MARKET_CAP_DELTA_MONTH"

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private lateinit var prefCurrency: String
    lateinit var prefPrice: String
    private lateinit var prefDayVolume: String
    private lateinit var prefMarketCap: String
    private lateinit var prefLastUpdate: String
    private var prefPriceDeltaDay: Float = 0f
    private var prefPriceDeltaWeek: Float = 0f
    private var prefPriceDeltaMonth: Float = 0f
    private var prefVolumeDeltaDay: Float = 0f
    private var prefVolumeDeltaWeek: Float = 0f
    private var prefVolumeDeltaMonth: Float = 0f
    private var prefMarketCapDeltaDay: Float = 0f
    private var prefMarketCapDeltaWeek: Float = 0f
    private var prefMarketCapDeltaMonth: Float = 0f
    private var lastReqTime: Long = 0

    private lateinit var tlMain: TableLayout
    private lateinit var tvLastPriceUpdate: TextView
    private lateinit var tvPriceUnits: TextView
    private lateinit var tvPrice: TextView
    private lateinit var tvMarketCap: TextView
    private lateinit var tvVolume: TextView
    private lateinit var tvLastUpdate: TextView
    private lateinit var tvUpdateIcon: TextView
    private lateinit var tvPriceDeltaDay: TextView
    private lateinit var tvPriceDeltaWeek: TextView
    private lateinit var tvPriceDeltaMonth: TextView
    private lateinit var tvVolumeDeltaDay: TextView
    private lateinit var tvVolumeDeltaWeek: TextView
    private lateinit var tvVolumeDeltaMonth: TextView
    private lateinit var tvMarketCapDeltaDay: TextView
    private lateinit var tvMarketCapDeltaWeek: TextView
    private lateinit var tvMarketCapDeltaMonth: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        tvUpdateIcon = findViewById(R.id.textview_update_icon)
        tvLastPriceUpdate = findViewById(R.id.textview_last_update)
        // update price on touch top table row
        tvLastPriceUpdate.setOnClickListener{
            //if last update less than 1m ago,
            if (System.currentTimeMillis() - lastReqTime <= 60000) {
                // blink price
                val anim: Animation = AlphaAnimation(0.0f, 1.0f)
                anim.duration = 50
                anim.startOffset = 20
                anim.repeatMode = Animation.REVERSE
                anim.repeatCount = 1
                tvLastUpdate.startAnimation(anim)
                tvUpdateIcon.startAnimation(anim)
            } else
                queryPriceServer()
        }

        tlMain = findViewById(R.id.main_table_layout)
        tvLastUpdate = findViewById(R.id.textview_last_update)
        tvPriceUnits = findViewById(R.id.textview_price)
        tvPrice = findViewById(R.id.textview_price_value)
        tvMarketCap = findViewById(R.id.textview_market_cap_value)
        tvVolume = findViewById(R.id.textview_volume_value)
        tvPriceDeltaDay = findViewById(R.id.textview_price_change_day_value)
        tvPriceDeltaWeek = findViewById(R.id.textview_price_change_week_value)
        tvPriceDeltaMonth = findViewById(R.id.textview_price_change_month_value)
        tvVolumeDeltaDay = findViewById(R.id.textview_volume_change_day_value)
        tvVolumeDeltaWeek = findViewById(R.id.textview_volume_change_week_value)
        tvVolumeDeltaMonth = findViewById(R.id.textview_volume_change_month_value)
        tvMarketCapDeltaDay = findViewById(R.id.textview_market_cap_change_day_value)
        tvMarketCapDeltaWeek = findViewById(R.id.textview_market_cap_change_week_value)
        tvMarketCapDeltaMonth = findViewById(R.id.textview_market_cap_change_month_value)
     }

    override fun onResume() {
        super.onResume()

        // update views with old preference data
        loadPrefs()
        updateUI()
        // get new data
        queryPriceServer()

        // register broadcast receiver
        val filters = IntentFilter()
        filters.addAction(BROADCAST_SHOW_TOAST)
        filters.addAction(BROADCAST_PRICE_UPDATED)
        filters.addAction(BROADCAST_MARKET_CHARTS_UPDATED)
        LocalBroadcastManager.getInstance(this).registerReceiver(br, filters)
    }

    override fun onPause() {
        super.onPause()

        savePrefs()

        // unregister broadcast receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(br)
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

    fun updateUI() {
        //update price
        runOnUiThread {
            tvLastUpdate.text = getDateTime(prefLastUpdate)
            tvPriceUnits.text = "$prefCurrency/BTC"
            tvPrice.text = prefPrice
            //tvDayChange.text = formatChangePercent(prefDayChange.toFloat())
            tvVolume.text = prefDayVolume
            tvMarketCap.text = prefMarketCap
            tvPriceDeltaDay.text = formatChangePercent(prefPriceDeltaDay)
            tvPriceDeltaWeek.text = formatChangePercent(prefPriceDeltaWeek)
            tvPriceDeltaMonth.text = formatChangePercent(prefPriceDeltaMonth)
            tvVolumeDeltaDay.text = formatChangePercent(prefVolumeDeltaDay)
            tvVolumeDeltaWeek.text = formatChangePercent(prefVolumeDeltaWeek)
            tvVolumeDeltaMonth.text = formatChangePercent(prefVolumeDeltaMonth)
            tvMarketCapDeltaDay.text = formatChangePercent(prefMarketCapDeltaDay)
            tvMarketCapDeltaWeek.text = formatChangePercent(prefMarketCapDeltaWeek)
            tvMarketCapDeltaMonth.text = formatChangePercent(prefMarketCapDeltaMonth)
            // change color of price based on 24h change
            /*if (prefPriceDeltaDay != "…") {
                val deltaColor: Int = if (prefDayChange.toFloat() > 0)
                    getColor(R.color.green)
                else
                    getColor(R.color.red)
                tvDayChange.setTextColor(deltaColor)
                tvPrice.setTextColor(deltaColor)
                tvMarketCap.setTextColor(deltaColor)
            } */
        }
    }

    private fun queryPriceServer() {
        // construct recurring price query
        val priceReq = OneTimeWorkRequestBuilder<RequestUpdateWorker>()
        val data = Data.Builder()
        data.putString(PREF_CURRENCY, prefCurrency)
        data.putInt(WIDGIT_ID, -1)
        priceReq.setInputData(data.build())
        priceReq.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)

        WorkManager.getInstance(this).enqueue(priceReq.build())
    }

    private val br = object : BroadcastReceiver() {
        override fun onReceive(contxt: Context?, intent: Intent?) {
            // ignore widget price updates
            val widgetId = intent?.getIntExtra(WIDGIT_ID, -1)
            if ( widgetId != -1) return

            when (intent.action) {
                BROADCAST_SHOW_TOAST -> {
                    intent.getStringExtra(MESSAGE)?.let { showToast(it) }
                }
                BROADCAST_PRICE_UPDATED -> {
                    prefPrice = intent.getStringExtra(PRICE).toString()
                    prefMarketCap = intent.getStringExtra(MARKET_CAP).toString()
                    prefDayVolume = intent.getStringExtra(DAY_VOLUME).toString()
                    prefLastUpdate = intent.getStringExtra(LAST_UPDATE).toString()

                    updateUI()
                    lastReqTime = System.currentTimeMillis()
                }
                BROADCAST_MARKET_CHARTS_UPDATED -> {
                    prefPriceDeltaDay = intent.getFloatExtra(PRICE_DELTA_DAY, 0f)
                    prefPriceDeltaWeek = intent.getFloatExtra(PRICE_DELTA_WEEK, 0f)
                    prefPriceDeltaMonth = intent.getFloatExtra(PRICE_DELTA_MONTH, 0f)
                    prefVolumeDeltaDay = intent.getFloatExtra(VOLUME_DELTA_DAY, 0f)
                    prefVolumeDeltaWeek = intent.getFloatExtra(VOLUME_DELTA_WEEK, 0f)
                    prefVolumeDeltaMonth = intent.getFloatExtra(VOLUME_DELTA_MONTH, 0f)
                    prefMarketCapDeltaDay = intent.getFloatExtra(MARKET_CAP_DELTA_DAY, 0f)
                    prefMarketCapDeltaWeek =intent.getFloatExtra(MARKET_CAP_DELTA_WEEK, 0f)
                    prefMarketCapDeltaMonth = intent.getFloatExtra(MARKET_CAP_DELTA_MONTH, 0f)
                    updateUI()
                }
            }
        }
    }

    fun showToast(message: String) =
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

    private fun loadPrefs() {
        val sharedPrefs = getSharedPreferences(MAIN_PREFS, 0)
        prefCurrency = sharedPrefs.getString(PREF_CURRENCY, "USD").toString()
        prefPrice = sharedPrefs.getString(MAIN_PREF_PRICE, getString(R.string.loading)).toString()
        prefMarketCap = sharedPrefs.getString(MAIN_PREF_MARKET_CAP, getString(R.string.loading)).toString()
        prefDayVolume = sharedPrefs.getString(MAIN_PREF_DAY_VOLUME, getString(R.string.loading)).toString()
        prefLastUpdate = sharedPrefs.getString(MAIN_PREF_LAST_UPDATE, getString(R.string.loading)).toString()
        prefPriceDeltaDay = sharedPrefs.getFloat(MAIN_PREF_PRICE_DELTA_DAY, 0f)
        prefPriceDeltaWeek = sharedPrefs.getFloat(MAIN_PREF_PRICE_DELTA_WEEK, 0f)
        prefPriceDeltaMonth = sharedPrefs.getFloat(MAIN_PREF_PRICE_DELTA_MONTH , 0f)
        prefVolumeDeltaDay = sharedPrefs.getFloat(MAIN_PREF_VOLUME_DELTA_DAY, 0f)
        prefVolumeDeltaWeek = sharedPrefs.getFloat(MAIN_PREF_VOLUME_DELTA_WEEK, 0f)
        prefVolumeDeltaMonth = sharedPrefs.getFloat(MAIN_PREF_VOLUME_DELTA_MONTH, 0f)
        prefMarketCapDeltaDay = sharedPrefs.getFloat(MAIN_PREF_MARKET_CAP_DELTA_DAY, 0f)
        prefMarketCapDeltaWeek = sharedPrefs.getFloat(MAIN_PREF_MARKET_CAP_DELTA_WEEK , 0f)
        prefMarketCapDeltaMonth = sharedPrefs.getFloat(MAIN_PREF_MARKET_CAP_DELTA_MONTH, 0f)
        //println("loaded sharedPrefs: ${sharedPrefs.all}")
    }

    private fun savePrefs() {
        //save last btc price to preferences to avoid null pointer exception
        //if server cannot be reached on next server request
        val prefs = getSharedPreferences(MAIN_PREFS, 0)
        val prefsEditor = prefs.edit()
        prefsEditor.apply {
            putString(PREF_CURRENCY, prefCurrency)
            putString(MAIN_PREF_PRICE, prefPrice)
            putString(MAIN_PREF_MARKET_CAP, prefMarketCap)
            putString(MAIN_PREF_DAY_VOLUME, prefDayVolume)
            putString(MAIN_PREF_LAST_UPDATE, prefLastUpdate)
            putFloat(MAIN_PREF_PRICE_DELTA_DAY, prefPriceDeltaDay)
            putFloat(MAIN_PREF_PRICE_DELTA_WEEK, prefPriceDeltaWeek)
            putFloat(MAIN_PREF_PRICE_DELTA_MONTH, prefPriceDeltaMonth)
            putFloat(MAIN_PREF_VOLUME_DELTA_DAY, prefVolumeDeltaDay)
            putFloat(MAIN_PREF_VOLUME_DELTA_WEEK, prefVolumeDeltaWeek)
            putFloat(MAIN_PREF_VOLUME_DELTA_MONTH, prefVolumeDeltaMonth)
            putFloat(MAIN_PREF_MARKET_CAP_DELTA_DAY, prefMarketCapDeltaDay)
            putFloat(MAIN_PREF_MARKET_CAP_DELTA_WEEK, prefMarketCapDeltaWeek)
            putFloat(MAIN_PREF_MARKET_CAP_DELTA_MONTH, prefMarketCapDeltaMonth)

        }.commit()
        println("saved sharedPrefs: ${prefs.all}")
    }
}

fun formatChangePercent(dc: Float): CharSequence {
    try {
        return "%.2f".format(dc)
    } catch (e: Exception) {
        return "…"
    }
}

fun getDateTime(s: String): String? {
    if (s == "…") return "…"
    val sdf = SimpleDateFormat("HH:mm:ss/dd/MM")
    val netDate = try {
        Date(s.toLong() * 1000)
    } catch (e: Exception) {
        return "…"
    }
    return sdf.format(netDate)
}