package com.freekiosk.device

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.SecureRandom
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Play Integrity API 检查器
 *
 * 用于验证设备完整性和应用完整性。
 * 支持 MEETS_DEVICE_INTEGRITY, MEETS_BASIC_INTEGRITY, MEETS_STRONG_INTEGRITY。
 */
class PlayIntegrityChecker(private val context: Context) {

    companion object {
        private const val TAG = "PlayIntegrityChecker"
        private const val NONCE_LENGTH = 24
    }

    private val integrityManager: IntegrityManager = IntegrityManagerFactory.create(context)

    /**
     * 完整性验证结果
     */
    data class IntegrityResult(
        val token: String? = null,
        val deviceRecognitionVerdict: DeviceVerdict = DeviceVerdict.UNKNOWN,
        val appRecognitionVerdict: AppVerdict = AppVerdict.UNKNOWN,
        val error: String? = null
    )

    /**
     * 设备完整性判定
     */
    enum class DeviceVerdict {
        MEETS_STRONG_INTEGRITY,  // 设备通过强完整性检查
        MEETS_DEVICE_INTEGRITY,  // 设备通过完整性检查
        MEETS_BASIC_INTEGRITY,   // 设备通过基本完整性检查
        NO_INTEGRITY,            // 设备未通过完整性检查
        UNKNOWN                  // 未知（可能是检查失败）
    }

    /**
     * 应用完整性判定
     */
    enum class AppVerdict {
        PLAY_RECOGNIZED,        // 使用 Google Play 认可的版本
        UNRECOGNIZED_VERSION,   // 使用未认证的版本
        UNEVALUATED,            // 未评估
        UNKNOWN                 // 未知
    }

    /**
     * 生成随机 Nonce
     *
     * @return Base64 编码的随机 Nonce
     */
    fun generateNonce(): String {
        val nonce = ByteArray(NONCE_LENGTH)
        SecureRandom().nextBytes(nonce)
        return Base64.getEncoder().encodeToString(nonce)
    }

    /**
     * 请求完整性 Token
     *
     * @param cloudProjectNumber Google Cloud 项目编号
     * @param nonce 用于请求的 Nonce
     * @return IntegrityTokenResponse 的 Task
     */
    fun requestIntegrityToken(
        cloudProjectNumber: Long,
        nonce: String
    ): Task<IntegrityTokenResponse> {
        Log.d(TAG, "Requesting integrity token with nonce: ${nonce.take(10)}...")

        val request = IntegrityTokenRequest.builder()
            .setNonce(nonce)
            .setCloudProjectNumber(cloudProjectNumber)
            .build()

        return integrityManager.requestIntegrityToken(request)
    }

    /**
     * 检查完整性（挂起函数版本）
     *
     * @param cloudProjectNumber Google Cloud 项目编号
     * @param nonce 用于请求的 Nonce（可选，不提供则自动生成）
     * @return 完整性检查结果
     */
    suspend fun checkIntegrity(
        cloudProjectNumber: Long,
        nonce: String? = null
    ): IntegrityResult = suspendCancellableCoroutine { continuation ->
        val actualNonce = nonce ?: generateNonce()

        val request = IntegrityTokenRequest.builder()
            .setNonce(actualNonce)
            .setCloudProjectNumber(cloudProjectNumber)
            .build()

        integrityManager.requestIntegrityToken(request)
            .addOnSuccessListener { response ->
                val token = response.token()
                Log.i(TAG, "Integrity token received: ${token.take(20)}...")
                continuation.resume(IntegrityResult(token = token))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Integrity check failed", e)
                continuation.resume(
                    IntegrityResult(
                        error = e.message ?: "Unknown error",
                        deviceRecognitionVerdict = DeviceVerdict.UNKNOWN,
                        appRecognitionVerdict = AppVerdict.UNKNOWN
                    )
                )
            }
    }

    /**
     * 解析完整性 Token（客户端解析，仅用于调试）
     *
     * 注意：生产环境中应该在服务端解析和验证 Token
     */
    fun parseTokenLocally(token: String): IntegrityResult {
        return try {
            // JWT Token 解析
            val parts = token.split(".")
            if (parts.size != 3) {
                return IntegrityResult(error = "Invalid token format")
            }

            // 解码 payload
            val payload = String(
                android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE),
                Charsets.UTF_8
            )

            // 解析 JSON
            val json = org.json.JSONObject(payload)

            // 提取设备判定
            val deviceVerdict = parseDeviceVerdict(json.optJSONObject("deviceIntegrity"))

            // 提取应用判定
            val appVerdict = parseAppVerdict(json.optString("appRecognitionVerdict"))

            IntegrityResult(
                token = token,
                deviceRecognitionVerdict = deviceVerdict,
                appRecognitionVerdict = appVerdict
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse token", e)
            IntegrityResult(error = e.message ?: "Parse error")
        }
    }

    private fun parseDeviceVerdict(deviceIntegrity: org.json.JSONObject?): DeviceVerdict {
        if (deviceIntegrity == null) return DeviceVerdict.UNKNOWN

        val verdicts = deviceIntegrity.optJSONArray("deviceRecognitionVerdict")
        if (verdicts == null || verdicts.length() == 0) {
            return DeviceVerdict.NO_INTEGRITY
        }

        // 检查最高级别的完整性
        for (i in 0 until verdicts.length()) {
            when (verdicts.getString(i)) {
                "MEETS_STRONG_INTEGRITY" -> return DeviceVerdict.MEETS_STRONG_INTEGRITY
                "MEETS_DEVICE_INTEGRITY" -> return DeviceVerdict.MEETS_DEVICE_INTEGRITY
                "MEETS_BASIC_INTEGRITY" -> return DeviceVerdict.MEETS_BASIC_INTEGRITY
            }
        }

        return DeviceVerdict.NO_INTEGRITY
    }

    private fun parseAppVerdict(verdict: String): AppVerdict {
        return when (verdict) {
            "PLAY_RECOGNIZED" -> AppVerdict.PLAY_RECOGNIZED
            "UNRECOGNIZED_VERSION" -> AppVerdict.UNRECOGNIZED_VERSION
            "UNEVALUATED" -> AppVerdict.UNEVALUATED
            else -> AppVerdict.UNKNOWN
        }
    }

    /**
     * 检查设备是否满足最低完整性要求
     *
     * @param result 完整性检查结果
     * @param minimumLevel 最低要求的完整性级别
     * @return 是否满足要求
     */
    fun meetsMinimumRequirement(
        result: IntegrityResult,
        minimumLevel: DeviceVerdict = DeviceVerdict.MEETS_BASIC_INTEGRITY
    ): Boolean {
        if (result.error != null) return false

        return when (minimumLevel) {
            DeviceVerdict.MEETS_STRONG_INTEGRITY ->
                result.deviceRecognitionVerdict == DeviceVerdict.MEETS_STRONG_INTEGRITY
            DeviceVerdict.MEETS_DEVICE_INTEGRITY ->
                result.deviceRecognitionVerdict == DeviceVerdict.MEETS_STRONG_INTEGRITY ||
                result.deviceRecognitionVerdict == DeviceVerdict.MEETS_DEVICE_INTEGRITY
            DeviceVerdict.MEETS_BASIC_INTEGRITY ->
                result.deviceRecognitionVerdict != DeviceVerdict.NO_INTEGRITY &&
                result.deviceRecognitionVerdict != DeviceVerdict.UNKNOWN
            else -> true
        }
    }
}
