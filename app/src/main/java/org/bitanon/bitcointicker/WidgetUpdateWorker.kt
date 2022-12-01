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

private const val urlCGReqPing = "https://api.coingecko.com/api/v3/ping"
private const val urlCGReqBtcPrice = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_market_cap=true&include_24hr_vol=true&include_24hr_change=true&include_last_updated_at=true&precision=0"

class WidgetUpdateWorker(private val appContext: Context, workerParams: WorkerParameters)
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
		val prefCurr =  inputData.getString("pref_curr")
		widgetId = inputData.getInt("widget_id", -1)

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
					getBitcoinPrice(prefCurrency)
				}
				else sendMainToast(appContext.getString(R.string.bad_server_response))
			}
		})
	}

	fun getBitcoinPrice(prefCurrency: String) {

		//build correct url based on currency preference
		val cur = prefCurrency.lowercase()
		val url = urlCGReqBtcPrice.replace("vs_currencies=usd","vs_currencies=$cur")

		val request = Request.Builder()
			.url(url)
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				println("coingecko.com bitcoin price request failed: ${e.message}")
			}
			override fun onResponse(call: Call, response: Response) {
				println("coingeck.com responded")

				val jsonObj = parseJson(response.body()!!.string())

				val price = numberToCurrency(jsonObj.getString(prefCurrency.lowercase()), prefCurrency)
				val marketCap = prettyNumber(jsonObj.getString("${prefCurrency.lowercase()}_market_cap").toDouble())
				val dayVolume = prettyNumber(jsonObj.getString("${prefCurrency.lowercase()}_24h_vol").toDouble())
				val dayChange = jsonObj.getString("${prefCurrency.lowercase()}_24h_change")
				val lastUpdate = jsonObj.getString("last_updated_at")

				Intent().also { intent ->
					intent.action = BROADCAST_PRICE_UPDATED
					intent.putExtra("widget_id", widgetId)
					intent.putExtra("price", price)
					intent.putExtra("day_change", dayChange)
					intent.putExtra("market_cap", marketCap)
					intent.putExtra("day_volume", dayVolume)
					intent.putExtra("last_update", lastUpdate)
					LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent)
				}

				// close response body once done with it
				response.body()!!.close()
			}
		})
	}

	private fun sendMainToast(message: String) {
		val intent = Intent().apply {
			action = BROADCAST_SHOW_TOAST
			putExtra("message", message)
		}
		LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent)
	}
}

fun parseJson(jsonString: String): JSONObject {
	// get JSONObject from JSON file
	val obj = JSONObject(jsonString)
	return obj.getJSONObject("bitcoin")
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

fun prettyNumber(number: Number): String? {
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