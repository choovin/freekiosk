package com.freekiosk.auth

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Token 刷新 Worker
 *
 * 使用 WorkManager 在后台定期刷新 JWT Token。
 * 在 Token 过期前 5 分钟自动触发刷新。
 */
class TokenRefreshWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "TokenRefreshWorker"
        private const val WORK_NAME = "token_refresh_work"

        /**
         * 调度 Token 刷新任务
         *
         * @param context Android 上下文
         * @param refreshIntervalHours 刷新间隔（小时），默认 1 小时
         */
        fun schedule(context: Context, refreshIntervalHours: Long = 1L) {
            val workRequest = PeriodicWorkRequestBuilder<TokenRefreshWorker>(
                refreshIntervalHours, TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Log.i(TAG, "Token refresh worker scheduled: interval=${refreshIntervalHours}h")
        }

        /**
         * 取消 Token 刷新任务
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Token refresh worker cancelled")
        }

        /**
         * 触发一次性刷新
         */
        fun refreshNow(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<TokenRefreshWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.i(TAG, "One-time token refresh triggered")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting token refresh check")

        val tokenManager = JwtTokenManager(applicationContext)

        // 检查是否需要刷新
        if (!tokenManager.needsRefresh()) {
            Log.d(TAG, "Token does not need refresh yet")
            return Result.success()
        }

        val refreshToken = tokenManager.getRefreshToken()
        if (refreshToken.isNullOrEmpty()) {
            Log.w(TAG, "No refresh token available")
            return Result.failure()
        }

        // 执行刷新
        return try {
            val result = performTokenRefresh(refreshToken)
            if (result) {
                Log.i(TAG, "Token refresh successful")
                Result.success()
            } else {
                Log.w(TAG, "Token refresh failed")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh error", e)
            Result.retry()
        }
    }

    /**
     * 执行 Token 刷新
     */
    private suspend fun performTokenRefresh(refreshToken: String): Boolean {
        // TODO: 实现实际的 API 调用
        // 这将在 AuthModule 中实现
        return false
    }
}
