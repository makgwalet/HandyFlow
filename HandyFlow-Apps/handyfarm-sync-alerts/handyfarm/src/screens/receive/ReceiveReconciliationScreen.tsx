import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { WizardHeader } from '../../components/WizardHeader';
import { Card } from '../../components/Card';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useAuthStore } from '../../store/authStore';
import {
  useReceiveFlowStore,
  RECEIVE_VARIANCE_WARNING_LITRES,
  RECEIVE_VARIANCE_CRITICAL_LITRES,
} from '../../store/receiveFlowStore';
import { varianceSeverity } from '../../store/dipFlowStore';
import { useTransactionQueueStore } from '../../store/transactionQueueStore';
import { MOCK_TANKS } from '../../data/mockData';
import { MOCK_SUPPLIERS } from '../../data/mockSuppliers';

export function ReceiveReconciliationScreen({ navigation }: any) {
  const user = useAuthStore((s) => s.user);
  const flow = useReceiveFlowStore();
  const enqueue = useTransactionQueueStore((s) => s.enqueue);

  const tank = MOCK_TANKS.find((t) => t.id === flow.tankId);
  const supplier = MOCK_SUPPLIERS.find((s) => s.id === flow.supplierId);
  if (!tank || !user) return null;

  const preDip = parseFloat(flow.preDipLitres);
  const postDip = parseFloat(flow.postDipLitres);
  const actualReceived = postDip - preDip;
  const variance = actualReceived - flow.waybillQuantityLitres;
  const severity = varianceSeverity(variance, RECEIVE_VARIANCE_WARNING_LITRES, RECEIVE_VARIANCE_CRITICAL_LITRES);
  const flagged = severity !== 'none';

  const submitClean = () => {
    const item = enqueue('FUEL_RECEIPT', user.id, {
      tankId: tank.id,
      supplierId: supplier?.id,
      waybillNumber: flow.waybillNumber,
      tankerRegistration: flow.tankerRegistration,
      driverName: flow.driverName,
      waybillQuantityLitres: flow.waybillQuantityLitres,
      preDipLitres: preDip,
      postDipLitres: postDip,
      actualReceivedLitres: actualReceived,
      photoUri: flow.photoUri,
      location: flow.gps.coords,
      capturedAt: new Date().toISOString(),
    });
    navigation.replace('Success', { clientRequestId: item.clientRequestId, flagged: false });
  };

  return (
    <View style={styles.container}>
      <WizardHeader title="Reconciliation" step={5} totalSteps={5} />
      <View style={styles.body}>
        <Card style={styles.summaryCard}>
          <View style={styles.row}>
            <Text style={styles.rowLabel}>Waybill Quantity</Text>
            <Text style={styles.rowValue}>{flow.waybillQuantityLitres.toLocaleString()} L</Text>
          </View>
          <View style={styles.divider} />
          <View style={styles.row}>
            <Text style={styles.rowLabel}>Actual Received</Text>
            <Text style={styles.rowValue}>{actualReceived.toLocaleString()} L</Text>
          </View>
          <View style={styles.divider} />
          <View style={styles.row}>
            <Text style={styles.rowLabel}>Variance</Text>
            <View style={{ alignItems: 'flex-end' }}>
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
              {severity !== 'none' && (
                <View style={styles.severityBadge}>
                  <Ionicons
                    name="warning"
                    size={12}
                    color={severity === 'critical' ? colors.danger : colors.warning}
                  />
                  <Text
                    style={[
                      styles.severityText,
                      { color: severity === 'critical' ? colors.danger : colors.warning },
                    ]}
                  >
                    {severity === 'critical' ? 'CRITICAL' : 'REVIEW'}
                  </Text>
                </View>
              )}
            </View>
          </View>
        </Card>

        <Card style={styles.detailCard}>
          <Text style={styles.detailRow}>{supplier?.name}</Text>
          <Text style={styles.detailMeta}>
            Waybill {flow.waybillNumber} · {flow.tankerRegistration}
          </Text>
        </Card>

        {flagged ? (
          <Text style={styles.notice}>
            Received quantity doesn't match the waybill. Extra evidence is required before this
            receipt can be submitted.
          </Text>
        ) : (
          <Text style={styles.noticeOk}>Received quantity matches the waybill.</Text>
        )}
      </View>

      <View style={styles.footer}>
        <Pressable
          onPress={() => (flagged ? navigation.navigate('ReceiveVarianceReason') : submitClean())}
          style={[styles.submit, flagged && styles.submitFlagged]}
        >
          <Text style={styles.submitText}>{flagged ? 'Continue' : 'Submit Receipt'}</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  body: { flex: 1, padding: spacing.lg },
  summaryCard: { marginBottom: spacing.md },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: spacing.sm },
  rowLabel: { ...typography.body, color: colors.textSecondary },
  rowValue: { ...typography.bodyStrong, fontSize: 16 },
  varianceValue: { fontSize: 18, fontWeight: '700', color: colors.textPrimary },
  divider: { height: 1, backgroundColor: colors.border },
  severityBadge: { flexDirection: 'row', alignItems: 'center', gap: 4, marginTop: 2 },
  severityText: { fontSize: 11, fontWeight: '800', letterSpacing: 0.4 },
  detailCard: { marginBottom: spacing.lg },
  detailRow: { ...typography.bodyStrong },
  detailMeta: { ...typography.caption },
  notice: { ...typography.body, color: colors.warning, textAlign: 'center' },
  noticeOk: { ...typography.caption, textAlign: 'center' },
  footer: { padding: spacing.lg, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border },
  submit: {
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  submitFlagged: { backgroundColor: colors.warning },
  submitText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
});
