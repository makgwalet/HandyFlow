import React, { useEffect } from 'react';
import { View, Text, TextInput, Pressable, Image, KeyboardAvoidingView, Platform, StyleSheet } from 'react-native';
import * as Location from 'expo-location';
import { Ionicons } from '@expo/vector-icons';
import { WizardHeader } from '../../components/WizardHeader';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useReceiveFlowStore } from '../../store/receiveFlowStore';
import { MOCK_TANKS } from '../../data/mockData';

export function DipReadingsScreen({ navigation }: any) {
  const flow = useReceiveFlowStore();
  const tank = MOCK_TANKS.find((t) => t.id === flow.tankId);

  useEffect(() => {
    if (!flow.preDipLitres) {
      flow.setDipReadings(String(tank?.currentLitres ?? ''), flow.postDipLitres);
    }
    if (flow.gps.status === 'idle') captureGps();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const captureGps = async () => {
    flow.setGps('capturing');
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        flow.setGps('denied');
        return;
      }
      const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      flow.setGps('captured', {
        lat: position.coords.latitude,
        lng: position.coords.longitude,
        accuracy: position.coords.accuracy,
      });
    } catch {
      flow.setGps('error');
    }
  };

  if (!tank) return null;

  const preDip = parseFloat(flow.preDipLitres);
  const postDip = parseFloat(flow.postDipLitres);
  const readingsValid = !Number.isNaN(preDip) && !Number.isNaN(postDip) && postDip > preDip;
  const canContinue = readingsValid && !!flow.photoUri;

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <WizardHeader title="Dip Readings" step={4} totalSteps={5} />
      <View style={styles.body}>
        <Text style={styles.tankLabel}>{tank.name}</Text>

        <View style={styles.field}>
          <Text style={styles.label}>Pre-delivery dip (L)</Text>
          <TextInput
            value={flow.preDipLitres}
            onChangeText={(v) => flow.setDipReadings(v, flow.postDipLitres)}
            keyboardType="decimal-pad"
            style={styles.input}
          />
        </View>

        <View style={styles.field}>
          <Text style={styles.label}>Post-delivery dip (L)</Text>
          <TextInput
            value={flow.postDipLitres}
            onChangeText={(v) => flow.setDipReadings(flow.preDipLitres, v)}
            keyboardType="decimal-pad"
            placeholder="Measure after the tanker finishes"
            placeholderTextColor={colors.textMuted}
            style={styles.input}
          />
          {!readingsValid && flow.postDipLitres.length > 0 && (
            <Text style={styles.error}>Post-delivery reading must be greater than pre-delivery</Text>
          )}
        </View>

        {readingsValid && (
          <Text style={styles.actualReceived}>
            Actual received: {(postDip - preDip).toLocaleString()} L
          </Text>
        )}

        <Text style={styles.sectionLabel}>Waybill Photo</Text>
        {flow.photoUri ? (
          <Pressable onPress={() => navigation.navigate('ReceiveCameraCapture', { target: 'photo' })}>
            <Image source={{ uri: flow.photoUri }} style={styles.photoPreview} />
          </Pressable>
        ) : (
          <Pressable
            onPress={() => navigation.navigate('ReceiveCameraCapture', { target: 'photo' })}
            style={styles.photoPlaceholder}
          >
            <Ionicons name="camera-outline" size={24} color={colors.primary} />
            <Text style={styles.photoPlaceholderText}>Capture Waybill</Text>
          </Pressable>
        )}

        <View style={styles.gpsRow}>
          <View
            style={[
              styles.gpsDot,
              { backgroundColor: flow.gps.status === 'captured' ? colors.success : colors.textMuted },
            ]}
          />
          <Text style={styles.gpsText}>
            {flow.gps.status === 'captured'
              ? 'GPS Captured'
              : flow.gps.status === 'capturing'
                ? 'Capturing GPS...'
                : 'GPS not captured'}
          </Text>
        </View>
      </View>

      <View style={styles.footer}>
        <Pressable
          disabled={!canContinue}
          onPress={() => navigation.navigate('ReceiveReconciliation')}
          style={[styles.next, !canContinue && styles.disabled]}
        >
          <Text style={styles.nextText}>Continue</Text>
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  body: { flex: 1, padding: spacing.lg },
  tankLabel: { ...typography.h3, marginBottom: spacing.lg },
  field: { gap: spacing.xs, marginBottom: spacing.md },
  label: { ...typography.caption },
  input: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    fontSize: 16,
    fontWeight: '600',
    color: colors.textPrimary,
  },
  error: { color: colors.danger, fontSize: 12 },
  actualReceived: { ...typography.bodyStrong, color: colors.success, marginBottom: spacing.lg },
  sectionLabel: { ...typography.label, textTransform: 'uppercase', marginBottom: spacing.sm },
  photoPlaceholder: {
    height: 110,
    borderRadius: radii.lg,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: colors.borderStrong,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.xs,
    marginBottom: spacing.md,
  },
  photoPlaceholderText: { ...typography.bodyStrong, color: colors.primary },
  photoPreview: { height: 130, borderRadius: radii.lg, marginBottom: spacing.md },
  gpsRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
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
