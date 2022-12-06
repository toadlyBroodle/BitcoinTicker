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

const val BROADCAST_SHOW_TOAST = "org.bitanon.bitcointicker.BROADCAST_SHOW_TOAST"
const val BROADCAST_PRICE_UPDATED = "org.bitanon.bitcointicker.BROADCAST_PRICE_UPDATED"
const val BROADCAST_MARKET_CHARTS_UPDATED = "org.bitanon.bitcointicker.BROADCAST_MARKET_CHARTS_UPDATED"
const val CURRENCY = "PREF_CURRENCY"
const val MESSAGE = "MESSAGE"
const val CURR_PRICE = "CURR_PRICE"
const val CURR_DAY_VOLUME = "CURR_DAY_VOLUME"
const val CURR_MARKET_CAP = "CURR_MARKET_CAP"
const val CURR_LAST_UPDATE = "CURR_LAST_UPDATE"
const val WIDGIT_ID = "WIDGET_ID"
const val PRICE_DELTA_DAY = "PRICE_DELTA_DAY"
const val PRICE_DELTA_WEEK = "PRICE_DELTA_WEEK"
const val PRICE_DELTA_MONTH = "PRICE_DELTA_MONTH"
const val VOLUME_DELTA_DAY = "VOLUME_DELTA_DAY"
const val VOLUME_DELTA_WEEK = "VOLUME_DELTA_WEEK"
const val VOLUME_DELTA_MONTH = "VOLUME_DELTA_MONTH"
const val MARKET_CAP_DELTA_DAY = "MARKET_CAP_DELTA_DAY"
const val MARKET_CAP_DELTA_WEEK = "MARKET_CAP_DELTA_WEEK"
const val MARKET_CAP_DELTA_MONTH = "MARKET_CAP_DELTA_MONTH"

private const val urlCGReqPing = "https://api.coingecko.com/api/v3/ping"
private const val urlCGReqBtcSimplePrice = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_market_cap=true&include_24hr_vol=true&include_24hr_change=true&include_last_updated_at=true&precision=0"
//private const val urlCGReqBtcCommunity = "https://api.coingecko.com/api/v3/coins/bitcoin?localization=false&tickers=false&market_data=false&community_data=true&developer_data=true&sparkline=false"
private const val urlCGReqBtcMarketCharts = "https://api.coingecko.com/api/v3/coins/bitcoin/market_chart?vs_currency=usd&days=29&interval=daily"

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
		}

		return Result.success()
	}

	private fun pingCoinGeckoCom(prefCurrency: String) {
		val request = Request.Builder()
			.url(urlCGReqPing)
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				println("ping failed: ${e.message}")
				sendMainToast(appContext.getString(R.string.fail_contact_server))
			}
			override fun onResponse(call: Call, response: Response) {
				if (response.body()?.string()?.contains("(V3) To the Moon!") == true) {
					println("ping echoed")
					getCoinGeckoCurrentPrice(prefCurrency)
				}
				else sendMainToast(appContext.getString(R.string.bad_server_response))
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
				println("coingeck.com responded with simple price")

				val jsonObj = parseJsonCurrentMarkets(response.body()!!.string())

				val price = jsonObj.getString(prefCurrency.lowercase())
				val volume = jsonObj.getString("${prefCurrency.lowercase()}_24h_vol")
				val marketCap = jsonObj.getString("${prefCurrency.lowercase()}_market_cap")
				val lastUpdate = jsonObj.getString("last_updated_at")

				val intent = Intent().apply {
					action = BROADCAST_PRICE_UPDATED
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

	fun getCoinGeckoDailyMarketCharts(prefCurrency: String, markets: List<Float>) {

		//build correct url based on currency preference
		val cur = prefCurrency.lowercase()
		val url = urlCGReqBtcMarketCharts.replace("vs_currencies=usd","vs_currencies=$cur")

		val request = Request.Builder()
			.url(url)
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				println("coingecko.com bitcoin market chart request failed: ${e.message}")
			}
			override fun onResponse(call: Call, response: Response) {
				println("coingeck.com responded with market charts")

				val charts = parseJsonMarketCharts(response.body()!!.string())

				// get deltas
				val priceDeltas = Calculator.getDeltas(charts.prices, markets[0])
				val volumeDeltas = Calculator.getDeltas(charts.total_volumes, markets[2])
				val marketCapDeltas = Calculator.getDeltas(charts.market_caps, markets[1])
				//println("priceDeltas-> daily: ${priceDeltas[0]}, weekly: ${priceDeltas[1]}, monthly: ${priceDeltas[2]}")

				val intent = Intent().apply {
					action = BROADCAST_MARKET_CHARTS_UPDATED
					putExtra(WIDGIT_ID, widgetId)
					putExtra(PRICE_DELTA_DAY, priceDeltas[0])
					putExtra(PRICE_DELTA_WEEK, priceDeltas[1])
					putExtra(PRICE_DELTA_MONTH, priceDeltas[2])
					putExtra(VOLUME_DELTA_DAY, volumeDeltas[0])
					putExtra(VOLUME_DELTA_WEEK, volumeDeltas[1])
					putExtra(VOLUME_DELTA_MONTH, volumeDeltas[2])
					putExtra(MARKET_CAP_DELTA_DAY, marketCapDeltas[0])
					putExtra(MARKET_CAP_DELTA_WEEK, marketCapDeltas[1])
					putExtra(MARKET_CAP_DELTA_MONTH, marketCapDeltas[2])
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

fun parseJsonCurrentMarkets(json: String): JSONObject {
	// get JSONObject from JSON file
	val obj = JSONObject(json)
	return obj.getJSONObject("bitcoin")
}

fun parseJsonMarketCharts(json: String): DailyCGCharts {
	val typeToken = object : TypeToken<DailyCGCharts>() {}.type
	return Gson().fromJson(json, typeToken)
}

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

data class DailyCGCharts(var prices: List<List<Number>>,
						 var market_caps: List<List<Number>>,
						 var total_volumes: List<List<Number>>) {}
