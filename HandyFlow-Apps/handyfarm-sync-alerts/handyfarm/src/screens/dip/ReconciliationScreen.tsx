import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { WizardHeader } from '../../components/WizardHeader';
import { Card } from '../../components/Card';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useAuthStore } from '../../store/authStore';
import { useDipFlowStore, varianceSeverity } from '../../store/dipFlowStore';
import { useTransactionQueueStore } from '../../store/transactionQueueStore';
import { MOCK_TANKS } from '../../data/mockData';

export function ReconciliationScreen({ navigation }: any) {
  const user = useAuthStore((s) => s.user);
  const flow = useDipFlowStore();
  const enqueue = useTransactionQueueStore((s) => s.enqueue);

  const tank = MOCK_TANKS.find((t) => t.id === flow.tankId);
  if (!tank || !user) return null;

  const bookLevel = tank.currentLitres;
  const variance = flow.physicalDipLitres - bookLevel;
  const severity = varianceSeverity(variance);
  const flagged = severity !== 'none';

  const submitClean = () => {
    const item = enqueue('TANK_DIP', user.id, {
      tankId: tank.id,
      bookLevel,
      physicalLevel: flow.physicalDipLitres,
      varianceLitres: variance,
      photoUri: flow.photoUri,
      location: flow.gps.coords,
      capturedAt: new Date().toISOString(),
    });
    navigation.replace('Success', { clientRequestId: item.clientRequestId, flagged: false });
  };

  return (
    <View style={styles.container}>
      <WizardHeader title="Reconciliation" step={3} totalSteps={3} />
      <View style={styles.body}>
        <Card style={styles.summaryCard}>
          <View style={styles.row}>
            <Text style={styles.rowLabel}>Book Level</Text>
            <Text style={styles.rowValue}>{bookLevel.toLocaleString()} L</Text>
          </View>
          <View style={styles.divider} />
          <View style={styles.row}>
            <Text style={styles.rowLabel}>Physical</Text>
            <Text style={styles.rowValue}>{flow.physicalDipLitres.toLocaleString()} L</Text>
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

        {flagged ? (
          <Text style={styles.notice}>
            Possible variance detected. Extra evidence is required before this reading can be
            submitted.
          </Text>
        ) : (
          <Text style={styles.noticeOk}>Reading is within normal range.</Text>
        )}
      </View>

      <View style={styles.footer}>
        <Pressable
          onPress={() => (flagged ? navigation.navigate('VarianceReason') : submitClean())}
          style={[styles.submit, flagged && styles.submitFlagged]}
        >
          <Text style={styles.submitText}>{flagged ? 'Continue' : 'Submit Reading'}</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  body: { flex: 1, padding: spacing.lg },
  summaryCard: { marginBottom: spacing.lg },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: spacing.sm },
  rowLabel: { ...typography.body, color: colors.textSecondary },
  rowValue: { ...typography.bodyStrong, fontSize: 16 },
  varianceValue: { fontSize: 18, fontWeight: '700', color: colors.textPrimary },
  divider: { height: 1, backgroundColor: colors.border },
  severityBadge: { flexDirection: 'row', alignItems: 'center', gap: 4, marginTop: 2 },
  severityText: { fontSize: 11, fontWeight: '800', letterSpacing: 0.4 },
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
