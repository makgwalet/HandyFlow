import React from 'react';
import { View, Text, StyleSheet, ActivityIndicator } from 'react-native';
import { colors, spacing, typography } from '../../theme/tokens';

export function SplashScreen() {
  return (
    <View style={styles.container}>
      <View style={styles.brandBlock}>
        <View style={styles.logoCircle}>
          <Text style={styles.logoGlyph}>⛽</Text>
        </View>
        <Text style={styles.title}>HandyFlow</Text>
        <Text style={styles.subtitle}>Fuel Operations</Text>
      </View>

      <ActivityIndicator color={colors.primaryLight} style={styles.spinner} />

      <Text style={styles.tagline}>Manage your fuel. Anywhere.</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.navy,
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: spacing.xxxl,
  },
  brandBlock: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.sm,
  },
  logoCircle: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: colors.primaryDark,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.md,
  },
  logoGlyph: { fontSize: 32 },
  title: { ...typography.h1, color: colors.textOnDark },
  subtitle: { ...typography.body, color: colors.textOnDarkMuted },
  spinner: { marginBottom: spacing.xl },
  tagline: { ...typography.caption, color: colors.textOnDarkMuted },
});
