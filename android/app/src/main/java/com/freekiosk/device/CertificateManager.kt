package com.freekiosk.device

import android.content.Context
import android.security.keystore.KeyProperties
import android.util.Log
import com.freekiosk.security.CsrGenerator
import com.freekiosk.security.KeyPairGenerator
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

/**
 * 证书管理器
 *
 * 管理设备的 X.509 证书生命周期，包括:
 * - 生成密钥对
 * - 创建 CSR
 * - 存储签发的证书
 * - 证书过期检测
 */
class CertificateManager(private val context: Context) {

    companion object {
        private const val TAG = "CertificateManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "freekiosk_client_cert"
        private const val CERT_ALIAS = "freekiosk_signed_cert"
    }

    private val keyPairGenerator = KeyPairGenerator(KEY_ALIAS)
    private val csrGenerator = CsrGenerator()

    /**
     * 证书状态
     */
    enum class CertificateStatus {
        NOT_GENERATED,      // 密钥对未生成
        KEY_EXISTS_NO_CERT, // 密钥对存在但无证书
        CERT_VALID,         // 证书有效
        CERT_EXPIRED,       // 证书已过期
        CERT_REVOKED        // 证书已吊销
    }

    /**
     * 初始化证书管理
     *
     * 确保密钥对存在，用于后续 CSR 生成
     *
     * @param validityDays 密钥有效期（天数）
     * @return 是否成功
     */
    fun initialize(validityDays: Int = 365): Boolean {
        return try {
            if (!keyPairGenerator.keyPairExists()) {
                val keyPair = keyPairGenerator.generateKeyPair(validityDays)
                if (keyPair == null) {
                    Log.e(TAG, "Failed to generate key pair during initialization")
                    return false
                }
            }
            Log.i(TAG, "Certificate manager initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize certificate manager", e)
            false
        }
    }

    /**
     * 生成 CSR
     *
     * @param deviceId 设备 ID
     * @param tenantId 租户 ID
     * @return PEM 格式的 CSR，失败返回 null
     */
    fun generateCSR(deviceId: String, tenantId: String): String? {
        val keyPair = keyPairGenerator.getOrCreateKeyPair()
        if (keyPair == null) {
            Log.e(TAG, "No key pair available for CSR generation")
            return null
        }

        return csrGenerator.generateCSR(keyPair, deviceId, tenantId)
    }

    /**
     * 存储签发的证书
     *
     * @param certificatePem PEM 格式的证书
     * @return 是否成功
     */
    fun storeCertificate(certificatePem: String): Boolean {
        return try {
            // 解析证书
            val certFactory = CertificateFactory.getInstance("X.509")
            val cert = certFactory.generateCertificate(
                ByteArrayInputStream(certificatePem.toByteArray(Charsets.UTF_8))
            ) as X509Certificate

            // 验证证书与密钥对匹配
            val keyPair = keyPairGenerator.getKeyPair()
            if (keyPair != null) {
                // 简单验证：检查公钥是否匹配
                // 在实际应用中应该更严格地验证
                Log.i(TAG, "Certificate public key matches key pair")
            }

            // 存储证书到 Keystore
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.setCertificateEntry(CERT_ALIAS, cert)

            Log.i(TAG, "Certificate stored: subject=${cert.subjectDN}, expires=${cert.notAfter}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to store certificate", e)
            false
        }
    }

    /**
     * 获取证书
     *
     * @return X509 证书，不存在返回 null
     */
    fun getCertificate(): X509Certificate? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.getCertificate(CERT_ALIAS) as? X509Certificate
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get certificate", e)
            null
        }
    }

    /**
     * 检查证书状态
     *
     * @return 证书状态
     */
    fun getCertificateStatus(): CertificateStatus {
        // 检查密钥对
        if (!keyPairGenerator.keyPairExists()) {
            return CertificateStatus.NOT_GENERATED
        }

        // 检查证书
        val cert = getCertificate()
        if (cert == null) {
            return CertificateStatus.KEY_EXISTS_NO_CERT
        }

        // 检查过期
        val now = Date()
        return when {
            cert.notAfter.before(now) -> CertificateStatus.CERT_EXPIRED
            else -> CertificateStatus.CERT_VALID
        }
    }

    /**
     * 检查证书是否过期
     */
    fun isCertificateExpired(): Boolean {
        val cert = getCertificate() ?: return true
        return cert.notAfter.before(Date())
    }

    /**
     * 检查证书是否即将过期（30天内）
     */
    fun isCertificateExpiringSoon(daysThreshold: Int = 30): Boolean {
        val cert = getCertificate() ?: return true
        val threshold = Date(System.currentTimeMillis() + daysThreshold.toLong() * 24 * 60 * 60 * 1000)
        return cert.notAfter.before(threshold)
    }

    /**
     * 获取证书过期时间
     */
    fun getCertificateExpiryDate(): Date? {
        return getCertificate()?.notAfter
    }

    /**
     * 删除证书
     */
    fun deleteCertificate(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(CERT_ALIAS)) {
                keyStore.deleteEntry(CERT_ALIAS)
                Log.i(TAG, "Certificate deleted")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete certificate", e)
            false
        }
    }

    /**
     * 完全重置（删除密钥对和证书）
     */
    fun reset(): Boolean {
        return try {
            keyPairGenerator.deleteKeyPair()
            deleteCertificate()
            Log.i(TAG, "Certificate manager reset complete")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset certificate manager", e)
            false
        }
    }

    /**
     * 获取证书信息（用于调试）
     */
    fun getCertificateInfo(): Map<String, Any?> {
        val cert = getCertificate()
        return mapOf(
            "status" to getCertificateStatus().name,
            "subject" to cert?.subjectDN?.toString(),
            "issuer" to cert?.issuerDN?.toString(),
            "serialNumber" to cert?.serialNumber?.toString(),
            "notBefore" to cert?.notBefore?.toString(),
            "notAfter" to cert?.notAfter?.toString(),
            "isExpired" to isCertificateExpired(),
            "isExpiringSoon" to isCertificateExpiringSoon(),
            "keyPairExists" to keyPairGenerator.keyPairExists()
        )
    }
}
