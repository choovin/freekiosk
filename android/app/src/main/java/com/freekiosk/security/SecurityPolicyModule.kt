package com.freekiosk.security

import android.util.Log
import com.facebook.react.bridge.*
import org.json.JSONObject

/**
 * 安全策略 React Native 桥接模块
 *
 * 为 JavaScript 提供安全策略管理能力:
 * - 解析和应用从服务器下发的安全策略
 * - 管理密码策略验证
 * - 管理超时设置
 * - 获取系统加固配置
 *
 * 使用方法 (JavaScript):
 * ```javascript
 * import { NativeModules } from 'react-native';
 * const { SecurityPolicyModule } = NativeModules;
 *
 * // 解析和应用策略
 * await SecurityPolicyModule.parsePolicy(policyJson);
 *
 * // 验证密码
 * const isValid = await SecurityPolicyModule.validatePassword('1234');
 *
 * // 获取当前设置
 * const settings = await SecurityPolicyModule.getCurrentSettings();
 * ```
 */
class SecurityPolicyModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val TAG = "SecurityPolicyModule"
        private const val NAME = "SecurityPolicyModule"
    }

    private val securityPolicyManager = SecurityPolicyManager(reactContext)

    override fun getName(): String = NAME

    /**
     * 解析并应用安全策略
     *
     * @param policyJson 服务器下发的策略 JSON
     * @return 是否应用成功
     */
    @ReactMethod
    fun parsePolicy(policyJson: String, promise: Promise) {
        try {
            val json = JSONObject(policyJson)
            val success = securityPolicyManager.parsePolicyFromJson(json)

            if (success) {
                Log.i(TAG, "安全策略应用成功")
                promise.resolve(true)
            } else {
                promise.reject("PARSE_ERROR", "策略解析失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析安全策略失败", e)
            promise.reject("PARSE_ERROR", e.message)
        }
    }

    /**
     * 获取当前策略设置
     *
     * @return 策略设置 JSON
     */
    @ReactMethod
    fun getCurrentSettings(promise: Promise) {
        try {
            val settings = securityPolicyManager.getCurrentSettings()
            if (settings != null) {
                val result = Arguments.createMap().apply {
                    // Password Policy
                    settings.passwordPolicy?.let { pp ->
                        val passwordPolicy = Arguments.createMap().apply {
                            putBoolean("enabled", pp.enabled)
                            putInt("minLength", pp.minLength)
                            putBoolean("requireUppercase", pp.requireUppercase)
                            putBoolean("requireLowercase", pp.requireLowercase)
                            putBoolean("requireNumbers", pp.requireNumbers)
                            putBoolean("requireSymbols", pp.requireSymbols)
                            putInt("maxAttempts", pp.maxAttempts)
                            putInt("lockoutDuration", pp.lockoutDuration)
                        }
                        putMap("passwordPolicy", passwordPolicy)
                    }

                    // Timeout Settings
                    settings.timeoutSettings?.let { ts ->
                        val timeoutSettings = Arguments.createMap().apply {
                            putInt("screenOffTimeout", ts.screenOffTimeout)
                            putInt("lockTimeout", ts.lockTimeout)
                            putInt("sessionTimeout", ts.sessionTimeout)
                            putInt("inactivityLockTimeout", ts.inactivityLockTimeout)
                        }
                        putMap("timeoutSettings", timeoutSettings)
                    }

                    // System Hardening
                    settings.systemHardening?.let { sh ->
                        val systemHardening = Arguments.createMap().apply {
                            putBoolean("disableUsbDebug", sh.disableUsbDebug)
                            putBoolean("disableAdbInstall", sh.disableAdbInstall)
                            putBoolean("disableSettingsAccess", sh.disableSettingsAccess)
                            putBoolean("disableScreenshot", sh.disableScreenshot)
                            putBoolean("disableScreenCapture", sh.disableScreenCapture)
                            putBoolean("disableStatusBar", sh.disableStatusBar)
                            putBoolean("disableNavigationBar", sh.disableNavigationBar)
                            putBoolean("safeBoot", sh.safeBoot)
                            putBoolean("disablePowerMenu", sh.disablePowerMenu)
                        }
                        putMap("systemHardening", systemHardening)
                    }

                    // App Hardening
                    settings.appHardening?.let { ah ->
                        val appHardening = Arguments.createMap().apply {
                            putBoolean("allowBackgroundSwitch", ah.allowBackgroundSwitch)
                            putBoolean("allowReturnKey", ah.allowReturnKey)
                            putBoolean("allowRecentApps", ah.allowRecentApps)
                            putBoolean("forceFullScreen", ah.forceFullScreen)
                            putBoolean("hideHomeIndicator", ah.hideHomeIndicator)
                        }
                        putMap("appHardening", appHardening)
                    }

                    // Network Restrictions
                    settings.networkRestrictions?.let { nr ->
                        val networkRestrictions = Arguments.createMap().apply {
                            putBoolean("allowWiFi", nr.allowWiFi)
                            putBoolean("allowBluetooth", nr.allowBluetooth)
                            putBoolean("allowMobileData", nr.allowMobileData)
                            putString("proxyAddress", nr.proxyAddress)
                            // SSIDs as Array
                            val ssidsArray = Arguments.createArray()
                            nr.allowedWiFiSSIDs.forEach { ssidsArray.pushString(it) }
                            putArray("allowedWiFiSSIDs", ssidsArray)
                            // Blocked ports as Array
                            val portsArray = Arguments.createArray()
                            nr.blockedPorts.forEach { portsArray.pushInt(it) }
                            putArray("blockedPorts", portsArray)
                        }
                        putMap("networkRestrictions", networkRestrictions)
                    }
                }
                promise.resolve(result)
            } else {
                promise.resolve(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取策略设置失败", e)
            promise.reject("GET_SETTINGS_ERROR", e.message)
        }
    }

    /**
     * 验证密码是否符合策略
     *
     * @param password 要验证的密码
     * @return 是否符合策略
     */
    @ReactMethod
    fun validatePassword(password: String, promise: Promise) {
        try {
            val isValid = securityPolicyManager.validatePassword(password)
            promise.resolve(isValid)
        } catch (e: Exception) {
            Log.e(TAG, "验证密码失败", e)
            promise.reject("VALIDATE_ERROR", e.message)
        }
    }

    /**
     * 获取密码策略
     *
     * @return 密码策略 JSON 或 null
     */
    @ReactMethod
    fun getPasswordPolicy(promise: Promise) {
        try {
            val policy = securityPolicyManager.getPasswordPolicy()
            if (policy != null) {
                val result = Arguments.createMap().apply {
                    putBoolean("enabled", policy.enabled)
                    putInt("minLength", policy.minLength)
                    putBoolean("requireUppercase", policy.requireUppercase)
                    putBoolean("requireLowercase", policy.requireLowercase)
                    putBoolean("requireNumbers", policy.requireNumbers)
                    putBoolean("requireSymbols", policy.requireSymbols)
                    putInt("maxAttempts", policy.maxAttempts)
                    putInt("lockoutDuration", policy.lockoutDuration)
                }
                promise.resolve(result)
            } else {
                promise.resolve(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取密码策略失败", e)
            promise.reject("GET_POLICY_ERROR", e.message)
        }
    }

    /**
     * 获取超时设置
     *
     * @return 超时设置 JSON 或 null
     */
    @ReactMethod
    fun getTimeoutSettings(promise: Promise) {
        try {
            val settings = securityPolicyManager.getTimeoutSettings()
            if (settings != null) {
                val result = Arguments.createMap().apply {
                    putInt("screenOffTimeout", settings.screenOffTimeout)
                    putInt("lockTimeout", settings.lockTimeout)
                    putInt("sessionTimeout", settings.sessionTimeout)
                    putInt("inactivityLockTimeout", settings.inactivityLockTimeout)
                }
                promise.resolve(result)
            } else {
                promise.resolve(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取超时设置失败", e)
            promise.reject("GET_TIMEOUT_ERROR", e.message)
        }
    }

    /**
     * 获取系统加固设置
     *
     * @return 系统加固设置 JSON 或 null
     */
    @ReactMethod
    fun getSystemHardening(promise: Promise) {
        try {
            val hardening = securityPolicyManager.getSystemHardening()
            if (hardening != null) {
                val result = Arguments.createMap().apply {
                    putBoolean("disableUsbDebug", hardening.disableUsbDebug)
                    putBoolean("disableAdbInstall", hardening.disableAdbInstall)
                    putBoolean("disableSettingsAccess", hardening.disableSettingsAccess)
                    putBoolean("disableScreenshot", hardening.disableScreenshot)
                    putBoolean("disableScreenCapture", hardening.disableScreenCapture)
                    putBoolean("disableStatusBar", hardening.disableStatusBar)
                    putBoolean("disableNavigationBar", hardening.disableNavigationBar)
                    putBoolean("safeBoot", hardening.safeBoot)
                    putBoolean("disablePowerMenu", hardening.disablePowerMenu)
                }
                promise.resolve(result)
            } else {
                promise.resolve(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取系统加固设置失败", e)
            promise.reject("GET_HARDENING_ERROR", e.message)
        }
    }

    /**
     * 获取应用加固设置
     *
     * @return 应用加固设置 JSON 或 null
     */
    @ReactMethod
    fun getAppHardening(promise: Promise) {
        try {
            val hardening = securityPolicyManager.getAppHardening()
            if (hardening != null) {
                val result = Arguments.createMap().apply {
                    putBoolean("allowBackgroundSwitch", hardening.allowBackgroundSwitch)
                    putBoolean("allowReturnKey", hardening.allowReturnKey)
                    putBoolean("allowRecentApps", hardening.allowRecentApps)
                    putBoolean("forceFullScreen", hardening.forceFullScreen)
                    putBoolean("hideHomeIndicator", hardening.hideHomeIndicator)
                }
                promise.resolve(result)
            } else {
                promise.resolve(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取应用加固设置失败", e)
            promise.reject("GET_APP_HARDENING_ERROR", e.message)
        }
    }

    /**
     * 获取网络限制设置
     *
     * @return 网络限制设置 JSON 或 null
     */
    @ReactMethod
    fun getNetworkRestrictions(promise: Promise) {
        try {
            val restrictions = securityPolicyManager.getNetworkRestrictions()
            if (restrictions != null) {
                val result = Arguments.createMap().apply {
                    putBoolean("allowWiFi", restrictions.allowWiFi)
                    putBoolean("allowBluetooth", restrictions.allowBluetooth)
                    putBoolean("allowMobileData", restrictions.allowMobileData)
                    putString("proxyAddress", restrictions.proxyAddress)
                    val ssidsArray = Arguments.createArray()
                    restrictions.allowedWiFiSSIDs.forEach { ssidsArray.pushString(it) }
                    putArray("allowedWiFiSSIDs", ssidsArray)
                    val portsArray = Arguments.createArray()
                    restrictions.blockedPorts.forEach { portsArray.pushInt(it) }
                    putArray("blockedPorts", portsArray)
                }
                promise.resolve(result)
            } else {
                promise.resolve(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取网络限制设置失败", e)
            promise.reject("GET_NETWORK_ERROR", e.message)
        }
    }

    /**
     * 检查 USB 调试是否被禁用
     *
     * @return 是否已禁用 USB 调试
     */
    @ReactMethod
    fun isUsbDebugDisabled(promise: Promise) {
        try {
            val disabled = securityPolicyManager.isUsbDebugDisabled()
            promise.resolve(disabled)
        } catch (e: Exception) {
            Log.e(TAG, "检查 USB 调试状态失败", e)
            promise.reject("CHECK_ERROR", e.message)
        }
    }

    /**
     * 获取最后同步时间
     *
     * @return 最后同步时间戳（毫秒）
     */
    @ReactMethod
    fun getLastSyncTime(promise: Promise) {
        try {
            val lastSync = securityPolicyManager.getLastSyncTime()
            promise.resolve(lastSync.toDouble())
        } catch (e: Exception) {
            Log.e(TAG, "获取最后同步时间失败", e)
            promise.reject("GET_SYNC_ERROR", e.message)
        }
    }

    /**
     * 清空策略
     */
    @ReactMethod
    fun clearPolicy(promise: Promise) {
        try {
            securityPolicyManager.clearPolicy()
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "清空策略失败", e)
            promise.reject("CLEAR_ERROR", e.message)
        }
    }

    /**
     * 获取应用白名单管理器
     *
     * @return AppWhitelistModule 实例
     */
    fun getAppWhitelistManager(): AppWhitelistManager {
        return securityPolicyManager.getAppWhitelistManager()
    }
}
