package org.bitanon.bitcointicker

import android.content.*
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.LinearLayout
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bitanon.bitcointicker.databinding.ActivityMainBinding
import java.text.DecimalFormat
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
    private var lastCGReqTime: Long = 0
    private var prefPriceDeltaDay: Float = 0f
    private var prefPriceDeltaWeek: Float = 0f
    private var prefPriceDeltaMonth: Float = 0f
    private var prefVolumeDeltaDay: Float = 0f
    private var prefVolumeDeltaWeek: Float = 0f
    private var prefVolumeDeltaMonth: Float = 0f
    private var prefMarketCapDeltaDay: Float = 0f
    private var prefMarketCapDeltaWeek: Float = 0f
    private var prefMarketCapDeltaMonth: Float = 0f
    private var prefAddrNew: MutableList<Float>? = null
    private var prefAddrActive: MutableList<Float>? = null
    private var prefFeeTot: MutableList<Float>? = null
    private var prefFeeMean: MutableList<Float>? = null
    private var prefFeeMedian: MutableList<Float>? = null
    private var prefSopr: MutableList<Float>? = null

    private lateinit var llMain: LinearLayout
    private lateinit var tvLastCGUpdate: TextView
    private lateinit var tvUpdateIcon: TextView
    private lateinit var tvPriceLabel: TextView
    private lateinit var tvPrice: TextView
    private lateinit var tvPriceDeltaDay: TextView
    private lateinit var tvPriceDeltaWeek: TextView
    private lateinit var tvPriceDeltaMonth: TextView
    private lateinit var tvVolume: TextView
    private lateinit var tvVolumeDeltaDay: TextView
    private lateinit var tvVolumeDeltaWeek: TextView
    private lateinit var tvVolumeDeltaMonth: TextView
    private lateinit var tvMarketCap: TextView
    private lateinit var tvMarketCapDeltaDay: TextView
    private lateinit var tvMarketCapDeltaWeek: TextView
    private lateinit var tvMarketCapDeltaMonth: TextView
    private lateinit var tvAddrNew: TextView
    private lateinit var tvAddrNewDeltaDay: TextView
    private lateinit var tvAddrNewDeltaWeek: TextView
    private lateinit var tvAddrNewDeltaMonth: TextView
    private lateinit var tvAddrActive: TextView
    private lateinit var tvAddrActiveDeltaDay: TextView
    private lateinit var tvAddrActiveDeltaWeek: TextView
    private lateinit var tvAddrActiveDeltaMonth: TextView
    private lateinit var tvFeeTot: TextView
    private lateinit var tvFeeTotDeltaDay: TextView
    private lateinit var tvFeeTotDeltaWeek: TextView
    private lateinit var tvFeeTotDeltaMonth: TextView
    private lateinit var tvFeeMean: TextView
    private lateinit var tvFeeMeanDeltaDay: TextView
    private lateinit var tvFeeMeanDeltaWeek: TextView
    private lateinit var tvFeeMeanDeltaMonth: TextView
    private lateinit var tvFeeMedian: TextView
    private lateinit var tvFeeMedianDeltaDay: TextView
    private lateinit var tvFeeMedianDeltaWeek: TextView
    private lateinit var tvFeeMedianDeltaMonth: TextView
    private lateinit var tvSopr: TextView
    private lateinit var tvSoprDeltaDay: TextView
    private lateinit var tvSoprDeltaWeek: TextView
    private lateinit var tvSoprDeltaMonth: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        tvUpdateIcon = findViewById(R.id.textview_header_update_icon)
        // update price on touch top table row
        tvUpdateIcon.setOnClickListener{
            //if last update less than 1m ago,
            if (System.currentTimeMillis() - lastCGReqTime <= 60000) {
                // blink update icon and time
                val anim: Animation = AlphaAnimation(0.0f, 1.0f)
                anim.duration = 50
                anim.startOffset = 20
                anim.repeatMode = Animation.REVERSE
                anim.repeatCount = 1
                tvUpdateIcon.startAnimation(anim)
                tvLastCGUpdate.startAnimation(anim)
            } else
                queryPriceServer()
        }

        llMain = findViewById(R.id.main_linear_layout)
        tvLastCGUpdate = findViewById(R.id.textview_header_last_update)
        tvPriceLabel = findViewById(R.id.textview_price)
        tvPrice = findViewById(R.id.textview_price_value)
        tvPriceDeltaDay = findViewById(R.id.textview_price_delta_day_value)
        tvPriceDeltaWeek = findViewById(R.id.textview_price_delta_week_value)
        tvPriceDeltaMonth = findViewById(R.id.textview_price_delta_month_value)
        tvVolume = findViewById(R.id.textview_volume_value)
        tvVolumeDeltaDay = findViewById(R.id.textview_volume_delta_day_value)
        tvVolumeDeltaWeek = findViewById(R.id.textview_volume_delta_week_value)
        tvVolumeDeltaMonth = findViewById(R.id.textview_volume_delta_month_value)
        tvMarketCap = findViewById(R.id.textview_market_cap_value)
        tvMarketCapDeltaDay = findViewById(R.id.textview_market_cap_delta_day_value)
        tvMarketCapDeltaWeek = findViewById(R.id.textview_market_cap_delta_week_value)
        tvMarketCapDeltaMonth = findViewById(R.id.textview_market_cap_delta_month_value)

        tvAddrNew = findViewById(R.id.textview_addr_new_value)
        tvAddrNewDeltaDay = findViewById(R.id.textview_addr_new_delta_day_value)
        tvAddrNewDeltaWeek = findViewById(R.id.textview_addr_new_delta_week_value)
        tvAddrNewDeltaMonth = findViewById(R.id.textview_addr_new_delta_month_value)
        tvAddrActive = findViewById(R.id.textview_addr_active_value)
        tvAddrActiveDeltaDay = findViewById(R.id.textview_addr_active_delta_day_value)
        tvAddrActiveDeltaWeek = findViewById(R.id.textview_addr_active_delta_week_value)
        tvAddrActiveDeltaMonth = findViewById(R.id.textview_addr_active_delta_month_value)
        tvFeeTot = findViewById(R.id.textview_fee_total_value)
        tvFeeTotDeltaDay = findViewById(R.id.textview_fee_total_delta_day_value)
        tvFeeTotDeltaWeek = findViewById(R.id.textview_fee_total_delta_week_value)
        tvFeeTotDeltaMonth = findViewById(R.id.textview_fee_total_delta_month_value)
        tvFeeMean = findViewById(R.id.textview_fee_mean_value)
        tvFeeMeanDeltaDay = findViewById(R.id.textview_fee_mean_delta_day_value)
        tvFeeMeanDeltaWeek = findViewById(R.id.textview_fee_mean_delta_week_value)
        tvFeeMeanDeltaMonth = findViewById(R.id.textview_fee_mean_delta_month_value)
        tvFeeMedian = findViewById(R.id.textview_fee_median_value)
        tvFeeMedianDeltaDay = findViewById(R.id.textview_fee_median_delta_day_value)
        tvFeeMedianDeltaWeek = findViewById(R.id.textview_fee_median_delta_week_value)
        tvFeeMedianDeltaMonth = findViewById(R.id.textview_fee_median_delta_month_value)
        tvSopr = findViewById(R.id.textview_sopr_value)
        tvSoprDeltaDay = findViewById(R.id.textview_sopr_delta_day_value)
        tvSoprDeltaWeek = findViewById(R.id.textview_sopr_delta_week_value)
        tvSoprDeltaMonth = findViewById(R.id.textview_sopr_delta_month_value)
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
        filters.addAction(BROADCAST_CG_PRICE_UPDATED)
        filters.addAction(BROADCAST_CG_MARKET_CHARTS_UPDATED)
        filters.addAction(BROADCAST_GN_METRICS_UPDATED)
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
            tvLastCGUpdate.text = getDateTime(prefLastUpdate)
            tvPriceLabel.text = "$prefCurrency/BTC"
            tvPrice.text = numberToCurrency(prefPrice, prefCurrency)
            tvPriceDeltaDay.text = formatChangePercent(prefPriceDeltaDay)
            tvPriceDeltaWeek.text = formatChangePercent(prefPriceDeltaWeek)
            tvPriceDeltaMonth.text = formatChangePercent(prefPriceDeltaMonth)
            tvVolume.text = prettyBigNumber(prefDayVolume)
            tvVolumeDeltaDay.text = formatChangePercent(prefVolumeDeltaDay)
            tvVolumeDeltaWeek.text = formatChangePercent(prefVolumeDeltaWeek)
            tvVolumeDeltaMonth.text = formatChangePercent(prefVolumeDeltaMonth)
            tvMarketCap.text = prettyBigNumber(prefMarketCap)
            tvMarketCapDeltaDay.text = formatChangePercent(prefMarketCapDeltaDay)
            tvMarketCapDeltaWeek.text = formatChangePercent(prefMarketCapDeltaWeek)
            tvMarketCapDeltaMonth.text = formatChangePercent(prefMarketCapDeltaMonth)
            tvAddrNew.text = prettyBigNumber(prefAddrNew?.get(0).toString())
            tvAddrNewDeltaDay.text = formatChangePercent(prefAddrNew?.get(1))
            tvAddrNewDeltaWeek.text = formatChangePercent(prefAddrNew?.get(2))
            tvAddrNewDeltaMonth.text = formatChangePercent(prefAddrNew?.get(3))
            tvAddrActive.text = prettyBigNumber(prefAddrActive?.get(0).toString())
            tvAddrActiveDeltaDay.text = formatChangePercent(prefAddrActive?.get(1))
            tvAddrActiveDeltaWeek.text = formatChangePercent(prefAddrActive?.get(2))
            tvAddrActiveDeltaMonth.text = formatChangePercent(prefAddrActive?.get(3))
            tvFeeTot.text = getString(R.string.bitcoin_symbol) +
                    prettyBigNumber(prefFeeTot?.get(0).toString())
            tvFeeTotDeltaDay.text = formatChangePercent(prefFeeTot?.get(1))
            tvFeeTotDeltaWeek.text = formatChangePercent(prefFeeTot?.get(2))
            tvFeeTotDeltaMonth.text = formatChangePercent(prefFeeTot?.get(3))
            tvFeeMean.text = prettyBigNumber(btcToSats(prefFeeMean?.get(0))) + "s"
            tvFeeMeanDeltaDay.text = formatChangePercent(prefFeeMean?.get(1))
            tvFeeMeanDeltaWeek.text = formatChangePercent(prefFeeMean?.get(2))
            tvFeeMeanDeltaMonth.text = formatChangePercent(prefFeeMean?.get(3))
            tvFeeMedian.text = prettyBigNumber(btcToSats(prefFeeMedian?.get(0))) + "s"
            tvFeeMedianDeltaDay.text = formatChangePercent(prefFeeMedian?.get(1))
            tvFeeMedianDeltaWeek.text = formatChangePercent(prefFeeMedian?.get(2))
            tvFeeMedianDeltaMonth.text = formatChangePercent(prefFeeMedian?.get(3))
            tvSopr.text = formatRatio(prefSopr?.get(0))
            tvSoprDeltaDay.text = formatChangePercent(prefSopr?.get(1))
            tvSoprDeltaWeek.text = formatChangePercent(prefSopr?.get(2))
            tvSoprDeltaMonth.text = formatChangePercent(prefSopr?.get(3))

            // change color of delta metrics
            for (row in llMain.children) {
                row as LinearLayout
                for (tv in row.children) {
                    tv as TextView

                    // don't color if value not yet set
                    if (tv.text == getString(R.string.dash))
                        continue

                    val tvId = resources.getResourceName(tv.id)
                    // not metric headers nor labels
                    if ("header" in tvId || "label" in tvId) continue

                    // set corresponding metrics same color as day deltas
                    if ("day" in tvId) {
                        // positive deltas green, neg red, zeros orange
                        val color = if (tv.text.toString().toFloat() > 0)
                                getColor(R.color.green)
                            else if (tv.text.toString().toFloat() < 0)
                                getColor(R.color.red) else getColor(R.color.orange)
                        tv.setTextColor(color)
                        // also turn corresponding metrics same color as delta day
                        if ("price" in tvId)
                            tvPrice.setTextColor(color)
                        if ("market_cap" in tvId)
                            tvMarketCap.setTextColor(color)
                        if ("volume" in tvId)
                            tvVolume.setTextColor(color)
                        if ("addr_new" in tvId)
                            tvAddrNew.setTextColor(color)
                        if ("addr_active" in tvId)
                            tvAddrActive.setTextColor(color)
                        if ("fee_total" in tvId)
                            tvFeeTot.setTextColor(color)
                        if ("fee_mean" in tvId)
                            tvFeeMean.setTextColor(color)
                        if ("fee_median" in tvId)
                            tvFeeMedian.setTextColor(color)
                        if ("sopr" in tvId)
                            tvSopr.setTextColor(color)
                    }

                    if ("week" in tvId || "month" in tvId) {
                        // positive deltas turn green
                        if (tv.text.toString().toFloat() > 0)
                            tv.setTextColor(getColor(R.color.green))
                        // positive deltas turn red, ignore zeros
                        if (tv.text.toString().toFloat() < 0)
                            tv.setTextColor(getColor(R.color.red))
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
                    return
                }
                BROADCAST_CG_PRICE_UPDATED -> {
                    prefPrice = intent.getStringExtra(CURR_PRICE).toString()
                    prefMarketCap = intent.getStringExtra(CURR_MARKET_CAP).toString()
                    prefDayVolume = intent.getStringExtra(CURR_DAY_VOLUME).toString()
                    prefLastUpdate = intent.getStringExtra(CURR_LAST_UPDATE).toString()

                    lastCGReqTime = System.currentTimeMillis()
                }
                BROADCAST_CG_MARKET_CHARTS_UPDATED -> {
                    prefPriceDeltaDay = intent.getFloatExtra(PRICE_DELTA_DAY, 0f)
                    prefPriceDeltaWeek = intent.getFloatExtra(PRICE_DELTA_WEEK, 0f)
                    prefPriceDeltaMonth = intent.getFloatExtra(PRICE_DELTA_MONTH, 0f)
                    prefVolumeDeltaDay = intent.getFloatExtra(VOLUME_DELTA_DAY, 0f)
                    prefVolumeDeltaWeek = intent.getFloatExtra(VOLUME_DELTA_WEEK, 0f)
                    prefVolumeDeltaMonth = intent.getFloatExtra(VOLUME_DELTA_MONTH, 0f)
                    prefMarketCapDeltaDay = intent.getFloatExtra(MARKET_CAP_DELTA_DAY, 0f)
                    prefMarketCapDeltaWeek =intent.getFloatExtra(MARKET_CAP_DELTA_WEEK, 0f)
                    prefMarketCapDeltaMonth = intent.getFloatExtra(MARKET_CAP_DELTA_MONTH, 0f)
                }
                BROADCAST_GN_METRICS_UPDATED -> {
                    when (intent.getStringExtra(METRIC_NAME)) {
                        METRIC_STD_ADDR_NEW ->
                            prefAddrNew = intent.getStringExtra(METRIC_STD_ADDR_NEW)
                                ?.let{ getListFromJson(it) }
                        METRIC_STD_ADDR_ACT ->
                            prefAddrActive = intent.getStringExtra(METRIC_STD_ADDR_ACT)
                                ?.let { getListFromJson(it) }
                        METRIC_STD_FEE_TOT ->
                            prefFeeTot = intent.getStringExtra(METRIC_STD_FEE_TOT)
                                ?.let { getListFromJson(it) }
                        METRIC_STD_FEE_MEAN ->
                            prefFeeMean = intent.getStringExtra(METRIC_STD_FEE_MEAN)
                                ?.let { getListFromJson(it) }
                        METRIC_STD_FEE_MEDIAN ->
                            prefFeeMedian = intent.getStringExtra(METRIC_STD_FEE_MEDIAN)
                                ?.let { getListFromJson(it) }
                        METRIC_STD_SOPR ->
                            prefSopr = intent.getStringExtra(METRIC_STD_SOPR)
                                ?.let { getListFromJson(it) }
                    }
                }
            }
            updateUI()
        }
    }

    fun showToast(message: String) =
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

    private fun loadPrefs() {
        val sharedPrefs = getSharedPreferences(MAIN_PREFS, 0)
        prefCurrency = sharedPrefs.getString(CURRENCY, "USD").toString()
        prefPrice = sharedPrefs.getString(CURR_PRICE, getString(R.string.dash)).toString()
        prefMarketCap = sharedPrefs.getString(CURR_MARKET_CAP, getString(R.string.dash)).toString()
        prefDayVolume = sharedPrefs.getString(CURR_DAY_VOLUME, getString(R.string.dash)).toString()
        prefLastUpdate = sharedPrefs.getString(CURR_LAST_UPDATE, getString(R.string.dash)).toString()
        prefPriceDeltaDay = sharedPrefs.getFloat(PRICE_DELTA_DAY, 0f)
        prefPriceDeltaWeek = sharedPrefs.getFloat(PRICE_DELTA_WEEK, 0f)
        prefPriceDeltaMonth = sharedPrefs.getFloat(PRICE_DELTA_MONTH , 0f)
        prefVolumeDeltaDay = sharedPrefs.getFloat(VOLUME_DELTA_DAY, 0f)
        prefVolumeDeltaWeek = sharedPrefs.getFloat(VOLUME_DELTA_WEEK, 0f)
        prefVolumeDeltaMonth = sharedPrefs.getFloat(VOLUME_DELTA_MONTH, 0f)
        prefMarketCapDeltaDay = sharedPrefs.getFloat(MARKET_CAP_DELTA_DAY, 0f)
        prefMarketCapDeltaWeek = sharedPrefs.getFloat(MARKET_CAP_DELTA_WEEK , 0f)
        prefMarketCapDeltaMonth = sharedPrefs.getFloat(MARKET_CAP_DELTA_MONTH, 0f)

        val addrNew = sharedPrefs.getString(METRIC_STD_ADDR_NEW, null)
        prefAddrNew = addrNew?.let { getListFromJson(it) }
        val addrAct = sharedPrefs.getString(METRIC_STD_ADDR_ACT, null)
        prefAddrActive = addrAct?.let { getListFromJson(it) }
        val feeTot = sharedPrefs.getString(METRIC_STD_FEE_TOT, null)
        prefFeeTot = feeTot?.let { getListFromJson(it) }
        val feeMean = sharedPrefs.getString(METRIC_STD_FEE_MEAN, null)
        prefFeeMean = feeMean?.let { getListFromJson(it) }
        val feeMed = sharedPrefs.getString(METRIC_STD_FEE_MEDIAN, null)
        prefFeeMedian = feeMed?.let { getListFromJson(it) }
        val sopr = sharedPrefs.getString(METRIC_STD_SOPR, null)
        prefSopr = sopr?.let { getListFromJson(it) }

        println("loaded sharedPrefs: ${sharedPrefs.all}")
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
            putString(METRIC_STD_ADDR_NEW, Gson().toJson(prefAddrNew))
            putString(METRIC_STD_ADDR_ACT, Gson().toJson(prefAddrActive))
            putString(METRIC_STD_FEE_TOT, Gson().toJson(prefFeeTot))
            putString(METRIC_STD_FEE_MEAN, Gson().toJson(prefFeeMean))
            putString(METRIC_STD_FEE_MEDIAN, Gson().toJson(prefFeeMedian))
            putString(METRIC_STD_SOPR, Gson().toJson(prefSopr))

        }.commit()
        println("saved sharedPrefs: ${prefs.all}")
    }
}

fun getListFromJson(jsonStr: String): MutableList<Float>? {
    val type = object : TypeToken<MutableList<Float>>() {}.type
    return Gson().fromJson(jsonStr, type)
}

fun formatChangePercent(dc: Float?): CharSequence {
    if (dc == null) return "-"
    return "%.2f".format(dc)
}

fun formatRatio(float: Float?): String {
    if (float == null) return "-"
    return DecimalFormat("##.##").format(float)
}

fun getDateTime(s: String?): String? {
    if (s.isNullOrBlank() || s == "-") return "-"
    val sdf = SimpleDateFormat("HH:mm[dd/MM]")
    val netDate = try {
        Date(s.toLong() * 1000)
    } catch (e: Exception) {
        return "-"
    }
    return sdf.format(netDate)
}