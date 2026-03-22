package com.freekiosk

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Hub-specific app whitelist manager.
 * Stores whitelist received from the Hub via fieldtrip group binding.
 * If no Hub config exists (no group_id), all apps are allowed.
 */
class AppWhitelistManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "HubConfig"
        private const val KEY_APP_WHITELIST = "app_whitelist"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Check if an app is allowed to launch based on Hub whitelist.
     * Returns true (allow all) if no Hub config / whitelist is present.
     */
    fun isAppAllowed(packageName: String): Boolean {
        val whitelistStr = prefs.getString(KEY_APP_WHITELIST, null) ?: return true // allow all if no hub config

        return try {
            val json = JSONObject(whitelistStr)
            val whitelist = json.optJSONArray("whitelist") ?: return true
            val packages = (0 until whitelist.length()).map { whitelist.getString(it) }
            packageName in packages
        } catch (e: Exception) {
            true // allow on parse error
        }
    }

    /**
     * Store the Hub app whitelist.
     */
    fun setWhitelist(packages: List<String>) {
        val json = JSONObject().put("whitelist", org.json.JSONArray(packages))
        prefs.edit().putString(KEY_APP_WHITELIST, json.toString()).apply()
    }

    /**
     * Get the stored Hub whitelist, or empty list if none.
     */
    fun getWhitelist(): List<String> {
        val whitelistStr = prefs.getString(KEY_APP_WHITELIST, null) ?: return emptyList()
        return try {
            val json = JSONObject(whitelistStr)
            val whitelist = json.optJSONArray("whitelist") ?: return emptyList()
            (0 until whitelist.length()).map { whitelist.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
