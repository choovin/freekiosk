# FreeKiosk Android 构建说明

## 环境要求

在开始之前，请确保已安装以下工具：

- **Node.js** >= 20
- **JDK** 17+
- **Android SDK** (包含 Build Tools 36.0.0)
- **Android NDK** 27.1.12297006

### 环境变量设置

```bash
# 设置 Android SDK 路径
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_SDK_ROOT=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/36.0.0
```

## 构建方法

### 方法 1: 使用构建脚本（推荐）

#### 构建调试版和发布版

```bash
cd freekiosk
chmod +x build-android.sh
./build-android.sh
```

#### 仅构建调试版（快速）

```bash
cd freekiosk
chmod +x build-debug.sh
./build-debug.sh
```

### 方法 2: 手动构建

```bash
cd freekiosk

# 安装依赖
npm install

# 进入 Android 目录
cd android

# 清理
./gradlew clean

# 构建调试版 APK
./gradlew assembleDebug

# 构建发布版 APK
./gradlew assembleRelease

# 构建 Play Store AAB（禁用自更新）
./gradlew bundleRelease -Pplaystore
```

## 输出文件

构建完成后，APK 文件位于：

- **调试版**: `android/app/build/outputs/apk/debug/app-debug.apk`
- **发布版**: `android/app/build/outputs/apk/release/app-release.apk`
- **AAB**: `android/app/build/outputs/bundle/release/app-release.aab`

## 签名配置

发布版 APK 默认使用 debug 密钥签名。如需使用自定义密钥签名：

1. 在 `freekiosk/android/` 目录下创建 `gradle.properties` 文件
2. 添加以下内容：

```properties
FREEKIOSK_UPLOAD_STORE_FILE=/path/to/your/keystore.jks
FREEKIOSK_UPLOAD_STORE_PASSWORD=your_store_password
FREEKIOSK_UPLOAD_KEY_ALIAS=your_key_alias
FREEKIOSK_UPLOAD_KEY_PASSWORD=your_key_password
```

## 版本信息

当前版本：1.2.17 (versionCode: 32)

## 故障排除

### 问题：Gradle 构建失败

```bash
# 清理 Gradle 缓存
cd android
./gradlew clean --refresh-dependencies
```

### 问题：Node 模块冲突

```bash
# 重新安装 Node 模块
rm -rf node_modules package-lock.json
npm install
```

### 问题：SDK 版本不匹配

确保 `android/build.gradle` 中的 SDK 版本与已安装的版本匹配：
- buildToolsVersion: 36.0.0
- compileSdkVersion: 36
- targetSdkVersion: 36
- minSdkVersion: 24
