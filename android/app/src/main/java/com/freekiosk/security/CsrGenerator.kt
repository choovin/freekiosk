package com.freekiosk.security

import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import java.io.StringWriter
import java.security.KeyPair
import java.security.PublicKey
import org.bouncycastle.openssl.jcajce.JcaPEMWriter

/**
 * CSR (Certificate Signing Request) 生成器
 *
 * 使用 Bouncy Castle 库生成 PKCS#10 格式的 CSR。
 * 用于设备注册时向服务器申请证书。
 */
class CsrGenerator {

    companion object {
        private const val TAG = "CsrGenerator"
        private const val SIGNATURE_ALGORITHM = "SHA256WithRSA"
    }

    /**
     * 生成 CSR
     *
     * @param keyPair 密钥对
     * @param commonName 通用名称（通常是设备 ID）
     * @param organization 组织名称
     * @param organizationalUnit 组织单位
     * @param country 国家代码
     * @return PEM 格式的 CSR 字符串
     */
    fun generateCSR(
        keyPair: KeyPair,
        commonName: String,
        organization: String = "FreeKiosk",
        organizationalUnit: String = "Devices",
        country: String = "CN"
    ): String? {
        return try {
            // 构建 Subject DN
            val subject = X500Name(
                "CN=$commonName,OU=$organizationalUnit,O=$organization,C=$country"
            )

            // 创建 CSR Builder
            val csrBuilder = JcaPKCS10CertificationRequestBuilder(
                subject,
                keyPair.public
            )

            // 创建签名器
            val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .build(keyPair.private)

            // 生成 CSR
            val csr: PKCS10CertificationRequest = csrBuilder.build(signer)

            // 转换为 PEM 格式
            val pemWriter = StringWriter()
            JcaPEMWriter(pemWriter).use { writer ->
                writer.writeObject(csr)
            }

            val csrPem = pemWriter.toString()
            Log.i(TAG, "CSR generated: CN=$commonName, size=${csrPem.length}")
            csrPem

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate CSR", e)
            null
        }
    }

    /**
     * 生成 CSR（简化版本）
     *
     * @param keyPair 密钥对
     * @param deviceId 设备 ID
     * @param tenantId 租户 ID
     * @return PEM 格式的 CSR 字符串
     */
    fun generateCSR(
        keyPair: KeyPair,
        deviceId: String,
        tenantId: String
    ): String? {
        return generateCSR(
            keyPair = keyPair,
            commonName = deviceId,
            organization = "FreeKiosk-$tenantId",
            organizationalUnit = "KioskDevices",
            country = "CN"
        )
    }

    /**
     * 验证 CSR 格式
     *
     * @param csrPem PEM 格式的 CSR
     * @return 是否有效
     */
    fun validateCSR(csrPem: String): Boolean {
        return try {
            // 移除 PEM 头尾
            val pemContent = csrPem
                .replace("-----BEGIN CERTIFICATE REQUEST-----", "")
                .replace("-----END CERTIFICATE REQUEST-----", "")
                .replace("\\s".toRegex(), "")

            // Base64 解码
            val derBytes = java.util.Base64.getDecoder().decode(pemContent)

            // 尝试解析 CSR（如果解析成功则说明 CSR 格式有效）
            PKCS10CertificationRequest(derBytes)
            true
        } catch (e: Exception) {
            Log.e(TAG, "CSR validation failed", e)
            false
        }
    }
}
