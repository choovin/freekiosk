package com.freekiosk.mqtt5.handlers

import android.content.Context
import android.util.Log
import com.freekiosk.mqtt5.EmqxMqttClient
import com.freekiosk.security.AppWhitelistManager
import com.freekiosk.security.SecurityPolicyManager
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * MQTT 命令处理器
 *
 * 处理从 Hub 服务器下发的命令，执行相应操作并返回结果。
 * 支持的命令类型包括：屏幕控制、音频控制、导航、系统控制等。
 *
 * @property mqttClient MQTT 客户端实例
 * @property commandExecutor 命令执行器接口
 * @property context Android 上下文
 */
class CommandHandler(
    private val mqttClient: EmqxMqttClient,
    private val commandExecutor: CommandExecutor,
    private val context: Context
) {
    companion object {
        private const val TAG = "CommandHandler"

        // 屏幕控制命令
        const val CMD_SET_BRIGHTNESS = "setBrightness"
        const val CMD_SET_SCREEN = "setScreen"
        const val CMD_SET_ROTATION = "setRotation"
        const val CMD_WAKE_UP = "wakeUp"
        const val CMD_SLEEP = "sleep"

        // 音频控制命令
        const val CMD_SET_VOLUME = "setVolume"
        const val CMD_PLAY_SOUND = "playSound"
        const val CMD_STOP_SOUND = "stopSound"
        const val CMD_SPEAK = "speak"

        // WebView 控制命令
        const val CMD_NAVIGATE = "navigate"
        const val CMD_RELOAD = "reload"
        const val CMD_GO_BACK = "goBack"
        const val CMD_GO_FORWARD = "goForward"
        const val CMD_EXECUTE_JS = "executeJs"

        // 系统控制命令
        const val CMD_REBOOT = "reboot"
        const val CMD_CLEAR_CACHE = "clearCache"
        const val CMD_UPDATE_APP = "updateApp"
        const val CMD_SET_KIOSK_MODE = "setKioskMode"

        // 应用管理命令
        const val CMD_INSTALL_APP = "installApp"
        const val CMD_UNINSTALL_APP = "uninstallApp"
        const val CMD_START_APP = "startApp"
        const val CMD_STOP_APP = "stopApp"

        // 信息获取命令
        const val CMD_SCREENSHOT = "screenshot"
        const val CMD_GET_LOGS = "getLogs"
        const val CMD_GET_WIFI_INFO = "getWifiInfo"
        const val CMD_GET_DEVICE_INFO = "getDeviceInfo"

        // 安全策略命令
        const val CMD_UPDATE_POLICY = "updatePolicy"
        const val CMD_UPDATE_WHITELIST = "updateWhitelist"
        const val CMD_GET_POLICY = "getPolicy"
        const val CMD_GET_WHITELIST = "getWhitelist"
        const val CMD_VALIDATE_PASSWORD = "validatePassword"
    }

    // 安全策略管理器
    private val securityPolicyManager = SecurityPolicyManager(context)
    private val appWhitelistManager = AppWhitelistManager(context)

    /**
     * 处理收到的命令消息
     *
     * 解析 JSON 格式的命令，执行对应操作，并返回结果。
     *
     * @param publish MQTT 发布消息
     */
    fun handle(publish: Mqtt5Publish) {
        try {
            // 解析消息内容
            val payload = publish.payloadAsBytes.toString(StandardCharsets.UTF_8)
            val json = JSONObject(payload)

            val commandId = json.getString("id")
            val commandType = json.getString("type")
            val timestamp = json.optLong("timestamp", System.currentTimeMillis())
            val params = json.optJSONObject("params") ?: JSONObject()
            val timeout = json.optInt("timeout", 30)

            Log.i(TAG, "收到命令: id=$commandId, type=$commandType")

            // 执行命令
            val result = executeCommand(commandType, params)

            // 发送响应
            sendResponse(commandId, result)

        } catch (e: Exception) {
            Log.e(TAG, "处理命令时出错", e)
        }
    }

    /**
     * 执行命令
     *
     * @param type 命令类型
     * @param params 命令参数
     * @return 执行结果
     */
    private fun executeCommand(type: String, params: JSONObject): CommandResult {
        return when (type) {
            // 屏幕控制
            CMD_SET_BRIGHTNESS -> {
                val brightness = params.optInt("brightness", 128)
                commandExecutor.setBrightness(brightness)
            }

            CMD_SET_SCREEN -> {
                val on = params.optBoolean("on", true)
                commandExecutor.setScreenOn(on)
            }

            CMD_SET_ROTATION -> {
                val rotation = params.optInt("rotation", 0)
                commandExecutor.setRotation(rotation)
            }

            CMD_WAKE_UP -> {
                commandExecutor.wakeUp()
            }

            CMD_SLEEP -> {
                commandExecutor.sleep()
            }

            // 音频控制
            CMD_SET_VOLUME -> {
                val volume = params.optInt("volume", 50)
                commandExecutor.setVolume(volume)
            }

            CMD_PLAY_SOUND -> {
                val soundUrl = params.optString("url", "")
                val loop = params.optBoolean("loop", false)
                if (soundUrl.isNotEmpty()) {
                    commandExecutor.playSound(soundUrl, loop)
                } else {
                    CommandResult(false, error = "Sound URL 不能为空")
                }
            }

            CMD_STOP_SOUND -> {
                commandExecutor.stopSound()
            }

            CMD_SPEAK -> {
                val text = params.optString("text", "")
                val language = params.optString("language", "zh-CN")
                if (text.isNotEmpty()) {
                    commandExecutor.speak(text, language)
                } else {
                    CommandResult(false, error = "Text 不能为空")
                }
            }

            // WebView 控制
            CMD_NAVIGATE -> {
                val url = params.optString("url", "")
                if (url.isNotEmpty()) {
                    commandExecutor.navigate(url)
                } else {
                    CommandResult(false, error = "URL 不能为空")
                }
            }

            CMD_RELOAD -> {
                commandExecutor.reload()
            }

            CMD_GO_BACK -> {
                commandExecutor.goBack()
            }

            CMD_GO_FORWARD -> {
                commandExecutor.goForward()
            }

            CMD_EXECUTE_JS -> {
                val jsCode = params.optString("code", "")
                if (jsCode.isNotEmpty()) {
                    commandExecutor.executeJs(jsCode)
                } else {
                    CommandResult(false, error = "JS code 不能为空")
                }
            }

            // 系统控制
            CMD_REBOOT -> {
                commandExecutor.reboot()
            }

            CMD_CLEAR_CACHE -> {
                commandExecutor.clearCache()
            }

            CMD_UPDATE_APP -> {
                val apkUrl = params.optString("apkUrl", "")
                if (apkUrl.isNotEmpty()) {
                    commandExecutor.updateApp(apkUrl)
                } else {
                    CommandResult(false, error = "APK URL 不能为空")
                }
            }

            CMD_SET_KIOSK_MODE -> {
                val enabled = params.optBoolean("enabled", true)
                commandExecutor.setKioskMode(enabled)
            }

            // 应用管理
            CMD_INSTALL_APP -> {
                val apkUrl = params.optString("apkUrl", "")
                if (apkUrl.isNotEmpty()) {
                    commandExecutor.installApp(apkUrl)
                } else {
                    CommandResult(false, error = "APK URL 不能为空")
                }
            }

            CMD_UNINSTALL_APP -> {
                val packageName = params.optString("packageName", "")
                if (packageName.isNotEmpty()) {
                    commandExecutor.uninstallApp(packageName)
                } else {
                    CommandResult(false, error = "Package name 不能为空")
                }
            }

            CMD_START_APP -> {
                val packageName = params.optString("packageName", "")
                if (packageName.isNotEmpty()) {
                    commandExecutor.startApp(packageName)
                } else {
                    CommandResult(false, error = "Package name 不能为空")
                }
            }

            CMD_STOP_APP -> {
                val packageName = params.optString("packageName", "")
                if (packageName.isNotEmpty()) {
                    commandExecutor.stopApp(packageName)
                } else {
                    CommandResult(false, error = "Package name 不能为空")
                }
            }

            // 信息获取
            CMD_SCREENSHOT -> {
                commandExecutor.screenshot()
            }

            CMD_GET_LOGS -> {
                val lines = params.optInt("lines", 100)
                commandExecutor.getLogs(lines)
            }

            CMD_GET_WIFI_INFO -> {
                commandExecutor.getWifiInfo()
            }

            CMD_GET_DEVICE_INFO -> {
                commandExecutor.getDeviceInfo()
            }

            // 安全策略命令
            CMD_UPDATE_POLICY -> {
                val policyJson = params.optJSONObject("policy")
                if (policyJson != null) {
                    val success = securityPolicyManager.parsePolicyFromJson(policyJson)
                    if (success) {
                        CommandResult(true, result = "策略已更新")
                    } else {
                        CommandResult(false, error = "策略解析失败")
                    }
                } else {
                    CommandResult(false, error = "策略数据不能为空")
                }
            }

            CMD_UPDATE_WHITELIST -> {
                val whitelistJson = params.optJSONArray("whitelist")
                if (whitelistJson != null) {
                    val entries = mutableListOf<AppWhitelistManager.WhitelistEntry>()
                    for (i in 0 until whitelistJson.length()) {
                        val obj = whitelistJson.getJSONObject(i)
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
                    CommandResult(true, result = "白名单已更新: ${entries.size} 个应用")
                } else {
                    CommandResult(false, error = "白名单数据不能为空")
                }
            }

            CMD_GET_POLICY -> {
                val settings = securityPolicyManager.getCurrentSettings()
                if (settings != null) {
                    val settingsJson = JSONObject().apply {
                        // Password Policy
                        settings.passwordPolicy?.let { pp ->
                            put("password_policy", JSONObject().apply {
                                put("enabled", pp.enabled)
                                put("min_length", pp.minLength)
                                put("require_uppercase", pp.requireUppercase)
                                put("require_lowercase", pp.requireLowercase)
                                put("require_numbers", pp.requireNumbers)
                                put("require_symbols", pp.requireSymbols)
                                put("max_attempts", pp.maxAttempts)
                                put("lockout_duration_minutes", pp.lockoutDuration)
                            })
                        }
                        // Timeout Settings
                        settings.timeoutSettings?.let { ts ->
                            put("timeout_settings", JSONObject().apply {
                                put("screen_off_timeout", ts.screenOffTimeout)
                                put("lock_timeout", ts.lockTimeout)
                                put("session_timeout", ts.sessionTimeout)
                                put("inactivity_lock_timeout", ts.inactivityLockTimeout)
                            })
                        }
                        // System Hardening
                        settings.systemHardening?.let { sh ->
                            put("system_hardening", JSONObject().apply {
                                put("disable_usb_debug", sh.disableUsbDebug)
                                put("disable_adb_install", sh.disableAdbInstall)
                                put("disable_settings_access", sh.disableSettingsAccess)
                                put("disable_screenshot", sh.disableScreenshot)
                                put("disable_screen_capture", sh.disableScreenCapture)
                                put("disable_status_bar", sh.disableStatusBar)
                                put("disable_navigation_bar", sh.disableNavigationBar)
                                put("safe_boot", sh.safeBoot)
                                put("disable_power_menu", sh.disablePowerMenu)
                            })
                        }
                        // App Hardening
                        settings.appHardening?.let { ah ->
                            put("app_hardening", JSONObject().apply {
                                put("allow_background_switch", ah.allowBackgroundSwitch)
                                put("allow_return_key", ah.allowReturnKey)
                                put("allow_recent_apps", ah.allowRecentApps)
                                put("force_full_screen", ah.forceFullScreen)
                                put("hide_home_indicator", ah.hideHomeIndicator)
                            })
                        }
                        // Network Restrictions
                        settings.networkRestrictions?.let { nr ->
                            put("network_restrictions", JSONObject().apply {
                                put("allow_wifi", nr.allowWiFi)
                                put("allow_bluetooth", nr.allowBluetooth)
                                put("allow_mobile_data", nr.allowMobileData)
                                put("proxy_address", nr.proxyAddress)
                                put("allowed_wifi_ssids", JSONArray(nr.allowedWiFiSSIDs))
                                put("blocked_ports", JSONArray(nr.blockedPorts))
                            })
                        }
                    }
                    CommandResult(true, result = settingsJson.toString())
                } else {
                    CommandResult(false, error = "未找到策略设置")
                }
            }

            CMD_GET_WHITELIST -> {
                val entries = appWhitelistManager.getWhitelist()
                val jsonArray = JSONArray()
                entries.forEach { entry ->
                    jsonArray.put(JSONObject().apply {
                        put("package_name", entry.packageName)
                        put("app_name", entry.appName)
                        put("auto_launch", entry.autoLaunch)
                        put("allow_notifications", entry.allowNotifications)
                        put("default_shortcut", entry.defaultShortcut)
                    })
                }
                CommandResult(true, result = jsonArray.toString())
            }

            CMD_VALIDATE_PASSWORD -> {
                val password = params.optString("password", "")
                if (password.isNotEmpty()) {
                    val isValid = securityPolicyManager.validatePassword(password)
                    CommandResult(true, result = isValid.toString())
                } else {
                    CommandResult(false, error = "密码不能为空")
                }
            }

            else -> {
                Log.w(TAG, "未知命令类型: $type")
                CommandResult(false, error = "未知命令类型: $type")
            }
        }
    }

    /**
     * 发送命令响应
     *
     * @param commandId 命令 ID
     * @param result 执行结果
     */
    private fun sendResponse(commandId: String, result: CommandResult) {
        try {
            val responseJson = JSONObject().apply {
                put("commandId", commandId)
                put("success", result.success)
                put("timestamp", System.currentTimeMillis())

                if (result.result != null) {
                    put("result", result.result)
                }

                if (result.error != null) {
                    put("error", result.error)
                }
            }

            mqttClient.publishResponse(commandId, responseJson.toString())
            Log.i(TAG, "已发送命令响应: commandId=$commandId, success=${result.success}")

        } catch (e: Exception) {
            Log.e(TAG, "发送响应失败", e)
        }
    }

    /**
     * 命令执行器接口
     *
     * 由 KioskModule 或其他模块实现，提供实际的命令执行能力。
     */
    interface CommandExecutor {
        // 屏幕控制
        fun setBrightness(brightness: Int): CommandResult
        fun setScreenOn(on: Boolean): CommandResult
        fun setRotation(rotation: Int): CommandResult
        fun wakeUp(): CommandResult
        fun sleep(): CommandResult

        // 音频控制
        fun setVolume(volume: Int): CommandResult
        fun playSound(url: String, loop: Boolean = false): CommandResult
        fun stopSound(): CommandResult
        fun speak(text: String, language: String = "zh-CN"): CommandResult

        // WebView 控制
        fun navigate(url: String): CommandResult
        fun reload(): CommandResult
        fun goBack(): CommandResult
        fun goForward(): CommandResult
        fun executeJs(code: String): CommandResult

        // 系统控制
        fun reboot(): CommandResult
        fun clearCache(): CommandResult
        fun updateApp(apkUrl: String): CommandResult
        fun setKioskMode(enabled: Boolean): CommandResult

        // 应用管理
        fun installApp(apkUrl: String): CommandResult
        fun uninstallApp(packageName: String): CommandResult
        fun startApp(packageName: String): CommandResult
        fun stopApp(packageName: String): CommandResult

        // 信息获取
        fun screenshot(): CommandResult
        fun getLogs(lines: Int = 100): CommandResult
        fun getWifiInfo(): CommandResult
        fun getDeviceInfo(): CommandResult
    }

    /**
     * 命令执行结果
     *
     * @property success 是否成功
     * @property result 结果数据（可选）
     * @property error 错误信息（可选）
     */
    data class CommandResult(
        val success: Boolean,
        val result: String? = null,
        val error: String? = null
    )
}