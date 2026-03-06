package com.freekiosk

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Handler
import android.os.Looper
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "android.intent.action.REBOOT") {
            
            DebugLog.d("BootReceiver", "Boot detected: ${intent.action}")
            
            // Re-enable accessibility service if Device Owner (includes managed apps whitelist)
            reEnableAccessibilityIfDeviceOwner(context)
            
            // Add delay to ensure system is ready (important for Android 9)
            Handler(Looper.getMainLooper()).postDelayed({
                // Check if auto-launch is enabled in settings before launching
                if (!isAutoLaunchEnabled(context)) {
                    DebugLog.d("BootReceiver", "Auto-launch is disabled, not starting app")
                    return@postDelayed
                }
                
                DebugLog.d("BootReceiver", "Auto-launch is enabled, starting app")
                
                // Launch background apps (launchOnBoot=true) before launching FreeKiosk
                launchBackgroundApps(context)
                
                // Give boot apps a moment to initialize before FreeKiosk takes foreground
                Thread.sleep(1000)
                
                // Launch the app on startup (FreeKiosk will be on top)
                val launchIntent = Intent(context, MainActivity::class.java)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                
                try {
                    context.startActivity(launchIntent)
                    DebugLog.d("BootReceiver", "Successfully launched MainActivity")
                } catch (e: Exception) {
                    DebugLog.errorProduction("BootReceiver", "Failed to launch app: ${e.message}")
                }
                
                // Start BackgroundAppMonitorService for keep-alive apps
                startBackgroundMonitorIfNeeded(context)
            }, 3000) // 3 second delay to ensure system is ready
        }
    }
    
    /**
     * Re-enable the accessibility service after boot if the app is Device Owner.
     * Android 13+ can disable accessibility services of sideloaded apps after reboot.
     * Also re-applies the managed apps accessibility whitelist.
     */
    private fun reEnableAccessibilityIfDeviceOwner(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (!dpm.isDeviceOwnerApp(context.packageName)) {
                DebugLog.d("BootReceiver", "Not Device Owner, skipping accessibility re-enable")
                return
            }

            val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)
            val serviceComponent = ComponentName(context, FreeKioskAccessibilityService::class.java)
            val serviceName = "${context.packageName}/${serviceComponent.className}"

            // Build permitted list: FreeKiosk + managed apps with allowAccessibility=true
            val permitted = mutableListOf(context.packageName)
            permitted.addAll(getManagedAppsWithAccessibility(context))
            
            dpm.setPermittedAccessibilityServices(adminComponent, permitted.distinct())
            DebugLog.d("BootReceiver", "Permitted accessibility services: $permitted")

            // Check if FreeKiosk's own service is already enabled
            val currentServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            if (!currentServices.contains(serviceName)) {
                val newServices = if (currentServices.isEmpty()) serviceName
                    else "$currentServices:$serviceName"
                Settings.Secure.putString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    newServices
                )
                Settings.Secure.putString(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    "1"
                )
                DebugLog.d("BootReceiver", "Accessibility service re-enabled after boot")
            } else {
                DebugLog.d("BootReceiver", "Accessibility service already enabled")
            }
        } catch (e: Exception) {
            DebugLog.errorProduction("BootReceiver", "Failed to re-enable accessibility: ${e.message}")
        }
    }

    /**
     * Launch managed apps that have launchOnBoot=true.
     * They are launched in the background (not brought to foreground).
     */
    private fun launchBackgroundApps(context: Context) {
        try {
            val apps = getManagedAppsForBoot(context)
            if (apps.isEmpty()) {
                DebugLog.d("BootReceiver", "No background apps to launch on boot")
                return
            }
            
            val pm = context.packageManager
            for (packageName in apps) {
                try {
                    // Verify app is installed first
                    try {
                        pm.getPackageInfo(packageName, 0)
                    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                        DebugLog.d("BootReceiver", "Boot app not installed, skipping: $packageName")
                        continue
                    }
                    
                    val launchIntent = pm.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        DebugLog.d("BootReceiver", "Boot app launched: $packageName")
                        // Small delay between launches to avoid overwhelming the system
                        Thread.sleep(500)
                    } else {
                        DebugLog.d("BootReceiver", "No launch intent for boot app: $packageName")
                    }
                } catch (e: Exception) {
                    DebugLog.errorProduction("BootReceiver", "Failed to launch boot app $packageName: ${e.message}")
                }
            }
        } catch (e: Exception) {
            DebugLog.errorProduction("BootReceiver", "Error launching boot apps: ${e.message}")
        }
    }

    /**
     * Start the BackgroundAppMonitorService if any managed app has keepAlive=true.
     */
    private fun startBackgroundMonitorIfNeeded(context: Context) {
        try {
            val keepAliveApps = getManagedAppsForKeepAlive(context)
            if (keepAliveApps.isEmpty()) {
                DebugLog.d("BootReceiver", "No keep-alive apps configured")
                return
            }
            
            val serviceIntent = Intent(context, BackgroundAppMonitorService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            DebugLog.d("BootReceiver", "BackgroundAppMonitorService started for ${keepAliveApps.size} apps")
        } catch (e: Exception) {
            DebugLog.errorProduction("BootReceiver", "Failed to start BackgroundAppMonitorService: ${e.message}")
        }
    }

    /**
     * Read managed apps from AsyncStorage and return those with allowAccessibility=true.
     */
    private fun getManagedAppsWithAccessibility(context: Context): List<String> {
        return getManagedAppsFiltered(context) { it.optBoolean("allowAccessibility", false) }
    }

    /**
     * Read managed apps from AsyncStorage and return those with launchOnBoot=true.
     */
    private fun getManagedAppsForBoot(context: Context): List<String> {
        return getManagedAppsFiltered(context) { it.optBoolean("launchOnBoot", false) }
    }

    /**
     * Read managed apps from AsyncStorage and return those with keepAlive=true.
     */
    private fun getManagedAppsForKeepAlive(context: Context): List<String> {
        return getManagedAppsFiltered(context) { it.optBoolean("keepAlive", false) }
    }

    /**
     * Generic helper to read managed apps from AsyncStorage with a filter predicate.
     */
    private fun getManagedAppsFiltered(context: Context, predicate: (org.json.JSONObject) -> Boolean): List<String> {
        return try {
            val dbPath = context.getDatabasePath("RKStorage").absolutePath
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
                    if (predicate(app)) {
                        packages.add(app.getString("packageName"))
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
            DebugLog.d("BootReceiver", "Could not read managed apps: ${e.message}")
            emptyList()
        }
    }

    /**
     * Check if auto-launch is enabled by reading from AsyncStorage (React Native storage)
     * Modern AsyncStorage (@react-native-async-storage/async-storage v2.x) uses SQLite database
     */
    private fun isAutoLaunchEnabled(context: Context): Boolean {
        return try {
            // AsyncStorage uses SQLite database "RKStorage" with table "catalystLocalStorage"
            val dbPath = context.getDatabasePath("RKStorage").absolutePath
            val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
            
            val cursor = db.rawQuery(
                "SELECT value FROM catalystLocalStorage WHERE key = ?",
                arrayOf("@kiosk_auto_launch")
            )
            
            val isEnabled = if (cursor.moveToFirst()) {
                val value = cursor.getString(0)
                cursor.close()
                db.close()
                
                // AsyncStorage stores values as JSON strings, so "true" or "false"
                val enabled = value == "true"
                DebugLog.d("BootReceiver", "Auto-launch setting: $enabled (value=$value)")
                enabled
            } else {
                cursor.close()
                db.close()
                
                // If not set, default to false (don't auto-launch unless explicitly enabled)
                DebugLog.d("BootReceiver", "Auto-launch setting not found, defaulting to false")
                false
            }
            
            isEnabled
        } catch (e: Exception) {
            DebugLog.errorProduction("BootReceiver", "Error reading auto-launch setting: ${e.message}")
            // In case of error, don't launch (safer default)
            false
        }
    }
}
