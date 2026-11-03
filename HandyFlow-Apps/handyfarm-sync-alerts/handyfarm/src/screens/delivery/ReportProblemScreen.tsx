import React, { useState } from 'react';
import { View, Text, TextInput, Pressable, KeyboardAvoidingView, Platform, StyleSheet } from 'react-native';
import * as Location from 'expo-location';
import { Ionicons } from '@expo/vector-icons';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useAuthStore } from '../../store/authStore';
import { useDeliveryStore } from '../../store/deliveryStore';

const REASONS = [
  'Customer unavailable',
  'Wrong location',
  'Quantity dispute',
  'Vehicle breakdown',
  'Tank unavailable',
  'Unsafe site',
  'Spill/leak',
  'Delivery rejected',
  'Other',
];

export function ReportProblemScreen({ route, navigation }: any) {
  const { deliveryId } = route.params;
  const user = useAuthStore((s) => s.user);
  const reportProblem = useDeliveryStore((s) => s.reportProblem);

  const [reason, setReason] = useState<string | null>(null);
  const [notes, setNotes] = useState('');
  const [gpsStatus, setGpsStatus] = useState<'idle' | 'capturing' | 'captured' | 'denied'>('idle');
  const [coords, setCoords] = useState<{ lat: number; lng: number } | null>(null);

  const captureGps = async () => {
    setGpsStatus('capturing');
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') {
      setGpsStatus('denied');
      return;
    }
    const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
    setCoords({ lat: position.coords.latitude, lng: position.coords.longitude });
    setGpsStatus('captured');
  };

  const canSubmit = !!reason && !!user;

  const handleSubmit = () => {
    if (!canSubmit || !reason) return;
    reportProblem(deliveryId, {
      reason,
      notes: notes.trim(),
      photoUri: null,
      gps: coords,
      reportedAt: new Date().toISOString(),
    });
    navigation.getParent()?.goBack();
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={styles.header}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12}>
          <Ionicons name="arrow-back" size={22} color={colors.textPrimary} />
        </Pressable>
        <Text style={styles.title}>REPORT PROBLEM</Text>
      </View>

      <View style={styles.body}>
        {REASONS.map((r) => (
          <Pressable key={r} onPress={() => setReason(r)} style={styles.reasonRow}>
            <Ionicons
              name={reason === r ? 'radio-button-on' : 'radio-button-off'}
              size={20}
              color={reason === r ? colors.primaryDark : colors.textMuted}
            />
            <Text style={styles.reasonText}>{r}</Text>
          </Pressable>
        ))}

        <Text style={styles.notesLabel}>Notes</Text>
        <TextInput
          value={notes}
          onChangeText={setNotes}
          placeholder="What happened?"
          placeholderTextColor={colors.textMuted}
          multiline
          style={styles.notesInput}
        />

        <Pressable onPress={captureGps} style={styles.gpsButton}>
          <Ionicons
            name={gpsStatus === 'captured' ? 'checkmark-circle' : 'location-outline'}
            size={18}
            color={gpsStatus === 'captured' ? colors.success : colors.primaryDark}
          />
          <Text style={styles.gpsButtonText}>
            {gpsStatus === 'captured'
              ? 'GPS captured'
              : gpsStatus === 'capturing'
                ? 'Capturing...'
                : gpsStatus === 'denied'
                  ? 'Permission denied — tap to retry'
                  : 'Capture GPS'}
          </Text>
        </Pressable>
      </View>

      <View style={styles.footer}>
        <Pressable
          disabled={!canSubmit}
          onPress={handleSubmit}
          style={[styles.submit, !canSubmit && styles.disabled]}
        >
          <Text style={styles.submitText}>Submit Report</Text>
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    padding: spacing.lg,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  title: { ...typography.h3 },
  body: { flex: 1, padding: spacing.lg },
  reasonRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, paddingVertical: spacing.sm },
  reasonText: { ...typography.body },
  notesLabel: { ...typography.caption, marginTop: spacing.md, marginBottom: spacing.xs },
  notesInput: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.md,
    padding: spacing.md,
    minHeight: 80,
    textAlignVertical: 'top',
    color: colors.textPrimary,
    marginBottom: spacing.md,
  },
  gpsButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.md,
  },
  gpsButtonText: { ...typography.bodyStrong, color: colors.primaryDark },
  footer: { padding: spacing.lg, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border },
  submit: {
    backgroundColor: colors.danger,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  disabled: { opacity: 0.4 },
  submitText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
});
