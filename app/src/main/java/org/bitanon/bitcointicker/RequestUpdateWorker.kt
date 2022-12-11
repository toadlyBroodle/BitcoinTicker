package org.bitanon.bitcointicker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

const val WIDGIT_ID = "WIDGET_ID"

// local broadcast filters
const val BROADCAST_SHOW_TOAST = "org.bitanon.bitcointicker.BROADCAST_SHOW_TOAST"
const val BROADCAST_CG_PRICE_UPDATED = "org.bitanon.bitcointicker.BROADCAST_CG_PRICE_UPDATED"
const val BROADCAST_CG_MARKET_CHARTS_UPDATED = "org.bitanon.bitcointicker.BROADCAST_CG_MARKET_CHARTS_UPDATED"
const val BROADCAST_GN_METRICS_UPDATED = "org.bitanon.bitcointicker.BROADCAST_GN_METRICS_UPDATED"

// Coin Gecko metrics
const val CURRENCY = "PREF_CURRENCY"
const val MESSAGE = "MESSAGE"
const val CURR_PRICE = "CURR_PRICE"
const val CURR_DAY_VOLUME = "CURR_DAY_VOLUME"
const val CURR_MARKET_CAP = "CURR_MARKET_CAP"
const val CURR_LAST_UPDATE = "CURR_LAST_UPDATE"
const val PRICE_DELTA_DAY = "PRICE_DELTA_DAY"
const val PRICE_DELTA_WEEK = "PRICE_DELTA_WEEK"
const val PRICE_DELTA_MONTH = "PRICE_DELTA_MONTH"
const val VOLUME_DELTA_DAY = "VOLUME_DELTA_DAY"
const val VOLUME_DELTA_WEEK = "VOLUME_DELTA_WEEK"
const val VOLUME_DELTA_MONTH = "VOLUME_DELTA_MONTH"
const val MARKET_CAP_DELTA_DAY = "MARKET_CAP_DELTA_DAY"
const val MARKET_CAP_DELTA_WEEK = "MARKET_CAP_DELTA_WEEK"
const val MARKET_CAP_DELTA_MONTH = "MARKET_CAP_DELTA_MONTH"
// Coin Gecko api url requests
const val urlCGReqPing = "https://api.coingecko.com/api/v3/ping"
const val urlCGReqBtcSimplePrice = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_market_cap=true&include_24hr_vol=true&include_24hr_change=true&include_last_updated_at=true&precision=0"
private const val urlCGReqBtcMarketCharts = "https://api.coingecko.com/api/v3/coins/bitcoin/market_chart?vs_currency=usd&days=29&interval=daily"
//private const val urlCGReqBtcCommunity = "https://api.coingecko.com/api/v3/coins/bitcoin?localization=false&tickers=false&market_data=false&community_data=true&developer_data=true&sparkline=false"

// Glass Node API categories and standard metrics
const val CATEGORY = "CTGY"
const val CTGY_ADDR = "addresses"
const val CTGY_BLKCHN = "blockchain"
const val CTGY_INDCTR = "indicators"
const val METRIC = "MTRC"
const val MTRC_STD_ADDR_ACT = "active_count"
const val MTRC_STD_SOPR = "sopr"
// Glass Node api url requests
private const val GN_API_KEY = "2IAEmfitRCvwMC16c1Qtrr61XXE"
private const val urlGNReqMetric= "https://api.glassnode.com/v1/metrics/$CATEGORY/$METRIC?a=btc&api_key=$GN_API_KEY"

class RequestUpdateWorker(private val appContext: Context, workerParams: WorkerParameters)
	: CoroutineWorker(appContext, workerParams) {

	// CoroutineWorker extras for Android versions < 12: for adding mandatory notification
	companion object {
		private const val NOTIFICATION_CHANNEL_ID = "11"
		private const val NOTIFICATION_CHANNEL_NAME = "Work Service"
	}
	override suspend fun getForegroundInfo(): ForegroundInfo {
		val notificationManager =
			appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				NOTIFICATION_CHANNEL_ID,
				NOTIFICATION_CHANNEL_NAME,
				NotificationManager.IMPORTANCE_HIGH
			)
			notificationManager.createNotificationChannel(channel)
		}
		val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
			.setContentIntent(PendingIntent.getActivity(appContext, 0, Intent(appContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
			.setSmallIcon(R.mipmap.ic_launcher)
			.setOngoing(true)
			.setAutoCancel(true)
			.setOnlyAlertOnce(true)
			.setPriority(NotificationCompat.PRIORITY_MIN)
			.setContentTitle(appContext.getString(R.string.app_name))
			.setLocalOnly(true)
			.setVisibility(NotificationCompat.VISIBILITY_SECRET)
			.setContentText(appContext.getString(R.string.updating_metrics))
			.build()
		return ForegroundInfo(1337, notification)
	}

	private var widgetId: Int = -1

	private val client = OkHttpClient()

	override suspend fun doWork(): Result {

		//get Input Data back using "inputData" variable
		val prefCurr =  inputData.getString(CURRENCY)
		widgetId = inputData.getInt(WIDGIT_ID, -1)

		if (prefCurr != null) {
			pingCoinGeckoCom(prefCurr)
			getReqGlassNodeMetrics()
		}

		return Result.success()
	}

	private fun pingCoinGeckoCom(prefCurrency: String) {
		val request = Request.Builder()
			.url(urlCGReqPing)
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				println("coingecko.com ping failed: ${e.message}")
				sendMainToast(appContext.getString(R.string.fail_contact_cg_server))
			}
			override fun onResponse(call: Call, response: Response) {
				if (response.body()?.string()?.contains("(V3) To the Moon!") == true) {
					println("coingecko.com ping echoed")
					getCoinGeckoCurrentPrice(prefCurrency)
				}
				else sendMainToast(appContext.getString(R.string.bad_cg_server_response))
			}
		})
	}

	fun getCoinGeckoCurrentPrice(prefCurrency: String) {

		//build correct url based on currency preference
		val cur = prefCurrency.lowercase()
		val url = urlCGReqBtcSimplePrice.replace("vs_currencies=usd","vs_currencies=$cur")

		val request = Request.Builder()
			.url(url)
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				println("coingecko.com bitcoin simple price request failed: ${e.message}")
			}
			override fun onResponse(call: Call, response: Response) {
				println("coingeck.com responded with simple price metrics")

				val jsonObj = parseCGJsonCurrentMarkets(response.body()!!.string())

				val price = jsonObj.getString(prefCurrency.lowercase())
				val volume = jsonObj.getString("${prefCurrency.lowercase()}_24h_vol")
				val marketCap = jsonObj.getString("${prefCurrency.lowercase()}_market_cap")
				val lastUpdate = jsonObj.getString("last_updated_at")

				val intent = Intent().apply {
					action = BROADCAST_CG_PRICE_UPDATED
					putExtra(WIDGIT_ID, widgetId)
					putExtra(CURR_PRICE, price)
					putExtra(CURR_DAY_VOLUME, volume)
					putExtra(CURR_MARKET_CAP, marketCap)
					putExtra(CURR_LAST_UPDATE, lastUpdate)
				}
				LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent)
				// close response body once done with it
				response.body()!!.close()

				val currData = mutableListOf(0f,0f,0f)
				currData[0] = price.toFloat()
				currData[1] = marketCap.toFloat()
				currData[2] = volume.toFloat()

				getCoinGeckoDailyMarketCharts(prefCurrency, currData)
			}
		})
	}

	fun getCoinGeckoDailyMarketCharts(prefCurrency: String, currData: List<Float>) {

		//build correct url based on currency preference
		val cur = prefCurrency.lowercase()
		val url = urlCGReqBtcMarketCharts.replace("vs_currency=usd","vs_currency=$cur")

		val request = Request.Builder()
			.url(url)
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				println("coingecko.com bitcoin market chart request failed: ${e.message}")
			}
			override fun onResponse(call: Call, response: Response) {
				println("coingeck.com responded with market charts")

				val charts = parseCGJsonMarketCharts(response.body()!!.string())

				// replace current day with most recently updated values
				charts.prices[charts.prices.lastIndex][1] = currData[0]
				charts.market_caps[charts.market_caps.lastIndex][1] = currData[1]
				charts.total_volumes[charts.total_volumes.lastIndex][1] = currData[2]

				// get deltas
				val priceDeltas = Calculator.getDeltas(charts.prices)
				val marketCapDeltas = Calculator.getDeltas(charts.market_caps)
				val volumeDeltas = Calculator.getDeltas(charts.total_volumes)
				//println("priceDeltas-> daily: ${priceDeltas[0]}, weekly: ${priceDeltas[1]}, monthly: ${priceDeltas[2]}")

				val intent = Intent().apply {
					action = BROADCAST_CG_MARKET_CHARTS_UPDATED
					putExtra(WIDGIT_ID, widgetId)
					putExtra(PRICE_DELTA_DAY, priceDeltas[1])
					putExtra(PRICE_DELTA_WEEK, priceDeltas[2])
					putExtra(PRICE_DELTA_MONTH, priceDeltas[3])
					putExtra(VOLUME_DELTA_DAY, volumeDeltas[1])
					putExtra(VOLUME_DELTA_WEEK, volumeDeltas[2])
					putExtra(VOLUME_DELTA_MONTH, volumeDeltas[3])
					putExtra(MARKET_CAP_DELTA_DAY, marketCapDeltas[1])
					putExtra(MARKET_CAP_DELTA_WEEK, marketCapDeltas[2])
					putExtra(MARKET_CAP_DELTA_MONTH, marketCapDeltas[3])
				}
				LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent)
				// close response body once done with it
				response.body()!!.close()
			}
		})
	}

	fun getReqGlassNodeMetrics() {
		// request active addresses
		val urlActvAddr = urlGNReqMetric.replace(CATEGORY, CTGY_ADDR).replace(METRIC, MTRC_STD_ADDR_ACT)
		val request = Request.Builder()
			.url(urlActvAddr)
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				println("api.glassnode.com request failed: ${e.message}")
				sendMainToast(appContext.getString(R.string.fail_contact_gn_server))
			}
			override fun onResponse(call: Call, response: Response) {
				println("api.glassnode.com responded with active addresses")

				// parse glassnode json response
				val listOfLists = response.body()?.let { parseGNJsonMetric(it.string()) }

				// get deltas
				val actAddrDeltas = listOfLists?.let { Calculator.getDeltas(it) }

				val intent = Intent().apply {
					action = BROADCAST_GN_METRICS_UPDATED
					putExtra(WIDGIT_ID, widgetId)
					// serialize deltas list and add to intent
					putExtra(MTRC_STD_ADDR_ACT, Gson().toJson(actAddrDeltas))
				}
				LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent)

				// close response body once done with it
				response.body()!!.close()
			}
		})
	}

	private fun sendMainToast(message: String) {
		val intent = Intent().apply {
			action = BROADCAST_SHOW_TOAST
			putExtra(WIDGIT_ID, widgetId)
			putExtra("message", message)
		}
		LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent)
	}
}

fun parseCGJsonCurrentMarkets(json: String): JSONObject {
	// get JSONObject from JSON string
	val obj = JSONObject(json)
	return obj.getJSONObject("bitcoin")
}

data class DailyCGCharts(var prices: List<MutableList<Number>>,
						 var market_caps: List<MutableList<Number>>,
						 var total_volumes: List<MutableList<Number>>) {}
fun parseCGJsonMarketCharts(json: String): DailyCGCharts {
	val typeToken = object : TypeToken<DailyCGCharts>() {}.type
	return Gson().fromJson(json, typeToken)
}

data class GNMetricMap(val t: Int, val v: Int) {}
fun parseGNJsonMetric(json: String): List<List<Number>> {
	val typeToken = object : TypeToken<Array<GNMetricMap>>() {}.type
	val arrayOfMaps: Array<GNMetricMap> = Gson().fromJson(json, typeToken)
	// convert glassnode data from <Array<GNMetricMap>> to List<List<Number>> format
	val listOfListOfNum = mutableListOf<List<Number>>()
	// fill list with last 30 days of data
	for ((i, map) in arrayOfMaps.withIndex().reversed()) {
		listOfListOfNum.add(listOf<Number>(map.t, map.v))
		if (i <= arrayOfMaps.size - 30) break
	} // return reverse ordered list
	return listOfListOfNum.reversed()
}

// Formatting functions
fun numberToCurrency(number: String?, prefCurrency: String): String {
	if (number == "…") return "…"
	val int = stringToInt(number)
	val format: NumberFormat = NumberFormat.getCurrencyInstance()
	format.maximumFractionDigits = 0
	format.currency = Currency.getInstance(prefCurrency)
	return format.format(int)
}

fun stringToInt (str: String?): Int {
	if (str == "-1") return -1
	val digits = str?.filter { it.isDigit() }
	//println("converted $currency to $digits")
	if (digits.isNullOrBlank()) return -1
	return digits.toInt()
}

fun prettyBigNumber(str: String): String? {
	if (str == "…") return "…"
	val number = str.toDouble()
	val suffix = charArrayOf(' ', 'k', 'M', 'B', 'T', 'P', 'E')
	val numValue = number.toLong()
	val value = floor(log10(numValue.toDouble())).toInt()
	val base = value / 3
	return if (value >= 3 && base < suffix.size) {
		DecimalFormat("#0.00").format(
			numValue / 10.0.pow((base * 3).toDouble())
		) + suffix[base]
	} else {
		DecimalFormat("#,##0").format(numValue)
	}
}
