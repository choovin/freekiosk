package com.freekiosk.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.freekiosk.R
import com.freekiosk.mqtt5.EmqxMqttClient
import com.freekiosk.mqtt5.Mqtt5Config
import kotlinx.coroutines.*

/**
 * MQTT 前台服务
 *
 * 维护 MQTT 连接的后台服务。
 * 在 Android 8.0+ 上，后台网络连接需要前台服务来保持活跃。
 *
 * 主要功能:
 * - 维持 MQTT 长连接
 * - 网络状态监控和自动重连
 * - 显示连接状态通知
 *
 * 使用方法:
 * ```kotlin
 * MqttForegroundService.start(context, mqttConfig)
 * MqttForegroundService.stop(context)
 * ```
 */
class MqttForegroundService : Service() {

    companion object {
        private const val TAG = "MqttForegroundService"
        private const val CHANNEL_ID = "mqtt_service_channel"
        private const val NOTIFICATION_ID = 1001

        // Intent Extra 键
        const val EXTRA_CONFIG = "mqtt_config"

        /**
         * 启动服务
         *
         * @param context 应用上下文
         * @param config MQTT 配置
         */
        fun start(context: Context, config: Mqtt5Config) {
            val intent = Intent(context, MqttForegroundService::class.java).apply {
                putExtra(EXTRA_CONFIG, config)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止服务
         *
         * @param context 应用上下文
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, MqttForegroundService::class.java))
        }
    }

    // MQTT 客户端
    private var mqttClient: EmqxMqttClient? = null

    // 网络监控器
    private var networkMonitor: NetworkMonitor? = null

    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("正在连接..."))
        Log.i(TAG, "MQTT 前台服务已创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand 被调用")

        // 获取 MQTT 配置
        val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_CONFIG, Mqtt5Config::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_CONFIG)
        }

        config?.let {
            initializeMqtt(it)
        } ?: run {
            Log.e(TAG, "未提供 MQTT 配置，停止服务")
            stopSelf()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "MQTT 前台服务正在销毁")
        scope.cancel()
        networkMonitor?.unregisterCallback()
        mqttClient?.destroy()
        super.onDestroy()
    }

    /**
     * 创建通知渠道
     *
     * Android 8.0+ 需要通知渠道才能显示通知。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MQTT 服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "维持设备与服务器的 MQTT 连接"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建状态通知
     *
     * @param statusText 状态文本
     * @return Notification 对象
     */
    private fun createNotification(statusText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kiosk 服务")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    /**
     * 更新通知状态
     *
     * @param status 状态文本
     */
    private fun updateNotification(status: String) {
        val notification = createNotification(status)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 初始化 MQTT 客户端
     *
     * @param config MQTT 配置
     */
    private fun initializeMqtt(config: Mqtt5Config) {
        // 创建 MQTT 客户端
        mqttClient = EmqxMqttClient(applicationContext, config).apply {
            onConnected = {
                Log.i(TAG, "MQTT 已连接")
                updateNotification("已连接")
            }
            onDisconnected = {
                Log.w(TAG, "MQTT 已断开")
                updateNotification("已断开 - 正在重连...")
            }
            onError = { error ->
                Log.e(TAG, "MQTT 错误", error)
                updateNotification("连接错误: ${error.message}")
            }
        }

        // 设置网络监控
        networkMonitor = NetworkMonitor(applicationContext).apply {
            registerCallback(
                onAvailable = {
                    Log.i(TAG, "网络可用，确保 MQTT 连接")
                    mqttClient?.let { client ->
                        if (!client.isConnected()) {
                            scope.launch {
                                try {
                                    client.connect().await()
                                } catch (e: Exception) {
                                    Log.e(TAG, "重连失败", e)
                                }
                            }
                        }
                    }
                },
                onLost = {
                    Log.w(TAG, "网络丢失，MQTT 将在网络恢复后自动重连")
                    updateNotification("网络不可用")
                }
            )
        }

        // 连接到 Broker
        scope.launch {
            try {
                mqttClient?.connect()?.await()
                Log.i(TAG, "MQTT 连接已发起")

                // 订阅命令和配置 Topic
                mqttClient?.subscribeToCommands { publish ->
                    Log.d(TAG, "收到命令: ${publish.topic}")
                    // TODO: 处理命令消息
                }?.await()

                mqttClient?.subscribeToConfig { publish ->
                    Log.d(TAG, "收到配置更新: ${publish.topic}")
                    // TODO: 处理配置消息
                }?.await()

            } catch (e: Exception) {
                Log.e(TAG, "连接 MQTT Broker 失败", e)
                updateNotification("连接失败: ${e.message}")
            }
        }
    }
}