import React from 'react';
import { View, Text, Pressable, StyleSheet, ActivityIndicator } from 'react-native';
import { useTheme } from '../design-system/ThemeProvider';

interface Props {
  pendingCount: number | null; // null while the local queue is still loading
  onPress?: () => void;
  isSyncing?: boolean;
}

export function SyncStatusBar({ pendingCount, onPress, isSyncing }: Props) {
  const { colors, spacing, radius, typography } = useTheme();
  const synced = pendingCount === 0;

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={
        pendingCount === null
          ? 'Checking sync status'
          : synced
            ? 'All records synced'
            : `${pendingCount} records waiting to sync`
      }
      style={[
        styles.bar,
        {
          backgroundColor: synced ? colors.accentSurface : colors.surfaceElevated,
          borderColor: colors.border,
          borderRadius: radius.pill,
          paddingVertical: spacing.sm,
          paddingHorizontal: spacing.md,
        },
      ]}
    >
      {isSyncing ? (
        <ActivityIndicator size="small" color={colors.textSecondary} style={{ marginRight: 6 }} />
      ) : (
        <Text style={{ marginRight: 6 }}>{pendingCount === null ? '⏳' : synced ? '🟢' : '☁️'}</Text>
      )}
      <Text style={[typography.caption, { color: colors.textSecondary }]}>
        {isSyncing
          ? 'Syncing…'
          : pendingCount === null
            ? 'Checking…'
            : synced
              ? 'Synced'
              : `${pendingCount} record${pendingCount === 1 ? '' : 's'} waiting to sync`}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  bar: { flexDirection: 'row', alignItems: 'center', alignSelf: 'flex-start', borderWidth: StyleSheet.hairlineWidth },
});
