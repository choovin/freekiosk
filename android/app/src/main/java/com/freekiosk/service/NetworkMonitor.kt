package com.freekiosk.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

/**
 * 网络连接状态监控器
 *
 * 监听设备网络连接变化，并在网络恢复时触发回调。
 * 用于 MQTT 连接的自动恢复。
 *
 * @property context Android 应用上下文
 */
class NetworkMonitor(private val context: Context) {
    companion object {
        private const val TAG = "NetworkMonitor"
    }

    // 系统连接管理器
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // 网络回调
    private var callback: ConnectivityManager.NetworkCallback? = null

    /**
     * 注册网络状态回调
     *
     * @param onAvailable 网络可用回调
     * @param onLost 网络丢失回调
     */
    fun registerCallback(
        onAvailable: () -> Unit,
        onLost: () -> Unit
    ) {
        // 构建网络请求 - 需要互联网能力
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "网络可用: $network")
                onAvailable()
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "网络丢失: $network")
                onLost()
            }

            override fun onUnavailable() {
                Log.w(TAG, "网络不可用")
                onLost()
            }
        }

        connectivityManager.registerNetworkCallback(request, callback!!)
        Log.i(TAG, "网络状态回调已注册")
    }

    /**
     * 注销网络状态回调
     */
    fun unregisterCallback() {
        callback?.let {
            connectivityManager.unregisterNetworkCallback(it)
            Log.i(TAG, "网络状态回调已注销")
        }
        callback = null
    }

    /**
     * 检查当前网络是否可用
     *
     * @return 网络是否可用
     */
    fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * 获取当前网络类型
     *
     * @return 网络类型字符串 (WiFi/Cellular/Unknown)
     */
    fun getNetworkType(): String {
        val network = connectivityManager.activeNetwork ?: return "None"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Unknown"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Unknown"
        }
    }
}