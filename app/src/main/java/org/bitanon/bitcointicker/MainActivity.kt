package org.bitanon.bitcointicker

import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
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

const val MAIN_PREFS = "main_prefs"
const val MAIN_PREF_CURRENCY = "main_pref_currency"
const val MAIN_PREF_PRICE = "main_pref_price"
const val MAIN_PREF_DAY_CHANGE = "main_pref_day_change"
const val MAIN_PREF_BG_COLOR_RADIO_ID = "main_pref_bg_color"

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private lateinit var prefCurrency: String
    lateinit var prefPrice: String
    private lateinit var prefDayChange: String
    private lateinit var prefBgColor: String
    private var lastReqTime: Long = 0

    private lateinit var btcPriceUnitsTextView: TextView
    private lateinit var btcPriceTextView: TextView
    private lateinit var dayChangeTextView: TextView
    private lateinit var mainTableLayout: TableLayout

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

        val sharedPrefs = getSharedPreferences(MAIN_PREFS, 0)
        prefCurrency = sharedPrefs.getString(MAIN_PREF_CURRENCY, "USD").toString()
        prefDayChange = sharedPrefs.getString(WIDGET_PREF_DAY_CHANGE,
            getString(R.string.loading)).toString()
        prefPrice = sharedPrefs.getString(MAIN_PREF_PRICE,
            getString(R.string.loading)).toString()
        prefBgColor = getString(getBgColor(sharedPrefs.getString(
            MAIN_PREF_BG_COLOR_RADIO_ID, "").toString())) // getBgColor will return default black color

        println("loaded sharedPrefs: ${sharedPrefs.all}")

        btcPriceUnitsTextView = findViewById(R.id.textview_btcprice_units)
        btcPriceTextView = findViewById(R.id.textview_btcprice)
        dayChangeTextView = findViewById(R.id.textview_day_change_value)
        mainTableLayout = findViewById(R.id.main_table_layout)

        updateUI()
        queryPriceServer()
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
            //if last update less than 1m ago,
            if (System.currentTimeMillis() - lastReqTime <= 60000) {
                val anim: Animation = AlphaAnimation(0.0f, 1.0f)
                anim.duration = 50
                anim.startOffset = 20
                anim.repeatMode = Animation.REVERSE
                anim.repeatCount = 1
                btcPriceTextView.startAnimation(anim)
            } else
                queryPriceServer()
        }

        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        // unregister broadcast receiver
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(br)
    }

    fun updateUI() {
        //update price
        runOnUiThread {
            btcPriceUnitsTextView.text = "$prefCurrency/BTC"
            btcPriceTextView.text = prefPrice
            dayChangeTextView.text = formatDayChange(prefDayChange)
            mainTableLayout.setBackgroundColor(Color.parseColor(prefBgColor))
            // change color of price based on 24h change
            if (prefDayChange != "…") {
                val deltaColor: Int = if (prefDayChange.toFloat() > 0)
                    getColor(R.color.green)
                else
                    getColor(R.color.red)
                dayChangeTextView.setTextColor(deltaColor)
                btcPriceTextView.setTextColor(deltaColor)
            }
        }
    }

    private fun queryPriceServer() {
        // construct recurring price query
        val priceReq = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
        val data = Data.Builder()
        data.putString("pref_curr", prefCurrency)
        data.putInt("widget_id", -1)
        priceReq.setInputData(data.build())
        priceReq.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)

        WorkManager.getInstance(this).enqueue(priceReq.build())
    }

    private val br = object : BroadcastReceiver() {
        override fun onReceive(contxt: Context?, intent: Intent?) {
            // ignore widget price updates
            val widgetId = intent?.getIntExtra("widget_id", 0)
            if ( widgetId != -1) return

            val message = intent.getStringExtra("message")
            when (intent.action) {
                BROADCAST_SHOW_TOAST -> message?.let { showToast(it) }
                BROADCAST_PRICE_UPDATED -> {
                    prefPrice = intent.getStringExtra("price").toString()
                    prefDayChange = intent.getStringExtra("day_change").toString()
                    savePrefs(baseContext, null, prefPrice, prefDayChange, null)
                    updateUI()
                    lastReqTime = System.currentTimeMillis()
                }
            }
        }
    }

    fun showToast(message: String) =
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
}

internal fun savePrefs(ctx: Context, currency: String?,
                      price: String?, dayChange: String?, bgColorRadioName: String?) {
    //save last btc price to preferences to avoid null pointer exception
    //if server cannot be reached on next server request
    val prefs = ctx.getSharedPreferences(MAIN_PREFS, 0)
    val prefsEditor = prefs.edit()
    prefsEditor.apply {
        if (currency != null)
            putString(MAIN_PREF_CURRENCY, currency)
        if (price != null)
            putString(MAIN_PREF_PRICE, price)
        if (dayChange != null)
            putString(MAIN_PREF_DAY_CHANGE, dayChange)
        if (bgColorRadioName!= null)
            putString(MAIN_PREF_BG_COLOR_RADIO_ID, bgColorRadioName)
    }.commit()
    println("saved sharedPrefs: ${prefs.all}")
}

private fun formatDayChange(dc: String?): CharSequence {
    if (dc == "…") return "…"
    return "%.2f".format(dc?.toFloat()) + "%"
}