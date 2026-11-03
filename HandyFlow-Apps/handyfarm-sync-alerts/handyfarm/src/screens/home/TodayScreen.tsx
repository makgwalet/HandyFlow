import React from 'react';
import { View, Text, ScrollView, StyleSheet, Pressable, Alert } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useAuthStore } from '../../store/authStore';
import { useSyncStore } from '../../store/syncStore';
import { useQueueCounts } from '../../hooks/useQueueCounts';
import { StatusPill } from '../../components/StatusPill';
import { QuickActionButton } from '../../components/QuickActionButton';
import { SectionLabel } from '../../components/SectionLabel';
import { Card } from '../../components/Card';
import { TankRow } from '../../components/TankRow';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { MOCK_TANKS, MOCK_TODAY, MOCK_VEHICLE } from '../../data/mockData';
import type { Role } from '../../types/domain';

type QuickAction = {
  key: string;
  label: string;
  icon: keyof typeof Ionicons.glyphMap;
};

const QUICK_ACTIONS: Record<Role, QuickAction[]> = {
  OPERATOR: [
    { key: 'dispense', label: 'Dispense Fuel', icon: 'water-outline' },
    { key: 'dip', label: 'Record Dip', icon: 'speedometer-outline' },
    { key: 'receive', label: 'Receive Fuel', icon: 'cube-outline' },
    { key: 'deliveries', label: 'My Deliveries', icon: 'car-outline' },
    { key: 'shift', label: 'My Shift', icon: 'time-outline' },
  ],
  DRIVER: [
    { key: 'deliveries', label: 'My Deliveries', icon: 'car-outline' },
    { key: 'navigate', label: 'Navigate', icon: 'navigate-outline' },
    { key: 'problem', label: 'Report Problem', icon: 'alert-circle-outline' },
    { key: 'vehicle', label: 'My Vehicle', icon: 'construct-outline' },
  ],
  FUEL_MANAGER: [
    { key: 'tanks', label: 'View Tanks', icon: 'water-outline' },
    { key: 'approvals', label: 'Approvals', icon: 'checkmark-done-outline' },
    { key: 'reports', label: 'Reports', icon: 'stats-chart-outline' },
    { key: 'suppliers', label: 'Suppliers', icon: 'people-outline' },
  ],
};

function greeting() {
  const h = new Date().getHours();
  if (h < 12) return 'Good morning';
  if (h < 18) return 'Good afternoon';
  return 'Good evening';
}

export function TodayScreen({ navigation }: any) {
  const user = useAuthStore((s) => s.user);
  const sync = useSyncStore();
  const queueCounts = useQueueCounts();

  if (!user) return null;

  const actions = QUICK_ACTIONS[user.role];
  const firstName = user.name.split(' ')[0];

  const handleAction = (key: string, label: string) => {
    if (key === 'dispense') {
      navigation.navigate('Dispense', { screen: 'SelectTank' });
      return;
    }
    if (key === 'dip') {
      navigation.navigate('Dip', { screen: 'DipSelectTank' });
      return;
    }
    if (key === 'deliveries') {
      navigation.navigate('Deliveries');
      return;
    }
    if (key === 'receive') {
      navigation.navigate('Receive', { screen: 'ReceiveSelectTank' });
      return;
    }
    if (key === 'shift') {
      navigation.navigate('Shift', { screen: 'ShiftDashboard' });
      return;
    }
    Alert.alert(label, 'Coming in a later phase.');
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.header}>
        <View style={{ flex: 1 }}>
          <Text style={styles.greeting}>
            {greeting()}, {firstName}
          </Text>
          <Text style={styles.site}>{user.site}</Text>
        </View>
        <StatusPill
          status={sync.status}
          lastSyncedAt={sync.lastSyncedAt}
          pendingCount={queueCounts.pending}
        />
      </View>

      {user.role === 'FUEL_MANAGER' ? (
        <>
          <SectionLabel>Overview</SectionLabel>
          <View style={styles.metricRow}>
            <Card style={styles.metricCard}>
              <Text style={styles.metricValue}>{MOCK_TANKS.length}</Text>
              <Text style={styles.metricLabel}>Tanks</Text>
            </Card>
            <Card style={styles.metricCard}>
              <Text style={[styles.metricValue, { color: colors.warning }]}>
                {MOCK_TODAY.variances}
              </Text>
              <Text style={styles.metricLabel}>Variances</Text>
            </Card>
            <Card style={styles.metricCard}>
              <Text style={[styles.metricValue, { color: colors.info }]}>
                {MOCK_TODAY.pendingApprovals}
              </Text>
              <Text style={styles.metricLabel}>Approvals</Text>
            </Card>
          </View>
        </>
      ) : (
        <>
          <SectionLabel>Today</SectionLabel>
          <Card style={styles.todayCard}>
            <View style={styles.todayRow}>
              <Text style={styles.todayValue}>{MOCK_TODAY.deliveries}</Text>
              <Text style={styles.todayLabel}>Deliveries</Text>
            </View>
            <View style={styles.divider} />
            <View style={styles.todayRow}>
              <Text style={styles.todayValue}>{MOCK_TODAY.fuelTasks}</Text>
              <Text style={styles.todayLabel}>Fuel Task{MOCK_TODAY.fuelTasks === 1 ? '' : 's'}</Text>
            </View>
          </Card>
        </>
      )}

      <SectionLabel>Quick Actions</SectionLabel>
      <View style={styles.actionsGrid}>
        {actions.map((a) => (
          <QuickActionButton
            key={a.key}
            label={a.label}
            icon={a.icon}
            onPress={() => handleAction(a.key, a.label)}
          />
        ))}
      </View>

      {(user.role === 'OPERATOR' || user.role === 'FUEL_MANAGER') && (
        <>
          <SectionLabel>Tank Status</SectionLabel>
          <Card padded={false} style={styles.tanksCard}>
            {MOCK_TANKS.map((t, i) => (
              <View key={t.id}>
                <View style={styles.tankPad}>
                  <TankRow tank={t} />
                </View>
                {i < MOCK_TANKS.length - 1 && <View style={styles.rowDivider} />}
              </View>
            ))}
          </Card>
        </>
      )}

      {user.role === 'DRIVER' && (
        <>
          <SectionLabel>Assigned Vehicle</SectionLabel>
          <Card style={styles.vehicleCard}>
            <View style={styles.vehicleIcon}>
              <Ionicons name="car-sport-outline" size={22} color={colors.primary} />
            </View>
            <View>
              <Text style={styles.vehicleLabel}>{MOCK_VEHICLE.label}</Text>
              <Text style={styles.vehicleFuel}>{MOCK_VEHICLE.fuelType}</Text>
            </View>
          </Card>
        </>
      )}

      <SectionLabel>Sync</SectionLabel>
      <Card style={styles.syncCard}>
        <View style={{ flex: 1 }}>
          <Text style={styles.syncTitle}>
            {queueCounts.pending > 0 ? `${queueCounts.pending} items waiting` : 'All caught up'}
          </Text>
          {queueCounts.failed > 0 && (
            <Text style={styles.syncFailed}>{queueCounts.failed} needs attention</Text>
          )}
        </View>
        <Pressable onPress={() => navigation.navigate('SyncCentre')}>
          <Text style={styles.syncLink}>View sync status</Text>
        </Pressable>
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg, paddingBottom: spacing.xxxl, gap: spacing.md },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: spacing.sm,
  },
  greeting: { ...typography.h2 },
  site: { ...typography.caption },

  metricRow: { flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.sm },
  metricCard: { flex: 1, alignItems: 'center', paddingVertical: spacing.lg },
  metricValue: { fontSize: 24, fontWeight: '700', color: colors.textPrimary },
  metricLabel: { ...typography.caption, marginTop: 2 },

  todayCard: { flexDirection: 'row', marginBottom: spacing.sm },
  todayRow: { flex: 1, alignItems: 'center' },
  todayValue: { fontSize: 28, fontWeight: '700', color: colors.textPrimary },
  todayLabel: { ...typography.caption, marginTop: 2 },
  divider: { width: 1, backgroundColor: colors.border, marginHorizontal: spacing.md },

  actionsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    marginBottom: spacing.sm,
  },

  tanksCard: { marginBottom: spacing.sm },
  tankPad: { paddingHorizontal: spacing.lg },
  rowDivider: { height: 1, backgroundColor: colors.border, marginHorizontal: spacing.lg },

  vehicleCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    marginBottom: spacing.sm,
  },
  vehicleIcon: {
    width: 44,
    height: 44,
    borderRadius: radii.md,
    backgroundColor: colors.infoBg,
    alignItems: 'center',
    justifyContent: 'center',
  },
  vehicleLabel: { ...typography.bodyStrong },
  vehicleFuel: { ...typography.caption },

  syncCard: { flexDirection: 'row', alignItems: 'center' },
  syncTitle: { ...typography.bodyStrong },
  syncFailed: { ...typography.caption, color: colors.danger, marginTop: 2 },
  syncLink: { ...typography.caption, color: colors.primaryDark, fontWeight: '700' },
});
