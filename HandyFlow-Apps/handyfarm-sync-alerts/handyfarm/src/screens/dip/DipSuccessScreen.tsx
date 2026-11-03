import React, { useEffect } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useDipFlowStore } from '../../store/dipFlowStore';
import { useSyncStore } from '../../store/syncStore';
import { MOCK_TANKS } from '../../data/mockData';

export function DipSuccessScreen({ route, navigation }: any) {
  const { clientRequestId, flagged, varianceId } = route.params ?? {};
  const flow = useDipFlowStore();
  const syncStatus = useSyncStore((s) => s.status);
  const reset = useDipFlowStore((s) => s.reset);

  const tank = MOCK_TANKS.find((t) => t.id === flow.tankId);
  const offline = syncStatus === 'offline';

  useEffect(() => {
    return () => reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!tank) return null;

  const variance = flow.physicalDipLitres - tank.currentLitres;

  const handleDone = () => navigation.getParent()?.goBack();
  const handleViewVariance = () => navigation.replace('VarianceDetail', { varianceId });

  return (
    <View style={styles.container}>
      <View style={[styles.iconWrap, (offline || flagged) && styles.iconWrapAlt]}>
        <Ionicons
          name={offline ? 'cloud-offline-outline' : flagged ? 'warning' : 'checkmark'}
          size={36}
          color={colors.textOnDark}
        />
      </View>
      <Text style={styles.title}>
        {offline ? 'SAVED OFFLINE' : flagged ? 'VARIANCE FLAGGED' : 'DIP RECORDED'}
      </Text>

      <View style={styles.detailsCard}>
        <DetailRow label="Tank" value={tank.name} />
        <DetailRow label="Book Level" value={`${tank.currentLitres.toLocaleString()} L`} />
        <DetailRow label="Physical" value={`${flow.physicalDipLitres.toLocaleString()} L`} />
        <DetailRow
          label="Variance"
          value={`${variance > 0 ? '+' : ''}${variance.toLocaleString()} L`}
          valueColor={flagged ? colors.danger : colors.textPrimary}
        />
        <DetailRow label="Reading" value={`DIP-${(clientRequestId ?? '').slice(0, 8).toUpperCase()}`} />
      </View>

      <View style={styles.statusRow}>
        <View style={[styles.statusDot, { backgroundColor: offline ? colors.textMuted : colors.success }]} />
        <Text style={styles.statusText}>{offline ? 'Pending sync' : 'Synced'}</Text>
      </View>

      {flagged && (
        <Pressable onPress={handleViewVariance} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>View Variance</Text>
        </Pressable>
      )}
      <Pressable onPress={handleDone} style={styles.doneButton}>
        <Text style={styles.doneButtonText}>Done</Text>
      </Pressable>
    </View>
  );
}

function DetailRow({ label, value, valueColor }: { label: string; value: string; valueColor?: string }) {
  return (
    <View style={styles.detailRow}>
      <Text style={styles.detailLabel}>{label}</Text>
      <Text style={[styles.detailValue, valueColor ? { color: valueColor } : null]}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg, alignItems: 'center', padding: spacing.xl, paddingTop: spacing.xxxl },
  iconWrap: {
    width: 64,
    height: 64,
    borderRadius: radii.pill,
    backgroundColor: colors.success,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.md,
  },
  iconWrapAlt: { backgroundColor: colors.warning },
  title: { ...typography.h3, letterSpacing: 0.5, marginBottom: spacing.lg, textAlign: 'center' },
  detailsCard: {
    width: '100%',
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    gap: spacing.sm,
    marginBottom: spacing.lg,
  },
  detailRow: { flexDirection: 'row', justifyContent: 'space-between' },
  detailLabel: { ...typography.caption },
  detailValue: { ...typography.bodyStrong, fontSize: 14 },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, marginBottom: spacing.xxl },
  statusDot: { width: 8, height: 8, borderRadius: radii.pill },
  statusText: { ...typography.caption, fontWeight: '700' },
  secondaryButton: {
    width: '100%',
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.warning,
    paddingVertical: spacing.md,
    alignItems: 'center',
    marginBottom: spacing.sm,
  },
  secondaryButtonText: { color: colors.warning, fontWeight: '700', fontSize: 16 },
  doneButton: {
    width: '100%',
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  doneButtonText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
});
