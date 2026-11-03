import React from 'react';
import { View, Text, TextInput, Pressable, StyleSheet, KeyboardAvoidingView, Platform } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useAuthStore } from '../../store/authStore';
import { useDipFlowStore, varianceSeverity } from '../../store/dipFlowStore';
import { useTransactionQueueStore } from '../../store/transactionQueueStore';
import { useVarianceStore } from '../../store/varianceStore';
import { MOCK_TANKS } from '../../data/mockData';

function ChecklistRow({ label, checked, action }: { label: string; checked: boolean; action?: React.ReactNode }) {
  return (
    <View style={styles.checkRow}>
      <Ionicons
        name={checked ? 'checkbox' : 'square-outline'}
        size={20}
        color={checked ? colors.success : colors.textMuted}
      />
      <Text style={styles.checkLabel}>{label}</Text>
      {action}
    </View>
  );
}

export function VarianceReasonScreen({ navigation }: any) {
  const user = useAuthStore((s) => s.user);
  const flow = useDipFlowStore();
  const enqueue = useTransactionQueueStore((s) => s.enqueue);
  const createVariance = useVarianceStore((s) => s.create);

  const tank = MOCK_TANKS.find((t) => t.id === flow.tankId);
  if (!tank || !user) return null;

  const bookLevel = tank.currentLitres;
  const variance = flow.physicalDipLitres - bookLevel;
  const severity = varianceSeverity(variance);

  const hasPhoto = !!flow.photoUri;
  const hasGps = flow.gps.status === 'captured';
  const hasReason = flow.reason.trim().length > 0;
  const canSubmit = hasPhoto && hasGps && hasReason;

  const handleSubmit = () => {
    if (!canSubmit) return;

    const varianceRecord = createVariance({
      tankId: tank.id,
      bookLevel,
      physicalLevel: flow.physicalDipLitres,
      varianceLitres: variance,
      reason: flow.reason.trim(),
      photoUri: flow.photoUri,
    });

    const item = enqueue('TANK_DIP', user.id, {
      tankId: tank.id,
      bookLevel,
      physicalLevel: flow.physicalDipLitres,
      varianceLitres: variance,
      photoUri: flow.photoUri,
      additionalEvidenceUri: flow.additionalEvidenceUri,
      reason: flow.reason.trim(),
      location: flow.gps.coords,
      varianceId: varianceRecord.id,
      capturedAt: new Date().toISOString(),
    });

    navigation.replace('Success', {
      clientRequestId: item.clientRequestId,
      flagged: true,
      varianceId: varianceRecord.id,
    });
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={styles.header}>
        <Text style={styles.title}>
          ⚠ {severity === 'critical' ? 'Critical Variance' : 'Variance Detected'}
        </Text>
        <Text style={styles.subtitle}>{Math.abs(variance).toLocaleString()} L difference</Text>
      </View>

      <View style={styles.body}>
        <Text style={styles.sectionLabel}>Required</Text>
        <ChecklistRow label="Gauge photo" checked={hasPhoto} />
        <ChecklistRow label="Operator identity" checked={!!user} />
        <ChecklistRow label="GPS" checked={hasGps} />
        <ChecklistRow label="Reason" checked={hasReason} />

        <Text style={[styles.sectionLabel, { marginTop: spacing.lg }]}>Optional</Text>
        <ChecklistRow
          label={flow.additionalEvidenceUri ? 'Additional evidence added' : 'Additional evidence'}
          checked={!!flow.additionalEvidenceUri}
          action={
            <Pressable
              onPress={() => navigation.navigate('DipCameraCapture', { target: 'evidence' })}
            >
              <Text style={styles.addLink}>{flow.additionalEvidenceUri ? 'Replace' : 'Add'}</Text>
            </Pressable>
          }
        />

        <Text style={styles.reasonLabel}>Reason</Text>
        <TextInput
          value={flow.reason}
          onChangeText={flow.setReason}
          placeholder="What happened? e.g. temperature expansion, recent top-up not yet reflected..."
          placeholderTextColor={colors.textMuted}
          multiline
          numberOfLines={4}
          style={styles.reasonInput}
        />
      </View>

      <View style={styles.footer}>
        <Pressable
          disabled={!canSubmit}
          onPress={handleSubmit}
          style={[styles.submit, !canSubmit && styles.disabled]}
        >
          <Text style={styles.submitText}>Submit & Flag</Text>
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  header: {
    padding: spacing.lg,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  title: { ...typography.h3, color: colors.danger },
  subtitle: { ...typography.caption, marginTop: 2 },
  body: { flex: 1, padding: spacing.lg },
  sectionLabel: { ...typography.label, textTransform: 'uppercase', marginBottom: spacing.sm },
  checkRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, paddingVertical: spacing.xs },
  checkLabel: { ...typography.body, flex: 1 },
  addLink: { ...typography.caption, color: colors.primaryDark, fontWeight: '700' },
  reasonLabel: { ...typography.caption, marginTop: spacing.lg, marginBottom: spacing.xs },
  reasonInput: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.md,
    padding: spacing.md,
    fontSize: 15,
    color: colors.textPrimary,
    minHeight: 90,
    textAlignVertical: 'top',
  },
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
