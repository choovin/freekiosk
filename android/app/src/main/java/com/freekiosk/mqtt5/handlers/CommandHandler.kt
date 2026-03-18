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

        // 支持的命令类型
        const val CMD_SET_BRIGHTNESS = "setBrightness"
        const val CMD_SET_SCREEN = "setScreen"
        const val CMD_SET_VOLUME = "setVolume"
        const val CMD_NAVIGATE = "navigate"
        const val CMD_RELOAD = "reload"
        const val CMD_REBOOT = "reboot"
        const val CMD_CLEAR_CACHE = "clearCache"
        const val CMD_UPDATE_APP = "updateApp"
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

            // 音频控制
            CMD_SET_VOLUME -> {
                val volume = params.optInt("volume", 50)
                commandExecutor.setVolume(volume)
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

        // 音频控制
        fun setVolume(volume: Int): CommandResult

        // WebView 控制
        fun navigate(url: String): CommandResult
        fun reload(): CommandResult

        // 系统控制
        fun reboot(): CommandResult
        fun clearCache(): CommandResult

        // 应用更新
        fun updateApp(apkUrl: String): CommandResult
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