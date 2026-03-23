import React, { useEffect, useState } from 'react';
import { StatusBar, View, ActivityIndicator, StyleSheet, Text } from 'react-native';
import { NativeModules } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import AppNavigator from './src/navigation/AppNavigator';
import OnboardingScreen from './src/screens/OnboardingScreen';
import { initI18n } from './src/i18n/config';
import { Colors } from './src/theme';

const { HubConfigModule } = NativeModules;

interface ErrorState {
  hasError: boolean;
  errorMessage: string;
}

const App: React.FC = () => {
  const [isCheckingConfig, setIsCheckingConfig] = useState(true);
  const [isBound, setIsBound] = useState(false);
  const [error, setError] = useState<ErrorState>({ hasError: false, errorMessage: '' });

  // Ref to track mounted state
  const mountedRef = React.useRef(true);

  useEffect(() => {
    initI18n();
    checkDeviceConfig();
  }, []);

  const checkDeviceConfig = async () => {
    try {
      const config = await HubConfigModule.getConfig();
      if (!mountedRef.current) return;
      setIsBound(!!(config && config.deviceId && config.groupId));
    } catch (err: any) {
      console.error('Failed to check device config:', err);
      if (!mountedRef.current) return;
      setError({ hasError: true, errorMessage: err.message || '配置检查失败' });
    } finally {
      if (mountedRef.current) {
        setIsCheckingConfig(false);
      }
    }
  };

  // Ref to track mounted state
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  // Error boundary fallback UI
  if (error.hasError) {
    return (
      <View style={styles.errorContainer}>
        <StatusBar hidden={true} />
        <Text style={styles.errorIcon}>!</Text>
        <Text style={styles.errorTitle}>出错了</Text>
        <Text style={styles.errorMessage}>{error.errorMessage}</Text>
        <Text
          style={styles.errorRetry}
          onPress={() => {
            setError({ hasError: false, errorMessage: '' });
            setIsCheckingConfig(true);
            checkDeviceConfig();
          }}
        >
          重试
        </Text>
      </View>
    );
  }

  // Show loading while checking config
  if (isCheckingConfig) {
    return (
      <View style={styles.loadingContainer}>
        <StatusBar hidden={true} />
        <ActivityIndicator size="large" color={Colors.primary} />
      </View>
    );
  }

  return (
    <NavigationContainer>
      <StatusBar hidden={true} />
      {isBound ? <AppNavigator /> : <OnboardingScreen />}
    </NavigationContainer>
  );
};

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: Colors.background,
  },
  errorContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: Colors.background,
    padding: 24,
  },
  errorIcon: {
    fontSize: 48,
    fontWeight: 'bold',
    color: Colors.error,
    marginBottom: 16,
  },
  errorTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: Colors.textPrimary,
    marginBottom: 8,
  },
  errorMessage: {
    fontSize: 16,
    color: Colors.textSecondary,
    textAlign: 'center',
    marginBottom: 24,
  },
  errorRetry: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.primary,
  },
});

export default App;
