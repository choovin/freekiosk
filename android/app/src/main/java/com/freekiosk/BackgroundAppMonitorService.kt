package com.freekiosk

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * BackgroundAppMonitorService - Monitors keep-alive managed apps.
 * 
 * Runs as a foreground service to periodically check if managed apps
 * with keepAlive=true are still running. If not, relaunches them.
 * 
 * Uses ActivityManager.getRunningAppProcesses() for reliable process detection
 * instead of UsageStatsManager which only tracks user interaction.
 * 
 * Feature #37: Launch app in background on startup / keep alive
 */
class BackgroundAppMonitorService : Service() {

    companion object {
        private const val TAG = "BgAppMonitor"
        private const val CHANNEL_ID = "freekiosk_bg_monitor"
        private const val NOTIFICATION_ID = 2001
        private const val CHECK_INTERVAL_MS = 30_000L // Check every 30 seconds
    }

    private val handler = Handler(Looper.getMainLooper())
    private var keepAlivePackages = listOf<String>()
    private var isRunning = false
    // Track recent relaunch timestamps to avoid relaunch storms
    private val relaunchTimestamps = mutableMapOf<String, Long>()
    private val RELAUNCH_COOLDOWN_MS = 60_000L // Min 60s between relaunches of same app

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            checkAndRelaunchApps()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Read keep-alive packages from AsyncStorage
        keepAlivePackages = readKeepAlivePackages()
        
        if (keepAlivePackages.isEmpty()) {
            DebugLog.d(TAG, "No keep-alive apps configured, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        // Start as foreground service
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        isRunning = true
        handler.removeCallbacks(checkRunnable)
        handler.postDelayed(checkRunnable, CHECK_INTERVAL_MS)
        
        DebugLog.d(TAG, "Monitoring ${keepAlivePackages.size} keep-alive apps: $keepAlivePackages")
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(checkRunnable)
        super.onDestroy()
        DebugLog.d(TAG, "Service destroyed")
    }

    /**
     * Check if keep-alive apps are running and relaunch if needed.
     */
    private fun checkAndRelaunchApps() {
        // Re-read in case config changed
        keepAlivePackages = readKeepAlivePackages()
        if (keepAlivePackages.isEmpty()) {
            DebugLog.d(TAG, "No more keep-alive apps, stopping service")
            stopSelf()
            return
        }

        val now = System.currentTimeMillis()
        for (packageName in keepAlivePackages) {
            if (!isAppProcessRunning(packageName)) {
                // Check cooldown to avoid relaunch storms
                val lastRelaunch = relaunchTimestamps[packageName] ?: 0L
                if (now - lastRelaunch < RELAUNCH_COOLDOWN_MS) {
                    DebugLog.d(TAG, "Cooldown active for $packageName, skipping relaunch (${(now - lastRelaunch) / 1000}s < ${RELAUNCH_COOLDOWN_MS / 1000}s)")
                    continue
                }
                
                DebugLog.d(TAG, "Keep-alive app process not found, relaunching: $packageName")
                relaunchApp(packageName)
                relaunchTimestamps[packageName] = now
            }
        }
    }

    /**
     * Check if an app's process is actually running using ActivityManager.
     * This is more reliable than UsageStatsManager which only tracks UI interaction.
     */
    private fun isAppProcessRunning(packageName: String): Boolean {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            
            // Method 1: Check running app processes (works for most cases)
            @Suppress("DEPRECATION")
            val runningProcesses = am.runningAppProcesses
            if (runningProcesses != null) {
                for (process in runningProcesses) {
                    if (process.pkgList?.contains(packageName) == true) {
                        return true
                    }
                }
            }
            
            // Method 2: Check running services (catches service-only apps)
            @Suppress("DEPRECATION")
            val runningServices = am.getRunningServices(200)
            if (runningServices != null) {
                for (service in runningServices) {
                    if (service.service.packageName == packageName) {
                        return true
                    }
                }
            }
            
            false
        } catch (e: Exception) {
            DebugLog.d(TAG, "Error checking if $packageName process is running: ${e.message}")
            // On error, assume running to avoid unnecessary relaunches
            true
        }
    }

    /**
     * Relaunch a dead keep-alive app, then bring FreeKiosk back to the foreground.
     * Android has no API to launch an app "in the background", so we launch it
     * and immediately return focus to FreeKiosk.
     */
    private fun relaunchApp(packageName: String) {
        try {
            val pm = packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                DebugLog.d(TAG, "Relaunched: $packageName — bringing FreeKiosk back to front")
                // Bring FreeKiosk back to the foreground after a short delay
                // so the relaunched app has time to initialize
                handler.postDelayed({ bringFreeKioskToFront() }, 800)
            } else {
                DebugLog.d(TAG, "No launch intent for: $packageName")
            }
        } catch (e: Exception) {
            DebugLog.errorProduction(TAG, "Failed to relaunch $packageName: ${e.message}")
        }
    }

    /**
     * Bring FreeKiosk's MainActivity back to the foreground without recreating it.
     */
    private fun bringFreeKioskToFront() {
        try {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            startActivity(intent)
            DebugLog.d(TAG, "FreeKiosk brought back to front")
        } catch (e: Exception) {
            DebugLog.d(TAG, "Failed to bring FreeKiosk to front: ${e.message}")
        }
    }

    /**
     * Read managed apps with keepAlive=true from AsyncStorage.
     */
    private fun readKeepAlivePackages(): List<String> {
        return try {
            val dbPath = getDatabasePath("RKStorage").absolutePath
            val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
            val cursor = db.rawQuery(
                "SELECT value FROM catalystLocalStorage WHERE key = ?",
                arrayOf("@kiosk_managed_apps")
            )
            val result = if (cursor.moveToFirst()) {
                val json = cursor.getString(0) ?: "[]"
                val apps = org.json.JSONArray(json)
                val packages = mutableListOf<String>()
                for (i in 0 until apps.length()) {
                    val app = apps.getJSONObject(i)
                    if (app.optBoolean("keepAlive", false)) {
                        val pkg = app.getString("packageName")
                        // Verify app is still installed
                        try {
                            packageManager.getPackageInfo(pkg, 0)
                            packages.add(pkg)
                        } catch (e: PackageManager.NameNotFoundException) {
                            DebugLog.d(TAG, "Keep-alive app not installed: $pkg")
                        }
                    }
                }
                packages
            } else {
                emptyList()
            }
            cursor.close()
            db.close()
            result
        } catch (e: Exception) {
            DebugLog.d(TAG, "Could not read managed apps: ${e.message}")
            emptyList()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background App Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors managed apps to keep them running"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FreeKiosk")
            .setContentText("Monitoring ${keepAlivePackages.size} background app(s)")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
