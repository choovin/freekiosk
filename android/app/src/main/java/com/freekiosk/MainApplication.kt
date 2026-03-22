package com.freekiosk

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.freekiosk.api.HttpServerPackage
import com.freekiosk.security.SecurityPolicyPackage
import com.freekiosk.security.AppWhitelistPackage
import com.freekiosk.auth.AuthPackage
import com.freekiosk.mqtt.MqttPackage
import com.freekiosk.mqtt5.Mqtt5Package

class MainApplication : Application(), ReactApplication {

  override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          // Packages that cannot be autolinked yet can be added manually here
          add(KioskPackage())
          add(CertificatePackage())
          add(MotionDetectionPackage())
          add(AppLauncherPackage())
          add(OverlayPermissionPackage())
          add(LauncherPackage())
          add(OverlayServicePackage())
          add(SystemInfoPackage())
          add(UpdatePackage())
          add(HttpServerPackage())
          add(MqttPackage())
          add(Mqtt5Package())  // MQTT 5.0 企业级通信模块
          add(AuthPackage())    // 企业级认证模块
          add(BlockingOverlayPackage())
          add(AutoBrightnessPackage())
          add(PrintPackage())
          add(AccessibilityPackage())
          add(FilePickerPackage())
          add(SecurityPolicyPackage())  // 安全策略模块
          add(AppWhitelistPackage())     // 应用白名单模块
          add(QrScannerPackage())       // QR 扫描模块
        },
    )
  }

  override fun onCreate() {
    super.onCreate()
    loadReactNative(this)
  }
}
