/**
 * MQTT 5.0 企业级通信服务
 *
 * 提供设备与 Hub 服务器之间的实时双向通信能力。
 * 主要功能:
 * - 连接/断开 MQTT Broker
 * - 发布设备状态、事件、遥测数据
 * - 接收并处理服务器下发的命令
 * - 连接状态监控
 *
 * 使用方法:
 * ```typescript
 * import Mqtt5Service from './services/Mqtt5Service';
 *
 * // 启动 MQTT 服务
 * await Mqtt5Service.start({
 *   brokerUrl: 'emqx.example.com',
 *   port: 1883,
 *   tenantId: 'tenant001',
 *   deviceId: 'device001'
 * });
 *
 * // 监听命令
 * Mqtt5Service.onCommand((command) => {
 *   console.log('收到命令:', command);
 * });
 *
 * // 发布状态
 * await Mqtt5Service.publishStatus({ ... });
 * ```
 */

import { NativeModules, NativeEventEmitter, Platform, EmitterSubscription } from 'react-native';
import DeviceControlService from './DeviceControlService';

const { Mqtt5Module } = NativeModules;

/**
 * MQTT 5.0 配置接口
 */
export interface Mqtt5Config {
  brokerUrl: string;       // MQTT Broker 地址
  port?: number;           // 端口号 (默认 1883)
  tenantId: string;        // 租户 ID
  deviceId: string;        // 设备 ID
  useTls?: boolean;        // 是否使用 TLS
  jwtToken?: string;       // JWT 认证令牌
  keepAlive?: number;      // 心跳间隔秒数 (默认 60)
  cleanStart?: boolean;    // 是否清除会话 (默认 false，保持持久会话)
  sessionExpiryInterval?: number;  // 会话过期时间秒数
  statusInterval?: number; // 状态上报间隔毫秒
}

/**
 * MQTT 命令接口
 */
export interface MqttCommand {
  command: string;         // 命令类型
  params: Record<string, any>;  // 命令参数
}

/**
 * 启动结果接口
 */
export interface MqttStartResult {
  success: boolean;
  clientId: string;        // 客户端 ID
  baseTopic: string;       // 基础 Topic
}

/**
 * 命令处理器类型
 */
type CommandHandler = (command: MqttCommand) => void;

/**
 * 连接状态处理器类型
 */
type ConnectionHandler = (connected: boolean) => void;

/**
 * MQTT 5.0 服务类
 */
class Mqtt5ServiceClass {
  private eventEmitter: NativeEventEmitter | null = null;
  private commandSubscription: EmitterSubscription | null = null;
  private connectionSubscription: EmitterSubscription | null = null;
  private commandHandlers: CommandHandler[] = [];
  private connectionHandlers: ConnectionHandler[] = [];
  private isRunning: boolean = false;
  private config: Mqtt5Config | null = null;

  constructor() {
    if (Platform.OS === 'android' && Mqtt5Module) {
      this.eventEmitter = new NativeEventEmitter(Mqtt5Module);
      this.setupEventListeners();
    }
  }

  /**
   * 设置事件监听器
   */
  private setupEventListeners(): void {
    if (!this.eventEmitter) return;

    // 监听命令事件
    this.commandSubscription = this.eventEmitter.addListener(
      'onMqttCommand',
      (event: MqttCommand) => {
        console.log('[Mqtt5Service] 收到命令:', event);
        this.handleCommand(event);
      }
    );

    // 监听连接状态变化
    this.connectionSubscription = this.eventEmitter.addListener(
      'onMqttConnectionChanged',
      (event: { connected: boolean }) => {
        console.log('[Mqtt5Service] 连接状态变化:', event.connected);
        this.connectionHandlers.forEach(handler => handler(event.connected));
      }
    );
  }

  /**
   * 处理收到的命令
   *
   * 将 MQTT 命令分发给注册的处理器，并执行相应操作。
   */
  private handleCommand(command: MqttCommand): void {
    // 通知所有注册的处理器
    this.commandHandlers.forEach(handler => handler(command));

    // 执行命令
    this.executeCommand(command);
  }

  /**
   * 执行命令
   *
   * 根据命令类型调用相应的设备控制方法。
   */
  private async executeCommand(command: MqttCommand): Promise<void> {
    const { command: type, params } = command;

    try {
      switch (type) {
        case 'setBrightness':
          // 设置屏幕亮度
          await DeviceControlService.setBrightness(params.brightness);
          console.log(`[Mqtt5Service] 亮度已设置为 ${params.brightness}`);
          break;

        case 'setScreen':
          // 开关屏幕
          if (params.on) {
            await DeviceControlService.screenOn();
            console.log('[Mqtt5Service] 屏幕已开启');
          } else {
            await DeviceControlService.screenOff();
            console.log('[Mqtt5Service] 屏幕已关闭');
          }
          break;

        case 'setVolume':
          // 设置音量 (TODO: 实现音量控制)
          console.log(`[Mqtt5Service] 设置音量: ${params.volume}`);
          break;

        case 'navigate':
          // 导航到指定 URL
          if (params.url) {
            await DeviceControlService.navigateToUrl(params.url);
            console.log(`[Mqtt5Service] 已导航到 ${params.url}`);
          }
          break;

        case 'reload':
          // 刷新 WebView
          await DeviceControlService.reloadWebView();
          console.log('[Mqtt5Service] WebView 已刷新');
          break;

        case 'reboot':
          // 重启设备 (需要 Device Owner 权限)
          console.log('[Mqtt5Service] 收到重启命令');
          // TODO: 调用 KioskModule.reboot()
          break;

        case 'clearCache':
          // 清除缓存
          console.log('[Mqtt5Service] 收到清除缓存命令');
          // TODO: 实现清除缓存
          break;

        case 'updateApp':
          // 更新应用
          console.log(`[Mqtt5Service] 收到更新命令: ${params.apkUrl}`);
          // TODO: 调用 UpdateModule
          break;

        default:
          console.warn(`[Mqtt5Service] 未知命令类型: ${type}`);
      }
    } catch (error) {
      console.error(`[Mqtt5Service] 执行命令失败:`, error);
    }
  }

  /**
   * 启动 MQTT 5.0 服务
   *
   * @param config MQTT 配置
   * @returns 启动结果
   */
  async start(config: Mqtt5Config): Promise<MqttStartResult> {
    if (Platform.OS !== 'android' || !Mqtt5Module) {
      throw new Error('Mqtt5Module 仅在 Android 平台可用');
    }

    if (this.isRunning) {
      console.warn('[Mqtt5Service] 服务已在运行中');
      return {
        success: true,
        clientId: `kiosk_${config.tenantId}_${config.deviceId}`,
        baseTopic: `kiosk/${config.tenantId}/${config.deviceId}`,
      };
    }

    try {
      console.log('[Mqtt5Service] 正在启动服务...');
      const result = await Mqtt5Module.start(config);

      this.isRunning = true;
      this.config = config;

      console.log('[Mqtt5Service] 服务已启动:', result);

      // 启动状态上报
      this.startStatusReporting();

      return result;
    } catch (error) {
      console.error('[Mqtt5Service] 启动服务失败:', error);
      throw error;
    }
  }

  /**
   * 停止 MQTT 5.0 服务
   */
  async stop(): Promise<boolean> {
    if (Platform.OS !== 'android' || !Mqtt5Module) {
      return false;
    }

    if (!this.isRunning) {
      return true;
    }

    try {
      console.log('[Mqtt5Service] 正在停止服务...');
      const result = await Mqtt5Module.stop();

      this.isRunning = false;
      this.config = null;

      console.log('[Mqtt5Service] 服务已停止');
      return result;
    } catch (error) {
      console.error('[Mqtt5Service] 停止服务失败:', error);
      throw error;
    }
  }

  /**
   * 检查是否已连接
   */
  async isConnected(): Promise<boolean> {
    if (Platform.OS !== 'android' || !Mqtt5Module) {
      return false;
    }

    return Mqtt5Module.isConnected();
  }

  /**
   * 发布设备状态
   *
   * @param status 状态数据
   */
  async publishStatus(status: Record<string, unknown>): Promise<boolean> {
    if (Platform.OS !== 'android' || !Mqtt5Module) {
      return false;
    }

    try {
      return await Mqtt5Module.publishStatus(JSON.stringify(status));
    } catch (error) {
      console.error('[Mqtt5Service] 发布状态失败:', error);
      return false;
    }
  }

  /**
   * 发布设备事件
   *
   * @param event 事件数据
   */
  async publishEvent(event: Record<string, unknown>): Promise<boolean> {
    if (Platform.OS !== 'android' || !Mqtt5Module) {
      return false;
    }

    try {
      return await Mqtt5Module.publishEvent(JSON.stringify(event));
    } catch (error) {
      console.error('[Mqtt5Service] 发布事件失败:', error);
      return false;
    }
  }

  /**
   * 发布遥测数据
   *
   * @param telemetry 遥测数据
   */
  async publishTelemetry(telemetry: Record<string, unknown>): Promise<boolean> {
    if (Platform.OS !== 'android' || !Mqtt5Module) {
      return false;
    }

    try {
      return await Mqtt5Module.publishTelemetry(JSON.stringify(telemetry));
    } catch (error) {
      console.error('[Mqtt5Service] 发布遥测数据失败:', error);
      return false;
    }
  }

  /**
   * 发送命令响应
   *
   * @param commandId 命令 ID
   * @param result 响应结果
   */
  async sendCommandResponse(commandId: string, result: Record<string, unknown>): Promise<boolean> {
    if (Platform.OS !== 'android' || !Mqtt5Module) {
      return false;
    }

    try {
      return await Mqtt5Module.sendCommandResponse(commandId, JSON.stringify(result));
    } catch (error) {
      console.error('[Mqtt5Service] 发送命令响应失败:', error);
      return false;
    }
  }

  /**
   * 注册命令处理器
   *
   * @param handler 命令处理函数
   * @returns 取消注册的函数
   */
  onCommand(handler: CommandHandler): () => void {
    this.commandHandlers.push(handler);

    return () => {
      const index = this.commandHandlers.indexOf(handler);
      if (index > -1) {
        this.commandHandlers.splice(index, 1);
      }
    };
  }

  /**
   * 注册连接状态处理器
   *
   * @param handler 连接状态处理函数
   * @returns 取消注册的函数
   */
  onConnectionChanged(handler: ConnectionHandler): () => void {
    this.connectionHandlers.push(handler);

    return () => {
      const index = this.connectionHandlers.indexOf(handler);
      if (index > -1) {
        this.connectionHandlers.splice(index, 1);
      }
    };
  }

  /**
   * 启动状态上报
   *
   * 定期采集并上报设备状态。
   */
  private startStatusReporting(): void {
    // 状态上报逻辑将在 Phase 4 实现
    // 这里先预留接口
    console.log('[Mqtt5Service] 状态上报已就绪');
  }

  /**
   * 采集并上报设备状态
   */
  async reportStatus(): Promise<void> {
    if (!this.isRunning) {
      return;
    }

    try {
      const status = await DeviceControlService.getStatus();
      await this.publishStatus(status);
      console.log('[Mqtt5Service] 状态已上报');
    } catch (error) {
      console.error('[Mqtt5Service] 上报状态失败:', error);
    }
  }

  /**
   * 获取服务运行状态
   */
  getRunningState(): boolean {
    return this.isRunning;
  }

  /**
   * 获取当前配置
   */
  getConfig(): Mqtt5Config | null {
    return this.config;
  }

  /**
   * 清理资源
   */
  cleanup(): void {
    if (this.commandSubscription) {
      this.commandSubscription.remove();
      this.commandSubscription = null;
    }

    if (this.connectionSubscription) {
      this.connectionSubscription.remove();
      this.connectionSubscription = null;
    }

    this.commandHandlers = [];
    this.connectionHandlers = [];
    this.isRunning = false;
    this.config = null;
  }
}

// 导出单例实例
const Mqtt5Service = new Mqtt5ServiceClass();
export default Mqtt5Service;