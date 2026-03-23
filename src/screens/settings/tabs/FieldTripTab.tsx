/**
 * FreeKiosk v1.2 - Field Trip Tab
 * Hub connection status, device info, GPS status, and field trip controls
 */

import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, Alert, NativeModules } from 'react-native';
import { useTranslation } from 'react-i18next';
import {
  SettingsSection,
  SettingsButton,
  SettingsInfoBox,
} from '../../../components/settings';
import { Colors, Spacing, Typography } from '../../../theme';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../../navigation/AppNavigator';

const { HubConfigModule } = NativeModules;

interface FieldTripTabProps {
  navigation: NativeStackNavigationProp<RootStackParamList>;
}

interface HubConfig {
  hubUrl: string;
  deviceId: string;
  groupId: string;
  groupName: string;
  deviceName: string;
}

const FieldTripTab: React.FC<FieldTripTabProps> = ({ navigation }) => {
  const { t } = useTranslation();
  const [hubConfig, setHubConfig] = useState<HubConfig | null>(null);
  const [isConfigured, setIsConfigured] = useState(false);
  const [loading, setLoading] = useState(true);

  const loadHubConfig = useCallback(async () => {
    try {
      const config = await HubConfigModule.getConfig();
      if (config) {
        setHubConfig(config);
        setIsConfigured(true);
      } else {
        setHubConfig(null);
        setIsConfigured(false);
      }
    } catch (error) {
      console.error('[FieldTripTab] Failed to load Hub config:', error);
      setHubConfig(null);
      setIsConfigured(false);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadHubConfig();
  }, [loadHubConfig]);

  const handleRebind = () => {
    Alert.alert(
      t('fieldtrip.rebindTitle') || '重新绑定',
      t('fieldtrip.rebindConfirm') || '这将清除当前配置并跳转到绑定页面。您可以扫描新的二维码来绑定到新的Hub。',
      [
        { text: t('common.cancel') || '取消', style: 'cancel' },
        {
          text: t('common.confirm') || '确认',
          onPress: () => {
            // Navigate to OnboardingScreen - the user can scan a new QR code
            // and the new binding will overwrite the old config
            navigation.reset({
              index: 0,
              routes: [{ name: 'Onboarding' }],
            });
          },
        },
      ],
    );
  };

  const handleExitFieldTrip = () => {
    Alert.alert(
      t('fieldtrip.exitTitle') || '退出研学模式',
      t('fieldtrip.exitConfirm') || '这将停止GPS定位并退出研学模式。设备将恢复到标准Kiosk模式。',
      [
        { text: t('common.cancel') || '取消', style: 'cancel' },
        {
          text: t('fieldtrip.exit') || '退出',
          style: 'destructive',
          onPress: async () => {
            try {
              // Stop GPS reporting
              await HubConfigModule.stopGpsReporting();
            } catch (error) {
              console.error('[FieldTripTab] Failed to stop GPS:', error);
            }
            // Navigate back to Kiosk (Field Trip config remains in SharedPreferences
            // but GPS reporting is stopped)
            navigation.reset({
              index: 0,
              routes: [{ name: 'Kiosk' }],
            });
          },
        },
      ],
    );
  };

  if (loading) {
    return (
      <View>
        <SettingsInfoBox variant="info">
          <Text style={styles.infoText}>
            {t('common.loading') || '加载中...'}
          </Text>
        </SettingsInfoBox>
      </View>
    );
  }

  if (!hubConfig) {
    return (
      <View>
        <SettingsInfoBox variant="warning" title={t('fieldtrip.notConfigured') || '未配置研学模式'}>
          <Text style={styles.infoText}>
            {t('fieldtrip.notConfiguredDesc') || '此设备尚未绑定到研学Hub。请在首次设置时扫描二维码进行绑定。'}
          </Text>
        </SettingsInfoBox>

        <SettingsSection title={t('fieldtrip.actions') || '操作'} icon="cog-outline">
          <SettingsButton
            title={t('fieldtrip.goToOnboarding') || '前往绑定'}
            icon="qrcode-scan"
            variant="primary"
            onPress={() => {
              navigation.reset({
                index: 0,
                routes: [{ name: 'Onboarding' }],
              });
            }}
          />
        </SettingsSection>
      </View>
    );
  }

  return (
    <View>
      {/* Hub Connection Status */}
      <SettingsSection title={t('fieldtrip.hubConnection') || 'Hub连接状态'} icon="server-network">
        <View style={styles.statusRow}>
          <Text style={styles.statusLabel}>
            {t('fieldtrip.status') || '状态'}
          </Text>
          <View style={[
            styles.statusBadge,
            { backgroundColor: isConfigured ? Colors.successLight : Colors.errorLight }
          ]}>
            <Text style={[
              styles.statusText,
              { color: isConfigured ? Colors.successDark : Colors.errorDark }
            ]}>
              {isConfigured ? t('fieldtrip.configuredStatus') : t('fieldtrip.notConfiguredStatus')}
            </Text>
          </View>
        </View>

        <View style={styles.infoRow}>
          <Text style={styles.infoLabel}>{t('fieldtrip.hubUrl')}</Text>
          <Text style={styles.infoValue} numberOfLines={1}>{hubConfig.hubUrl}</Text>
        </View>
      </SettingsSection>

      {/* Device Information */}
      <SettingsSection title={t('fieldtrip.deviceInfo') || '设备信息'} icon="tablet">
        <View style={styles.infoRow}>
          <Text style={styles.infoLabel}>{t('fieldtrip.deviceId')}</Text>
          <Text style={styles.infoValueMono} numberOfLines={1}>{hubConfig.deviceId}</Text>
        </View>

        <View style={styles.infoRow}>
          <Text style={styles.infoLabel}>{t('fieldtrip.groupId')}</Text>
          <Text style={styles.infoValueMono} numberOfLines={1}>{hubConfig.groupName || hubConfig.groupId || '-'}</Text>
        </View>

        <View style={styles.infoRow}>
          <Text style={styles.infoLabel}>{t('fieldtrip.deviceName')}</Text>
          <Text style={styles.infoValue} numberOfLines={1}>{hubConfig.deviceName || hubConfig.deviceId?.slice(-6) || '-'}</Text>
        </View>
      </SettingsSection>

      {/* GPS Status */}
      <SettingsSection title={t('fieldtrip.gpsStatus') || 'GPS状态'} icon="map-marker-radius">
        <View style={styles.statusRow}>
          <Text style={styles.statusLabel}>
            {t('fieldtrip.gpsReporting') || 'GPS定位'}
          </Text>
          <View style={[
            styles.statusBadge,
            { backgroundColor: isConfigured ? Colors.successLight : Colors.warningLight }
          ]}>
            <Text style={[
              styles.statusText,
              { color: isConfigured ? Colors.successDark : Colors.warningDark }
            ]}>
              {isConfigured ? t('fieldtrip.gpsEnabled') : t('fieldtrip.gpsDisabled')}
            </Text>
          </View>
        </View>

        <Text style={styles.hint}>
          {t('fieldtrip.gpsDashboardHint') || 'GPS 位置请在 Hub 管理后台查看'}
        </Text>
      </SettingsSection>

      {/* Actions */}
      <SettingsSection title={t('fieldtrip.actions') || '操作'} icon="cog-outline">
        <SettingsButton
          title={t('fieldtrip.rebind') || '重新绑定'}
          icon="qrcode-scan"
          variant="primary"
          onPress={handleRebind}
        />

        <SettingsButton
          title={t('fieldtrip.exitFieldTrip') || '退出研学模式'}
          icon="exit-to-app"
          variant="danger"
          onPress={handleExitFieldTrip}
        />
      </SettingsSection>
    </View>
  );
};

const styles = StyleSheet.create({
  infoText: {
    ...Typography.body,
    lineHeight: 22,
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Spacing.sm,
    marginBottom: Spacing.xs,
  },
  statusLabel: {
    ...Typography.body,
    fontWeight: '600',
  },
  statusBadge: {
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.xs,
    borderRadius: 12,
  },
  statusText: {
    ...Typography.label,
    fontSize: 13,
  },
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Spacing.sm,
    borderBottomWidth: 1,
    borderBottomColor: Colors.border,
  },
  infoLabel: {
    ...Typography.body,
    color: Colors.textSecondary,
    flex: 1,
  },
  infoValue: {
    ...Typography.label,
    color: Colors.textPrimary,
    flex: 2,
    textAlign: 'right',
  },
  infoValueMono: {
    ...Typography.mono,
    color: Colors.textPrimary,
    flex: 2,
    textAlign: 'right',
    fontSize: 13,
  },
  hint: {
    ...Typography.hint,
    marginTop: Spacing.sm,
  },
});

export default FieldTripTab;
