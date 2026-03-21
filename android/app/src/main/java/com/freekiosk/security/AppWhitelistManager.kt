package com.freekiosk.security

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 应用白名单管理器
 *
 * 负责管理和执行应用白名单策略，防止未授权应用的安装和启动。
 */
class AppWhitelistManager(private val context: Context) {

    companion object {
        private const val TAG = "AppWhitelistManager"
        private const val PREFS_NAME = "app_whitelist_prefs"
        private const val KEY_WHITELIST = "whitelist"
        private const val KEY_ENABLED = "whitelist_enabled"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 白名单条目
     */
    data class WhitelistEntry(
        val packageName: String,
        val appName: String,
        val autoLaunch: Boolean = false,
        val allowNotifications: Boolean = false,
        val defaultShortcut: Boolean = false
    ) {
        companion object {
            fun fromJson(json: JSONObject): WhitelistEntry {
                return WhitelistEntry(
                    packageName = json.optString("package_name", ""),
                    appName = json.optString("app_name", ""),
                    autoLaunch = json.optBoolean("auto_launch", false),
                    allowNotifications = json.optBoolean("allow_notifications", false),
                    defaultShortcut = json.optBoolean("default_shortcut", false)
                )
            }
        }

        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("package_name", packageName)
                put("app_name", appName)
                put("auto_launch", autoLaunch)
                put("allow_notifications", allowNotifications)
                put("default_shortcut", defaultShortcut)
            }
        }
    }

    /**
     * 设置白名单
     */
    fun setWhitelist(entries: List<WhitelistEntry>) {
        val jsonArray = JSONArray()
        entries.forEach { entry ->
            jsonArray.put(entry.toJson())
        }

        prefs.edit()
            .putString(KEY_WHITELIST, jsonArray.toString())
            .apply()

        Log.i(TAG, "Whitelist updated with ${entries.size} entries")
    }

    /**
     * 获取白名单
     */
    fun getWhitelist(): List<WhitelistEntry> {
        val json = prefs.getString(KEY_WHITELIST, "[]") ?: "[]"
        val entries = mutableListOf<WhitelistEntry>()

        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                entries.add(WhitelistEntry.fromJson(jsonArray.getJSONObject(i)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse whitelist", e)
        }

        return entries
    }

    /**
     * 设置白名单是否启用
     */
    fun setWhitelistEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        Log.i(TAG, "Whitelist enforcement: $enabled")
    }

    /**
     * 检查白名单是否启用
     */
    fun isWhitelistEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLED, false)
    }

    /**
     * 检查应用是否在白名单中
     */
    fun isAppAllowed(packageName: String): Boolean {
        if (!isWhitelistEnabled()) {
            return true
        }

        val whitelist = getWhitelist()
        return whitelist.any { it.packageName == packageName }
    }

    /**
     * 获取所有已安装应用的白名单状态
     */
    fun getInstalledAppsWithStatus(): List<Pair<String, Boolean>> {
        val whitelist = getWhitelist()
        val allowedPackages = whitelist.map { it.packageName }.toSet()
        val result = mutableListOf<Pair<String, Boolean>>()

        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_ACTIVITIES)
        for (info in resolveInfos) {
            val packageName = info.activityInfo.packageName
            result.add(packageName to (packageName in allowedPackages))
        }

        return result
    }

    /**
     * 获取不在白名单中的应用列表
     */
    fun getBlockedApps(): List<String> {
        val whitelist = getWhitelist()
        val allowedPackages = whitelist.map { it.packageName }.toSet()
        val blocked = mutableListOf<String>()

        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_ACTIVITIES)
        for (info in resolveInfos) {
            val packageName = info.activityInfo.packageName
            if (packageName !in allowedPackages && packageName != context.packageName) {
                blocked.add(packageName)
            }
        }

        return blocked
    }

    /**
     * 添加应用到白名单
     */
    fun addToWhitelist(entry: WhitelistEntry) {
        val current = getWhitelist().toMutableList()
        // 检查是否已存在
        current.removeAll { it.packageName == entry.packageName }
        current.add(entry)
        setWhitelist(current)
        Log.i(TAG, "Added ${entry.packageName} to whitelist")
    }

    /**
     * 从白名单移除应用
     */
    fun removeFromWhitelist(packageName: String) {
        val current = getWhitelist().toMutableList()
        current.removeAll { it.packageName == packageName }
        setWhitelist(current)
        Log.i(TAG, "Removed $packageName from whitelist")
    }

    /**
     * 更新白名单中的应用
     */
    fun updateInWhitelist(entry: WhitelistEntry) {
        val current = getWhitelist().toMutableList()
        val index = current.indexOfFirst { it.packageName == entry.packageName }
        if (index >= 0) {
            current[index] = entry
            setWhitelist(current)
            Log.i(TAG, "Updated ${entry.packageName} in whitelist")
        }
    }

    /**
     * 获取需要自启动的应用列表
     */
    fun getAutoLaunchApps(): List<WhitelistEntry> {
        return getWhitelist().filter { it.autoLaunch }
    }

    /**
     * 验证应用签名
     *
     * 使用 PackageManager.GET_SIGNING_CERTIFICATES (API 28+) 进行签名验证。
     * 如果设备 API 版本低于 28，则回退到已废弃的 GET_SIGNATURES。
     */
    @Suppress("DEPRECATION")
    fun verifyAppSignature(packageName: String, expectedHash: String): Boolean {
        return try {
            val pm = context.packageManager

            val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                packageInfo.signingInfo?.signingCertificateHistory
            } else {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signatures != null) {
                for (signature in signatures) {
                    val md = MessageDigest.getInstance("SHA-256")
                    val hash = md.digest(signature.toByteArray()).joinToString("") {
                        "%02x".format(it)
                    }
                    if (hash == expectedHash) {
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify app signature", e)
            false
        }
    }

    /**
     * 检查应用是否已安装
     */
    fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 启动应用（如果允许）
     *
     * @param packageName 要启动的应用包名
     * @return 是否成功启动
     */
    fun launchAppIfAllowed(packageName: String): Boolean {
        if (!isAppAllowed(packageName)) {
            Log.w(TAG, "Launch blocked: $packageName not in whitelist")
            return false
        }

        // 检查应用是否已安装
        if (!isAppInstalled(packageName)) {
            Log.w(TAG, "App not installed: $packageName")
            return false
        }

        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.i(TAG, "App launched: $packageName")
                true
            } else {
                Log.e(TAG, "No launch intent for $packageName (may not have launcher activity)")
                false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception launching $packageName: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $packageName", e)
            false
        }
    }

    /**
     * 解析从服务器接收的策略配置
     */
    fun parsePolicyFromJson(policyJson: JSONObject): Boolean {
        try {
            // 解析应用白名单
            val whitelistArray = policyJson.optJSONArray("app_whitelist")
            if (whitelistArray != null) {
                val entries = mutableListOf<WhitelistEntry>()
                for (i in 0 until whitelistArray.length()) {
                    entries.add(WhitelistEntry.fromJson(whitelistArray.getJSONObject(i)))
                }
                setWhitelist(entries)
            }

            // 检查是否启用白名单
            val settings = policyJson.optJSONObject("settings")
            if (settings != null) {
                val appHardening = settings.optJSONObject("app_hardening")
                // 如果有 app_hardening 配置，白名单默认启用
                if (appHardening != null) {
                    setWhitelistEnabled(true)
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse policy from JSON", e)
            return false
        }
    }

    /**
     * 清空白名单
     */
    fun clearWhitelist() {
        setWhitelist(emptyList())
        Log.i(TAG, "Whitelist cleared")
    }
}
