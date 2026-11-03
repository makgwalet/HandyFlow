import React from 'react';
import { View, Text, TextInput, Pressable, KeyboardAvoidingView, Platform, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useAuthStore } from '../../store/authStore';
import {
  useReceiveFlowStore,
  RECEIVE_VARIANCE_WARNING_LITRES,
  RECEIVE_VARIANCE_CRITICAL_LITRES,
} from '../../store/receiveFlowStore';
import { varianceSeverity } from '../../store/dipFlowStore';
import { useTransactionQueueStore } from '../../store/transactionQueueStore';
import { useVarianceStore } from '../../store/varianceStore';
import { MOCK_TANKS } from '../../data/mockData';
import { MOCK_SUPPLIERS } from '../../data/mockSuppliers';

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

export function ReceiveVarianceReasonScreen({ navigation }: any) {
  const user = useAuthStore((s) => s.user);
  const flow = useReceiveFlowStore();
  const enqueue = useTransactionQueueStore((s) => s.enqueue);
  const createVariance = useVarianceStore((s) => s.create);

  const tank = MOCK_TANKS.find((t) => t.id === flow.tankId);
  const supplier = MOCK_SUPPLIERS.find((s) => s.id === flow.supplierId);
  if (!tank || !user) return null;

  const preDip = parseFloat(flow.preDipLitres);
  const postDip = parseFloat(flow.postDipLitres);
  const actualReceived = postDip - preDip;
  const variance = actualReceived - flow.waybillQuantityLitres;
  const severity = varianceSeverity(variance, RECEIVE_VARIANCE_WARNING_LITRES, RECEIVE_VARIANCE_CRITICAL_LITRES);

  const hasPhoto = !!flow.photoUri;
  const hasGps = flow.gps.status === 'captured';
  const hasReason = flow.reason.trim().length > 0;
  const canSubmit = hasPhoto && hasGps && hasReason;

  const handleSubmit = () => {
    if (!canSubmit) return;

    const varianceRecord = createVariance({
      tankId: tank.id,
      bookLevel: flow.waybillQuantityLitres,
      physicalLevel: actualReceived,
      varianceLitres: variance,
      reason: flow.reason.trim(),
      photoUri: flow.photoUri,
    });

    const item = enqueue('FUEL_RECEIPT', user.id, {
      tankId: tank.id,
      supplierId: supplier?.id,
      waybillNumber: flow.waybillNumber,
      tankerRegistration: flow.tankerRegistration,
      waybillQuantityLitres: flow.waybillQuantityLitres,
      preDipLitres: preDip,
      postDipLitres: postDip,
      actualReceivedLitres: actualReceived,
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
        <Text style={styles.subtitle}>{Math.abs(variance).toLocaleString()} L difference from waybill</Text>
      </View>

      <View style={styles.body}>
        <Text style={styles.sectionLabel}>Required</Text>
        <ChecklistRow label="Waybill photo" checked={hasPhoto} />
        <ChecklistRow label="Received by identity" checked={!!user} />
        <ChecklistRow label="GPS" checked={hasGps} />
        <ChecklistRow label="Reason" checked={hasReason} />

        <Text style={[styles.sectionLabel, { marginTop: spacing.lg }]}>Optional</Text>
        <ChecklistRow
          label={flow.additionalEvidenceUri ? 'Additional evidence added' : 'Additional evidence'}
          checked={!!flow.additionalEvidenceUri}
          action={
            <Pressable
              onPress={() => navigation.navigate('ReceiveCameraCapture', { target: 'evidence' })}
            >
              <Text style={styles.addLink}>{flow.additionalEvidenceUri ? 'Replace' : 'Add'}</Text>
            </Pressable>
          }
        />

        <Text style={styles.reasonLabel}>Reason</Text>
        <TextInput
          value={flow.reason}
          onChangeText={flow.setReason}
          placeholder="What happened? e.g. tanker compartment not fully drained, meter calibration..."
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
