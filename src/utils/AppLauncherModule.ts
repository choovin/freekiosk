import { NativeModules, NativeEventEmitter } from 'react-native';

export interface AppInfo {
  packageName: string;
  appName: string;
}

interface IAppLauncherModule {
  launchExternalApp(packageName: string): Promise<boolean>;
  isAppInstalled(packageName: string): Promise<boolean>;
  getInstalledApps(): Promise<AppInfo[]>;
  getPackageLabel(packageName: string): Promise<string>;
  getAppIcon(packageName: string, size: number): Promise<string>;
  launchBootApps(): Promise<number>;
  startBackgroundMonitor(): Promise<boolean>;
  stopBackgroundMonitor(): Promise<boolean>;
}

const { AppLauncherModule } = NativeModules;

if (!AppLauncherModule) {
  console.error('[AppLauncherModule] Native module not found. Did you rebuild the app?');
}

export const appLauncherEmitter = new NativeEventEmitter(AppLauncherModule);
export default AppLauncherModule as IAppLauncherModule;
