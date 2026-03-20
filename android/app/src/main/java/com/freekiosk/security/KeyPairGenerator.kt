package com.freekiosk.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Date

/**
 * RSA 密钥对生成器
 *
 * 使用 Android Keystore 生成 RSA 2048 位密钥对。
 * 私钥永远不会离开 Keystore，确保安全。
 */
class KeyPairGenerator(private val alias: String = DEFAULT_ALIAS) {

    companion object {
        private const val TAG = "KeyPairGenerator"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_ALIAS = "freekiosk_client_key"
        private const val KEY_SIZE = 2048
    }

    /**
     * 生成新的 RSA 密钥对
     *
     * @param validityDays 证书有效期（天数）
     * @return 生成的密钥对
     */
    fun generateKeyPair(validityDays: Int = 365): KeyPair? {
        return try {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                ANDROID_KEYSTORE
            )

            val now = System.currentTimeMillis()
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(KEY_SIZE)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setDigests(
                    KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA384,
                    KeyProperties.DIGEST_SHA512
                )
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setKeyValidityStart(Date(now))
                .setKeyValidityEnd(Date(now + validityDays.toLong() * 24 * 60 * 60 * 1000))
                .setUserAuthenticationRequired(false)
                .build()

            keyPairGenerator.initialize(spec)
            val keyPair = keyPairGenerator.generateKeyPair()

            Log.i(TAG, "RSA key pair generated: alias=$alias, keySize=$KEY_SIZE")
            keyPair

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate key pair", e)
            null
        }
    }

    /**
     * 获取已存在的密钥对
     *
     * @return 密钥对，如果不存在则返回 null
     */
    fun getKeyPair(): KeyPair? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            if (entry != null) {
                KeyPair(entry.certificate.publicKey, entry.privateKey)
            } else {
                Log.d(TAG, "Key pair not found: alias=$alias")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get key pair", e)
            null
        }
    }

    /**
     * 获取公钥
     */
    fun getPublicKey(): PublicKey? {
        return getKeyPair()?.public
    }

    /**
     * 获取私钥
     */
    fun getPrivateKey(): PrivateKey? {
        return getKeyPair()?.private
    }

    /**
     * 检查密钥对是否存在
     */
    fun keyPairExists(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.containsAlias(alias)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check key existence", e)
            false
        }
    }

    /**
     * 删除密钥对
     */
    fun deleteKeyPair(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
                Log.i(TAG, "Key pair deleted: alias=$alias")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete key pair", e)
            false
        }
    }

    /**
     * 获取或创建密钥对
     *
     * @param validityDays 证书有效期（天数）
     * @return 密钥对
     */
    fun getOrCreateKeyPair(validityDays: Int = 365): KeyPair? {
        return getKeyPair() ?: generateKeyPair(validityDays)
    }
}
