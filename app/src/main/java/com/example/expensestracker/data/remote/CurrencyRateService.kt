package com.example.expensestracker.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches live exchange rates from the free, keyless Frankfurter API (ECB reference rates).
 * Returns a map of currency code -> value of 1 unit of that currency expressed in EUR.
 * Callers should fall back to locally stored/manual rates if this fails (no network, etc.).
 */
class CurrencyRateService {
    suspend fun fetchRatesInEur(codes: List<String>): Result<Map<String, Double>> = withContext(Dispatchers.IO) {
        runCatching {
            val targets = codes.filter { it != "EUR" }
            if (targets.isEmpty()) return@runCatching mapOf("EUR" to 1.0)

            val url = URL("https://api.frankfurter.app/latest?from=EUR&to=${targets.joinToString(",")}")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"

            val body = try {
                if (connection.responseCode !in 200..299) {
                    error("HTTP ${connection.responseCode}")
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }

            val json = JSONObject(body)
            val rates = json.getJSONObject("rates")
            val result = mutableMapOf("EUR" to 1.0)
            rates.keys().forEach { code ->
                val eurPerUnit = rates.getDouble(code).let { if (it != 0.0) 1.0 / it else 0.0 }
                if (eurPerUnit > 0.0) result[code] = eurPerUnit
            }
            result
        }
    }
}
