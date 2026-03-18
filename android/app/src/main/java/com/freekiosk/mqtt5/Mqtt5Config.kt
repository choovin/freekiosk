package com.freekiosk.mqtt5

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * MQTT 5.0 配置数据类
 *
 * 用于企业级 Kiosk 平台的 MQTT 连接配置。
 * 支持 TLS 加密、JWT 认证和会话持久化。
 *
 * @property brokerUrl MQTT Broker 地址
 * @property port MQTT Broker 端口
 * @property tenantId 租户 ID（用于多租户隔离）
 * @property deviceId 设备 ID（唯一标识）
 * @property useTls 是否使用 TLS 加密
 * @property jwtToken JWT 认证令牌（可选）
 * @property clientCertPath 客户端证书路径（可选，用于双向 TLS）
 * @property clientKeyPath 客户端私钥路径（可选，用于双向 TLS）
 * @property keepAlive 心跳间隔（秒）
 * @property cleanStart 是否清除之前的会话状态
 * @property sessionExpiryInterval 会话过期时间（秒）
 * @property maxPacketSize 最大数据包大小（字节）
 * @property statusInterval 状态上报间隔（毫秒）
 * @property autoReconnect 是否自动重连
 * @property reconnectDelayMin 最小重连延迟（毫秒）
 * @property reconnectDelayMax 最大重连延迟（毫秒）
 */
@Parcelize
data class Mqtt5Config(
    // 连接设置
    val brokerUrl: String,
    val port: Int = 1883,
    val tenantId: String,
    val deviceId: String,
    val useTls: Boolean = false,

    // 认证配置
    val jwtToken: String? = null,
    val clientCertPath: String? = null,
    val clientKeyPath: String? = null,

    // MQTT 5.0 特定配置
    val keepAlive: Int = 60,                    // 心跳间隔（秒）
    val cleanStart: Boolean = false,            // 是否清除会话
    val sessionExpiryInterval: Long = 3600,     // 会话过期时间：1小时
    val maxPacketSize: Int = 1048576,           // 最大数据包：1MB

    // 应用层配置
    val statusInterval: Long = 30000,           // 状态上报间隔：30秒
    val autoReconnect: Boolean = true,          // 自动重连
    val reconnectDelayMin: Long = 1000,         // 最小重连延迟：1秒
    val reconnectDelayMax: Long = 30000         // 最大重连延迟：30秒
) : Parcelable {

    /**
     * 生成 MQTT Client ID
     *
     * 遵循企业级格式: kiosk_{tenantId}_{deviceId}
     * 例如: kiosk_tenant001_device123
     *
     * @return 格式化的 Client ID 字符串
     */
    fun toClientId(): String {
        return "kiosk_${tenantId}_${deviceId}"
    }

    /**
     * 获取设备的基础 Topic
     *
     * 格式: kiosk/{tenantId}/{deviceId}
     * 例如: kiosk/tenant001/device123
     *
     * @return 基础 Topic 字符串
     */
    fun baseTopic(): String {
        return "kiosk/${tenantId}/${deviceId}"
    }

    /**
     * 获取状态 Topic
     *
     * 用于发布设备状态信息（保留消息）
     * 格式: kiosk/{tenantId}/{deviceId}/status
     *
     * @return 状态 Topic 字符串
     */
    fun statusTopic(): String = "${baseTopic()}/status"

    /**
     * 获取命令 Topic
     *
     * 用于接收来自服务器的命令
     * 格式: kiosk/{tenantId}/{deviceId}/command
     *
     * @return 命令 Topic 字符串
     */
    fun commandTopic(): String = "${baseTopic()}/command"

    /**
     * 获取配置 Topic
     *
     * 用于接收配置更新
     * 格式: kiosk/{tenantId}/{deviceId}/config
     *
     * @return 配置 Topic 字符串
     */
    fun configTopic(): String = "${baseTopic()}/config"

    /**
     * 获取事件 Topic
     *
     * 用于发布设备事件（如用户交互、错误等）
     * 格式: kiosk/{tenantId}/{deviceId}/event
     *
     * @return 事件 Topic 字符串
     */
    fun eventTopic(): String = "${baseTopic()}/event"

    /**
     * 获取遥测 Topic
     *
     * 用于发布高频遥测数据（如性能指标）
     * 格式: kiosk/{tenantId}/{deviceId}/telemetry
     *
     * @return 遥测 Topic 字符串
     */
    fun telemetryTopic(): String = "${baseTopic()}/telemetry"

    /**
     * 获取响应 Topic
     *
     * 用于发送命令执行结果
     * 格式: kiosk/{tenantId}/{deviceId}/response/{commandId}
     *
     * @param commandId 命令 ID
     * @return 响应 Topic 字符串
     */
    fun responseTopic(commandId: String): String = "${baseTopic()}/response/$commandId"

    /**
     * 获取固件更新 Topic
     *
     * 用于接收固件更新通知
     * 格式: kiosk/{tenantId}/{deviceId}/firmware
     *
     * @return 固件 Topic 字符串
     */
    fun firmwareTopic(): String = "${baseTopic()}/firmware"

    companion object {
        /**
         * 默认 MQTT 端口
         */
        const val DEFAULT_PORT = 1883

        /**
         * 默认 MQTT TLS 端口
         */
        const val DEFAULT_TLS_PORT = 8883

        /**
         * 默认心跳间隔（秒）
         */
        const val DEFAULT_KEEP_ALIVE = 60

        /**
         * 默认会话过期时间（秒）
         */
        const val DEFAULT_SESSION_EXPIRY = 3600L

        /**
         * 默认最大数据包大小（1MB）
         */
        const val DEFAULT_MAX_PACKET_SIZE = 1048576

        /**
         * 默认状态上报间隔（毫秒）
         */
        const val DEFAULT_STATUS_INTERVAL = 30000L
    }
}