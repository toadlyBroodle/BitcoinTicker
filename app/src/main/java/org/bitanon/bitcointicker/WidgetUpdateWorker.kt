package org.bitanon.bitcointicker

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.NumberFormat
import java.util.*

const val BROADCAST_SHOW_TOAST = "org.bitanon.bitcointicker.BROADCAST_SHOW_TOAST"
const val BROADCAST_PRICE_UPDATED = "org.bitanon.bitcointicker.BROADCAST_PRICE_UPDATED"

private const val urlCGReqPing = "https://api.coingecko.com/api/v3/ping"
private const val urlCGReqBtcPrice = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_market_cap=true&include_24hr_vol=true&include_24hr_change=true&include_last_updated_at=true&precision=0"

class WidgetUpdateWorker(private val appContext: Context, workerParams: WorkerParameters)
	: Worker(appContext, workerParams) {

	private var widgetId: Int = -1

	private val client = OkHttpClient()

	override fun doWork(): Result {

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
		var price: String
		var dayChange: String
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
				//OLD val parsedResponse = response.body()?.string()?.substringAfter("$cur\":","")?.substringBefore(",","")
				val jsonObj = parseJson(response.body()!!.string())

				price = numberToCurrency(jsonObj.getString(prefCurrency.lowercase()), prefCurrency)
				dayChange = jsonObj.getString("${prefCurrency.lowercase()}_24h_change")

				Intent().also { intent ->
					intent.action = BROADCAST_PRICE_UPDATED
					intent.putExtra("widget_id", widgetId)
					intent.putExtra("price", price)
					intent.putExtra("day_change", dayChange)
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