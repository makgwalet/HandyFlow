import React from 'react';
import { Text, StyleSheet } from 'react-native';
import { typography, spacing } from '../theme/tokens';

export function SectionLabel({ children }: { children: React.ReactNode }) {
  return <Text style={styles.label}>{children}</Text>;
}

const styles = StyleSheet.create({
  label: {
    ...typography.label,
    textTransform: 'uppercase',
    marginBottom: spacing.sm,
  },
});
