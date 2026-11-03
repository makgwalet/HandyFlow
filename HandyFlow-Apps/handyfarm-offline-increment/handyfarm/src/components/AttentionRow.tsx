import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../design-system/ThemeProvider';
import { AttentionItem } from '../data/placeholderHomeData';

const SEVERITY_META: Record<AttentionItem['severity'], { icon: string; labelKey: keyof ReturnType<typeof useTheme>['colors'] }> = {
  critical: { icon: '🔴', labelKey: 'danger' },
  action: { icon: '🟠', labelKey: 'warning' },
  upcoming: { icon: '🟡', labelKey: 'warning' },
  info: { icon: '🔵', labelKey: 'info' },
};

export function AttentionRow({ item }: { item: AttentionItem }) {
  const { colors, spacing, typography, radius } = useTheme();
  const meta = SEVERITY_META[item.severity];
  const accent = colors[meta.labelKey];

  return (
    <View
      style={[
        styles.row,
        {
          backgroundColor: colors.surface,
          borderColor: colors.border,
          borderRadius: radius.md,
          padding: spacing.md,
          marginBottom: spacing.sm,
        },
      ]}
      // Colour is never the only status signal — the emoji + text label carry it too.
      accessibilityLabel={`${item.severity} alert: ${item.title}`}
    >
      <Text style={styles.icon}>{meta.icon}</Text>
      <Text style={[typography.body, { color: colors.textPrimary, flex: 1 }]} numberOfLines={2}>
        {item.title}
      </Text>
      <View style={[styles.dot, { backgroundColor: accent }]} />
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', borderWidth: StyleSheet.hairlineWidth },
  icon: { fontSize: 18, marginRight: 10 },
  dot: { width: 8, height: 8, borderRadius: 4, marginLeft: 8 },
});
