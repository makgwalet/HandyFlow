import React, { useEffect } from 'react';
import { View, Text, Pressable, Image, StyleSheet } from 'react-native';
import * as Location from 'expo-location';
import { Ionicons } from '@expo/vector-icons';
import { WizardHeader } from '../../components/WizardHeader';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useDipFlowStore } from '../../store/dipFlowStore';
import { MOCK_TANKS } from '../../data/mockData';

const STEP = 10;

export function PhysicalReadingScreen({ navigation }: any) {
  const { tankId, physicalDipLitres, setPhysicalDip, photoUri, gps, setGps } = useDipFlowStore();
  const tank = MOCK_TANKS.find((t) => t.id === tankId);

  useEffect(() => {
    if (gps.status === 'idle') captureGps();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const captureGps = async () => {
    setGps('capturing');
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        setGps('denied');
        return;
      }
      const position = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });
      setGps('captured', {
        lat: position.coords.latitude,
        lng: position.coords.longitude,
        accuracy: position.coords.accuracy,
      });
    } catch {
      setGps('error');
    }
  };

  if (!tank) return null;

  const clamp = (v: number) => Math.max(0, v);
  const canContinue = physicalDipLitres > 0;

  return (
    <View style={styles.container}>
      <WizardHeader title="Record Dip" step={2} totalSteps={3} />
      <View style={styles.body}>
        <Text style={styles.tankLabel}>{tank.name}</Text>
        <Text style={styles.bookLabel}>
          Book Level: {tank.currentLitres.toLocaleString()} L
        </Text>

        <View style={styles.stepperRow}>
          <Pressable
            onPress={() => setPhysicalDip(clamp(physicalDipLitres - STEP))}
            style={styles.stepperButton}
          >
            <Text style={styles.stepperGlyph}>−</Text>
          </Pressable>
          <View style={styles.amountBlock}>
            <Text style={styles.amountValue}>{physicalDipLitres}</Text>
            <Text style={styles.amountUnit}>L</Text>
          </View>
          <Pressable
            onPress={() => setPhysicalDip(physicalDipLitres + STEP)}
            style={styles.stepperButton}
          >
            <Text style={styles.stepperGlyph}>+</Text>
          </Pressable>
        </View>

        <Text style={styles.sectionLabel}>Gauge Photo</Text>
        {photoUri ? (
          <Pressable onPress={() => navigation.navigate('DipCameraCapture', { target: 'photo' })}>
            <Image source={{ uri: photoUri }} style={styles.photoPreview} />
          </Pressable>
        ) : (
          <Pressable
            onPress={() => navigation.navigate('DipCameraCapture', { target: 'photo' })}
            style={styles.photoPlaceholder}
          >
            <Ionicons name="camera-outline" size={24} color={colors.primary} />
            <Text style={styles.photoPlaceholderText}>Capture Photo</Text>
          </Pressable>
        )}

        <View style={styles.gpsRow}>
          <View
            style={[
              styles.gpsDot,
              { backgroundColor: gps.status === 'captured' ? colors.success : colors.textMuted },
            ]}
          />
          <Text style={styles.gpsText}>
            {gps.status === 'captured'
              ? 'GPS Captured'
              : gps.status === 'capturing'
                ? 'Capturing GPS...'
                : gps.status === 'denied'
                  ? 'GPS permission denied'
                  : 'GPS not captured'}
          </Text>
        </View>
      </View>

      <View style={styles.footer}>
        <Pressable
          disabled={!canContinue}
          onPress={() => navigation.navigate('Reconciliation')}
          style={[styles.next, !canContinue && styles.disabled]}
        >
          <Text style={styles.nextText}>Continue</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  body: { flex: 1, padding: spacing.lg, alignItems: 'center' },
  tankLabel: { ...typography.h3, marginTop: spacing.sm },
  bookLabel: { ...typography.caption, marginBottom: spacing.xl },
  stepperRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.xl, marginBottom: spacing.xl },
  stepperButton: {
    width: 48,
    height: 48,
    borderRadius: radii.pill,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepperGlyph: { fontSize: 26, fontWeight: '700', color: colors.primaryDark },
  amountBlock: { alignItems: 'center', minWidth: 140 },
  amountValue: { fontSize: 52, fontWeight: '700', color: colors.textPrimary },
  amountUnit: { ...typography.label, marginTop: -4 },
  sectionLabel: { ...typography.label, textTransform: 'uppercase', alignSelf: 'flex-start', marginBottom: spacing.sm },
  photoPlaceholder: {
    width: '100%',
    height: 120,
    borderRadius: radii.lg,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: colors.borderStrong,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.xs,
    marginBottom: spacing.lg,
  },
  photoPlaceholderText: { ...typography.bodyStrong, color: colors.primary },
  photoPreview: { width: '100%', height: 140, borderRadius: radii.lg, marginBottom: spacing.lg },
  gpsRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, alignSelf: 'flex-start' },
  gpsDot: { width: 8, height: 8, borderRadius: radii.pill },
  gpsText: { ...typography.caption },
  footer: { padding: spacing.lg, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border },
  next: {
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  disabled: { opacity: 0.4 },
  nextText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
});
