/**
 * FreeKiosk v1.2 - QrScannerView
 * QR Scanner with visible camera preview using VisionCamera
 */

import React, { useCallback, useRef, useState, useEffect } from 'react';
import { View, Text, StyleSheet, Dimensions, Alert, Platform, PermissionsAndroid, TouchableOpacity } from 'react-native';
import { Camera, useCameraDevice, useCameraPermission, Code, useCodeScanner } from 'react-native-vision-camera';
import type { CodeScanner } from 'react-native-vision-camera';
import { Colors } from '../theme';

const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');

interface QrScannerViewProps {
  onCodeScanned: (data: string) => void;
  onCancel: () => void;
  scanTimeout?: number; // ms, default 30000
}

const QrScannerView: React.FC<QrScannerViewProps> = ({
  onCodeScanned,
  onCancel,
  scanTimeout = 30000,
}) => {
  const device = useCameraDevice('back');
  const { hasPermission, requestPermission } = useCameraPermission();
  const [isScanning, setIsScanning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const scannedRef = useRef(false);
  const cameraRef = useRef<Camera>(null);

  // Request camera permission on mount
  useEffect(() => {
    if (!hasPermission) {
      // Request permission and handle potential rejection
      requestPermission().catch((error) => {
        console.warn('[QrScanner] Camera permission error:', error);
      });
    }
  }, [hasPermission, requestPermission]);

  // Cleanup timeout on unmount
  useEffect(() => {
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, []);

  // Start scanning when camera is ready
  useEffect(() => {
    if (device && hasPermission && !isScanning && !scannedRef.current) {
      setIsScanning(true);
      // Set timeout
      timeoutRef.current = setTimeout(() => {
        if (!scannedRef.current) {
          Alert.alert('扫描超时', '未扫描到二维码，请重试', [
            { text: '确定', onPress: onCancel },
          ]);
        }
      }, scanTimeout);
    }

    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
        timeoutRef.current = null;
      }
    };
  }, [device, hasPermission, isScanning, scanTimeout, onCancel]);

  // Handle code scanned
  const handleCodeScanned = useCallback((codes: Code[]) => {
    // Defensive: ensure codes is a valid non-empty array
    if (scannedRef.current || !codes || codes.length === 0) {
      return;
    }

    const code = codes[0];
    // Defensive: ensure code exists and has a value
    if (code && code.value) {
      scannedRef.current = true;
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
        timeoutRef.current = null;
      }
      onCodeScanned(code.value);
    }
  }, [onCodeScanned]);

  // Code scanner configuration
  const codeScanner: CodeScanner = useCodeScanner({
    codeTypes: ['qr'],
    onCodeScanned: handleCodeScanned,
  });

  // Permission denied
  if (!hasPermission) {
    return (
      <View style={styles.container}>
        <View style={styles.permissionContainer}>
          <Text style={styles.permissionText}>需要相机权限来扫描二维码</Text>
        </View>
      </View>
    );
  }

  // No camera device
  if (!device) {
    return (
      <View style={styles.container}>
        <View style={styles.permissionContainer}>
          <Text style={styles.permissionText}>未找到相机设备</Text>
        </View>
      </View>
    );
  }

  // Error state
  if (error) {
    return (
      <View style={styles.container}>
        <View style={styles.permissionContainer}>
          <Text style={styles.errorText}>{error}</Text>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {/* Camera preview */}
      <Camera
        ref={cameraRef}
        style={styles.camera}
        device={device}
        isActive={true}
        codeScanner={codeScanner}
      />

      {/* Overlay with scan frame indicator */}
      <View style={styles.overlay}>
        {/* Top overlay */}
        <View style={styles.overlayTop} />

        {/* Middle row with scan area */}
        <View style={styles.overlayMiddle}>
          <View style={styles.overlaySide} />
          <View style={styles.scanFrame}>
            {/* Corner indicators */}
            <View style={[styles.corner, styles.cornerTopLeft]} />
            <View style={[styles.corner, styles.cornerTopRight]} />
            <View style={[styles.corner, styles.cornerBottomLeft]} />
            <View style={[styles.corner, styles.cornerBottomRight]} />
          </View>
          <View style={styles.overlaySide} />
        </View>

        {/* Bottom overlay with instructions */}
        <View style={styles.overlayBottom}>
          <Text style={styles.instructionText}>将二维码放入框内</Text>
          <Text style={styles.hintText}>扫描中...</Text>
          <TouchableOpacity style={styles.cancelButton} onPress={onCancel}>
            <Text style={styles.cancelButtonText}>取消</Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.background,
  },
  camera: {
    flex: 1,
  },
  overlay: {
    ...StyleSheet.absoluteFillObject,
  },
  overlayTop: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
  },
  overlayMiddle: {
    flexDirection: 'row',
  },
  overlaySide: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
  },
  scanFrame: {
    width: SCREEN_WIDTH * 0.7,
    height: SCREEN_WIDTH * 0.7,
    position: 'relative',
  },
  corner: {
    position: 'absolute',
    width: 40,
    height: 40,
    borderColor: Colors.primary,
  },
  cornerTopLeft: {
    top: 0,
    left: 0,
    borderTopWidth: 4,
    borderLeftWidth: 4,
  },
  cornerTopRight: {
    top: 0,
    right: 0,
    borderTopWidth: 4,
    borderRightWidth: 4,
  },
  cornerBottomLeft: {
    bottom: 0,
    left: 0,
    borderBottomWidth: 4,
    borderLeftWidth: 4,
  },
  cornerBottomRight: {
    bottom: 0,
    right: 0,
    borderBottomWidth: 4,
    borderRightWidth: 4,
  },
  overlayBottom: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
    alignItems: 'center',
    paddingTop: 40,
  },
  instructionText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '600',
  },
  hintText: {
    color: 'rgba(255, 255, 255, 0.7)',
    fontSize: 14,
    marginTop: 12,
  },
  cancelButton: {
    marginTop: 24,
    paddingHorizontal: 32,
    paddingVertical: 12,
    borderRadius: 8,
    borderWidth: 2,
    borderColor: Colors.primary,
    backgroundColor: 'rgba(0, 0, 0, 0.3)',
  },
  cancelButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
    textAlign: 'center',
  },
  permissionContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: Colors.background,
    padding: 24,
  },
  permissionText: {
    color: Colors.textPrimary,
    fontSize: 16,
    textAlign: 'center',
  },
  errorText: {
    color: Colors.error,
    fontSize: 16,
    textAlign: 'center',
  },
});

export default QrScannerView;
