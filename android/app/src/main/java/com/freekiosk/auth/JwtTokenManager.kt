package com.freekiosk.auth

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.util.Date

/**
 * JWT Token 管理器
 *
 * 负责安全存储和管理 JWT 访问令牌和刷新令牌。
 * 使用 EncryptedSharedPreferences 确保令牌的安全存储。
 *
 * 主要功能:
 * - 安全存储 Access Token 和 Refresh Token
 * - Token 过期检测和自动刷新
 * - Token 生命周期管理
 */
class JwtTokenManager(private val context: Context) {

    companion object {
        private const val TAG = "JwtTokenManager"
        private const val PREFS_FILE_NAME = "freekiosk_auth_tokens"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_TENANT_ID = "tenant_id"
    }

    // 加密共享 preferences
    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * 存储 Token 对
     *
     * @param accessToken 访问令牌
     * @param refreshToken 刷新令牌
     * @param expiresAt 过期时间戳（毫秒）
     * @param deviceId 设备 ID
     * @param tenantId 租户 ID
     */
    fun storeTokens(
        accessToken: String,
        refreshToken: String,
        expiresAt: Long,
        deviceId: String,
        tenantId: String
    ): Boolean {
        return try {
            encryptedPrefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putLong(KEY_TOKEN_EXPIRES_AT, expiresAt)
                .putString(KEY_DEVICE_ID, deviceId)
                .putString(KEY_TENANT_ID, tenantId)
                .apply()
            Log.i(TAG, "Tokens stored successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store tokens", e)
            false
        }
    }

    /**
     * 获取 Access Token
     */
    fun getAccessToken(): String? {
        return try {
            encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get access token", e)
            null
        }
    }

    /**
     * 获取 Refresh Token
     */
    fun getRefreshToken(): String? {
        return try {
            encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get refresh token", e)
            null
        }
    }

    /**
     * 获取 Token 过期时间
     */
    fun getTokenExpiresAt(): Long {
        return try {
            encryptedPrefs.getLong(KEY_TOKEN_EXPIRES_AT, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get token expiry", e)
            0
        }
    }

    /**
     * 获取设备 ID
     */
    fun getDeviceId(): String? {
        return try {
            encryptedPrefs.getString(KEY_DEVICE_ID, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get device ID", e)
            null
        }
    }

    /**
     * 获取租户 ID
     */
    fun getTenantId(): String? {
        return try {
            encryptedPrefs.getString(KEY_TENANT_ID, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get tenant ID", e)
            null
        }
    }

    /**
     * 检查是否需要刷新 Token
     *
     * @param thresholdMs 过期阈值（毫秒），默认 5 分钟
     * @return 是否需要刷新
     */
    fun needsRefresh(thresholdMs: Long = AuthConfig.DEFAULT_TOKEN_REFRESH_THRESHOLD): Boolean {
        val expiresAt = getTokenExpiresAt()
        if (expiresAt == 0L) return true

        val now = System.currentTimeMillis()
        return (expiresAt - now) < thresholdMs
    }

    /**
     * 检查 Token 是否已过期
     */
    fun isTokenExpired(): Boolean {
        val expiresAt = getTokenExpiresAt()
        if (expiresAt == 0L) return true
        return System.currentTimeMillis() >= expiresAt
    }

    /**
     * 检查是否有有效的 Token
     */
    fun hasValidToken(): Boolean {
        val accessToken = getAccessToken()
        return !accessToken.isNullOrEmpty() && !isTokenExpired()
    }

    /**
     * 更新 Access Token（刷新后）
     */
    fun updateAccessToken(accessToken: String, expiresAt: Long): Boolean {
        return try {
            encryptedPrefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putLong(KEY_TOKEN_EXPIRES_AT, expiresAt)
                .apply()
            Log.i(TAG, "Access token updated")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update access token", e)
            false
        }
    }

    /**
     * 更新 Refresh Token
     */
    fun updateRefreshToken(refreshToken: String): Boolean {
        return try {
            encryptedPrefs.edit()
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply()
            Log.i(TAG, "Refresh token updated")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update refresh token", e)
            false
        }
    }

    /**
     * 清除所有 Token
     */
    fun clearTokens() {
        try {
            encryptedPrefs.edit().clear().apply()
            Log.i(TAG, "All tokens cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear tokens", e)
        }
    }

    /**
     * 解析 JWT Token 获取过期时间
     */
    fun parseTokenExpiry(token: String): Long? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null

            // 解码 payload (第二部分)
            val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
            val json = JSONObject(payload)

            // 获取 exp 字段（秒级时间戳）
            if (json.has("exp")) {
                json.getLong("exp") * 1000 // 转换为毫秒
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse token expiry", e)
            null
        }
    }

    /**
     * 获取 Token 信息（用于调试）
     */
    fun getTokenInfo(): Map<String, Any?> {
        return mapOf(
            "hasAccessToken" to !getAccessToken().isNullOrEmpty(),
            "hasRefreshToken" to !getRefreshToken().isNullOrEmpty(),
            "expiresAt" to getTokenExpiresAt(),
            "expiresAtDate" to Date(getTokenExpiresAt()).toString(),
            "deviceId" to getDeviceId(),
            "tenantId" to getTenantId(),
            "needsRefresh" to needsRefresh(),
            "isExpired" to isTokenExpired()
        )
    }
}
