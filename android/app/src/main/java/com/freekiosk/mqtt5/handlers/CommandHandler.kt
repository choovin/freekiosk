package com.freekiosk.mqtt5.handlers

import android.util.Log
import com.freekiosk.mqtt5.EmqxMqttClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
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
 */
class CommandHandler(
    private val mqttClient: EmqxMqttClient,
    private val commandExecutor: CommandExecutor
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
    }

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