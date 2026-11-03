import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radii, spacing, typography } from '../../theme/tokens';

interface PlaceholderScreenProps {
  title: string;
  icon: keyof typeof Ionicons.glyphMap;
  note?: string;
}

/**
 * Standing in for screens scheduled in later phases (Fuel, Deliveries,
 * Tanks, Alerts, ...). Keeps every tab in the role-based navigator
 * tappable and honest about what's built vs. coming next, instead of a
 * dead tab or fake data.
 */
export function PlaceholderScreen({ title, icon, note }: PlaceholderScreenProps) {
  return (
    <View style={styles.container}>
      <View style={styles.iconWrap}>
        <Ionicons name={icon} size={28} color={colors.primary} />
      </View>
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.note}>{note ?? 'Coming in a later phase.'}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.bg,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
    gap: spacing.sm,
  },
  iconWrap: {
    width: 56,
    height: 56,
    borderRadius: radii.lg,
    backgroundColor: colors.infoBg,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.sm,
  },
  title: { ...typography.h3 },
  note: { ...typography.caption, textAlign: 'center' },
});
