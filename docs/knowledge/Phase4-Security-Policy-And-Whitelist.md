# Phase 4 开发总结：安全策略与应用白名单管理

**开发时间**: 2026-03-21
**阶段**: Phase 4
**状态**: ✅ 已完成

---

## 概述

Phase 4 实现了企业级安全策略管理和应用白名单控制功能。通过 MQTT 5.0 通道，Hub 服务器可以远程向设备下发安全策略配置，设备端自动解析并应用这些策略，实现集中化安全管理。

---

## 功能架构

```
┌─────────────────────────────────────────────────────────────┐
│                        Hub 服务器                            │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │ Policy API  │  │ MQTT Broker  │  │  Web Dashboard   │   │
│  └─────────────┘  └──────────────┘  └──────────────────────┘   │
└────────────────────────────┬────────────────────────────────┘
                             │ MQTT 5.0
                             │ Commands: updatePolicy / updateWhitelist
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                     FreeKiosk 客户端                         │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              SecurityPolicyManager                      │   │
│  │  ├── 密码策略 (PasswordPolicy)                         │   │
│  │  ├── 超时设置 (TimeoutSettings)                       │   │
│  │  ├── 系统加固 (SystemHardening)                       │   │
│  │  ├── 应用加固 (AppHardening)                          │   │
│  │  └── 网络限制 (NetworkRestrictions)                    │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              AppWhitelistManager                      │   │
│  │  ├── 白名单管理 (WhitelistEntry)                       │   │
│  │  ├── 应用验证 (isAppAllowed)                          │   │
│  │  └── 受限应用列表 (getBlockedApps)                     │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## Android 端实现

### 核心组件

#### 1. SecurityPolicyManager.kt

负责管理和应用从服务器下发的安全策略。

**主要数据结构**:

```kotlin
data class PolicySettings(
    val passwordPolicy: PasswordPolicy? = null,
    val timeoutSettings: TimeoutSettings? = null,
    val systemHardening: SystemHardening? = null,
    val appHardening: AppHardening? = null,
    val networkRestrictions: NetworkRestrictions? = null
)

data class PasswordPolicy(
    val enabled: Boolean = false,        // 是否启用密码验证
    val minLength: Int = 4,              // 最小密码长度
    val requireUppercase: Boolean = false,
    val requireLowercase: Boolean = false,
    val requireNumbers: Boolean = false,
    val requireSymbols: Boolean = false,
    val maxAttempts: Int = 3,            // 最大尝试次数
    val lockoutDuration: Int = 15        // 锁定时长（分钟）
)

data class TimeoutSettings(
    val screenOffTimeout: Int = 300,     // 屏幕关闭超时（秒）
    val lockTimeout: Int = 60,           // 锁屏超时
    val sessionTimeout: Int = 3600,       // 会话超时
    val inactivityLockTimeout: Int = 300  // 无操作锁定超时
)

data class SystemHardening(
    val disableUsbDebug: Boolean = true,       // 禁用 USB 调试
    val disableAdbInstall: Boolean = true,     // 禁用 ADB 安装
    val disableSettingsAccess: Boolean = true,  // 禁用设置访问
    val disableScreenshot: Boolean = true,       // 禁用截图
    val disableScreenCapture: Boolean = true,   // 禁用屏幕录制
    val disableStatusBar: Boolean = true,       // 隐藏状态栏
    val disableNavigationBar: Boolean = true,   // 隐藏导航栏
    val safeBoot: Boolean = false,              // 安全启动模式
    val disablePowerMenu: Boolean = true        // 禁用电源菜单
)

data class AppHardening(
    val allowBackgroundSwitch: Boolean = false,  // 允许后台切换
    val allowReturnKey: Boolean = false,         // 允许返回键
    val allowRecentApps: Boolean = false,         // 允许最近应用
    val forceFullScreen: Boolean = true,           // 强制全屏
    val hideHomeIndicator: Boolean = true          // 隐藏主页指示器
)

data class NetworkRestrictions(
    val allowWiFi: Boolean = true,
    val allowBluetooth: Boolean = false,
    val allowMobileData: Boolean = true,
    val allowedWiFiSSIDs: List<String> = emptyList(),
    val blockedPorts: List<Int> = emptyList(),
    val proxyAddress: String = ""
)
```

**核心方法**:

| 方法 | 说明 |
|------|------|
| `parsePolicyFromJson(JSONObject)` | 解析服务器下发的策略 JSON |
| `applyPolicy(PolicySettings)` | 应用策略到设备 |
| `validatePassword(String)` | 验证密码是否符合策略 |
| `getCurrentSettings()` | 获取当前策略设置 |
| `getLastSyncTime()` | 获取最后同步时间 |

#### 2. AppWhitelistManager.kt

负责管理应用白名单，控制哪些应用可以在 kiosk 模式下运行。

**白名单条目**:

```kotlin
data class WhitelistEntry(
    val packageName: String,           // 包名
    val appName: String,                // 应用名称
    val autoLaunch: Boolean = false,    // 是否自启动
    val allowNotifications: Boolean = false,  // 允许通知
    val defaultShortcut: Boolean = false // 默认快捷方式
)
```

**核心方法**:

| 方法 | 说明 |
|------|------|
| `setWhitelist(List<WhitelistEntry>)` | 设置白名单 |
| `getWhitelist()` | 获取白名单 |
| `isAppAllowed(String)` | 检查应用是否允许 |
| `isWhitelistEnabled()` | 检查白名单是否启用 |
| `getBlockedApps()` | 获取被阻止的应用列表 |
| `launchAppIfAllowed(String)` | 允许则启动应用 |
| `addToWhitelist(WhitelistEntry)` | 添加应用到白名单 |
| `removeFromWhitelist(String)` | 从白名单移除应用 |

---

### React Native 桥接

#### SecurityPolicyModule.kt

将 SecurityPolicyManager 暴露给 JavaScript 层。

**可用方法**:

```javascript
// 解析和应用策略
await SecurityPolicyModule.parsePolicy(policyJson);

// 获取当前设置
const settings = await SecurityPolicyModule.getCurrentSettings();

// 验证密码
const isValid = await SecurityPolicyModule.validatePassword('1234');

// 获取各项配置
await SecurityPolicyModule.getPasswordPolicy();
await SecurityPolicyModule.getTimeoutSettings();
await SecurityPolicyModule.getSystemHardening();
await SecurityPolicyModule.getAppHardening();
await SecurityPolicyModule.getNetworkRestrictions();

// 检查 USB 调试状态
const usbDebugDisabled = await SecurityPolicyModule.isUsbDebugDisabled();

// 清空策略
await SecurityPolicyModule.clearPolicy();
```

#### AppWhitelistModule.kt

将 AppWhitelistManager 暴露给 JavaScript 层。

**可用方法**:

```javascript
// 设置白名单
await AppWhitelistModule.setWhitelist(JSON.stringify(whitelistArray));

// 获取白名单
const whitelist = await AppWhitelistModule.getWhitelist();

// 检查应用是否允许
const isAllowed = await AppWhitelistModule.isAppAllowed('com.example.app');

// 获取被阻止的应用
const blocked = await AppWhitelistModule.getBlockedApps();

// 添加/移除/更新白名单
await AppWhitelistModule.addToWhitelist(entryJson);
await AppWhitelistModule.removeFromWhitelist('com.example.app');
await AppWhitelistModule.updateInWhitelist(entryJson);

// 启动应用（如果允许）
await AppWhitelistModule.launchAppIfAllowed('com.example.app');

// 检查白名单启用状态
const enabled = await AppWhitelistModule.isWhitelistEnabled();
```

---

### MQTT 命令集成

CommandHandler 新增了以下命令处理：

| 命令 | 说明 | 参数 |
|------|------|------|
| `updatePolicy` | 更新安全策略 | `{ policy: {...} }` |
| `updateWhitelist` | 更新应用白名单 | `{ whitelist: [...] }` |
| `getPolicy` | 获取当前策略 | - |
| `getWhitelist` | 获取当前白名单 | - |
| `validatePassword` | 验证密码 | `{ password: "..." }` |

**命令格式**:

```json
// updatePolicy 命令
{
  "id": "cmd-uuid-123",
  "type": "updatePolicy",
  "params": {
    "policy": {
      "settings": {
        "password_policy": { "enabled": true, "min_length": 6 },
        "timeout_settings": { "screen_off_timeout": 300 },
        "system_hardening": { "disable_usb_debug": true },
        "app_hardening": { "force_full_screen": true },
        "network_restrictions": { "allow_wifi": true }
      }
    }
  }
}

// updateWhitelist 命令
{
  "id": "cmd-uuid-456",
  "type": "updateWhitelist",
  "params": {
    "whitelist": [
      { "package_name": "com.android.chrome", "app_name": "Chrome", "auto_launch": true },
      { "package_name": "com.google.android.youtube", "app_name": "YouTube", "auto_launch": false }
    ]
  }
}
```

---

## Hub 服务端实现

### API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v2/tenants/:tenantId/policies` | 创建安全策略 |
| GET | `/api/v2/tenants/:tenantId/policies` | 获取策略列表 |
| GET | `/api/v2/tenants/:tenantId/policies/:policyId` | 获取指定策略 |
| PUT | `/api/v2/tenants/:tenantId/policies/:policyId` | 更新策略 |
| DELETE | `/api/v2/tenants/:tenantId/policies/:policyId` | 删除策略 |
| POST | `/api/v2/devices/:deviceId/policies/:policyId/assign` | 分配策略到设备 |
| GET | `/api/v2/devices/:deviceId/policy` | 获取设备当前策略 |
| GET | `/api/v2/devices/:deviceId/whitelist` | 获取设备白名单 |

### 数据库模型

**security_policies 表**:

```sql
CREATE TABLE security_policies (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    settings JSONB NOT NULL,
    app_whitelist JSONB NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);
```

---

## 数据流

### 策略下发流程

```
1. 管理员在 Web Dashboard 创建/编辑策略
         │
         ▼
2. Hub 服务器保存策略到数据库
         │
         ▼
3. Hub 通过 MQTT 发布 updatePolicy 命令到设备
         │
         ▼
4. 设备端 CommandHandler 接收命令
         │
         ▼
5. SecurityPolicyManager.parsePolicyFromJson() 解析 JSON
         │
         ▼
6. applyPolicy() 应用各项策略设置
         │
         ▼
7. 策略存储到 SharedPreferences 持久化
```

### 白名单验证流程

```
1. 用户尝试启动应用
         │
         ▼
2. AppWhitelistManager.isAppAllowed(packageName)
         │
         ▼
3. 检查白名单启用状态
         │
         ├── 未启用 → 允许启动
         │
         └── 已启用 → 检查是否在白名单中
                       │
                       ├── 在白名单 → 允许启动
                       │
                       └── 不在白名单 → 阻止启动，记录日志
```

---

## 安全考虑

1. **密码策略验证**: 本地密码强度检查，防止简单密码被使用
2. **USB 调试控制**: 可远程禁用 USB 调试，防止物理攻击
3. **白名单机制**: 只允许白名单应用运行，防止恶意软件
4. **配置加密**: 敏感配置通过 Android KeyStore 加密存储

---

## 文件清单

### 新增文件

| 文件路径 | 说明 |
|----------|------|
| `security/SecurityPolicyManager.kt` | 安全策略管理器 |
| `security/AppWhitelistManager.kt` | 应用白名单管理器 |
| `security/SecurityPolicyModule.kt` | React Native 桥接 |
| `security/AppWhitelistModule.kt` | React Native 桥接 |
| `security/SecurityPolicyPackage.kt` | 包注册 |
| `security/AppWhitelistPackage.kt` | 包注册 |

### 修改文件

| 文件路径 | 修改内容 |
|----------|----------|
| `MainApplication.kt` | 注册 SecurityPolicyPackage 和 AppWhitelistPackage |
| `mqtt5/handlers/CommandHandler.kt` | 添加安全策略命令处理 |
| `service/MqttForegroundService.kt` | 传入 Context 给 CommandHandler |
| `auth/AuthModule.kt` | 修复 coroutines cancel 导入 |
| `security/CsrGenerator.kt` | 修复 isSignatureValid 调用 |
| `security/AppWhitelistManager.kt` | 修复 signatures 空值处理 |

---

## 测试验证

### 本地测试

```bash
# 构建调试 APK
cd android && ./gradlew assembleDebug

# 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### MQTT 命令测试

```bash
# 发布更新策略命令
mosquitto_pub -h localhost -p 1883 -t "kiosk/{tenant}/{device}/commands" \
  -m '{"id":"test-1","type":"updatePolicy","params":{"policy":{"settings":{}}}}'

# 查询设备策略
mosquitto_pub -h localhost -p 1883 -t "kiosk/{tenant}/{device}/commands" \
  -m '{"id":"test-2","type":"getPolicy","params":{}}'
```

---

## 后续工作

- [ ] 实现 AccessibilityService 实际拦截非白名单应用
- [ ] 实现系统加固的实际系统调用
- [ ] 添加策略版本管理和回滚机制
- [ ] 实现策略同步状态反馈到服务器

---

## 相关文档

- [MQTT 文档](../MQTT.md)
- [REST API 文档](../REST_API.md)
- [ADB 配置文档](../ADB_CONFIG.md)
