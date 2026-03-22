package com.freekiosk

import android.content.SharedPreferences
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HubConfigModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

    companion object {
        private const val NAME = "HubConfigModule"
        private const val PREFS_NAME = "HubConfig"
        private const val KEY_HUB_URL = "hub_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_GROUP_ID = "group_id"
        private const val KEY_SIGNING_PUBKEY = "signing_pubkey"
        private const val KEY_BROADCAST_SOUND = "broadcast_sound"
        private const val KEY_UPDATE_POLICY = "update_policy"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var prefs: SharedPreferences? = null
    private var fusedLocationClient = LocationServices.getFusedLocationProviderClient(reactContext)
    private var gpsReportingJob: Job? = null
    private var gpsIntervalSeconds = 30
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        prefs = reactContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        reactContext.addLifecycleEventListener(this)
    }

    override fun getName(): String = NAME

    @ReactMethod
    fun getConfig(promise: Promise) {
        try {
            val hubUrl = prefs?.getString(KEY_HUB_URL, "") ?: ""
            val deviceId = prefs?.getString(KEY_DEVICE_ID, "") ?: ""
            val groupId = prefs?.getString(KEY_GROUP_ID, "") ?: ""

            if (hubUrl.isEmpty() || deviceId.isEmpty()) {
                promise.resolve(null)
                return
            }

            val result = Arguments.createMap().apply {
                putString("hubUrl", hubUrl)
                putString("deviceId", deviceId)
                putString("groupId", groupId)
            }
            promise.resolve(result)
        } catch (e: Exception) {
            android.util.Log.e(NAME, "Failed to get config: ${e.message}")
            promise.reject("ERROR", "Failed to get config: ${e.message}")
        }
    }

    @ReactMethod
    fun bindWithQrPayload(qrData: String, promise: Promise) {
        scope.launch {
            try {
                android.util.Log.d(NAME, "Parsing QR payload: $qrData")

                // Parse QR JSON payload
                val json = JSONObject(qrData)
                val deviceId = json.getString("device_id")
                val groupKey = json.getString("group_key")
                val apiKey = json.getString("api_key")
                val hubUrl = json.getString("hub_url")

                // Store required config
                prefs?.edit()
                    ?.putString(KEY_HUB_URL, hubUrl)
                    ?.putString(KEY_DEVICE_ID, deviceId)
                    ?.putString(KEY_API_KEY, apiKey)
                    ?.apply()

                // Make bind request to hub
                val bindUrl = "$hubUrl/api/v2/fieldtrip/devices/bind"
                val requestBody = JSONObject().apply {
                    put("device_id", deviceId)
                    put("group_key", groupKey)
                    put("api_key", apiKey)
                }.toString()

                val response = makeHttpRequest(bindUrl, "POST", requestBody, null)

                if (response != null) {
                    val responseJson = JSONObject(response)
                    val groupId = responseJson.optString("group_id", "")
                    val signingPubkey = responseJson.optString("signing_pubkey", "")
                    val broadcastSound = responseJson.optString("broadcast_sound", "default")
                    val updatePolicy = responseJson.optString("update_policy", "auto")

                    // Store full config
                    prefs?.edit()
                        ?.putString(KEY_GROUP_ID, groupId)
                        ?.putString(KEY_SIGNING_PUBKEY, signingPubkey)
                        ?.putString(KEY_BROADCAST_SOUND, broadcastSound)
                        ?.putString(KEY_UPDATE_POLICY, updatePolicy)
                        ?.apply()

                    val result = Arguments.createMap().apply {
                        putString("deviceId", deviceId)
                        putString("groupId", groupId)
                        putString("hubUrl", hubUrl)
                        putString("signingPubkey", signingPubkey)
                        putString("broadcastSound", broadcastSound)
                        putString("updatePolicy", updatePolicy)
                    }

                    withContext(Dispatchers.Main) {
                        promise.resolve(result)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        promise.reject("BIND_FAILED", "Failed to bind to hub: no response")
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e(NAME, "Bind failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    promise.reject("BIND_ERROR", "Bind failed: ${e.message}")
                }
            }
        }
    }

    @ReactMethod
    fun startGpsReporting(intervalSeconds: Int, promise: Promise) {
        if (intervalSeconds <= 0) {
            promise.reject("INVALID_INTERVAL", "Interval must be positive")
            return
        }

        gpsIntervalSeconds = intervalSeconds

        // Check location permission
        val hasPermission = ContextCompat.checkSelfPermission(
                reactContext,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            promise.reject("PERMISSION_DENIED", "Location permission is required for GPS reporting")
            return
        }

        // Cancel any existing job
        gpsReportingJob?.cancel()

        gpsReportingJob = scope.launch {
            while (isActive) {
                try {
                    reportLocation()
                } catch (e: Exception) {
                    android.util.Log.e(NAME, "GPS report failed: ${e.message}")
                }
                delay(intervalSeconds * 1000L)
            }
        }

        android.util.Log.d(NAME, "GPS reporting started with interval: ${intervalSeconds}s")
        promise.resolve(true)
    }

    @ReactMethod
    fun stopGpsReporting(promise: Promise) {
        gpsReportingJob?.cancel()
        gpsReportingJob = null
        android.util.Log.d(NAME, "GPS reporting stopped")
        promise.resolve(true)
    }

    private suspend fun reportLocation() {
        val hubUrl = prefs?.getString(KEY_HUB_URL, null) ?: return
        val deviceId = prefs?.getString(KEY_DEVICE_ID, null) ?: return
        val apiKey = prefs?.getString(KEY_API_KEY, null) ?: return

        if (hubUrl.isEmpty() || deviceId.isEmpty() || apiKey.isEmpty()) {
            android.util.Log.w(NAME, "Hub config not set, skipping GPS report")
            return
        }

        try {
            val location: Location? = withContext(Dispatchers.Main) {
                try {
                    Tasks.await(fusedLocationClient.lastLocation)
                } catch (e: Exception) {
                    android.util.Log.e(NAME, "Failed to get location: ${e.message}")
                    null
                }
            }

            if (location == null) {
                android.util.Log.w(NAME, "No location available")
                return
            }

            val reportUrl = "$hubUrl/api/v2/fieldtrip/devices/$deviceId/location"
            val body = JSONObject().apply {
                put("lat", location.latitude)
                put("lng", location.longitude)
                put("accuracy", location.accuracy.toDouble())
                put("timestamp", System.currentTimeMillis())
            }.toString()

            val response = makeHttpRequest(reportUrl, "POST", body, apiKey)

            if (response != null) {
                android.util.Log.d(NAME, "GPS reported: ${location.latitude}, ${location.longitude}")
            } else {
                android.util.Log.w(NAME, "GPS report failed: no response")
            }

        } catch (e: Exception) {
            android.util.Log.e(NAME, "Error reporting location: ${e.message}")
        }
    }

    @ReactMethod
    fun pollCommands(promise: Promise) {
        scope.launch {
            try {
                val hubUrl = prefs?.getString(KEY_HUB_URL, null) ?: run {
                    withContext(Dispatchers.Main) {
                        promise.reject("NOT_CONFIGURED", "Hub is not configured")
                    }
                    return@launch
                }
                val deviceId = prefs?.getString(KEY_DEVICE_ID, null) ?: run {
                    withContext(Dispatchers.Main) {
                        promise.reject("NOT_CONFIGURED", "Device ID not set")
                    }
                    return@launch
                }

                val pollUrl = "$hubUrl/api/v2/fieldtrip/commands?device_id=$deviceId"
                val apiKey = prefs?.getString(KEY_API_KEY, null)

                val response = makeHttpRequest(pollUrl, "GET", null, apiKey)

                if (response != null) {
                    val commandsJson = JSONObject(response)
                    val commands = commandsJson.optJSONArray("commands") ?: org.json.JSONArray()

                    val result = Arguments.createArray()
                    for (i in 0 until commands.length()) {
                        val cmd = commands.getJSONObject(i)
                        val cmdMap = Arguments.createMap().apply {
                            putString("id", cmd.optString("id", ""))
                            putString("action", cmd.optString("action", ""))
                            putString("params", cmd.optString("params", "{}"))
                            putDouble("timestamp", cmd.optDouble("timestamp", 0.0))
                        }
                        result.pushMap(cmdMap)
                    }

                    withContext(Dispatchers.Main) {
                        promise.resolve(result)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        promise.reject("POLL_FAILED", "Failed to poll commands")
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e(NAME, "Poll commands failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    promise.reject("ERROR", "Poll failed: ${e.message}")
                }
            }
        }
    }

    private fun makeHttpRequest(urlString: String, method: String, body: String?, apiKey: String?): String? {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")

            if (apiKey != null) {
                conn.setRequestProperty("X-Api-Key", apiKey)
            }

            if (body != null && (method == "POST" || method == "PUT")) {
                conn.doOutput = true
                conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
            }

            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                android.util.Log.w(NAME, "HTTP $responseCode: ${conn.errorStream?.bufferedReader()?.readText()}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e(NAME, "HTTP request failed: ${e.message}")
            null
        }
    }

    // LifecycleEventListener implementation
    override fun onHostResume() {
        // Resume GPS reporting if it was active
        android.util.Log.d(NAME, "Activity resumed")
    }

    override fun onHostPause() {
        // Pause GPS reporting when app is paused
        android.util.Log.d(NAME, "Activity paused")
    }

    override fun onHostDestroy() {
        // Stop GPS reporting on destroy
        gpsReportingJob?.cancel()
        gpsReportingJob = null
        scope.cancel()
        android.util.Log.d(NAME, "Module destroyed, GPS reporting stopped")
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        reactContext.removeLifecycleEventListener(this)
        gpsReportingJob?.cancel()
        gpsReportingJob = null
        scope.cancel()
    }
}
