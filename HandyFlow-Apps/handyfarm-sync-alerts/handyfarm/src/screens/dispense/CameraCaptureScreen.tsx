import React, { useRef, useState } from 'react';
import { View, Text, Pressable, StyleSheet, ActivityIndicator } from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import { Ionicons } from '@expo/vector-icons';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useDispenseFlowStore } from '../../store/dispenseFlowStore';

export function CameraCaptureScreen({ navigation }: any) {
  const [permission, requestPermission] = useCameraPermissions();
  const [capturing, setCapturing] = useState(false);
  const cameraRef = useRef<CameraView>(null);
  const setPhoto = useDispenseFlowStore((s) => s.setPhoto);

  if (!permission) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }

  if (!permission.granted) {
    return (
      <View style={styles.center}>
        <Ionicons name="camera-outline" size={40} color={colors.textMuted} />
        <Text style={styles.permissionTitle}>Camera access needed</Text>
        <Text style={styles.permissionBody}>
          HandyFlow needs the camera to capture evidence photos for this transaction.
        </Text>
        <Pressable onPress={requestPermission} style={styles.permissionButton}>
          <Text style={styles.permissionButtonText}>Grant Access</Text>
        </Pressable>
        <Pressable onPress={() => navigation.goBack()} style={{ marginTop: spacing.md }}>
          <Text style={styles.cancelText}>Cancel</Text>
        </Pressable>
      </View>
    );
  }

  const handleCapture = async () => {
    if (!cameraRef.current || capturing) return;
    setCapturing(true);
    try {
      const photo = await cameraRef.current.takePictureAsync({ quality: 0.6 });
      if (photo?.uri) {
        setPhoto(photo.uri);
        navigation.goBack();
      }
    } finally {
      setCapturing(false);
    }
  };

  return (
    <View style={styles.container}>
      <CameraView ref={cameraRef} style={StyleSheet.absoluteFill} facing="back" />

      <Pressable onPress={() => navigation.goBack()} style={styles.closeButton} hitSlop={12}>
        <Ionicons name="close" size={26} color="#fff" />
      </Pressable>

      <View style={styles.shutterRow}>
        <Pressable onPress={handleCapture} disabled={capturing} style={styles.shutterOuter}>
          {capturing ? (
            <ActivityIndicator color={colors.textOnDark} />
          ) : (
            <View style={styles.shutterInner} />
          )}
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#000' },
  center: {
    flex: 1,
    backgroundColor: colors.bg,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
    gap: spacing.sm,
  },
  permissionTitle: { ...typography.h3, marginTop: spacing.sm },
  permissionBody: { ...typography.caption, textAlign: 'center', marginBottom: spacing.md },
  permissionButton: {
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.xl,
  },
  permissionButtonText: { color: colors.textOnDark, fontWeight: '700' },
  cancelText: { color: colors.textSecondary, fontWeight: '600' },
  closeButton: {
    position: 'absolute',
    top: spacing.xl,
    left: spacing.lg,
    width: 40,
    height: 40,
    borderRadius: radii.pill,
    backgroundColor: 'rgba(0,0,0,0.4)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  shutterRow: {
    position: 'absolute',
    bottom: spacing.xxxl,
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  shutterOuter: {
    width: 76,
    height: 76,
    borderRadius: 38,
    borderWidth: 4,
    borderColor: 'rgba(255,255,255,0.8)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  shutterInner: { width: 60, height: 60, borderRadius: 30, backgroundColor: '#fff' },
});
