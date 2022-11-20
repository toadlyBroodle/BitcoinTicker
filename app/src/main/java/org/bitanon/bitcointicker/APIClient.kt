package org.bitanon.bitcointicker

import android.content.SharedPreferences
import okhttp3.*
import java.io.IOException
import java.text.NumberFormat
import java.util.*

private const val urlCGReqPing = "https://api.coingecko.com/api/v3/ping"
private const val urlCGReqBtcPrice = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_market_cap=false&include_24hr_vol=false&include_24hr_change=true&include_last_updated_at=true"

class APIClient {

	private lateinit var mainActivity: MainActivity
	private val client = OkHttpClient()
	var lastRealBtcPrice: String? = null
	private var lastReqTime: Long? = 0

	fun init(activity: MainActivity, sharedPrefs: SharedPreferences): APIClient {
		mainActivity = activity
		lastRealBtcPrice = sharedPrefs.getString(
			R.string.last_real_btc_price.toString(), (-1f).toString()
		)
		println("prefLastRealBtcPrice=$lastRealBtcPrice")
		return this
	}

	fun pingCoinGeckoCom() {
		val request = Request.Builder()
			.url(urlCGReqPing)
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				println("coingecko.com ping failed")
				mainActivity.showToast(R.string.fail_contact_server)
			}
			override fun onResponse(call: Call, response: Response) {
				if (response.body()?.string()?.contains("(V3) To the Moon!") == true)
					println("coingecko.com echoed ping")
				else println("coingecko.com did NOT echo ping")
			}
		})
	}

	fun getBitcoinPrice(prefCurrency: String) {
		var price: String? = null
		//build correct url based on currency preference
		val cur = prefCurrency.lowercase()
		val url = urlCGReqBtcPrice.replace("vs_currencies=usd","vs_currencies=$cur")
		//println("url=$url")

		//if last one was less than 2m ago, update last real price with random price jitter
		if (System.currentTimeMillis() - lastReqTime!! <= 120000) {
			val rand = (-999..999).random() / 100.00f
			val jitter = lastRealBtcPrice!!.toFloat() + rand
			println("price jitter: $jitter")
			mainActivity.updateUI(price + jitter)
		}

		val request = Request.Builder()
			.url(url)
			.build()

		client.newCall(request).enqueue(object : Callback {
			override fun onFailure(call: Call, e: IOException) {
				println("coingecko.com bitcoin price request failed")
				price = null
			}
			override fun onResponse(call: Call, response: Response) {
				val parsedResponse = response.body()?.string()?.substringAfter("$cur\":","")?.substringBefore(",","")
				// return if string is empty
				if (parsedResponse?.isEmpty() == true) {
					price = null
					println("coingecko.com bitcoin price request return empty string")
					return
				}

				price = floatToCurrency(parsedResponse, prefCurrency)
				println("updated real price to $price")

				mainActivity.updateUI(price)
			}
		})
		lastReqTime = System.currentTimeMillis()
	}
}

fun floatToCurrency(float: String?, prefCurrency: String): String {
	val format: NumberFormat = NumberFormat.getCurrencyInstance()
	format.maximumFractionDigits = 2
	format.currency = Currency.getInstance(prefCurrency)
	return format.format(float)
}

fun currencyToFloat (currency: String): Float {
	val digits = currency.filter { it.isDigit() }
	return digits.toFloat()
}