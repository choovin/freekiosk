package com.freekiosk.auth

import android.content.Context
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.freekiosk.device.CertificateManager
import com.freekiosk.device.DeviceRegistrar
import com.freekiosk.device.PlayIntegrityChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 认证 React Native 桥接模块
 *
 * 为 JavaScript 提供设备注册、Token 管理、证书管理功能。
 */
class AuthModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val TAG = "AuthModule"
        private const val NAME = "AuthModule"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun getName(): String = NAME

    /**
     * 设备注册
     *
     * @param configMap 配置参数
     *   - apiBaseUrl: API 服务器地址 (必需)
     *   - tenantId: 租户 ID (必需)
     *   - deviceKey: 设备唯一标识 (必需)
     *   - playIntegrityCloudProjectNumber: Play Integrity 项目编号 (可选)
     */
    @ReactMethod
    fun registerDevice(configMap: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val apiBaseUrl = configMap.getString("apiBaseUrl")
                if (apiBaseUrl.isNullOrEmpty()) {
                    promise.reject("INVALID_CONFIG", "apiBaseUrl is required")
                    return@launch
                }

                val tenantId = configMap.getString("tenantId")
                if (tenantId.isNullOrEmpty()) {
                    promise.reject("INVALID_CONFIG", "tenantId is required")
                    return@launch
                }

                val deviceKey = configMap.getString("deviceKey")
                if (deviceKey.isNullOrEmpty()) {
                    promise.reject("INVALID_CONFIG", "deviceKey is required")
                    return@launch
                }

                val cloudProjectNumber = if (configMap.hasKey("playIntegrityCloudProjectNumber")) {
                    configMap.getDouble("playIntegrityCloudProjectNumber").toLong()
                } else null

                val authConfig = AuthConfig(
                    apiBaseUrl = apiBaseUrl,
                    tenantId = tenantId,
                    deviceKey = deviceKey,
                    playIntegrityCloudProjectNumber = cloudProjectNumber
                )

                val registrar = DeviceRegistrar(reactContext, authConfig)
                val result = registrar.register(skipIntegrityCheck = cloudProjectNumber == null)

                when (result) {
                    is DeviceRegistrar.RegistrationResult.Success -> {
                        val response = Arguments.createMap().apply {
                            putBoolean("success", true)
                            putString("deviceId", result.deviceId)
                            putString("accessToken", result.accessToken)
                            putString("refreshToken", result.refreshToken)
                            putDouble("expiresAt", result.expiresAt.toDouble())
                            putString("certificatePem", result.certificatePem)
                        }
                        promise.resolve(response)
                    }
                    is DeviceRegistrar.RegistrationResult.Error -> {
                        promise.reject("REGISTRATION_ERROR", result.message)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Registration failed", e)
                promise.reject("REGISTRATION_ERROR", e.message)
            }
        }
    }

    /**
     * 刷新 Token
     */
    @ReactMethod
    fun refreshToken(promise: Promise) {
        scope.launch {
            try {
                val tokenManager = JwtTokenManager(reactContext)
                val refreshToken = tokenManager.getRefreshToken()

                if (refreshToken.isNullOrEmpty()) {
                    promise.reject("NO_REFRESH_TOKEN", "No refresh token available")
                    return@launch
                }

                // TODO: Call API to refresh token
                promise.reject("NOT_IMPLEMENTED", "Token refresh API not yet implemented")

            } catch (e: Exception) {
                Log.e(TAG, "Token refresh failed", e)
                promise.reject("REFRESH_ERROR", e.message)
            }
        }
    }

    /**
     * 检查注册状态
     */
    @ReactMethod
    fun getRegistrationStatus(promise: Promise) {
        try {
            val tokenManager = JwtTokenManager(reactContext)
            val certManager = CertificateManager(reactContext)

            val result = Arguments.createMap().apply {
                putBoolean("hasToken", !tokenManager.getAccessToken().isNullOrEmpty())
                putBoolean("isTokenExpired", tokenManager.isTokenExpired())
                putString("deviceId", tokenManager.getDeviceId())
                putString("tenantId", tokenManager.getTenantId())
                putString("certificateStatus", certManager.getCertificateStatus().name)
                putBoolean("isRegistered",
                    tokenManager.hasValidToken() &&
                    certManager.getCertificateStatus() == CertificateManager.CertificateStatus.CERT_VALID
                )
            }
            promise.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get registration status", e)
            promise.reject("STATUS_ERROR", e.message)
        }
    }

    /**
     * 获取存储的 Token 信息
     */
    @ReactMethod
    fun getTokenInfo(promise: Promise) {
        try {
            val tokenManager = JwtTokenManager(reactContext)
            val info = tokenManager.getTokenInfo()

            val result = Arguments.createMap().apply {
                putBoolean("hasAccessToken", info["hasAccessToken"] as Boolean)
                putBoolean("hasRefreshToken", info["hasRefreshToken"] as Boolean)
                putDouble("expiresAt", (info["expiresAt"] as Long).toDouble())
                putString("expiresAtDate", info["expiresAtDate"] as String?)
                putString("deviceId", info["deviceId"] as String?)
                putString("tenantId", info["tenantId"] as String?)
                putBoolean("needsRefresh", info["needsRefresh"] as Boolean)
                putBoolean("isExpired", info["isExpired"] as Boolean)
            }
            promise.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get token info", e)
            promise.reject("TOKEN_INFO_ERROR", e.message)
        }
    }

    /**
     * 获取证书信息
     */
    @ReactMethod
    fun getCertificateInfo(promise: Promise) {
        try {
            val certManager = CertificateManager(reactContext)
            val info = certManager.getCertificateInfo()

            val result = Arguments.createMap().apply {
                putString("status", info["status"] as String)
                putString("subject", info["subject"] as String?)
                putString("issuer", info["issuer"] as String?)
                putString("serialNumber", info["serialNumber"] as String?)
                putString("notBefore", info["notBefore"] as String?)
                putString("notAfter", info["notAfter"] as String?)
                putBoolean("isExpired", info["isExpired"] as Boolean)
                putBoolean("isExpiringSoon", info["isExpiringSoon"] as Boolean)
                putBoolean("keyPairExists", info["keyPairExists"] as Boolean)
            }
            promise.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get certificate info", e)
            promise.reject("CERT_INFO_ERROR", e.message)
        }
    }

    /**
     * 清除所有认证数据
     */
    @ReactMethod
    fun clearAuthData(promise: Promise) {
        try {
            val tokenManager = JwtTokenManager(reactContext)
            val certManager = CertificateManager(reactContext)

            tokenManager.clearTokens()
            certManager.reset()

            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear auth data", e)
            promise.reject("CLEAR_ERROR", e.message)
        }
    }

    /**
     * 检查 Play Integrity
     */
    @ReactMethod
    fun checkIntegrity(cloudProjectNumber: Double, promise: Promise) {
        scope.launch {
            try {
                val checker = PlayIntegrityChecker(reactContext)
                val result = checker.checkIntegrity(cloudProjectNumber.toLong())

                val response = Arguments.createMap().apply {
                    if (result.error != null) {
                        putBoolean("success", false)
                        putString("error", result.error)
                    } else {
                        putBoolean("success", true)
                        putString("token", result.token)
                        putString("deviceVerdict", result.deviceRecognitionVerdict.name)
                        putString("appVerdict", result.appRecognitionVerdict.name)
                    }
                }
                promise.resolve(response)
            } catch (e: Exception) {
                Log.e(TAG, "Integrity check failed", e)
                promise.reject("INTEGRITY_ERROR", e.message)
            }
        }
    }

    // React Native event system requirements
    @ReactMethod
    fun addListener(eventName: String) {}

    @ReactMethod
    fun removeListeners(count: Int) {}

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        scope.cancel()
    }
}
