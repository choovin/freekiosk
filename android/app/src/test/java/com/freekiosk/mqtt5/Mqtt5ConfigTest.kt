package com.freekiosk.mqtt5

import org.junit.Assert.*
import org.junit.Test

/**
 * MQTT 5.0 配置类的单元测试
 *
 * 测试 Mqtt5Config 数据类的各种功能：
 * - Client ID 生成
 * - Topic 构建
 * - 默认值验证
 */
class Mqtt5ConfigTest {

    // =====================================
    // Client ID 测试
    // =====================================

    /**
     * 测试 Client ID 格式
     *
     * 预期格式: kiosk_{tenantId}_{deviceId}
     */
    @Test
    fun testClientIdFormat() {
        val config = Mqtt5Config(
            brokerUrl = "emqx.example.com",
            tenantId = "tenant001",
            deviceId = "device123"
        )

        val expectedClientId = "kiosk_tenant001_device123"
        assertEquals("Client ID 格式应该正确", expectedClientId, config.toClientId())
    }

    /**
     * 测试不同租户和设备的 Client ID
     */
    @Test
    fun testClientIdWithDifferentValues() {
        val config = Mqtt5Config(
            brokerUrl = "broker.local",
            tenantId = "acme_corp",
            deviceId = "kiosk_lobby_01"
        )

        assertEquals("kiosk_acme_corp_kiosk_lobby_01", config.toClientId())
    }

    // =====================================
    // Topic 构建测试
    // =====================================

    /**
     * 测试基础 Topic 格式
     *
     * 预期格式: kiosk/{tenantId}/{deviceId}
     */
    @Test
    fun testBaseTopicFormat() {
        val config = createTestConfig()

        assertEquals("kiosk/tenant001/device001", config.baseTopic())
    }

    /**
     * 测试状态 Topic
     *
     * 预期格式: kiosk/{tenantId}/{deviceId}/status
     */
    @Test
    fun testStatusTopic() {
        val config = createTestConfig()

        assertEquals("kiosk/tenant001/device001/status", config.statusTopic())
    }

    /**
     * 测试命令 Topic
     *
     * 预期格式: kiosk/{tenantId}/{deviceId}/command
     */
    @Test
    fun testCommandTopic() {
        val config = createTestConfig()

        assertEquals("kiosk/tenant001/device001/command", config.commandTopic())
    }

    /**
     * 测试配置 Topic
     *
     * 预期格式: kiosk/{tenantId}/{deviceId}/config
     */
    @Test
    fun testConfigTopic() {
        val config = createTestConfig()

        assertEquals("kiosk/tenant001/device001/config", config.configTopic())
    }

    /**
     * 测试事件 Topic
     *
     * 预期格式: kiosk/{tenantId}/{deviceId}/event
     */
    @Test
    fun testEventTopic() {
        val config = createTestConfig()

        assertEquals("kiosk/tenant001/device001/event", config.eventTopic())
    }

    /**
     * 测试遥测 Topic
     *
     * 预期格式: kiosk/{tenantId}/{deviceId}/telemetry
     */
    @Test
    fun testTelemetryTopic() {
        val config = createTestConfig()

        assertEquals("kiosk/tenant001/device001/telemetry", config.telemetryTopic())
    }

    /**
     * 测试响应 Topic
     *
     * 预期格式: kiosk/{tenantId}/{deviceId}/response/{commandId}
     */
    @Test
    fun testResponseTopic() {
        val config = createTestConfig()
        val commandId = "cmd-12345"

        assertEquals("kiosk/tenant001/device001/response/cmd-12345", config.responseTopic(commandId))
    }

    /**
     * 测试固件 Topic
     *
     * 预期格式: kiosk/{tenantId}/{deviceId}/firmware
     */
    @Test
    fun testFirmwareTopic() {
        val config = createTestConfig()

        assertEquals("kiosk/tenant001/device001/firmware", config.firmwareTopic())
    }

    // =====================================
    // 默认值测试
    // =====================================

    /**
     * 测试默认端口
     */
    @Test
    fun testDefaultPort() {
        val config = createMinimalConfig()

        assertEquals("默认端口应为 1883", Mqtt5Config.DEFAULT_PORT, config.port)
    }

    /**
     * 测试 TLS 默认关闭
     */
    @Test
    fun testDefaultTlsDisabled() {
        val config = createMinimalConfig()

        assertFalse("TLS 默认应关闭", config.useTls)
    }

    /**
     * 测试默认心跳间隔
     */
    @Test
    fun testDefaultKeepAlive() {
        val config = createMinimalConfig()

        assertEquals("默认心跳应为 60 秒", Mqtt5Config.DEFAULT_KEEP_ALIVE, config.keepAlive)
    }

    /**
     * 测试默认会话过期时间
     */
    @Test
    fun testDefaultSessionExpiry() {
        val config = createMinimalConfig()

        assertEquals("默认会话过期时间应为 1 小时", Mqtt5Config.DEFAULT_SESSION_EXPIRY, config.sessionExpiryInterval)
    }

    /**
     * 测试默认数据包大小
     */
    @Test
    fun testDefaultMaxPacketSize() {
        val config = createMinimalConfig()

        assertEquals("默认最大数据包应为 1MB", Mqtt5Config.DEFAULT_MAX_PACKET_SIZE, config.maxPacketSize)
    }

    /**
     * 测试默认状态上报间隔
     */
    @Test
    fun testDefaultStatusInterval() {
        val config = createMinimalConfig()

        assertEquals("默认状态间隔应为 30 秒", Mqtt5Config.DEFAULT_STATUS_INTERVAL, config.statusInterval)
    }

    /**
     * 测试默认清除会话设置
     */
    @Test
    fun testDefaultCleanStart() {
        val config = createMinimalConfig()

        assertFalse("默认不应清除会话（保持持久会话）", config.cleanStart)
    }

    /**
     * 测试默认自动重连
     */
    @Test
    fun testDefaultAutoReconnect() {
        val config = createMinimalConfig()

        assertTrue("默认应启用自动重连", config.autoReconnect)
    }

    // =====================================
    // TLS 配置测试
    // =====================================

    /**
     * 测试启用 TLS
     */
    @Test
    fun testTlsEnabled() {
        val config = Mqtt5Config(
            brokerUrl = "emqx.example.com",
            port = 8883,
            tenantId = "tenant001",
            deviceId = "device001",
            useTls = true
        )

        assertTrue("TLS 应启用", config.useTls)
        assertEquals("TLS 端口应为 8883", 8883, config.port)
    }

    /**
     * 测试客户端证书配置
     */
    @Test
    fun testClientCertificateConfig() {
        val config = Mqtt5Config(
            brokerUrl = "emqx.example.com",
            tenantId = "tenant001",
            deviceId = "device001",
            useTls = true,
            clientCertPath = "/data/certs/client.pem",
            clientKeyPath = "/data/certs/client.key"
        )

        assertEquals("客户端证书路径应正确", "/data/certs/client.pem", config.clientCertPath)
        assertEquals("客户端私钥路径应正确", "/data/certs/client.key", config.clientKeyPath)
    }

    // =====================================
    // JWT 认证测试
    // =====================================

    /**
     * 测试 JWT Token 配置
     */
    @Test
    fun testJwtTokenConfig() {
        val jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test"
        val config = Mqtt5Config(
            brokerUrl = "emqx.example.com",
            tenantId = "tenant001",
            deviceId = "device001",
            jwtToken = jwtToken
        )

        assertEquals("JWT Token 应正确设置", jwtToken, config.jwtToken)
    }

    // =====================================
    // 辅助方法
    // =====================================

    /**
     * 创建测试用配置
     */
    private fun createTestConfig(): Mqtt5Config {
        return Mqtt5Config(
            brokerUrl = "emqx.example.com",
            tenantId = "tenant001",
            deviceId = "device001"
        )
    }

    /**
     * 创建最小配置（只设置必需参数）
     */
    private fun createMinimalConfig(): Mqtt5Config {
        return Mqtt5Config(
            brokerUrl = "broker.local",
            tenantId = "test_tenant",
            deviceId = "test_device"
        )
    }
}