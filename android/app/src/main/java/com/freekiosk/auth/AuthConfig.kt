package com.freekiosk.auth

/**
 * 认证配置数据类
 *
 * 定义设备认证相关的配置参数。
 */
data class AuthConfig(
    // 服务端 API 地址
    val apiBaseUrl: String,

    // JWT 配置
    val accessTokenExpiry: Long = 3600000, // 1 hour in milliseconds
    val refreshTokenExpiry: Long = 2592000000, // 30 days in milliseconds
    val tokenRefreshThreshold: Long = 300000, // 5 minutes before expiry

    // 证书配置
    val certificateValidityDays: Int = 365,

    // Play Integrity 配置
    val playIntegrityCloudProjectNumber: Long? = null,

    // 设备注册配置
    val tenantId: String,
    val deviceKey: String
) {
    companion object {
        const val DEFAULT_ACCESS_TOKEN_EXPIRY = 3600000L // 1 hour
        const val DEFAULT_REFRESH_TOKEN_EXPIRY = 2592000000L // 30 days
        const val DEFAULT_TOKEN_REFRESH_THRESHOLD = 300000L // 5 minutes
        const val DEFAULT_CERTIFICATE_VALIDITY_DAYS = 365
    }
}
