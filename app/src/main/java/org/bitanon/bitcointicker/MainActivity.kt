package org.bitanon.bitcointicker

import android.content.*
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
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

        tvUpdateIcon = findViewById(R.id.textview_header_update_icon)
        tvLastPriceUpdate = findViewById(R.id.textview_header_last_update)
        // update price on touch top table row
        tvUpdateIcon.setOnClickListener{
            //if last update less than 1m ago,
            if (System.currentTimeMillis() - lastReqTime <= 60000) {
                // blink update icon and time
                val anim: Animation = AlphaAnimation(0.0f, 1.0f)
                anim.duration = 50
                anim.startOffset = 20
                anim.repeatMode = Animation.REVERSE
                anim.repeatCount = 1
                tvUpdateIcon.startAnimation(anim)
                tvLastUpdate.startAnimation(anim)
            } else
                queryPriceServer()
        }

        tlMain = findViewById(R.id.main_table_layout)
        tvLastUpdate = findViewById(R.id.textview_header_last_update)
        tvPriceUnits = findViewById(R.id.textview_price)
        tvPrice = findViewById(R.id.textview_price_value)
        tvMarketCap = findViewById(R.id.textview_market_cap_value)
        tvVolume = findViewById(R.id.textview_volume_value)
        tvPriceDeltaDay = findViewById(R.id.textview_price_delta_day_value)
        tvPriceDeltaWeek = findViewById(R.id.textview_price_delta_week_value)
        tvPriceDeltaMonth = findViewById(R.id.textview_price_delta_month_value)
        tvVolumeDeltaDay = findViewById(R.id.textview_volume_delta_day_value)
        tvVolumeDeltaWeek = findViewById(R.id.textview_volume_delta_week_value)
        tvVolumeDeltaMonth = findViewById(R.id.textview_volume_delta_month_value)
        tvMarketCapDeltaDay = findViewById(R.id.textview_market_cap_delta_day_value)
        tvMarketCapDeltaWeek = findViewById(R.id.textview_market_cap_delta_week_value)
        tvMarketCapDeltaMonth = findViewById(R.id.textview_market_cap_delta_month_value)
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

            tvPrice.text = numberToCurrency(prefPrice, prefCurrency)
            tvVolume.text = prettyBigNumber(prefDayVolume)
            tvMarketCap.text = prettyBigNumber(prefMarketCap)
            tvPriceDeltaDay.text = formatChangePercent(prefPriceDeltaDay)
            tvPriceDeltaWeek.text = formatChangePercent(prefPriceDeltaWeek)
            tvPriceDeltaMonth.text = formatChangePercent(prefPriceDeltaMonth)
            tvVolumeDeltaDay.text = formatChangePercent(prefVolumeDeltaDay)
            tvVolumeDeltaWeek.text = formatChangePercent(prefVolumeDeltaWeek)
            tvVolumeDeltaMonth.text = formatChangePercent(prefVolumeDeltaMonth)
            tvMarketCapDeltaDay.text = formatChangePercent(prefMarketCapDeltaDay)
            tvMarketCapDeltaWeek.text = formatChangePercent(prefMarketCapDeltaWeek)
            tvMarketCapDeltaMonth.text = formatChangePercent(prefMarketCapDeltaMonth)

            // change color of delta metrics
            for (tableRow in tlMain.children) {
                tableRow as TableRow
                for (child in tableRow.children) {

                    val childId = resources.getResourceName(child.id)
                    // not metric headers nor labels
                    if ("header" in childId || "label" in childId) continue

                    if ("week" in childId || "month" in childId) {
                        // only textviews
                        //if (child::class !is TextView) continue
                        child as TextView
                        // positive deltas turn green
                        if (child.text.toString().toFloat() > 0)
                            child.setTextColor(getColor(R.color.green))
                        // positive deltas turn red, ignore zeros
                        if (child.text.toString().toFloat() < 0)
                            child.setTextColor(getColor(R.color.red))
                    }
                    // set corresponding metrics same color as day deltas
                    if ("day" in childId) {
                        // only textviews
                        //if (child::class !is TextView) continue
                        child as TextView
                        // positive deltas green, neg red, zeros orange
                        val color = if (child.text.toString().toFloat() > 0)
                                getColor(R.color.green)
                            else if (child.text.toString().toFloat() < 0)
                                getColor(R.color.red) else getColor(R.color.orange)
                        child.setTextColor(color)
                        // also turn corresponding metrics same color as delta day
                        if ("price" in childId)
                            tvPrice.setTextColor(color)
                        // also turn corresponding metrics same color as delta day
                        if ("market_cap" in childId)
                            tvMarketCap.setTextColor(color)
                        // also turn corresponding metrics same color as delta day
                        if ("volume" in childId)
                            tvVolume.setTextColor(color)
                    }
                }
            }
        }
    }

    private fun queryPriceServer() {
        // construct recurring price query
        val priceReq = OneTimeWorkRequestBuilder<RequestUpdateWorker>()
        val data = Data.Builder()
        data.putString(CURRENCY, prefCurrency)
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
                    prefPrice = intent.getStringExtra(CURR_PRICE).toString()
                    prefMarketCap = intent.getStringExtra(CURR_MARKET_CAP).toString()
                    prefDayVolume = intent.getStringExtra(CURR_DAY_VOLUME).toString()
                    prefLastUpdate = intent.getStringExtra(CURR_LAST_UPDATE).toString()

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
                    savePrefs()
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
        prefCurrency = sharedPrefs.getString(CURRENCY, "USD").toString()
        prefPrice = sharedPrefs.getString(CURR_PRICE, getString(R.string.loading)).toString()
        prefMarketCap = sharedPrefs.getString(CURR_MARKET_CAP, getString(R.string.loading)).toString()
        prefDayVolume = sharedPrefs.getString(CURR_DAY_VOLUME, getString(R.string.loading)).toString()
        prefLastUpdate = sharedPrefs.getString(CURR_LAST_UPDATE, getString(R.string.loading)).toString()
        prefPriceDeltaDay = sharedPrefs.getFloat(PRICE_DELTA_DAY, 0f)
        prefPriceDeltaWeek = sharedPrefs.getFloat(PRICE_DELTA_WEEK, 0f)
        prefPriceDeltaMonth = sharedPrefs.getFloat(PRICE_DELTA_MONTH , 0f)
        prefVolumeDeltaDay = sharedPrefs.getFloat(VOLUME_DELTA_DAY, 0f)
        prefVolumeDeltaWeek = sharedPrefs.getFloat(VOLUME_DELTA_WEEK, 0f)
        prefVolumeDeltaMonth = sharedPrefs.getFloat(VOLUME_DELTA_MONTH, 0f)
        prefMarketCapDeltaDay = sharedPrefs.getFloat(MARKET_CAP_DELTA_DAY, 0f)
        prefMarketCapDeltaWeek = sharedPrefs.getFloat(MARKET_CAP_DELTA_WEEK , 0f)
        prefMarketCapDeltaMonth = sharedPrefs.getFloat(MARKET_CAP_DELTA_MONTH, 0f)
        //println("loaded sharedPrefs: ${sharedPrefs.all}")
    }

    private fun savePrefs() {
        //save last btc price to preferences to avoid null pointer exception
        //if server cannot be reached on next server request
        val prefs = getSharedPreferences(MAIN_PREFS, 0)
        val prefsEditor = prefs.edit()
        prefsEditor.apply {
            putString(CURRENCY, prefCurrency)
            putString(CURR_PRICE, prefPrice)
            putString(CURR_MARKET_CAP, prefMarketCap)
            putString(CURR_DAY_VOLUME, prefDayVolume)
            putString(CURR_LAST_UPDATE, prefLastUpdate)
            putFloat(PRICE_DELTA_DAY, prefPriceDeltaDay)
            putFloat(PRICE_DELTA_WEEK, prefPriceDeltaWeek)
            putFloat(PRICE_DELTA_MONTH, prefPriceDeltaMonth)
            putFloat(VOLUME_DELTA_DAY, prefVolumeDeltaDay)
            putFloat(VOLUME_DELTA_WEEK, prefVolumeDeltaWeek)
            putFloat(VOLUME_DELTA_MONTH, prefVolumeDeltaMonth)
            putFloat(MARKET_CAP_DELTA_DAY, prefMarketCapDeltaDay)
            putFloat(MARKET_CAP_DELTA_WEEK, prefMarketCapDeltaWeek)
            putFloat(MARKET_CAP_DELTA_MONTH, prefMarketCapDeltaMonth)

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
    val sdf = SimpleDateFormat("HH:mm[dd/MM]")
    val netDate = try {
        Date(s.toLong() * 1000)
    } catch (e: Exception) {
        return "…"
    }
    return sdf.format(netDate)
}