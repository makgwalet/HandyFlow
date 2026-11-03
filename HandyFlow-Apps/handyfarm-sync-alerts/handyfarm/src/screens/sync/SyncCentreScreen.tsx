import React from 'react';
import { View, Text, Pressable, FlatList, Alert, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Card } from '../../components/Card';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useSyncStore } from '../../store/syncStore';
import { useTransactionQueueStore, type QueueItem } from '../../store/transactionQueueStore';
import { useQueueCounts } from '../../hooks/useQueueCounts';
import { OPERATION_LABEL, OPERATION_ICON, queueItemSummary, queueItemHasEvidence } from '../../utils/queueLabels';

const STATUS_CONFIG: Record<QueueItem['status'], { label: string; color: string; icon: keyof typeof Ionicons.glyphMap }> = {
  PENDING: { label: 'Pending', color: colors.textSecondary, icon: 'time-outline' },
  UPLOADING: { label: 'Uploading', color: colors.info, icon: 'cloud-upload-outline' },
  CONFIRMED: { label: 'Confirmed', color: colors.success, icon: 'checkmark-circle' },
  CONFLICT: { label: 'Needs attention', color: colors.danger, icon: 'warning' },
};

export function SyncCentreScreen({ navigation }: any) {
  const status = useSyncStore((s) => s.status);
  const lastSyncedAt = useSyncStore((s) => s.lastSyncedAt);
  const simulateSync = useSyncStore((s) => s.simulateSync);
  const [syncing, setSyncing] = React.useState(false);
  const items = useTransactionQueueStore((s) => s.items);
  const counts = useQueueCounts();

  const photosPending = items.filter((i) => i.status === 'PENDING' && queueItemHasEvidence(i)).length;

  const handleSync = async () => {
    setSyncing(true);
    await simulateSync();
    setSyncing(false);
  };

  const handleViewProblems = () => {
    if (counts.failed === 0) {
      Alert.alert('No problems', 'Every synced transaction was accepted — nothing needs attention.');
    }
    // Failed items are already visible in the list below when present.
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12}>
          <Ionicons name="arrow-back" size={22} color={colors.textPrimary} />
        </Pressable>
        <Text style={styles.title}>Sync Centre</Text>
        <View style={{ width: 22 }} />
      </View>

      <View style={styles.statusRow}>
        <View style={[styles.statusDot, { backgroundColor: status === 'online' ? colors.success : colors.textMuted }]} />
        <Text style={styles.statusText}>{status === 'online' ? 'Online' : status === 'syncing' ? 'Syncing' : 'Offline'}</Text>
        {lastSyncedAt && (
          <Text style={styles.lastSync}>
            · Last sync{' '}
            {new Date(lastSyncedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </Text>
        )}
      </View>

      <View style={styles.metricsRow}>
        <Metric label="Completed" value={counts.confirmed} icon="checkmark-circle" color={colors.success} />
        <Metric label="Pending" value={counts.pending} icon="sync-outline" color={colors.info} />
        <Metric label="Photos" value={photosPending} icon="camera-outline" color={colors.textSecondary} />
        <Metric label="Failed" value={counts.failed} icon="warning" color={colors.danger} />
      </View>

      <View style={styles.actionsRow}>
        <Pressable onPress={handleSync} disabled={syncing} style={[styles.syncButton, syncing && styles.disabled]}>
          <Text style={styles.syncButtonText}>{syncing ? 'Syncing...' : 'Sync Now'}</Text>
        </Pressable>
        <Pressable onPress={handleViewProblems} style={styles.problemsButton}>
          <Text style={styles.problemsButtonText}>View Problems</Text>
        </Pressable>
      </View>

      <Text style={styles.listLabel}>ALL TRANSACTIONS</Text>
      <FlatList
        data={items}
        keyExtractor={(i) => i.clientRequestId}
        contentContainerStyle={styles.list}
        ListEmptyComponent={<Text style={styles.empty}>No transactions yet this session.</Text>}
        renderItem={({ item }) => {
          const sc = STATUS_CONFIG[item.status];
          return (
            <Card style={styles.itemCard}>
              <View style={styles.itemIconWrap}>
                <Ionicons name={OPERATION_ICON[item.operation]} size={18} color={colors.primaryDark} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.itemTitle}>{OPERATION_LABEL[item.operation]}</Text>
                <Text style={styles.itemMeta}>{queueItemSummary(item)}</Text>
              </View>
              <View style={{ alignItems: 'flex-end' }}>
                <View style={styles.itemStatusRow}>
                  <Ionicons name={sc.icon} size={13} color={sc.color} />
                  <Text style={[styles.itemStatusText, { color: sc.color }]}>{sc.label}</Text>
                </View>
                <Text style={styles.itemTime}>
                  {new Date(item.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </Text>
              </View>
            </Card>
          );
        }}
      />
    </View>
  );
}

function Metric({
  label,
  value,
  icon,
  color,
}: {
  label: string;
  value: number;
  icon: keyof typeof Ionicons.glyphMap;
  color: string;
}) {
  return (
    <View style={styles.metric}>
      <Ionicons name={icon} size={18} color={color} />
      <Text style={styles.metricValue}>{value}</Text>
      <Text style={styles.metricLabel}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: spacing.lg,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  title: { ...typography.h3 },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.lg,
  },
  statusDot: { width: 8, height: 8, borderRadius: radii.pill },
  statusText: { ...typography.bodyStrong },
  lastSync: { ...typography.caption },
  metricsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    padding: spacing.lg,
  },
  metric: { alignItems: 'center', gap: 2, flex: 1 },
  metricValue: { fontSize: 20, fontWeight: '700', color: colors.textPrimary },
  metricLabel: { ...typography.caption, fontSize: 11 },
  actionsRow: { flexDirection: 'row', gap: spacing.sm, paddingHorizontal: spacing.lg, marginBottom: spacing.lg },
  syncButton: {
    flex: 1,
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  disabled: { opacity: 0.6 },
  syncButtonText: { color: colors.textOnDark, fontWeight: '700' },
  problemsButton: {
    flex: 1,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  problemsButtonText: { color: colors.textSecondary, fontWeight: '700' },
  listLabel: { ...typography.label, textTransform: 'uppercase', paddingHorizontal: spacing.lg, marginBottom: spacing.sm },
  list: { paddingHorizontal: spacing.lg, paddingBottom: spacing.xxxl, gap: spacing.sm },
  empty: { ...typography.caption, textAlign: 'center', marginTop: spacing.xl },
  itemCard: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, marginBottom: spacing.sm },
  itemIconWrap: {
    width: 36,
    height: 36,
    borderRadius: radii.md,
    backgroundColor: colors.infoBg,
    alignItems: 'center',
    justifyContent: 'center',
  },
  itemTitle: { ...typography.bodyStrong, fontSize: 14 },
  itemMeta: { ...typography.caption },
  itemStatusRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  itemStatusText: { fontSize: 11, fontWeight: '700' },
  itemTime: { ...typography.caption, fontSize: 11, marginTop: 2 },
});
