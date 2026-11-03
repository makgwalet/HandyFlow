import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { colors, radii, spacing } from '../theme/tokens';
import type { ConnectivityStatus } from '../types/domain';

interface StatusPillProps {
  status: ConnectivityStatus;
  lastSyncedAt?: string | null;
  pendingCount?: number;
  dark?: boolean;
}

const CONFIG: Record<ConnectivityStatus, { label: string; color: string }> = {
  online: { label: 'Online', color: colors.success },
  offline: { label: 'Offline', color: colors.textMuted },
  syncing: { label: 'Syncing', color: colors.info },
  error: { label: 'Sync issue', color: colors.danger },
};

function formatTime(iso?: string | null) {
  if (!iso) return null;
  const d = new Date(iso);
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

export function StatusPill({ status, lastSyncedAt, pendingCount, dark }: StatusPillProps) {
  const { label, color } = CONFIG[status];
  const time = formatTime(lastSyncedAt);

  const subtitle =
    status === 'offline' && pendingCount
      ? `${pendingCount} waiting`
      : status === 'error'
        ? 'Needs attention'
        : time
          ? `Last synced ${time}`
          : undefined;

  return (
    <View style={styles.row}>
      <View style={[styles.dot, { backgroundColor: color }]} />
      <View>
        <Text style={[styles.label, dark && styles.labelDark]}>{label}</Text>
        {subtitle ? (
          <Text style={[styles.subtitle, dark && styles.subtitleDark]}>{subtitle}</Text>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.xs,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: radii.pill,
    marginTop: 5,
  },
  label: {
    fontSize: 13,
    fontWeight: '700',
    color: colors.textPrimary,
  },
  labelDark: {
    color: colors.textOnDark,
  },
  subtitle: {
    fontSize: 11,
    color: colors.textSecondary,
  },
  subtitleDark: {
    color: colors.textOnDarkMuted,
  },
});
