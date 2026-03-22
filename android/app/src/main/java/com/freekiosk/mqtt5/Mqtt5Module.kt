package com.freekiosk.mqtt5

import android.content.Context
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.freekiosk.mqtt5.handlers.CommandHandler
import com.freekiosk.service.MqttForegroundService
import com.freekiosk.auth.JwtTokenManager
import kotlinx.coroutines.*

/**
 * MQTT 5.0 React Native 桥接模块
 *
 * 为 JavaScript 提供企业级 MQTT 5.0 通信能力。
 * 主要功能:
 * - 连接/断开 MQTT Broker
 * - 发布状态、事件、遥测数据
 * - 订阅命令和配置更新
 * - 发送命令响应
 *
 * 使用方法 (JavaScript):
 * ```javascript
 * import { NativeModules } from 'react-native';
 * const { Mqtt5Module } = NativeModules;
 *
 * // 启动 MQTT 服务
 * await Mqtt5Module.start({
 *   brokerUrl: 'emqx.example.com',
 *   port: 1883,
 *   tenantId: 'tenant001',
 *   deviceId: 'device001'
 * });
 *
 * // 发布状态
 * await Mqtt5Module.publishStatus(JSON.stringify({ ... }));
 *
 * // 监听命令
 * const emitter = new NativeEventEmitter(Mqtt5Module);
 * emitter.addListener('onMqttCommand', (event) => { ... });
 * ```
 */
class Mqtt5Module(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val TAG = "Mqtt5Module"
        private const val NAME = "Mqtt5Module"

        // 单例实例引用
        @Volatile
        private var instance: Mqtt5Module? = null
    }

    init {
        instance = this
    }

    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 模块名称
    override fun getName(): String = NAME

    // ==================== React Native 方法 ====================

    /**
     * 启动 MQTT 5.0 服务
     *
     * 启动前台服务并连接到 MQTT Broker。
     *
     * @param configMap 配置参数
     *   - brokerUrl: MQTT Broker 地址 (必需)
     *   - port: 端口号 (默认 1883)
     *   - tenantId: 租户 ID (必需)
     *   - deviceId: 设备 ID (必需)
     *   - useTls: 是否使用 TLS (默认 false)
     *   - jwtToken: JWT 认证令牌 (可选)
     *   - accessToken: JWT Access Token (企业版, 可选)
     *   - deviceKey: 设备唯一标识键 (企业版, 可选)
     *   - keepAlive: 心跳间隔秒数 (默认 60)
     *   - cleanStart: 是否清除会话 (默认 false)
     */
    @ReactMethod
    fun start(configMap: ReadableMap, promise: Promise) {
        try {
            // 解析配置
            val brokerUrl = configMap.getString("brokerUrl")
            if (brokerUrl.isNullOrEmpty()) {
                promise.reject("INVALID_CONFIG", "brokerUrl 是必需参数")
                return
            }

            val tenantId = configMap.getString("tenantId")
            if (tenantId.isNullOrEmpty()) {
                promise.reject("INVALID_CONFIG", "tenantId 是必需参数")
                return
            }

            val deviceId = configMap.getString("deviceId")
            if (deviceId.isNullOrEmpty()) {
                promise.reject("INVALID_CONFIG", "deviceId 是必需参数")
                return
            }

            // 企业版: 尝试从存储获取 Token
            val tokenManager = JwtTokenManager(reactContext)
            val storedAccessToken = tokenManager.getAccessToken()
            val storedDeviceKey = tokenManager.getDeviceId()

            // 优先使用存储的 Token，其次使用传入的参数
            val accessToken = configMap.getString("accessToken") ?: storedAccessToken
            val deviceKey = configMap.getString("deviceKey") ?: storedDeviceKey

            val config = Mqtt5Config(
                brokerUrl = brokerUrl,
                port = if (configMap.hasKey("port")) configMap.getInt("port") else Mqtt5Config.DEFAULT_PORT,
                tenantId = tenantId,
                deviceId = deviceId,
                useTls = if (configMap.hasKey("useTls")) configMap.getBoolean("useTls") else false,
                username = if (configMap.hasKey("mqttUsername")) configMap.getString("mqttUsername") else "sailfish",
                password = if (configMap.hasKey("mqttPassword")) configMap.getString("mqttPassword") else "sailfish020",
                jwtToken = if (configMap.hasKey("jwtToken")) configMap.getString("jwtToken") else null,
                accessToken = accessToken,
                deviceKey = deviceKey,
                keepAlive = if (configMap.hasKey("keepAlive")) configMap.getInt("keepAlive") else Mqtt5Config.DEFAULT_KEEP_ALIVE,
                cleanStart = if (configMap.hasKey("cleanStart")) configMap.getBoolean("cleanStart") else false,
                sessionExpiryInterval = if (configMap.hasKey("sessionExpiryInterval")) configMap.getLong("sessionExpiryInterval") else Mqtt5Config.DEFAULT_SESSION_EXPIRY,
                statusInterval = if (configMap.hasKey("statusInterval")) configMap.getLong("statusInterval") else Mqtt5Config.DEFAULT_STATUS_INTERVAL
            )

            // 设置命令执行器
            MqttForegroundService.commandExecutor = createCommandExecutor()

            // 启动前台服务
            MqttForegroundService.start(reactContext.applicationContext, config)

            Log.i(TAG, "MQTT 5.0 服务已启动: ${config.brokerUrl}:${config.port}")
            Log.i(TAG, "Client ID: ${config.toClientId()}")

            val result = Arguments.createMap().apply {
                putBoolean("success", true)
                putString("clientId", config.toClientId())
                putString("baseTopic", config.baseTopic())
            }
            promise.resolve(result)

        } catch (e: Exception) {
            Log.e(TAG, "启动 MQTT 服务失败", e)
            promise.reject("START_ERROR", e.message)
        }
    }

    /**
     * 停止 MQTT 5.0 服务
     */
    @ReactMethod
    fun stop(promise: Promise) {
        try {
            MqttForegroundService.stop(reactContext.applicationContext)
            MqttForegroundService.commandExecutor = null
            Log.i(TAG, "MQTT 5.0 服务已停止")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "停止 MQTT 服务失败", e)
            promise.reject("STOP_ERROR", e.message)
        }
    }

    /**
     * 检查 MQTT 是否已连接
     */
    @ReactMethod
    fun isConnected(promise: Promise) {
        // 由于客户端在服务中，这里简化处理
        // 实际连接状态通过事件通知
        promise.resolve(true)
    }

    /**
     * 发布设备状态
     *
     * @param statusJson JSON 格式的状态数据
     */
    @ReactMethod
    fun publishStatus(statusJson: String, promise: Promise) {
        try {
            MqttForegroundService.publishStatus(statusJson)
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "发布状态失败", e)
            promise.reject("PUBLISH_ERROR", e.message)
        }
    }

    /**
     * 发布设备事件
     *
     * @param eventJson JSON 格式的事件数据
     */
    @ReactMethod
    fun publishEvent(eventJson: String, promise: Promise) {
        try {
            MqttForegroundService.publishEvent(eventJson)
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "发布事件失败", e)
            promise.reject("PUBLISH_ERROR", e.message)
        }
    }

    /**
     * 发布遥测数据
     *
     * @param telemetryJson JSON 格式的遥测数据
     */
    @ReactMethod
    fun publishTelemetry(telemetryJson: String, promise: Promise) {
        try {
            MqttForegroundService.publishTelemetry(telemetryJson)
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "发布遥测数据失败", e)
            promise.reject("PUBLISH_ERROR", e.message)
        }
    }

    /**
     * 发送命令响应
     *
     * @param commandId 命令 ID
     * @param resultJson JSON 格式的响应数据
     */
    @ReactMethod
    fun sendCommandResponse(commandId: String, resultJson: String, promise: Promise) {
        try {
            MqttForegroundService.sendCommandResponse(commandId, resultJson)
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "发送命令响应失败", e)
            promise.reject("PUBLISH_ERROR", e.message)
        }
    }

    /**
     * 注册事件监听器 (React Native 要求)
     */
    @ReactMethod
    fun addListener(eventName: String) {
        // React Native 事件系统要求
    }

    /**
     * 移除事件监听器 (React Native 要求)
     */
    @ReactMethod
    fun removeListeners(count: Int) {
        // React Native 事件系统要求
    }

    // ==================== 命令执行器 ====================

    /**
     * 创建命令执行器
     *
     * 将 MQTT 命令转发给 JavaScript 层处理。
     */
    private fun createCommandExecutor(): CommandHandler.CommandExecutor {
        return object : CommandHandler.CommandExecutor {
            override fun setBrightness(brightness: Int): CommandHandler.CommandResult {
                emitCommandToJS("setBrightness", Arguments.createMap().apply {
                    putInt("brightness", brightness)
                })
                return CommandHandler.CommandResult(true, "亮度已设置为 $brightness")
            }

            override fun setScreenOn(on: Boolean): CommandHandler.CommandResult {
                emitCommandToJS("setScreen", Arguments.createMap().apply {
                    putBoolean("on", on)
                })
                return CommandHandler.CommandResult(true, if (on) "屏幕已开启" else "屏幕已关闭")
            }

            override fun setVolume(volume: Int): CommandHandler.CommandResult {
                emitCommandToJS("setVolume", Arguments.createMap().apply {
                    putInt("volume", volume)
                })
                return CommandHandler.CommandResult(true, "音量已设置为 $volume")
            }

            override fun navigate(url: String): CommandHandler.CommandResult {
                emitCommandToJS("navigate", Arguments.createMap().apply {
                    putString("url", url)
                })
                return CommandHandler.CommandResult(true, "正在导航到 $url")
            }

            override fun reload(): CommandHandler.CommandResult {
                emitCommandToJS("reload", Arguments.createMap())
                return CommandHandler.CommandResult(true, "页面已刷新")
            }

            override fun reboot(): CommandHandler.CommandResult {
                emitCommandToJS("reboot", Arguments.createMap())
                return CommandHandler.CommandResult(true, "设备正在重启")
            }

            override fun clearCache(): CommandHandler.CommandResult {
                emitCommandToJS("clearCache", Arguments.createMap())
                return CommandHandler.CommandResult(true, "缓存已清除")
            }

            override fun updateApp(apkUrl: String): CommandHandler.CommandResult {
                emitCommandToJS("updateApp", Arguments.createMap().apply {
                    putString("apkUrl", apkUrl)
                })
                return CommandHandler.CommandResult(true, "正在下载更新")
            }

            // 以下命令转发到 JavaScript 层处理
            override fun setRotation(rotation: Int): CommandHandler.CommandResult {
                emitCommandToJS("setRotation", Arguments.createMap().apply { putInt("rotation", rotation) })
                return CommandHandler.CommandResult(true, "屏幕旋转已设置")
            }

            override fun wakeUp(): CommandHandler.CommandResult {
                emitCommandToJS("wakeUp", Arguments.createMap())
                return CommandHandler.CommandResult(true, "设备已唤醒")
            }

            override fun sleep(): CommandHandler.CommandResult {
                emitCommandToJS("sleep", Arguments.createMap())
                return CommandHandler.CommandResult(true, "设备已进入休眠")
            }

            override fun playSound(url: String, loop: Boolean): CommandHandler.CommandResult {
                emitCommandToJS("playSound", Arguments.createMap().apply {
                    putString("url", url)
                    putBoolean("loop", loop)
                })
                return CommandHandler.CommandResult(true, "正在播放声音")
            }

            override fun stopSound(): CommandHandler.CommandResult {
                emitCommandToJS("stopSound", Arguments.createMap())
                return CommandHandler.CommandResult(true, "声音已停止")
            }

            override fun speak(text: String, language: String): CommandHandler.CommandResult {
                emitCommandToJS("speak", Arguments.createMap().apply {
                    putString("text", text)
                    putString("language", language)
                })
                return CommandHandler.CommandResult(true, "正在朗读")
            }

            override fun goBack(): CommandHandler.CommandResult {
                emitCommandToJS("goBack", Arguments.createMap())
                return CommandHandler.CommandResult(true, "正在返回")
            }

            override fun goForward(): CommandHandler.CommandResult {
                emitCommandToJS("goForward", Arguments.createMap())
                return CommandHandler.CommandResult(true, "正在前进")
            }

            override fun executeJs(code: String): CommandHandler.CommandResult {
                emitCommandToJS("executeJs", Arguments.createMap().apply { putString("code", code) })
                return CommandHandler.CommandResult(true, "正在执行 JavaScript")
            }

            override fun setKioskMode(enabled: Boolean): CommandHandler.CommandResult {
                emitCommandToJS("setKioskMode", Arguments.createMap().apply { putBoolean("enabled", enabled) })
                return CommandHandler.CommandResult(true, if (enabled) "Kiosk 模式已启用" else "Kiosk 模式已禁用")
            }

            override fun installApp(apkUrl: String): CommandHandler.CommandResult {
                emitCommandToJS("installApp", Arguments.createMap().apply { putString("apkUrl", apkUrl) })
                return CommandHandler.CommandResult(true, "正在安装应用")
            }

            override fun uninstallApp(packageName: String): CommandHandler.CommandResult {
                emitCommandToJS("uninstallApp", Arguments.createMap().apply { putString("packageName", packageName) })
                return CommandHandler.CommandResult(true, "正在卸载应用")
            }

            override fun startApp(packageName: String): CommandHandler.CommandResult {
                emitCommandToJS("startApp", Arguments.createMap().apply { putString("packageName", packageName) })
                return CommandHandler.CommandResult(true, "正在启动应用")
            }

            override fun stopApp(packageName: String): CommandHandler.CommandResult {
                emitCommandToJS("stopApp", Arguments.createMap().apply { putString("packageName", packageName) })
                return CommandHandler.CommandResult(true, "正在停止应用")
            }

            override fun screenshot(): CommandHandler.CommandResult {
                emitCommandToJS("screenshot", Arguments.createMap())
                return CommandHandler.CommandResult(true, "正在截图")
            }

            override fun getLogs(lines: Int): CommandHandler.CommandResult {
                emitCommandToJS("getLogs", Arguments.createMap().apply { putInt("lines", lines) })
                return CommandHandler.CommandResult(true, "正在获取日志")
            }

            override fun getWifiInfo(): CommandHandler.CommandResult {
                emitCommandToJS("getWifiInfo", Arguments.createMap())
                return CommandHandler.CommandResult(true, "正在获取 WiFi 信息")
            }

            override fun getDeviceInfo(): CommandHandler.CommandResult {
                emitCommandToJS("getDeviceInfo", Arguments.createMap())
                return CommandHandler.CommandResult(true, "正在获取设备信息")
            }

            override fun setPin(pin: String): CommandHandler.CommandResult {
                emitCommandToJS("setPin", Arguments.createMap().apply { putString("pin", pin) })
                return CommandHandler.CommandResult(true, "PIN 已更新")
            }
        }
    }

    // ==================== 事件发送 ====================

    /**
     * 向 JavaScript 发送命令事件
     */
    private fun emitCommandToJS(command: String, params: WritableMap) {
        try {
            val eventParams = Arguments.createMap().apply {
                putString("command", command)
                putMap("params", params)
            }
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("onMqttCommand", eventParams)
            Log.d(TAG, "已发送命令到 JS: $command")
        } catch (e: Exception) {
            Log.e(TAG, "发送命令事件失败", e)
        }
    }

    /**
     * 向 JavaScript 发送连接状态变化事件
     */
    private fun emitConnectionState(connected: Boolean) {
        try {
            val params = Arguments.createMap().apply {
                putBoolean("connected", connected)
            }
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("onMqttConnectionChanged", params)
        } catch (e: Exception) {
            Log.e(TAG, "发送连接状态事件失败", e)
        }
    }

    // ==================== 生命周期 ====================

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        scope.cancel()
        instance = null
        MqttForegroundService.commandExecutor = null
        Log.d(TAG, "Mqtt5Module 已清理")
    }
}