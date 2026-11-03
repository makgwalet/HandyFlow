import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { colors, radii, spacing, typography } from '../theme/tokens';
import type { TankSummary } from '../types/domain';

const HEALTH_CONFIG: Record<TankSummary['health'], { color: string; label: string }> = {
  healthy: { color: colors.success, label: 'Healthy' },
  low: { color: colors.warning, label: 'Low' },
  critical: { color: colors.danger, label: 'Critical' },
};

export function TankRow({ tank }: { tank: TankSummary }) {
  const pct = Math.round((tank.currentLitres / tank.capacityLitres) * 100);
  const health = HEALTH_CONFIG[tank.health];

  return (
    <View style={styles.row}>
      <View style={styles.header}>
        <Text style={styles.name}>{tank.name}</Text>
        <View style={styles.badge}>
          <View style={[styles.dot, { backgroundColor: health.color }]} />
          <Text style={[styles.badgeText, { color: health.color }]}>{health.label}</Text>
        </View>
      </View>
      <View style={styles.barTrack}>
        <View
          style={[
            styles.barFill,
            { width: `${Math.min(pct, 100)}%`, backgroundColor: health.color },
          ]}
        />
      </View>
      <View style={styles.footer}>
        <Text style={styles.meta}>
          {tank.currentLitres.toLocaleString()} / {tank.capacityLitres.toLocaleString()} L ·{' '}
          {tank.fuelType}
        </Text>
        <Text style={styles.pct}>{pct}%</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    paddingVertical: spacing.md,
    gap: spacing.xs,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  name: { ...typography.bodyStrong },
  badge: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  dot: { width: 6, height: 6, borderRadius: radii.pill },
  badgeText: { fontSize: 12, fontWeight: '700' },
  barTrack: {
    height: 8,
    borderRadius: radii.pill,
    backgroundColor: colors.border,
    overflow: 'hidden',
  },
  barFill: { height: '100%', borderRadius: radii.pill },
  footer: { flexDirection: 'row', justifyContent: 'space-between' },
  meta: { ...typography.caption },
  pct: { ...typography.caption, fontWeight: '700', color: colors.textPrimary },
});
