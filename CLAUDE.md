# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此代码库中工作提供指导。

## 项目概述

FreeKiosk 是一个面向 Android 平板 (8.0+) 的免费开源 kiosk 模式应用，通过 Android Device Owner API 实现完整的设备锁定，并集成了 REST API 和 MQTT 用于 Home Assistant。

## 开发命令

```bash
# 安装依赖
npm install

# 启动 Metro bundler
npm start

# 在 Android 设备上运行
npm run android

# 代码检查
npm run lint

# 运行测试
npm run test

# 构建发布版 APK
cd android && gradlew assembleRelease

# 构建 AAB (Play Store，禁用自更新)
cd android && gradlew bundleRelease -Pplaystore
```

## 架构概览

### 前端 (React Native + TypeScript)
- **入口**: `App.tsx` → `AppNavigator.tsx` (React Navigation 栈)
- **主要页面**: `KioskScreen` (WebView 显示), `PinScreen`, `SettingsScreenNew` (4 个标签页：通用、显示、安全、高级)
- **状态管理**: AsyncStorage 持久化，react-native-keychain 安全存储 (PIN、API 密钥)
- **原生桥接**: React Native 模块向 JavaScript 暴露 Android Device Owner API

### 后端 (原生 Android - Kotlin)
位于 `android/app/src/main/java/com/freekiosk/`:
- **KioskModule**: Device Owner API、锁定任务模式、系统 UI 隐藏
- **HttpServerModule**: 基于 NanoHTTPD 的 REST API 服务器 (40+ 端点)
- **MqttModule**: HiveMQ MQTT 客户端，支持 Home Assistant 自动发现 (27 个实体)
- **AccessibilityServiceModule**: 跨应用键盘注入和按键分发
- **MotionDetectionModule**: 基于摄像头的运动检测 (CameraX + Camera2 回退)
- **UpdateModule**: 通过 GitHub Releases 实现应用内更新

### 关键原生服务
- `KioskWatchdogService`: OOM 杀死后自动重启 FreeKiosk
- `BootLockActivity`: 低端设备的原生 lock-task 活动 (React Native 启动前运行)
- `BackgroundAppMonitorService`: 监控外部应用前台状态
- `OverlayService`: 外部应用模式下的触摸日志阻断覆盖层

### 显示模式
1. **WebView 模式**: 单个 URL 或仪表板网格 + 导航栏
2. **External App 模式**: 锁定任意已安装的 Android 应用 (测试版)
3. **Media Player 模式**: 视频/图片播放列表，支持交叉淡入淡出、随机播放

### 通信模式
- 原生模块通过 `ReactContext` 向 JavaScript 发送事件
- REST API 和 MQTT 通过共享的原生命令处理器分发命令
- ADB 配置使用 intent extras → SharedPreferences → AsyncStorage 桥接

## 重要文件

| 路径 | 用途 |
|------|------|
| `App.tsx` | 根组件 |
| `src/navigation/AppNavigator.tsx` | React Navigation 栈配置 |
| `src/screens/KioskScreen.tsx` | 主 WebView/显示逻辑 |
| `src/screens/settings/SettingsScreenNew.tsx` | 设置 UI (Material 标签页) |
| `android/app/src/main/java/com/freekiosk/KioskModule.kt` | Device Owner API、锁定任务 |
| `android/app/src/main/java/com/freekiosk/api/KioskHttpServer.kt` | REST API 服务器 |
| `android/app/src/main/java/com/freekiosk/mqtt/KioskMqttClient.kt` | MQTT 客户端 |
| `android/app/build.gradle` | 原生依赖、构建配置 |

## 原生模块模式

添加新功能时遵循此结构：
```
android/app/src/main/java/com/freekiosk/
├── YourModule.kt      // ReactModule 实现
└── YourPackage.kt     // ReactPackage 注册
```

在 `MainApplication.kt` 的 `getPackages()` 中注册。

## 测试说明

- 在真实 Android 设备上测试（模拟器不支持 Device Owner 功能）
- Device Owner 模式需要恢复出厂设置或新设备配置
- MQTT 和 REST API 可同时运行
- 运动检测需要相机权限，默认在屏幕保护模式下工作

## 文档

- `docs/REST_API.md`: 40+ HTTP 端点用于 Home Assistant
- `docs/MQTT.md`: MQTT + Home Assistant 自动发现 (27 个实体)
- `docs/ADB_CONFIG.md`: 通过 ADB intent 进行无头配置
- `docs/INSTALL.md`: 安装和 Device Owner 设置
