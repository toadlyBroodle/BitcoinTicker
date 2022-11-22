package org.bitanon.bitcointicker

import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.NumberFormat
import java.util.*

private const val urlCGReqPing = "https://api.coingecko.com/api/v3/ping"
private const val urlCGReqBtcPrice = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_market_cap=true&include_24hr_vol=true&include_24hr_change=true&include_last_updated_at=true&precision=0"

class APIClient {

	private var mainActivity: MainActivity? = null
	private var widgetActivity: AppWidgetConfigureActivity? = null
	private val client = OkHttpClient()
	private var lastReqTime: Long = 0

	fun init(mainActiv: MainActivity?, widgetActiv: AppWidgetConfigureActivity?): APIClient {
		mainActivity = mainActiv
		widgetActivity = widgetActiv
		return this
	}

	fun pingCoinGeckoCom(prefCurrency: String) {
		val request = Request.Builder()
			.url(urlCGReqPing)
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				println("coingecko.com ping failed")
				mainActivity?.showToast(R.string.fail_contact_server)
			}
			override fun onResponse(call: Call, response: Response) {
				if (response.body()?.string()?.contains("(V3) To the Moon!") == true) {
					println("coingecko.com echoed ping")
					getBitcoinPrice(prefCurrency)
				}
				else println("coingecko.com did NOT echo ping")
			}
		})
	}

	fun getBitcoinPrice(prefCurrency: String) {
		var price: String
		//build correct url based on currency preference
		val cur = prefCurrency.lowercase()
		val url = urlCGReqBtcPrice.replace("vs_currencies=usd","vs_currencies=$cur")
		//println("url=$url")

		//if last one was less than 2m ago, update last real price with random price jitter
		if (System.currentTimeMillis() - lastReqTime <= 120000) {
			val rand = (-9..9).random()
			val jitteredPrice = mainActivity?.lastRealBtcPrice?.toInt()?.plus(rand).toString()
			println("jittered price: $jitteredPrice")
			mainActivity?.updateUI(numberToCurrency(jitteredPrice, prefCurrency))
			return
		}

		val request = Request.Builder()
			.url(url)
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				println("coingecko.com bitcoin price request failed")
			}
			override fun onResponse(call: Call, response: Response) {
				//OLD val parsedResponse = response.body()?.string()?.substringAfter("$cur\":","")?.substringBefore(",","")
				val jsonObj = parseJson(response.body()!!.string())

				price = numberToCurrency(jsonObj.getString(prefCurrency.lowercase()), prefCurrency)

				if (mainActivity != null) {
					//save last real btc price to preferences to avoid null pointer exception
					//if server cannot be reached on next server request
					val priceInt = stringToInt(price)
					mainActivity?.sharedPrefs?.edit()?.putInt(PREF_LAST_REAL_BTC_PRICE, priceInt
					)?.apply()
					mainActivity?.lastRealBtcPrice = priceInt.toString()
					println("saved last real price pref:$priceInt")

					mainActivity?.updateUI(price)
				}
				if (widgetActivity != null) {
					//save price to widget preferences
					widgetActivity?.savePrefPriceUpdate(price)
				}
			}
		})
		lastReqTime = System.currentTimeMillis()
	}
}

fun numberToCurrency(number: String?, prefCurrency: String): String {
	val int = stringToInt(number)
	val format: NumberFormat = NumberFormat.getCurrencyInstance()
	format.maximumFractionDigits = 0
	format.currency = Currency.getInstance(prefCurrency)
	return format.format(int)
}

fun stringToInt (str: String?): Int {
	val digits = str?.filter { it.isDigit() }
	//println("converted $currency to $digits")
	if (digits.isNullOrBlank()) return -1
	return digits.toInt()
}

/*fun intToString (int: Int?): String {

}*/

fun parseJson (jsonString: String): JSONObject {
	// get JSONObject from JSON file
	val obj = JSONObject(jsonString)
	val bitcoin: JSONObject = obj.getJSONObject("bitcoin")
	return bitcoin
}