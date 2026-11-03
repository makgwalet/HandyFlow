import React from 'react';
import { View, Text } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useTheme } from '../design-system/ThemeProvider';

export function PlaceholderScreen({ title }: { title: string }) {
  const { colors, typography, spacing } = useTheme();
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }}>
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl }}>
        <Text style={[typography.h1, { color: colors.textPrimary, marginBottom: spacing.sm }]}>{title}</Text>
        <Text style={[typography.body, { color: colors.textSecondary, textAlign: 'center' }]}>
          Not built yet — see /docs/agriculture/00-OVERVIEW.md for the sequencing plan.
        </Text>
      </View>
    </SafeAreaView>
  );
}
