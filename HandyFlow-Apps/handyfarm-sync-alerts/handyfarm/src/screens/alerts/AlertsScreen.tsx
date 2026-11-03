import React from 'react';
import { View, Text, Pressable, ScrollView, Alert, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Card } from '../../components/Card';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useVarianceStore } from '../../store/varianceStore';
import { useDeliveryStore } from '../../store/deliveryStore';
import { useQueueCounts } from '../../hooks/useQueueCounts';
import { MOCK_TANKS } from '../../data/mockData';

function Section({
  title,
  icon,
  color,
  count,
  children,
}: {
  title: string;
  icon: keyof typeof Ionicons.glyphMap;
  color: string;
  count: number;
  children: React.ReactNode;
}) {
  return (
    <View style={styles.section}>
      <View style={styles.sectionHeader}>
        <Ionicons name={icon} size={16} color={color} />
        <Text style={styles.sectionTitle}>{title}</Text>
        {count > 0 && (
          <View style={[styles.countBadge, { backgroundColor: color }]}>
            <Text style={styles.countBadgeText}>{count}</Text>
          </View>
        )}
      </View>
      {children}
    </View>
  );
}

function AlertRow({
  title,
  subtitle,
  onPress,
}: {
  title: string;
  subtitle: string;
  onPress?: () => void;
}) {
  return (
    <Pressable onPress={onPress} disabled={!onPress}>
      <Card style={styles.alertCard}>
        <View style={{ flex: 1 }}>
          <Text style={styles.alertTitle}>{title}</Text>
          <Text style={styles.alertSubtitle}>{subtitle}</Text>
        </View>
        {onPress && <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />}
      </Card>
    </Pressable>
  );
}

function EmptyRow({ label }: { label: string }) {
  return (
    <View style={styles.emptyRow}>
      <Ionicons name="checkmark-circle-outline" size={16} color={colors.textMuted} />
      <Text style={styles.emptyText}>{label}</Text>
    </View>
  );
}

export function AlertsScreen({ navigation }: any) {
  const varianceRecords = useVarianceStore((s) => s.records.filter((r) => r.status !== 'RESOLVED'));
  const deliveries = useDeliveryStore((s) => s.deliveries.filter((d) => d.status === 'PROBLEM'));
  const lowStockTanks = MOCK_TANKS.filter((t) => t.health !== 'healthy');
  const queueCounts = useQueueCounts();

  const totalAlerts = varianceRecords.length + deliveries.length + lowStockTanks.length + queueCounts.failed;

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.header}>
        <Text style={styles.title}>Alerts</Text>
        {totalAlerts > 0 && (
          <View style={styles.headerBadge}>
            <Text style={styles.headerBadgeText}>{totalAlerts}</Text>
          </View>
        )}
      </View>

      <Section title="Low Stock" icon="water" color={colors.warning} count={lowStockTanks.length}>
        {lowStockTanks.length === 0 ? (
          <EmptyRow label="All tanks at healthy levels" />
        ) : (
          lowStockTanks.map((t) => (
            <AlertRow
              key={t.id}
              title={t.name}
              subtitle={`${t.currentLitres.toLocaleString()} / ${t.capacityLitres.toLocaleString()} L · ${t.health === 'critical' ? 'Critical' : 'Low'}`}
            />
          ))
        )}
      </Section>

      <Section title="Variance" icon="warning" color={colors.danger} count={varianceRecords.length}>
        {varianceRecords.length === 0 ? (
          <EmptyRow label="No open variances" />
        ) : (
          varianceRecords.map((v) => {
            const tank = MOCK_TANKS.find((t) => t.id === v.tankId);
            return (
              <AlertRow
                key={v.id}
                title={`${Math.abs(v.varianceLitres).toLocaleString()} L variance`}
                subtitle={`${tank?.name ?? v.tankId} · ${v.status}`}
                onPress={() =>
                  navigation.navigate('Dip', { screen: 'VarianceDetail', params: { varianceId: v.id } })
                }
              />
            );
          })
        )}
      </Section>

      <Section title="Failed Sync" icon="cloud-offline" color={colors.danger} count={queueCounts.failed}>
        {queueCounts.failed === 0 ? (
          <EmptyRow label="Nothing needs attention" />
        ) : (
          <AlertRow
            title={`${queueCounts.failed} transaction${queueCounts.failed === 1 ? '' : 's'} need attention`}
            subtitle="Open Sync Centre from More to review"
          />
        )}
      </Section>

      <Section title="Delivery Issues" icon="car" color={colors.danger} count={deliveries.length}>
        {deliveries.length === 0 ? (
          <EmptyRow label="No reported delivery problems" />
        ) : (
          deliveries.map((d) => (
            <AlertRow
              key={d.id}
              title={`#${d.reference}`}
              subtitle={d.problem?.reason ?? 'Problem reported'}
              onPress={() =>
                navigation.navigate('Delivery', { screen: 'DeliveryDetail', params: { deliveryId: d.id } })
              }
            />
          ))
        )}
      </Section>

      <Section title="Approvals" icon="checkmark-done" color={colors.info} count={0}>
        <Pressable onPress={() => Alert.alert('Approvals', 'Coming in a later phase.')}>
          <EmptyRow label="No approval workflow yet — tap for details" />
        </Pressable>
      </Section>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg, paddingBottom: spacing.xxxl },
  header: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, marginBottom: spacing.lg },
  title: { ...typography.h2 },
  headerBadge: {
    backgroundColor: colors.danger,
    borderRadius: radii.pill,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  headerBadgeText: { color: colors.textOnDark, fontWeight: '700', fontSize: 12 },
  section: { marginBottom: spacing.lg },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, marginBottom: spacing.sm },
  sectionTitle: { ...typography.label, textTransform: 'uppercase' },
  countBadge: { borderRadius: radii.pill, paddingHorizontal: 6, paddingVertical: 1, marginLeft: 2 },
  countBadgeText: { color: colors.textOnDark, fontSize: 11, fontWeight: '700' },
  alertCard: { flexDirection: 'row', alignItems: 'center', marginBottom: spacing.sm },
  alertTitle: { ...typography.bodyStrong, fontSize: 14 },
  alertSubtitle: { ...typography.caption },
  emptyRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, paddingVertical: spacing.xs },
  emptyText: { ...typography.caption },
});
