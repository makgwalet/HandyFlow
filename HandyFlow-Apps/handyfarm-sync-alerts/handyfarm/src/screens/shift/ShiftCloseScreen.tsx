import React, { useState } from 'react';
import { View, Text, TextInput, Pressable, KeyboardAvoidingView, Platform, ScrollView, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Card } from '../../components/Card';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useAuthStore } from '../../store/authStore';
import { useShiftStore } from '../../store/shiftStore';
import { useShiftTotals } from '../../hooks/useShiftTotals';
import { useTransactionQueueStore } from '../../store/transactionQueueStore';
import { useVarianceStore } from '../../store/varianceStore';
import { varianceSeverity } from '../../store/dipFlowStore';
import { MOCK_TANKS } from '../../data/mockData';

const SHIFT_VARIANCE_THRESHOLD_LITRES = 20;

export function ShiftCloseScreen({ navigation }: any) {
  const user = useAuthStore((s) => s.user);
  const activeShift = useShiftStore((s) => s.activeShift);
  const closeShift = useShiftStore((s) => s.closeShift);
  const enqueue = useTransactionQueueStore((s) => s.enqueue);
  const createVariance = useVarianceStore((s) => s.create);

  const [physical, setPhysical] = useState('');
  const [explaining, setExplaining] = useState(false);
  const [explanation, setExplanation] = useState('');

  if (!activeShift || !user) return null;

  const tank = MOCK_TANKS.find((t) => t.id === activeShift.tankId);
  const { received, dispensed } = useShiftTotals(activeShift.tankId, activeShift.startedAt);
  const expected = activeShift.openingLevel + received - dispensed;

  if (!tank) return null;

  const physicalNum = parseFloat(physical);
  const hasPhysical = !Number.isNaN(physicalNum) && physical.length > 0;
  const variance = hasPhysical ? physicalNum - expected : 0;
  const severity = hasPhysical
    ? varianceSeverity(variance, SHIFT_VARIANCE_THRESHOLD_LITRES, SHIFT_VARIANCE_THRESHOLD_LITRES * 3)
    : 'none';
  const flagged = severity !== 'none';

  const canClose = hasPhysical && (!flagged || explanation.trim().length > 0);

  const handleClose = () => {
    if (!canClose) return;

    const closed = closeShift(physicalNum, variance, flagged ? explanation.trim() : undefined);

    let varianceId: string | undefined;
    if (flagged) {
      const record = createVariance({
        tankId: tank.id,
        bookLevel: expected,
        physicalLevel: physicalNum,
        varianceLitres: variance,
        reason: explanation.trim(),
        photoUri: null,
      });
      varianceId = record.id;
    }

    const item = enqueue('SHIFT_CLOSE', user.id, {
      shiftId: closed.id,
      tankId: tank.id,
      operatorId: closed.operatorId,
      startedAt: closed.startedAt,
      closedAt: closed.closedAt,
      openingLevel: closed.openingLevel,
      receivedLitres: received,
      dispensedLitres: dispensed,
      expectedLitres: expected,
      physicalReading: physicalNum,
      varianceLitres: variance,
      explanation: closed.explanation,
      varianceId,
    });

    navigation.replace('ShiftSummary', {
      clientRequestId: item.clientRequestId,
      flagged,
      varianceId,
      received,
      dispensed,
      expected,
      physical: physicalNum,
      variance,
    });
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={styles.header}>
        <Text style={styles.title}>SHIFT CLOSE</Text>
      </View>

      <ScrollView style={styles.body} contentContainerStyle={{ paddingBottom: spacing.xl }}>
        <Text style={styles.operator}>{activeShift.operatorName}</Text>
        <Text style={styles.tank}>{tank.name}</Text>

        <Card style={styles.card}>
          <Row label="Opening" value={`${activeShift.openingLevel.toLocaleString()} L`} />
          <Divider />
          <Row label="Received" value={`${received.toLocaleString()} L`} />
          <Divider />
          <Row label="Dispensed" value={`${dispensed.toLocaleString()} L`} />
          <Divider />
          <Row label="Expected" value={`${expected.toLocaleString()} L`} strong />
        </Card>

        <View style={styles.field}>
          <Text style={styles.label}>Physical (closing dip)</Text>
          <TextInput
            value={physical}
            onChangeText={setPhysical}
            keyboardType="decimal-pad"
            placeholder="Enter measured tank level"
            placeholderTextColor={colors.textMuted}
            style={styles.input}
          />
        </View>

        {hasPhysical && (
          <View style={styles.varianceRow}>
            <Text style={styles.varianceLabel}>Variance</Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
              <Text
                style={[
                  styles.varianceValue,
                  severity === 'critical' && { color: colors.danger },
                  severity === 'warning' && { color: colors.warning },
                ]}
              >
                {variance > 0 ? '+' : ''}
                {variance.toLocaleString()} L
              </Text>
              {flagged && <Ionicons name="warning" size={16} color={severity === 'critical' ? colors.danger : colors.warning} />}
            </View>
          </View>
        )}

        {flagged && (
          <View style={styles.explainBlock}>
            {!explaining ? (
              <Pressable onPress={() => setExplaining(true)} style={styles.explainButton}>
                <Text style={styles.explainButtonText}>Explain Variance</Text>
              </Pressable>
            ) : (
              <TextInput
                value={explanation}
                onChangeText={setExplanation}
                placeholder="What might explain this variance?"
                placeholderTextColor={colors.textMuted}
                multiline
                autoFocus
                style={styles.explainInput}
              />
            )}
          </View>
        )}
      </ScrollView>

      <View style={styles.footer}>
        <Pressable
          disabled={!canClose}
          onPress={handleClose}
          style={[styles.closeButton, !canClose && styles.disabled]}
        >
          <Text style={styles.closeButtonText}>Close Shift</Text>
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={[styles.rowValue, strong && { fontSize: 18 }]}>{value}</Text>
    </View>
  );
}

function Divider() {
  return <View style={styles.divider} />;
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  header: {
    padding: spacing.lg,
    paddingTop: spacing.xl,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  title: { ...typography.label, textTransform: 'uppercase' },
  body: { flex: 1, padding: spacing.lg },
  operator: { ...typography.h2 },
  tank: { ...typography.body, color: colors.textSecondary, marginBottom: spacing.lg },
  card: { marginBottom: spacing.lg },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: spacing.sm },
  rowLabel: { ...typography.body, color: colors.textSecondary },
  rowValue: { ...typography.bodyStrong, fontSize: 16 },
  divider: { height: 1, backgroundColor: colors.border },
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
  varianceRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: spacing.sm,
    marginBottom: spacing.md,
  },
  varianceLabel: { ...typography.body, color: colors.textSecondary },
  varianceValue: { fontSize: 18, fontWeight: '700', color: colors.textPrimary },
  explainBlock: { marginBottom: spacing.md },
  explainButton: {
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.warning,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  explainButtonText: { color: colors.warning, fontWeight: '700' },
  explainInput: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.md,
    padding: spacing.md,
    minHeight: 80,
    textAlignVertical: 'top',
    color: colors.textPrimary,
  },
  footer: { padding: spacing.lg, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border },
  closeButton: {
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  disabled: { opacity: 0.4 },
  closeButtonText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
});
