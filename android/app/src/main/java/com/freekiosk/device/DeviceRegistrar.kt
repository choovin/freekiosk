package com.freekiosk.device

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.freekiosk.auth.AuthConfig
import com.freekiosk.auth.JwtTokenManager
import com.freekiosk.security.CsrGenerator
import com.freekiosk.security.KeyPairGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * 设备注册流程控制器
 *
 * 编排完整的设备注册流程:
 * 1. 生成 RSA 密钥对
 * 2. 创建 CSR (Certificate Signing Request)
 * 3. 获取 Play Integrity Token
 * 4. 调用注册 API
 * 5. 存储证书和 Token
 */
class DeviceRegistrar(private val context: Context, private val config: AuthConfig) {

    companion object {
        private const val TAG = "DeviceRegistrar"
        private const val REGISTRATION_ENDPOINT = "/api/v2/auth/register"
        private const val TOKEN_REFRESH_ENDPOINT = "/api/v2/auth/refresh"
        private const val CONTENT_TYPE_JSON = "application/json"
        private const val TIMEOUT_MS = 30000
    }

    private val certificateManager = CertificateManager(context)
    private val tokenManager = JwtTokenManager(context)
    private val integrityChecker = PlayIntegrityChecker(context)

    /**
     * 注册结果
     */
    sealed class RegistrationResult {
        data class Success(
            val deviceId: String,
            val accessToken: String,
            val refreshToken: String,
            val expiresAt: Long,
            val certificatePem: String
        ) : RegistrationResult()

        data class Error(val message: String, val code: Int? = null) : RegistrationResult()
    }

    /**
     * 执行设备注册
     *
     * @param skipIntegrityCheck 是否跳过完整性检查（用于调试）
     * @return 注册结果
     */
    suspend fun register(skipIntegrityCheck: Boolean = false): RegistrationResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting device registration for tenant=${config.tenantId}, deviceKey=${config.deviceKey}")

        try {
            // 1. 初始化证书管理器
            if (!certificateManager.initialize()) {
                return@withContext RegistrationResult.Error("Failed to initialize certificate manager")
            }

            // 2. 生成 CSR
            val csrPem = certificateManager.generateCSR(config.deviceKey, config.tenantId)
            if (csrPem == null) {
                return@withContext RegistrationResult.Error("Failed to generate CSR")
            }
            Log.d(TAG, "CSR generated: ${csrPem.take(50)}...")

            // 3. 获取设备信息
            val deviceInfo = collectDeviceInfo()

            // 4. 获取 Play Integrity Token（如果配置了）
            var integrityToken: String? = null
            var nonce: String? = null

            if (!skipIntegrityCheck && config.playIntegrityCloudProjectNumber != null) {
                nonce = integrityChecker.generateNonce()
                Log.d(TAG, "Requesting Play Integrity token...")

                val integrityResult = integrityChecker.checkIntegrity(
                    config.playIntegrityCloudProjectNumber,
                    nonce
                )

                if (integrityResult.error != null) {
                    Log.w(TAG, "Play Integrity check failed: ${integrityResult.error}")
                    // 继续注册，但记录警告
                } else {
                    integrityToken = integrityResult.token
                    Log.i(TAG, "Play Integrity token obtained")
                }
            }

            // 5. 构建注册请求
            val requestJson = JSONObject().apply {
                put("tenant_id", config.tenantId)
                put("device_key", config.deviceKey)
                put("csr_pem", csrPem)
                put("device_info", JSONObject(deviceInfo))
                if (integrityToken != null) {
                    put("integrity_token", integrityToken)
                    put("nonce", nonce)
                }
            }

            // 6. 调用注册 API
            val response = callApi(REGISTRATION_ENDPOINT, "POST", requestJson.toString())

            if (!response.success) {
                return@withContext RegistrationResult.Error(
                    response.error ?: "Registration failed",
                    response.statusCode
                )
            }

            // 7. 解析响应
            val responseJson = JSONObject(response.body)
            val deviceId = responseJson.getString("device_id")
            val accessToken = responseJson.getString("access_token")
            val refreshToken = responseJson.getString("refresh_token")
            val expiresAt = responseJson.getLong("expires_at")
            val certificatePem = responseJson.getString("certificate_pem")

            // 8. 存储证书
            if (!certificateManager.storeCertificate(certificatePem)) {
                Log.w(TAG, "Failed to store certificate, continuing anyway")
            }

            // 9. 存储 Token
            if (!tokenManager.storeTokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAt = expiresAt,
                    deviceId = deviceId,
                    tenantId = config.tenantId
                )
            ) {
                Log.w(TAG, "Failed to store tokens")
            }

            Log.i(TAG, "Device registration successful: deviceId=$deviceId")

            RegistrationResult.Success(
                deviceId = deviceId,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
                certificatePem = certificatePem
            )

        } catch (e: Exception) {
            Log.e(TAG, "Registration failed", e)
            RegistrationResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 刷新 Token
     *
     * @param refreshToken 刷新令牌
     * @return 新的 Token 对
     */
    suspend fun refreshToken(refreshToken: String): TokenRefreshResult = withContext(Dispatchers.IO) {
        try {
            val requestJson = JSONObject().apply {
                put("refresh_token", refreshToken)
            }

            val response = callApi(TOKEN_REFRESH_ENDPOINT, "POST", requestJson.toString())

            if (!response.success) {
                return@withContext TokenRefreshResult.Error(response.error ?: "Token refresh failed")
            }

            val responseJson = JSONObject(response.body)
            val newAccessToken = responseJson.getString("access_token")
            val newRefreshToken = responseJson.optString("refresh_token", refreshToken)
            val expiresAt = responseJson.getLong("expires_at")

            // 更新存储
            tokenManager.updateAccessToken(newAccessToken, expiresAt)
            if (newRefreshToken != refreshToken) {
                tokenManager.updateRefreshToken(newRefreshToken)
            }

            TokenRefreshResult.Success(
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
                expiresAt = expiresAt
            )

        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed", e)
            TokenRefreshResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 检查设备是否已注册
     */
    fun isRegistered(): Boolean {
        return tokenManager.hasValidToken() &&
                certificateManager.getCertificateStatus() == CertificateManager.CertificateStatus.CERT_VALID
    }

    /**
     * 获取注册状态
     */
    fun getRegistrationStatus(): RegistrationStatus {
        val hasToken = tokenManager.hasValidToken()
        val certStatus = certificateManager.getCertificateStatus()

        return when {
            hasToken && certStatus == CertificateManager.CertificateStatus.CERT_VALID ->
                RegistrationStatus.REGISTERED

            tokenManager.getAccessToken() != null && tokenManager.isTokenExpired() ->
                RegistrationStatus.TOKEN_EXPIRED

            certStatus == CertificateManager.CertificateStatus.CERT_EXPIRED ->
                RegistrationStatus.CERTIFICATE_EXPIRED

            certStatus == CertificateManager.CertificateStatus.KEY_EXISTS_NO_CERT ->
                RegistrationStatus.PENDING_CERTIFICATE

            certStatus == CertificateManager.CertificateStatus.NOT_GENERATED ->
                RegistrationStatus.NOT_REGISTERED

            else -> RegistrationStatus.NOT_REGISTERED
        }
    }

    /**
     * 收集设备信息
     */
    private fun collectDeviceInfo(): Map<String, Any?> {
        return mapOf(
            "brand" to Build.BRAND,
            "model" to Build.MODEL,
            "android_version" to Build.VERSION.RELEASE,
            "sdk_version" to Build.VERSION.SDK_INT,
            "serial_number" to getSerialNumber(),
            "device_id" to Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
        )
    }

    private fun getSerialNumber(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Build.getSerial()
            } else {
                Build.SERIAL
            }
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * 调用 API
     */
    private fun callApi(endpoint: String, method: String, body: String? = null): ApiResponse {
        val url = URL("${config.apiBaseUrl}$endpoint")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = method
            connection.setRequestProperty("Content-Type", CONTENT_TYPE_JSON)
            connection.setRequestProperty("Accept", CONTENT_TYPE_JSON)
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS

            if (body != null) {
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode
            val success = responseCode in 200..299

            val responseBody = if (success) {
                connection.inputStream.bufferedReader().use(BufferedReader::readText)
            } else {
                connection.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            }

            ApiResponse(
                success = success,
                statusCode = responseCode,
                body = responseBody,
                error = if (!success) "HTTP $responseCode: ${responseBody.take(200)}" else null
            )

        } catch (e: Exception) {
            ApiResponse(
                success = false,
                error = e.message ?: "Network error"
            )
        } finally {
            connection.disconnect()
        }
    }

    private data class ApiResponse(
        val success: Boolean,
        val statusCode: Int? = null,
        val body: String = "",
        val error: String? = null
    )

    sealed class TokenRefreshResult {
        data class Success(
            val accessToken: String,
            val refreshToken: String,
            val expiresAt: Long
        ) : TokenRefreshResult()

        data class Error(val message: String) : TokenRefreshResult()
    }

    enum class RegistrationStatus {
        NOT_REGISTERED,          // 未注册
        PENDING_CERTIFICATE,     // 待签发证书
        REGISTERED,              // 已注册
        TOKEN_EXPIRED,           // Token 已过期
        CERTIFICATE_EXPIRED      // 证书已过期
    }
}
