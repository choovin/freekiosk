/**
 * FreeKiosk v1.2 - OnboardingScreen
 * 4-step tablet binding flow for Field Trip Edition
 */

import React, { useState, useCallback, useEffect, useRef } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Dimensions,
  NativeModules,
  Alert,
  ScrollView,
  ActivityIndicator,
  Modal,
  Animated,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../navigation/AppNavigator';
import Icon, { IconName } from '../components/Icon';
import { useTranslation } from 'react-i18next';
import { Colors } from '../theme';
import QrScannerView from '../components/QrScannerView';

// Constants
const GPS_INTERVAL_SECONDS = 30;
const QR_SCAN_TIMEOUT_MS = 30000;

const { HubConfigModule, MqttModule } = NativeModules;

type OnboardingNavigationProp = NativeStackNavigationProp<RootStackParamList, 'Onboarding'>;

type OnboardingStep = 'welcome' | 'scanGroup' | 'selectNumber' | 'scanDevice' | 'success';

// Inline translations following the issue requirement to avoid file proliferation
const translations = {
  welcomeTitle: '研学版',
  welcomeDescription: '快速绑定平板，加入分组，开始研学之旅',
  scanGroupTitle: '扫描分组码',
  scanGroupDescription: '请扫描教师提供的分组二维码',
  selectNumberTitle: '选择设备编号',
  selectNumberDescription: '选择这台平板的编号（1-99）',
  scanDeviceTitle: '扫描设备码',
  scanDeviceDescription: '请扫描设备专属二维码完成绑定',
  successTitle: '绑定成功',
  // Dynamic descriptions
  cameraPermissionTitle: '相机权限',
  cameraPermissionMessage: '需要相机权限来扫描二维码',
  cameraPermissionNeutral: '稍后询问',
  cameraPermissionNegative: '取消',
  cameraPermissionPositive: '确定',
  permissionDenied: '权限不足',
  scanCanceled: '扫描取消',
  scanRetry: '未扫描到二维码，请重试',
  invalidGroupQr: '无效二维码',
  invalidGroupQrMessage: '此二维码不是分组码，请扫描正确的分组二维码',
  invalidDeviceQr: '无效二维码',
  invalidDeviceQrMessage: '此二维码不是设备码，请扫描正确的设备二维码',
  selectNumberPrompt: '请选择编号',
  selectNumberMessage: '请在下方选择这台平板的编号（1-99）',
  scanError: '扫描失败',
  scanRetryMessage: '无法扫描二维码，请重试',
  bindError: '绑定失败',
  bindRetryMessage: '请重新扫描分组码',
  scanTimeout: '扫描超时',
  scanTimeoutMessage: '扫描超时，请重试',
  configuring: '配置中...',
  configuringMessage: '正在配置设备，请稍候',
  btnStartBinding: '开始绑定',
  btnScanning: '扫描中...',
  btnScanGroup: '扫描分组码',
  btnSelectNumber: '请选择编号',
  btnSelected: '已选择 ',
  btnContinue: '，继续',
  btnScanDevice: '扫描设备码',
  btnEnterKiosk: '进入研学',
  btnSkip: '跳过，直接进入',
  btnBack: '返回上一步',
  btnRescanGroup: '重新扫描分组码',
  btnReselectNumber: '重新选择编号',
  successWithGroup: '已成功加入 ',
  successWithGroupAndDevice: '，设备编号: ',
  successNoGroup: '已成功绑定，设备编号: ',
};

// Step configuration
const STEPS: { key: OnboardingStep; icon: IconName }[] = [
  { key: 'welcome', icon: 'account-group' },
  { key: 'scanGroup', icon: 'qrcode-scan' },
  { key: 'selectNumber', icon: 'numeric' },
  { key: 'scanDevice', icon: 'qrcode' },
  { key: 'success', icon: 'check-circle' },
];

const STEP_ORDER: OnboardingStep[] = ['welcome', 'scanGroup', 'selectNumber', 'scanDevice', 'success'];

const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');

// Device number grid: 10 columns x 10 rows = 100 numbers (1-100, but we show 1-99)
const NUMBERS_PER_ROW = 10;
const NUMBER_BUTTON_SIZE = (SCREEN_WIDTH - 80) / NUMBERS_PER_ROW;

interface OnboardingScreenProps {
  onSkip?: () => void;
}

const OnboardingScreen: React.FC<OnboardingScreenProps> = ({ onSkip }) => {
  const navigation = useNavigation<OnboardingNavigationProp>();
  const { t } = useTranslation();

  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [groupKey, setGroupKey] = useState<string | null>(null);
  const [deviceNumber, setDeviceNumber] = useState<number | null>(null);
  const [groupName, setGroupName] = useState<string>('');
  const [scanningFor, setScanningFor] = useState<'group' | 'device' | null>(null);
  const [isConfiguring, setIsConfiguring] = useState(false);
  const [hasError, setHasError] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [showScanner, setShowScanner] = useState(false);

  // Progress animation for success step
  const progressAnim = useRef(new Animated.Value(0)).current;

  // Ref to track mounted state for cleanup
  const mountedRef = useRef(true);
  // Ref to track timeout ID
  const scanTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const currentStep = STEP_ORDER[currentStepIndex];

  // Cleanup on unmount
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      if (scanTimeoutRef.current) {
        clearTimeout(scanTimeoutRef.current);
      }
    };
  }, []);

  // Auto-navigate after success animation (3 seconds)
  useEffect(() => {
    if (currentStep !== 'success') {
      // Reset progress when leaving success
      progressAnim.setValue(0);
      return;
    }

    // Animate progress from 0 to 100% over 3 seconds
    Animated.timing(progressAnim, {
      toValue: 100,
      duration: 3000,
      useNativeDriver: false,
    }).start();

    // Navigate to Kiosk screen after 3 seconds
    const timeout = setTimeout(() => {
      navigation.reset({
        index: 0,
        routes: [{ name: 'Kiosk' }],
      });
    }, 3000);

    return () => clearTimeout(timeout);
  }, [currentStep, progressAnim, navigation]);

  // Clear group key on error for consistent state
  const clearGroupKey = useCallback(() => {
    setGroupKey(null);
    setGroupName('');
    setDeviceNumber(null);
  }, []);

  // Start QR scanning for group
  const startGroupQrScan = useCallback(async () => {
    setScanningFor('group');
    setShowScanner(true);
  }, []);

  // Handle group QR code scanned
  const handleGroupQrScanned = useCallback(async (qrData: string) => {
    setShowScanner(false);
    setScanningFor(null);

    if (!qrData) {
      Alert.alert(translations.scanCanceled, translations.scanRetry);
      return;
    }

    try {
      const payload = JSON.parse(qrData);
      if (!payload.group_key) {
        Alert.alert(translations.invalidGroupQr, translations.invalidGroupQrMessage);
        return;
      }

      setGroupKey(payload.group_key);
      setGroupName(payload.group_name || '研学小组');
      // Move to select number step
      setCurrentStepIndex(STEP_ORDER.indexOf('selectNumber'));
    } catch (error) {
      Alert.alert(translations.invalidGroupQr, translations.invalidGroupQrMessage);
    }
  }, []);

  // Cancel group scan
  const cancelGroupScan = useCallback(() => {
    setShowScanner(false);
    setScanningFor(null);
  }, []);

  // Start QR scanning for device
  const startDeviceQrScan = useCallback(async () => {
    if (!groupKey || !deviceNumber) {
      Alert.alert(translations.permissionDenied, translations.selectNumberMessage);
      return;
    }

    setScanningFor('device');
    setShowScanner(true);
  }, [groupKey, deviceNumber]);

  // Handle device QR code scanned
  const handleDeviceQrScanned = useCallback(async (qrData: string) => {
    setShowScanner(false);
    setScanningFor(null);

    if (!qrData) {
      Alert.alert(translations.scanCanceled, translations.scanRetry);
      return;
    }

    try {
      const payload = JSON.parse(qrData);
      if (!payload.device_id || !payload.api_key || !payload.hub_url) {
        Alert.alert(translations.invalidDeviceQr, translations.invalidDeviceQrMessage);
        return;
      }

      // Combine with group key from first scan
      const bindPayload = {
        device_id: payload.device_id,
        group_key: groupKey,
        api_key: payload.api_key,
        hub_url: payload.hub_url,
        device_number: deviceNumber,
      };

      // Call bindWithQrPayload with error handling for inconsistent state
      let bindResult;
      try {
        bindResult = await HubConfigModule.bindWithQrPayload(JSON.stringify(bindPayload));
      } catch (bindError: any) {
        // Clear groupKey to avoid inconsistent state
        clearGroupKey();
        console.error('Bind error:', bindError);
        Alert.alert(translations.bindError, translations.bindRetryMessage);
        // Go back to step 1 (scan group)
        setCurrentStepIndex(STEP_ORDER.indexOf('scanGroup'));
        return;
      }

      if (!mountedRef.current) return;

      // Show configuring overlay while setting up GPS/MQTT
      setIsConfiguring(true);

      try {
        // Start GPS reporting
        await HubConfigModule.startGpsReporting(GPS_INTERVAL_SECONDS);

        if (!mountedRef.current) return;

        // Subscribe to MQTT broadcast with groupId
        if (bindResult.groupId) {
          await MqttModule.setGroupId(bindResult.groupId);
        }

        if (!mountedRef.current) return;

        setIsConfiguring(false);
        // Move to success step
        setCurrentStepIndex(STEP_ORDER.indexOf('success'));
      } catch (setupError: any) {
        if (!mountedRef.current) return;
        setIsConfiguring(false);
        // Clear groupKey on setup failure
        clearGroupKey();
        console.error('Setup error:', setupError);
        Alert.alert(translations.bindError, translations.bindRetryMessage);
        // Go back to step 1 (scan group)
        setCurrentStepIndex(STEP_ORDER.indexOf('scanGroup'));
      }
    } catch (error) {
      Alert.alert(translations.invalidDeviceQr, translations.invalidDeviceQrMessage);
    }
  }, [groupKey, deviceNumber]);

  // Cancel device scan
  const cancelDeviceScan = useCallback(() => {
    setShowScanner(false);
    setScanningFor(null);
  }, []);

  // Handle primary button press based on current step
  const handlePrimaryPress = useCallback(() => {
    switch (currentStep) {
      case 'welcome':
        // Move to scan group step
        setCurrentStepIndex(STEP_ORDER.indexOf('scanGroup'));
        break;
      case 'scanGroup':
        startGroupQrScan();
        break;
      case 'selectNumber':
        if (!deviceNumber) {
          Alert.alert(translations.selectNumberPrompt, translations.selectNumberMessage);
          return;
        }
        // Move to scan device step
        setCurrentStepIndex(STEP_ORDER.indexOf('scanDevice'));
        break;
      case 'scanDevice':
        startDeviceQrScan();
        break;
      case 'success':
        // Navigate to Kiosk screen
        navigation.reset({
          index: 0,
          routes: [{ name: 'Kiosk' }],
        });
        break;
    }
  }, [currentStep, deviceNumber, startGroupQrScan, startDeviceQrScan, navigation]);

  // Handle secondary button press
  const handleSecondaryPress = useCallback(() => {
    if (currentStep === 'welcome') {
      // Skip onboarding and go directly to Kiosk
      if (onSkip) {
        onSkip();
      } else {
        navigation.reset({
          index: 0,
          routes: [{ name: 'Kiosk' }],
        });
      }
    } else if (currentStep === 'scanGroup') {
      // Go back to welcome
      setCurrentStepIndex(STEP_ORDER.indexOf('welcome'));
    } else if (currentStep === 'selectNumber') {
      // Go back to scan group
      setCurrentStepIndex(STEP_ORDER.indexOf('scanGroup'));
    } else if (currentStep === 'scanDevice') {
      // Go back to select number
      setCurrentStepIndex(STEP_ORDER.indexOf('selectNumber'));
    }
  }, [currentStep, navigation]);

  // Get step title
  const getStepTitle = () => {
    switch (currentStep) {
      case 'welcome':
        return translations.welcomeTitle;
      case 'scanGroup':
        return translations.scanGroupTitle;
      case 'selectNumber':
        return translations.selectNumberTitle;
      case 'scanDevice':
        return translations.scanDeviceTitle;
      case 'success':
        return translations.successTitle;
    }
  };

  // Get step description (with dynamic content for success)
  const getStepDescription = () => {
    switch (currentStep) {
      case 'welcome':
        return translations.welcomeDescription;
      case 'scanGroup':
        return translations.scanGroupDescription;
      case 'selectNumber':
        return translations.selectNumberDescription;
      case 'scanDevice':
        return translations.scanDeviceDescription;
      case 'success':
        if (groupName) {
          return `${translations.successWithGroup}${groupName}${translations.successWithGroupAndDevice}${deviceNumber}`;
        }
        return `${translations.successNoGroup}${deviceNumber}`;
    }
  };

  // Render step indicator dots
  const renderStepIndicator = () => {
    // For display, we show 4 main steps (not including success as a dot)
    const indicatorSteps = STEP_ORDER.filter(s => s !== 'success');

    return (
      <View style={styles.stepIndicator}>
        {indicatorSteps.map((step, index) => {
          const stepKey = STEP_ORDER.indexOf(step);
          const isActive = stepKey === currentStepIndex || (currentStep === 'success' && index === indicatorSteps.length - 1);
          return (
            <View
              key={step}
              style={[
                styles.dot,
                isActive ? styles.dotActive : styles.dotInactive,
              ]}
            />
          );
        })}
      </View>
    );
  };

  // Render device number grid
  const renderNumberGrid = () => {
    const numbers = Array.from({ length: 99 }, (_, i) => i + 1); // 1-99

    return (
      <View style={styles.numberGrid}>
        {numbers.map((num) => (
          <TouchableOpacity
            key={num}
            style={[
              styles.numberButton,
              deviceNumber === num && styles.numberButtonSelected,
            ]}
            onPress={() => setDeviceNumber(num)}
          >
            <Text
              style={[
                styles.numberButtonText,
                deviceNumber === num && styles.numberButtonTextSelected,
              ]}
            >
              {num}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
    );
  };

  // Render content based on current step
  const renderContent = () => {
    // Success step
    if (currentStep === 'success') {
      const progressWidth = progressAnim.interpolate({
        inputRange: [0, 100],
        outputRange: ['0%', '100%'],
      });
      return (
        <View style={styles.cardContent}>
          <Icon
            name="check-circle"
            size={80}
            color={Colors.success}
            style={styles.cardIcon}
          />
          <Text style={styles.cardTitle}>{getStepTitle()}</Text>
          <Text style={styles.cardDescriptionSuccess}>{getStepDescription()}</Text>
          <View style={styles.progressBarContainer}>
            <Animated.View style={[styles.progressBar, { width: progressWidth }]} />
          </View>
        </View>
      );
    }

    // Select number step - special rendering
    if (currentStep === 'selectNumber') {
      return (
        <View style={styles.cardContent}>
          <Icon
            name={STEPS[currentStepIndex]?.icon || 'numeric'}
            size={64}
            color={Colors.primary}
            style={styles.cardIcon}
          />
          <Text style={styles.cardTitle}>{getStepTitle()}</Text>
          <Text style={styles.cardDescription}>{getStepDescription()}</Text>
          {renderNumberGrid()}
        </View>
      );
    }

    // Standard step (welcome, scanGroup, scanDevice)
    return (
      <View style={styles.cardContent}>
        <Icon
          name={STEPS[currentStepIndex]?.icon || 'account-group'}
          size={64}
          color={Colors.primary}
          style={styles.cardIcon}
        />
        <Text style={styles.cardTitle}>{getStepTitle()}</Text>
        <Text style={styles.cardDescription}>{getStepDescription()}</Text>
      </View>
    );
  };

  // Get primary button text
  const getPrimaryButtonText = () => {
    switch (currentStep) {
      case 'welcome':
        return translations.btnStartBinding;
      case 'scanGroup':
        return scanningFor === 'group' ? translations.btnScanning : translations.btnScanGroup;
      case 'selectNumber':
        return deviceNumber ? `${translations.btnSelected}${deviceNumber}${translations.btnContinue}` : translations.btnSelectNumber;
      case 'scanDevice':
        return scanningFor === 'device' ? translations.btnScanning : translations.btnScanDevice;
      case 'success':
        return translations.btnEnterKiosk;
    }
  };

  // Is primary button disabled?
  const isPrimaryDisabled = () => {
    if (currentStep === 'selectNumber' && !deviceNumber) return true;
    if (scanningFor) return true;
    return false;
  };

  // Get secondary button text
  const getSecondaryButtonText = () => {
    switch (currentStep) {
      case 'welcome':
        return translations.btnSkip;
      case 'scanGroup':
        return translations.btnBack;
      case 'selectNumber':
        return translations.btnRescanGroup;
      case 'scanDevice':
        return translations.btnReselectNumber;
      default:
        return '';
    }
  };

  // Render error boundary fallback
  if (hasError) {
    return (
      <View style={styles.container}>
        <View style={styles.card}>
          <View style={styles.cardContent}>
            <Icon
              name="alert-circle"
              size={64}
              color={Colors.error}
              style={styles.cardIcon}
            />
            <Text style={styles.cardTitle}>{translations.bindError}</Text>
            <Text style={styles.cardDescription}>{errorMessage}</Text>
          </View>
        </View>
        <TouchableOpacity
          style={styles.primaryButton}
          onPress={() => {
            setHasError(false);
            setCurrentStepIndex(STEP_ORDER.indexOf('welcome'));
            clearGroupKey();
          }}
          activeOpacity={0.8}
        >
          <Text style={styles.primaryButtonText}>{translations.btnBack}</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {/* Configuring overlay */}
      <Modal visible={isConfiguring} transparent animationType="fade">
        <View style={styles.overlay}>
          <View style={styles.overlayContent}>
            <ActivityIndicator size="large" color={Colors.primary} />
            <Text style={styles.overlayText}>{translations.configuring}</Text>
            <Text style={styles.overlaySubText}>{translations.configuringMessage}</Text>
          </View>
        </View>
      </Modal>

      {/* QR Scanner overlay */}
      <Modal visible={showScanner} animationType="slide">
        {scanningFor === 'group' ? (
          <QrScannerView
            onCodeScanned={handleGroupQrScanned}
            onCancel={cancelGroupScan}
            scanTimeout={QR_SCAN_TIMEOUT_MS}
          />
        ) : (
          <QrScannerView
            onCodeScanned={handleDeviceQrScanned}
            onCancel={cancelDeviceScan}
            scanTimeout={QR_SCAN_TIMEOUT_MS}
          />
        )}
      </Modal>

      {/* Step indicator */}
      {renderStepIndicator()}

      {/* Card */}
      <View style={styles.card}>
        <ScrollView
          contentContainerStyle={styles.cardScrollContent}
          showsVerticalScrollIndicator={false}
        >
          {renderContent()}
        </ScrollView>
      </View>

      {/* Primary button */}
      <TouchableOpacity
        style={[
          styles.primaryButton,
          isPrimaryDisabled() && styles.primaryButtonDisabled,
        ]}
        onPress={handlePrimaryPress}
        disabled={isPrimaryDisabled()}
        activeOpacity={0.8}
      >
        <Text style={styles.primaryButtonText}>{getPrimaryButtonText()}</Text>
      </TouchableOpacity>

      {/* Secondary button */}
      {currentStep !== 'success' && (
        <TouchableOpacity
          style={styles.secondaryButton}
          onPress={handleSecondaryPress}
          activeOpacity={0.8}
        >
          <Text style={styles.secondaryButtonText}>{getSecondaryButtonText()}</Text>
        </TouchableOpacity>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.background,
    paddingHorizontal: 24,
    paddingTop: 48,
    paddingBottom: 32,
  },
  stepIndicator: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 32,
  },
  dot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    marginHorizontal: 6,
  },
  dotActive: {
    backgroundColor: Colors.primary,
  },
  dotInactive: {
    backgroundColor: Colors.surfaceVariant,
  },
  card: {
    flex: 1,
    backgroundColor: Colors.surface,
    borderRadius: 24,
    marginBottom: 24,
    overflow: 'hidden',
  },
  cardScrollContent: {
    flexGrow: 1,
  },
  cardContent: {
    alignItems: 'center',
    paddingVertical: 40,
    paddingHorizontal: 24,
  },
  cardIcon: {
    marginBottom: 24,
  },
  cardTitle: {
    fontSize: 28,
    fontWeight: 'bold',
    color: Colors.textPrimary,
    textAlign: 'center',
    marginBottom: 12,
  },
  cardDescription: {
    fontSize: 16,
    color: Colors.textSecondary,
    textAlign: 'center',
    lineHeight: 24,
  },
  cardDescriptionSuccess: {
    fontSize: 16,
    color: Colors.success,
    textAlign: 'center',
    lineHeight: 24,
  },
  progressBarContainer: {
    width: '100%',
    height: 6,
    backgroundColor: 'rgba(74, 222, 128, 0.2)',
    borderRadius: 3,
    marginTop: 20,
    overflow: 'hidden',
  },
  progressBar: {
    height: '100%',
    backgroundColor: Colors.success,
    borderRadius: 3,
  },
  numberGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    marginTop: 24,
    maxWidth: SCREEN_WIDTH - 120,
  },
  numberButton: {
    width: NUMBER_BUTTON_SIZE - 8,
    height: NUMBER_BUTTON_SIZE - 8,
    justifyContent: 'center',
    alignItems: 'center',
    margin: 4,
    borderRadius: 8,
    backgroundColor: Colors.surfaceVariant,
  },
  numberButtonSelected: {
    backgroundColor: Colors.primary,
  },
  numberButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.textSecondary,
  },
  numberButtonTextSelected: {
    color: Colors.textOnPrimary,
  },
  primaryButton: {
    backgroundColor: Colors.primary,
    borderRadius: 16,
    paddingVertical: 18,
    alignItems: 'center',
    marginBottom: 12,
  },
  primaryButtonDisabled: {
    backgroundColor: Colors.textDisabled,
  },
  primaryButtonText: {
    fontSize: 18,
    fontWeight: '600',
    color: Colors.textOnPrimary,
  },
  secondaryButton: {
    paddingVertical: 12,
    alignItems: 'center',
  },
  secondaryButtonText: {
    fontSize: 16,
    color: Colors.textHint,
  },
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  overlayContent: {
    backgroundColor: Colors.surface,
    borderRadius: 16,
    padding: 32,
    alignItems: 'center',
    minWidth: 200,
  },
  overlayText: {
    fontSize: 18,
    fontWeight: '600',
    color: Colors.textPrimary,
    marginTop: 16,
  },
  overlaySubText: {
    fontSize: 14,
    color: Colors.textSecondary,
    marginTop: 8,
    textAlign: 'center',
  },
});

export default OnboardingScreen;
