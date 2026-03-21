package com.freekiosk.security

import android.util.Log
import com.facebook.react.bridge.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * 应用白名单 React Native 桥接模块
 *
 * 为 JavaScript 提供应用白名单管理能力:
 * - 设置和获取白名单
 * - 检查应用是否允许
 * - 获取被阻止的应用列表
 * - 添加/移除白名单应用
 *
 * 使用方法 (JavaScript):
 * ```javascript
 * import { NativeModules } from 'react-native';
 * const { AppWhitelistModule } = NativeModules;
 *
 * // 设置白名单
 * await AppWhitelistModule.setWhitelist(whitelistJson);
 *
 * // 检查应用是否允许
 * const isAllowed = await AppWhitelistModule.isAppAllowed('com.example.app');
 *
 * // 获取被阻止的应用
 * const blocked = await AppWhitelistModule.getBlockedApps();
 * ```
 */
class AppWhitelistModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val TAG = "AppWhitelistModule"
        private const val NAME = "AppWhitelistModule"
    }

    private val appWhitelistManager = AppWhitelistManager(reactContext)

    override fun getName(): String = NAME

    /**
     * 设置白名单
     *
     * @param whitelistJson 白名单 JSON 数组
     */
    @ReactMethod
    fun setWhitelist(whitelistJson: String, promise: Promise) {
        try {
            val jsonArray = JSONArray(whitelistJson)
            val entries = mutableListOf<AppWhitelistManager.WhitelistEntry>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                entries.add(
                    AppWhitelistManager.WhitelistEntry(
                        packageName = obj.optString("package_name", ""),
                        appName = obj.optString("app_name", ""),
                        autoLaunch = obj.optBoolean("auto_launch", false),
                        allowNotifications = obj.optBoolean("allow_notifications", false),
                        defaultShortcut = obj.optBoolean("default_shortcut", false)
                    )
                )
            }

            appWhitelistManager.setWhitelist(entries)
            Log.i(TAG, "白名单已设置: ${entries.size} 个应用")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "设置白名单失败", e)
            promise.reject("SET_WHITELIST_ERROR", e.message)
        }
    }

    /**
     * 获取白名单
     *
     * @return 白名单 JSON 数组
     */
    @ReactMethod
    fun getWhitelist(promise: Promise) {
        try {
            val entries = appWhitelistManager.getWhitelist()
            val jsonArray = JSONArray()

            entries.forEach { entry ->
                val obj = JSONObject().apply {
                    put("package_name", entry.packageName)
                    put("app_name", entry.appName)
                    put("auto_launch", entry.autoLaunch)
                    put("allow_notifications", entry.allowNotifications)
                    put("default_shortcut", entry.defaultShortcut)
                }
                jsonArray.put(obj)
            }

            promise.resolve(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "获取白名单失败", e)
            promise.reject("GET_WHITELIST_ERROR", e.message)
        }
    }

    /**
     * 设置白名单是否启用
     *
     * @param enabled 是否启用
     */
    @ReactMethod
    fun setWhitelistEnabled(enabled: Boolean, promise: Promise) {
        try {
            appWhitelistManager.setWhitelistEnabled(enabled)
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "设置白名单启用状态失败", e)
            promise.reject("SET_ENABLED_ERROR", e.message)
        }
    }

    /**
     * 检查白名单是否启用
     *
     * @return 是否启用
     */
    @ReactMethod
    fun isWhitelistEnabled(promise: Promise) {
        try {
            val enabled = appWhitelistManager.isWhitelistEnabled()
            promise.resolve(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "检查白名单启用状态失败", e)
            promise.reject("CHECK_ENABLED_ERROR", e.message)
        }
    }

    /**
     * 检查应用是否在白名单中
     *
     * @param packageName 包名
     * @return 是否允许
     */
    @ReactMethod
    fun isAppAllowed(packageName: String, promise: Promise) {
        try {
            val allowed = appWhitelistManager.isAppAllowed(packageName)
            promise.resolve(allowed)
        } catch (e: Exception) {
            Log.e(TAG, "检查应用允许状态失败", e)
            promise.reject("CHECK_ALLOWED_ERROR", e.message)
        }
    }

    /**
     * 获取所有已安装应用的白名单状态
     *
     * @return 应用列表及状态 JSON
     */
    @ReactMethod
    fun getInstalledAppsWithStatus(promise: Promise) {
        try {
            val apps = appWhitelistManager.getInstalledAppsWithStatus()
            val result = Arguments.createArray()

            apps.forEach { (packageName, isAllowed) ->
                val appInfo = Arguments.createMap().apply {
                    putString("packageName", packageName)
                    putBoolean("allowed", isAllowed)
                }
                result.pushMap(appInfo)
            }

            promise.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "获取安装应用状态失败", e)
            promise.reject("GET_APPS_ERROR", e.message)
        }
    }

    /**
     * 获取不在白名单中的应用列表
     *
     * @return 被阻止的应用包名数组
     */
    @ReactMethod
    fun getBlockedApps(promise: Promise) {
        try {
            val blocked = appWhitelistManager.getBlockedApps()
            val result = Arguments.createArray()
            blocked.forEach { result.pushString(it) }
            promise.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "获取被阻止应用失败", e)
            promise.reject("GET_BLOCKED_ERROR", e.message)
        }
    }

    /**
     * 添加应用到白名单
     *
     * @param entryJson 白名单条目 JSON
     */
    @ReactMethod
    fun addToWhitelist(entryJson: String, promise: Promise) {
        try {
            val obj = JSONObject(entryJson)
            val entry = AppWhitelistManager.WhitelistEntry(
                packageName = obj.optString("package_name", ""),
                appName = obj.optString("app_name", ""),
                autoLaunch = obj.optBoolean("auto_launch", false),
                allowNotifications = obj.optBoolean("allow_notifications", false),
                defaultShortcut = obj.optBoolean("default_shortcut", false)
            )
            appWhitelistManager.addToWhitelist(entry)
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "添加到白名单失败", e)
            promise.reject("ADD_ERROR", e.message)
        }
    }

    /**
     * 从白名单移除应用
     *
     * @param packageName 包名
     */
    @ReactMethod
    fun removeFromWhitelist(packageName: String, promise: Promise) {
        try {
            appWhitelistManager.removeFromWhitelist(packageName)
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "从白名单移除失败", e)
            promise.reject("REMOVE_ERROR", e.message)
        }
    }

    /**
     * 更新白名单中的应用
     *
     * @param entryJson 白名单条目 JSON
     */
    @ReactMethod
    fun updateInWhitelist(entryJson: String, promise: Promise) {
        try {
            val obj = JSONObject(entryJson)
            val entry = AppWhitelistManager.WhitelistEntry(
                packageName = obj.optString("package_name", ""),
                appName = obj.optString("app_name", ""),
                autoLaunch = obj.optBoolean("auto_launch", false),
                allowNotifications = obj.optBoolean("allow_notifications", false),
                defaultShortcut = obj.optBoolean("default_shortcut", false)
            )
            appWhitelistManager.updateInWhitelist(entry)
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "更新白名单失败", e)
            promise.reject("UPDATE_ERROR", e.message)
        }
    }

    /**
     * 获取需要自启动的应用列表
     *
     * @return 自启动应用 JSON 数组
     */
    @ReactMethod
    fun getAutoLaunchApps(promise: Promise) {
        try {
            val apps = appWhitelistManager.getAutoLaunchApps()
            val jsonArray = JSONArray()

            apps.forEach { entry ->
                val obj = JSONObject().apply {
                    put("package_name", entry.packageName)
                    put("app_name", entry.appName)
                    put("auto_launch", entry.autoLaunch)
                    put("allow_notifications", entry.allowNotifications)
                    put("default_shortcut", entry.defaultShortcut)
                }
                jsonArray.put(obj)
            }

            promise.resolve(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "获取自启动应用失败", e)
            promise.reject("GET_AUTO_LAUNCH_ERROR", e.message)
        }
    }

    /**
     * 启动应用（如果允许）
     *
     * @param packageName 包名
     * @return 是否启动成功
     */
    @ReactMethod
    fun launchAppIfAllowed(packageName: String, promise: Promise) {
        try {
            val launched = appWhitelistManager.launchAppIfAllowed(packageName)
            promise.resolve(launched)
        } catch (e: Exception) {
            Log.e(TAG, "启动应用失败", e)
            promise.reject("LAUNCH_ERROR", e.message)
        }
    }

    /**
     * 检查应用是否已安装
     *
     * @param packageName 包名
     * @return 是否已安装
     */
    @ReactMethod
    fun isAppInstalled(packageName: String, promise: Promise) {
        try {
            val installed = appWhitelistManager.isAppInstalled(packageName)
            promise.resolve(installed)
        } catch (e: Exception) {
            Log.e(TAG, "检查应用安装状态失败", e)
            promise.reject("CHECK_INSTALLED_ERROR", e.message)
        }
    }

    /**
     * 解析从服务器接收的策略配置
     *
     * @param policyJson 策略 JSON
     * @return 是否解析成功
     */
    @ReactMethod
    fun parsePolicyFromJson(policyJson: String, promise: Promise) {
        try {
            val json = JSONObject(policyJson)
            val success = appWhitelistManager.parsePolicyFromJson(json)
            promise.resolve(success)
        } catch (e: Exception) {
            Log.e(TAG, "解析策略失败", e)
            promise.reject("PARSE_ERROR", e.message)
        }
    }

    /**
     * 清空白名单
     */
    @ReactMethod
    fun clearWhitelist(promise: Promise) {
        try {
            appWhitelistManager.clearWhitelist()
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "清空白名单失败", e)
            promise.reject("CLEAR_ERROR", e.message)
        }
    }
}
