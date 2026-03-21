package com.freekiosk.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import org.json.JSONObject

/**
 * 安全策略管理器
 *
 * 负责接收、存储和执行从服务器下发的安全策略配置。
 */
class SecurityPolicyManager(private val context: Context) {

    companion object {
        private const val TAG = "SecurityPolicyManager"
        private const val PREFS_NAME = "security_policy_prefs"
        private const val KEY_POLICY = "current_policy"
        private const val KEY_LAST_SYNC = "last_sync_time"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appWhitelistManager = AppWhitelistManager(context)

    /**
     * 策略设置数据类
     */
    data class PolicySettings(
        val passwordPolicy: PasswordPolicy? = null,
        val timeoutSettings: TimeoutSettings? = null,
        val systemHardening: SystemHardening? = null,
        val appHardening: AppHardening? = null,
        val networkRestrictions: NetworkRestrictions? = null
    )

    data class PasswordPolicy(
        val enabled: Boolean = false,
        val minLength: Int = 4,
        val requireUppercase: Boolean = false,
        val requireLowercase: Boolean = false,
        val requireNumbers: Boolean = false,
        val requireSymbols: Boolean = false,
        val maxAttempts: Int = 3,
        val lockoutDuration: Int = 15
    )

    data class TimeoutSettings(
        val screenOffTimeout: Int = 300,
        val lockTimeout: Int = 60,
        val sessionTimeout: Int = 3600,
        val inactivityLockTimeout: Int = 300
    )

    data class SystemHardening(
        val disableUsbDebug: Boolean = true,
        val disableAdbInstall: Boolean = true,
        val disableSettingsAccess: Boolean = true,
        val disableScreenshot: Boolean = true,
        val disableScreenCapture: Boolean = true,
        val disableStatusBar: Boolean = true,
        val disableNavigationBar: Boolean = true,
        val safeBoot: Boolean = false,
        val disablePowerMenu: Boolean = true
    )

    data class AppHardening(
        val allowBackgroundSwitch: Boolean = false,
        val allowReturnKey: Boolean = false,
        val allowRecentApps: Boolean = false,
        val forceFullScreen: Boolean = true,
        val hideHomeIndicator: Boolean = true
    )

    data class NetworkRestrictions(
        val allowWiFi: Boolean = true,
        val allowBluetooth: Boolean = false,
        val allowMobileData: Boolean = true,
        val allowedWiFiSSIDs: List<String> = emptyList(),
        val blockedPorts: List<Int> = emptyList(),
        val proxyAddress: String = ""
    )

    /**
     * 获取当前策略设置
     */
    fun getCurrentSettings(): PolicySettings? {
        val json = prefs.getString(KEY_POLICY, null) ?: return null
        return try {
            parseSettingsFromJson(JSONObject(json))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse current settings", e)
            null
        }
    }

    /**
     * 获取最后同步时间
     */
    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC, 0)
    }

    /**
     * 解析策略配置
     */
    fun parsePolicyFromJson(policyJson: JSONObject): Boolean {
        try {
            val settingsJson = policyJson.optJSONObject("settings") ?: return false
            val settings = parseSettingsFromJson(settingsJson)

            // 保存策略
            prefs.edit()
                .putString(KEY_POLICY, settingsJson.toString())
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply()

            // 应用策略
            applyPolicy(settings)

            Log.i(TAG, "Policy applied successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse policy", e)
            return false
        }
    }

    /**
     * 解析设置数据
     */
    private fun parseSettingsFromJson(json: JSONObject): PolicySettings {
        val passwordPolicyJson = json.optJSONObject("password_policy")
        val timeoutJson = json.optJSONObject("timeout_settings")
        val systemHardeningJson = json.optJSONObject("system_hardening")
        val appHardeningJson = json.optJSONObject("app_hardening")
        val networkJson = json.optJSONObject("network_restrictions")

        return PolicySettings(
            passwordPolicy = passwordPolicyJson?.let {
                PasswordPolicy(
                    enabled = it.optBoolean("enabled", false),
                    minLength = it.optInt("min_length", 4),
                    requireUppercase = it.optBoolean("require_uppercase", false),
                    requireLowercase = it.optBoolean("require_lowercase", false),
                    requireNumbers = it.optBoolean("require_numbers", false),
                    requireSymbols = it.optBoolean("require_symbols", false),
                    maxAttempts = it.optInt("max_attempts", 3),
                    lockoutDuration = it.optInt("lockout_duration_minutes", 15)
                )
            },
            timeoutSettings = timeoutJson?.let {
                TimeoutSettings(
                    screenOffTimeout = it.optInt("screen_off_timeout", 300),
                    lockTimeout = it.optInt("lock_timeout", 60),
                    sessionTimeout = it.optInt("session_timeout", 3600),
                    inactivityLockTimeout = it.optInt("inactivity_lock_timeout", 300)
                )
            },
            systemHardening = systemHardeningJson?.let {
                SystemHardening(
                    disableUsbDebug = it.optBoolean("disable_usb_debug", true),
                    disableAdbInstall = it.optBoolean("disable_adb_install", true),
                    disableSettingsAccess = it.optBoolean("disable_settings_access", true),
                    disableScreenshot = it.optBoolean("disable_screenshot", true),
                    disableScreenCapture = it.optBoolean("disable_screen_capture", true),
                    disableStatusBar = it.optBoolean("disable_status_bar", true),
                    disableNavigationBar = it.optBoolean("disable_navigation_bar", true),
                    safeBoot = it.optBoolean("safe_boot", false),
                    disablePowerMenu = it.optBoolean("disable_power_menu", true)
                )
            },
            appHardening = appHardeningJson?.let {
                AppHardening(
                    allowBackgroundSwitch = it.optBoolean("allow_background_switch", false),
                    allowReturnKey = it.optBoolean("allow_return_key", false),
                    allowRecentApps = it.optBoolean("allow_recent_apps", false),
                    forceFullScreen = it.optBoolean("force_full_screen", true),
                    hideHomeIndicator = it.optBoolean("hide_home_indicator", true)
                )
            },
            networkRestrictions = networkJson?.let {
                val ssidsArray = it.optJSONArray("allowed_wifi_ssids")
                val ssids = mutableListOf<String>()
                ssidsArray?.let { arr ->
                    for (i in 0 until arr.length()) {
                        ssids.add(arr.getString(i))
                    }
                }
                val portsArray = it.optJSONArray("blocked_ports")
                val ports = mutableListOf<Int>()
                portsArray?.let { arr ->
                    for (i in 0 until arr.length()) {
                        ports.add(arr.getInt(i))
                    }
                }
                NetworkRestrictions(
                    allowWiFi = it.optBoolean("allow_wifi", true),
                    allowBluetooth = it.optBoolean("allow_bluetooth", false),
                    allowMobileData = it.optBoolean("allow_mobile_data", true),
                    allowedWiFiSSIDs = ssids,
                    blockedPorts = ports,
                    proxyAddress = it.optString("proxy_address", "")
                )
            }
        )
    }

    /**
     * 应用策略设置
     */
    private fun applyPolicy(settings: PolicySettings) {
        // 应用系统加固
        settings.systemHardening?.let { applySystemHardening(it) }

        // 应用应用加固
        settings.appHardening?.let { applyAppHardening(it) }

        // 应用超时设置
        settings.timeoutSettings?.let { applyTimeoutSettings(it) }

        // 应用网络限制
        settings.networkRestrictions?.let { applyNetworkRestrictions(it) }
    }

    /**
     * 应用系统加固设置
     *
     * 注意：部分设置需要 Device Owner 权限才能生效。
     * - USB 调试控制需要 Device Owner 权限或 root 权限
     * - 状态栏/导航栏隐藏需要 Device Owner 权限
     * - 截图禁用需要 Device Owner 权限
     */
    private fun applySystemHardening(hardening: SystemHardening) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val adminComponent = ComponentName(context, com.freekiosk.DeviceAdminReceiver::class.java)

            val isAdminActive = dpm?.isAdminActive(adminComponent) == true

            if (isAdminActive) {
                // 使用 DevicePolicyManager API 进行系统加固
                // 这些操作需要 Device Owner 权限
                Log.i(TAG, "Device Owner 模式：系统加固可用")

                // 记录加固配置（实际系统调用由 AccessibilityService 执行）
                if (hardening.disableStatusBar || hardening.disableNavigationBar) {
                    prefs.edit().putBoolean("hide_status_bar", hardening.disableStatusBar)
                        .putBoolean("hide_navigation_bar", hardening.disableNavigationBar)
                        .apply()
                }

                if (hardening.disableScreenshot || hardening.disableScreenCapture) {
                    prefs.edit().putBoolean("disable_screenshot", hardening.disableScreenshot)
                        .apply()
                }
            } else {
                Log.w(TAG, "非 Device Owner 模式，部分系统加固设置无法生效")
            }

            // USB 调试设置（需要 root 或 Device Owner）
            // 注意：普通应用无法直接修改 ADB_ENABLED，这是系统级保护
            if (hardening.disableUsbDebug) {
                try {
                    Settings.Global.putInt(
                        context.contentResolver,
                        Settings.Global.ADB_ENABLED,
                        0
                    )
                    Log.i(TAG, "USB 调试已禁用")
                } catch (e: SecurityException) {
                    Log.w(TAG, "无法修改 USB 调试设置，需要 root 或 Device Owner 权限: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "修改 USB 调试设置失败", e)
                }
            }

            Log.i(TAG, "系统加固配置已应用 (adminActive=$isAdminActive)")
        } catch (e: Exception) {
            Log.e(TAG, "应用系统加固设置失败", e)
        }
    }

    /**
     * 应用应用加固设置
     */
    private fun applyAppHardening(hardening: AppHardening) {
        // 应用加固主要是控制应用切换和导航行为
        // 这些设置会影响 AccessibilityService 的行为
        Log.i(TAG, "App hardening applied: allowBackgroundSwitch=${hardening.allowBackgroundSwitch}, forceFullScreen=${hardening.forceFullScreen}")
    }

    /**
     * 应用超时设置
     */
    private fun applyTimeoutSettings(timeout: TimeoutSettings) {
        // 保存超时设置到 SharedPreferences
        // 这些值会被 ScreenSchedulerReceiver 和其他组件使用
        prefs.edit()
            .putInt("screen_off_timeout", timeout.screenOffTimeout)
            .putInt("lock_timeout", timeout.lockTimeout)
            .putInt("session_timeout", timeout.sessionTimeout)
            .putInt("inactivity_lock_timeout", timeout.inactivityLockTimeout)
            .apply()

        Log.i(TAG, "Timeout settings applied")
    }

    /**
     * 应用网络限制
     */
    private fun applyNetworkRestrictions(restrictions: NetworkRestrictions) {
        // 保存网络限制设置
        prefs.edit()
            .putBoolean("allow_wifi", restrictions.allowWiFi)
            .putBoolean("allow_bluetooth", restrictions.allowBluetooth)
            .putBoolean("allow_mobile_data", restrictions.allowMobileData)
            .putString("proxy_address", restrictions.proxyAddress)
            .apply()

        Log.i(TAG, "Network restrictions applied")
    }

    /**
     * 获取密码策略
     */
    fun getPasswordPolicy(): PasswordPolicy? {
        return getCurrentSettings()?.passwordPolicy
    }

    /**
     * 检查密码是否符合策略
     */
    fun validatePassword(password: String): Boolean {
        val policy = getPasswordPolicy() ?: return true

        if (!policy.enabled) return true

        if (password.length < policy.minLength) return false
        if (policy.requireUppercase && !password.any { it.isUpperCase() }) return false
        if (policy.requireLowercase && !password.any { it.isLowerCase() }) return false
        if (policy.requireNumbers && !password.any { it.isDigit() }) return false
        if (policy.requireSymbols && !password.any { !it.isLetterOrDigit() }) return false

        return true
    }

    /**
     * 获取超时设置
     */
    fun getTimeoutSettings(): TimeoutSettings? {
        return getCurrentSettings()?.timeoutSettings
    }

    /**
     * 获取系统加固设置
     */
    fun getSystemHardening(): SystemHardening? {
        return getCurrentSettings()?.systemHardening
    }

    /**
     * 获取应用加固设置
     */
    fun getAppHardening(): AppHardening? {
        return getCurrentSettings()?.appHardening
    }

    /**
     * 获取网络限制设置
     */
    fun getNetworkRestrictions(): NetworkRestrictions? {
        return getCurrentSettings()?.networkRestrictions
    }

    /**
     * 检查 USB 调试是否被禁用
     */
    fun isUsbDebugDisabled(): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED) == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取应用白名单管理器
     */
    fun getAppWhitelistManager(): AppWhitelistManager {
        return appWhitelistManager
    }

    /**
     * 清空策略
     */
    fun clearPolicy() {
        prefs.edit().clear().apply()
        Log.i(TAG, "Policy cleared")
    }
}
