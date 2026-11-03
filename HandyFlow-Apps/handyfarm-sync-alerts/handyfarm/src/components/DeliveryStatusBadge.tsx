import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { colors, radii, spacing } from '../theme/tokens';
import type { DeliveryStatus } from '../types/delivery';

const CONFIG: Record<DeliveryStatus, { label: string; color: string; bg: string }> = {
  SCHEDULED: { label: 'Scheduled', color: colors.textSecondary, bg: colors.bg },
  IN_TRANSIT: { label: 'In Transit', color: colors.info, bg: colors.infoBg },
  ARRIVED: { label: 'Arrived', color: colors.warning, bg: colors.warningBg },
  COMPLETED: { label: 'Completed', color: colors.success, bg: colors.successBg },
  PROBLEM: { label: 'Problem', color: colors.danger, bg: colors.dangerBg },
};

export function DeliveryStatusBadge({ status }: { status: DeliveryStatus }) {
  const c = CONFIG[status];
  return (
    <View style={[styles.badge, { backgroundColor: c.bg }]}>
      <Text style={[styles.text, { color: c.color }]}>{c.label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    paddingVertical: 4,
    paddingHorizontal: spacing.sm,
    borderRadius: radii.pill,
    alignSelf: 'flex-start',
  },
  text: { fontSize: 11, fontWeight: '700' },
});
