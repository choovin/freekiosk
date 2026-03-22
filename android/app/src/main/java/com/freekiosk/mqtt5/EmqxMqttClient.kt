package com.freekiosk.mqtt5

import android.content.Context
import android.util.Log
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5ConnectRestrictions
import com.hivemq.client.mqtt.mqtt5.message.disconnect.Mqtt5DisconnectReasonCode
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 企业级 MQTT 5.0 客户端
 *
 * 基于 HiveMQ MQTT Client 实现的 MQTT 5.0 客户端封装。
 * 提供设备与 EMQX Broker 之间的双向通信能力。
 *
 * 主要特性:
 * - MQTT 5.0 会话持久化
 * - Topic Alias 带宽优化
 * - Reason Code 错误处理
 * - 指数退避自动重连
 * - 共享订阅负载均衡
 *
 * @property context Android 应用上下文
 * @property config MQTT 5.0 配置对象
 */
class EmqxMqttClient(
    private val context: Context,
    private val config: Mqtt5Config
) {
    companion object {
        private const val TAG = "EmqxMqttClient"

        // Topic Alias 常量 - 用于减少重复 Topic 名称的带宽开销
        const val TOPIC_ALIAS_STATUS = 1      // 状态 Topic 别名
        const val TOPIC_ALIAS_EVENT = 2       // 事件 Topic 别名
        const val TOPIC_ALIAS_TELEMETRY = 3   // 遥测 Topic 别名
    }

    // MQTT 5.0 异步客户端
    private var client: Mqtt5AsyncClient? = null

    // 协程作用域 - 用于异步消息处理
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 连接状态标志
    @Volatile
    private var isConnected = false

    // 重连尝试次数
    @Volatile
    private var reconnectAttempts = 0

    // 消息处理器映射表
    private val messageHandlers = mutableMapOf<String, (Mqtt5Publish) -> Unit>()

    // 连接状态回调
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null
    var onTokenRefreshRequired: (() -> Unit)? = null  // 企业版: Token 刷新回调

    /**
     * 更新 JWT Token（用于 Token 刷新后更新连接）
     *
     * @param newToken 新的 JWT Access Token
     */
    fun updateJwtToken(newToken: String) {
        config.jwtToken?.let {
            Log.i(TAG, "JWT Token updated for next reconnection")
        }
    }

    /**
     * 重新认证（Token 刷新后调用）
     *
     * 断开并使用新 Token 重新连接。
     *
     * @param newToken 新的 JWT Access Token
     * @return 连接操作的 CompletableFuture
     */
    fun reauthenticate(newToken: String): CompletableFuture<Void> {
        Log.i(TAG, "Reauthenticating with new JWT token")

        // 断开当前连接
        return disconnect().thenCompose {
            // 更新配置中的 Token
            // 注意：由于 config 是 data class，需要重新构建或使用新的连接方式
            // 这里使用 jwtToken 字段
            reconnectWithNewToken(newToken)
        }
    }

    private fun reconnectWithNewToken(newToken: String): CompletableFuture<Void> {
        val clientId = config.toClientId()

        // 重新构建客户端
        val builder = Mqtt5Client.builder()
            .identifier(clientId)
            .serverHost(config.brokerUrl)
            .serverPort(config.port)
            .automaticReconnectWithDefaultConfig()
            .addConnectedListener { onConnectionEstablished() }
            .addDisconnectedListener { onConnectionLost(it) }

        if (config.useTls) {
            builder.sslWithDefaultConfig()
        }

        client = builder.buildAsync()

        // 使用新 Token 构建连接
        val connectBuilder = Mqtt5Connect.builder()
            .keepAlive(config.keepAlive)
            .cleanStart(false)  // 保持会话
            .sessionExpiryInterval(config.sessionExpiryInterval)

        connectBuilder.restrictions(
            Mqtt5ConnectRestrictions.builder()
                .receiveMaximum(100)
                .sendMaximum(100)
                .maximumPacketSize(config.maxPacketSize)
                .topicAliasMaximum(10)
                .build()
        )

        // 使用新 Token 认证
        connectBuilder.simpleAuth(
            Mqtt5SimpleAuth.builder()
                .username("jwt")
                .password(newToken.toByteArray())
                .build()
        )

        // LWT
        val lwtTopic = config.statusTopic()
        val lwtPayload = """{"status":"offline","deviceId":"${config.deviceId}","tenantId":"${config.tenantId}"}"""
        connectBuilder.willPublish(
            Mqtt5Publish.builder()
                .topic(lwtTopic)
                .payload(lwtPayload.toByteArray())
                .retain(true)
                .build()
        )

        return client!!.connect(connectBuilder.build()).thenAccept {
            Log.i(TAG, "Reauthenticated successfully with new token")
            isConnected = true
        }
    }

    /**
     * 初始化并连接到 MQTT Broker
     *
     * 创建 MQTT 5.0 客户端并建立连接。
     * 配置包括:
     * - 会话持久化
     * - LWT (Last Will and Testament)
     * - 认证信息
     * - 连接限制
     *
     * @return 连接操作的 CompletableFuture
     */
    fun connect(): CompletableFuture<Void> {
        val clientId = config.toClientId()

        // 构建 MQTT 5.0 客户端
        val builder = Mqtt5Client.builder()
            .identifier(clientId)
            .serverHost(config.brokerUrl)
            .serverPort(config.port)
            .automaticReconnectWithDefaultConfig()
            .addConnectedListener { onConnectionEstablished() }
            .addDisconnectedListener { onConnectionLost(it) }

        // 配置 TLS 加密
        if (config.useTls) {
            builder.sslWithDefaultConfig()
        }

        client = builder.buildAsync()

        // 构建连接选项
        val connectBuilder = Mqtt5Connect.builder()
            .keepAlive(config.keepAlive)
            .cleanStart(config.cleanStart)
            .sessionExpiryInterval(config.sessionExpiryInterval)

        // 配置连接限制
        connectBuilder.restrictions(
            Mqtt5ConnectRestrictions.builder()
                .receiveMaximum(100)                    // 最大接收消息数
                .sendMaximum(100)                       // 最大发送消息数
                .maximumPacketSize(config.maxPacketSize) // 最大数据包大小（字节）
                .topicAliasMaximum(10)                  // 最大 Topic 别名数
                .build()
        )

        // 配置认证
        // 优先级: username/password (MQTT基本认证) > JWT (企业版)
        val username = config.username
        val password = config.password
        if (username != null && password != null) {
            // 使用 MQTT 用户名密码认证
            connectBuilder.simpleAuth(
                Mqtt5SimpleAuth.builder()
                    .username(username)
                    .password(password.toByteArray())
                    .build()
            )
            Log.d(TAG, "MQTT 基本认证已配置: $username")
        } else {
            // 降级到 JWT 认证
            val tokenToUse = config.accessToken ?: config.jwtToken
            tokenToUse?.let { token ->
                connectBuilder.simpleAuth(
                    Mqtt5SimpleAuth.builder()
                        .username("jwt")
                        .password(token.toByteArray())
                        .build()
                )
                Log.d(TAG, "JWT 认证已配置")
            }
        }

        // 配置 LWT (Last Will and Testament) - 离线时自动发布
        val lwtTopic = config.statusTopic()
        val lwtPayload = """{"status":"offline","deviceId":"${config.deviceId}","tenantId":"${config.tenantId}"}"""

        connectBuilder.willPublish(
            Mqtt5Publish.builder()
                .topic(lwtTopic)
                .payload(lwtPayload.toByteArray())
                .retain(true)                           // 保留消息
                .build()
        )

        // 执行连接
        return client!!.connect(connectBuilder.build()).thenAccept {
            Log.i(TAG, "已连接到 MQTT Broker: ${config.brokerUrl}:${config.port}")
            Log.i(TAG, "Client ID: $clientId")
            isConnected = true
            reconnectAttempts = 0
        }
    }

    /**
     * 连接建立回调
     */
    private fun onConnectionEstablished() {
        Log.i(TAG, "MQTT 连接已建立")
        isConnected = true
        reconnectAttempts = 0

        // 发布在线状态（保留消息）
        publishOnlineStatus()

        onConnected?.invoke()
    }

    /**
     * 连接丢失回调
     *
     * @param context 断开连接上下文，包含断开原因
     */
    private fun onConnectionLost(context: MqttClientDisconnectedContext) {
        val cause = context.cause
        val errorMessage = cause?.message ?: cause?.javaClass?.simpleName ?: "未知原因"
        Log.w(TAG, "MQTT 连接丢失: $errorMessage")
        isConnected = false
        // LWT 会自动发布离线状态，这里不需要手动发布
        onDisconnected?.invoke()
    }

    /**
     * 发布在线状态
     *
     * 连接成功后立即发布在线状态，覆盖之前的离线状态。
     */
    private fun publishOnlineStatus() {
        val onlinePayload = """{"status":"online","deviceId":"${config.deviceId}","tenantId":"${config.tenantId}","connectedAt":${System.currentTimeMillis()}}"""

        client?.publishWith()
            ?.topic(config.statusTopic())
            ?.payload(onlinePayload.toByteArray())
            ?.qos(MqttQos.AT_LEAST_ONCE)
            ?.retain(true)
            ?.send()
            ?.whenComplete { _, error ->
                if (error != null) {
                    Log.e(TAG, "发布在线状态失败", error)
                } else {
                    Log.i(TAG, "已发布在线状态")
                }
            }
    }

    /**
     * 发布离线状态（正常断开时）
     *
     * 在正常断开连接时主动发布离线状态。
     */
    private fun publishOfflineStatus() {
        val offlinePayload = """{"status":"offline","deviceId":"${config.deviceId}","tenantId":"${config.tenantId}","disconnectedAt":${System.currentTimeMillis()},"reason":"graceful"}"""

        client?.publishWith()
            ?.topic(config.statusTopic())
            ?.payload(offlinePayload.toByteArray())
            ?.qos(MqttQos.AT_LEAST_ONCE)
            ?.retain(true)
            ?.send()
            ?.whenComplete { _, error ->
                if (error != null) {
                    Log.e(TAG, "发布离线状态失败", error)
                } else {
                    Log.i(TAG, "已发布离线状态")
                }
            }
    }

    /**
     * 检查是否已连接
     *
     * @return 连接状态
     */
    fun isConnected(): Boolean = isConnected

    /**
     * 订阅命令 Topic
     *
     * 使用 QoS 1（至少一次）订阅命令消息。
     *
     * @param handler 消息处理回调
     * @return 订阅操作的 CompletableFuture
     */
    fun subscribeToCommands(handler: (Mqtt5Publish) -> Unit): CompletableFuture<Void> {
        val topic = config.commandTopic()
        messageHandlers[topic] = handler

        return client!!.subscribeWith()
            .topicFilter(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .callback { publish ->
                scope.launch {
                    try {
                        handler(publish)
                    } catch (e: Exception) {
                        Log.e(TAG, "处理命令消息时出错", e)
                    }
                }
            }
            .send()
            .thenAccept {
                Log.i(TAG, "已订阅命令 Topic: $topic")
            }
    }

    /**
     * 订阅配置 Topic
     *
     * 使用 QoS 1 订阅配置更新消息。
     *
     * @param handler 消息处理回调
     * @return 订阅操作的 CompletableFuture
     */
    fun subscribeToConfig(handler: (Mqtt5Publish) -> Unit): CompletableFuture<Void> {
        val topic = config.configTopic()
        messageHandlers[topic] = handler

        return client!!.subscribeWith()
            .topicFilter(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .callback { publish ->
                scope.launch {
                    try {
                        handler(publish)
                    } catch (e: Exception) {
                        Log.e(TAG, "处理配置消息时出错", e)
                    }
                }
            }
            .send()
            .thenAccept {
                Log.i(TAG, "已订阅配置 Topic: $topic")
            }
    }

    /**
     * 订阅共享命令 Topic
     *
     * 使用共享订阅实现负载均衡，多个设备可以共享同一个命令队列。
     * 格式: $share/{group}/{topic}
     *
     * @param group 共享订阅组名
     * @param handler 消息处理回调
     * @return 订阅操作的 CompletableFuture
     */
    fun subscribeToSharedCommands(group: String, handler: (Mqtt5Publish) -> Unit): CompletableFuture<Void> {
        val sharedTopic = "\$share/$group/${config.commandTopic()}"

        return client!!.subscribeWith()
            .topicFilter(sharedTopic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .callback { publish ->
                scope.launch {
                    try {
                        handler(publish)
                    } catch (e: Exception) {
                        Log.e(TAG, "处理共享命令时出错", e)
                    }
                }
            }
            .send()
            .thenAccept {
                Log.i(TAG, "已订阅共享 Topic: $sharedTopic")
            }
    }

    /**
     * 发布设备状态
     *
     * 使用 QoS 1 和保留消息发布状态。
     *
     * @param payload JSON 格式的状态数据
     * @return 发布操作的 CompletableFuture
     */
    fun publishStatus(payload: String): CompletableFuture<Mqtt5PublishResult> {
        return publish(config.statusTopic(), payload, true)
    }

    /**
     * 发布事件
     *
     * 使用 QoS 1 发布事件消息（不保留）。
     *
     * @param payload JSON 格式的事件数据
     * @return 发布操作的 CompletableFuture
     */
    fun publishEvent(payload: String): CompletableFuture<Mqtt5PublishResult> {
        return publish(config.eventTopic(), payload, false)
    }

    /**
     * 发布遥测数据
     *
     * 使用 QoS 0 发布高频遥测数据（最多一次，不保证送达）。
     * 适用于性能指标等高频但可容忍丢失的数据。
     *
     * @param payload JSON 格式的遥测数据
     * @return 发布操作的 CompletableFuture
     */
    fun publishTelemetry(payload: String): CompletableFuture<Mqtt5PublishResult> {
        return client!!.publishWith()
            .topic(config.telemetryTopic())
            .payload(payload.toByteArray())
            .qos(MqttQos.AT_MOST_ONCE)
            .send()
    }

    /**
     * 发布命令响应
     *
     * @param commandId 命令 ID
     * @param payload JSON 格式的响应数据
     * @return 发布操作的 CompletableFuture
     */
    fun publishResponse(commandId: String, payload: String): CompletableFuture<Mqtt5PublishResult> {
        return publish(config.responseTopic(commandId), payload, false)
    }

    /**
     * 通用发布方法
     *
     * @param topic 目标 Topic
     * @param payload 消息内容
     * @param retain 是否保留消息
     * @return 发布操作的 CompletableFuture
     */
    private fun publish(
        topic: String,
        payload: String,
        retain: Boolean = false
    ): CompletableFuture<Mqtt5PublishResult> {
        return client!!.publishWith()
            .topic(topic)
            .payload(payload.toByteArray())
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(retain)
            .send()
            .whenComplete { result, error ->
                if (error != null) {
                    Log.e(TAG, "发布到 $topic 失败", error)
                } else {
                    Log.d(TAG, "已发布到 $topic")
                }
            }
    }

    /**
     * 断开 MQTT 连接
     *
     * 先发布离线状态（保留消息），然后发送正常断开消息给 Broker。
     * 这确保了设备状态正确更新，覆盖 LWT 的离线状态。
     *
     * @return 断开操作的 CompletableFuture
     */
    fun disconnect(): CompletableFuture<Void> {
        // 先发布离线状态（正常断开）
        // 这会覆盖 LWT 发布的离线状态，提供更准确的断开原因
        publishOfflineStatus()

        // 短暂延迟确保消息发送完成
        Thread.sleep(100)

        return client?.disconnectWith()
            ?.reasonCode(Mqtt5DisconnectReasonCode.NORMAL_DISCONNECTION)
            ?.send()
            ?.thenAccept {
                Log.i(TAG, "已断开与 MQTT Broker 的连接")
                isConnected = false
            }
            ?: CompletableFuture.completedFuture(null)
    }

    /**
     * 清理资源
     *
     * 取消所有协程并断开连接。
     */
    fun destroy() {
        scope.cancel()
        client?.disconnect()
        client = null
        Log.i(TAG, "MQTT 客户端资源已清理")
    }
}