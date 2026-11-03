import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { useTheme } from '../design-system/ThemeProvider';
import { QuickCaptureAction } from '../data/placeholderHomeData';

interface Props {
  actions: QuickCaptureAction[];
  onSelect: (action: QuickCaptureAction) => void;
}

export function QuickCaptureGrid({ actions, onSelect }: Props) {
  const { colors, spacing, radius, typography } = useTheme();

  return (
    <View style={styles.grid}>
      {actions.map((action) => (
        <Pressable
          key={action.id}
          onPress={() => onSelect(action)}
          accessibilityRole="button"
          accessibilityLabel={`Quick capture: ${action.label}`}
          style={({ pressed }) => [
            styles.tile,
            {
              backgroundColor: colors.surface,
              borderColor: colors.border,
              borderRadius: radius.lg,
              padding: spacing.md,
              opacity: pressed ? 0.7 : 1,
            },
          ]}
        >
          <Text style={styles.emoji}>{action.icon}</Text>
          <Text style={[typography.bodyStrong, { color: colors.textPrimary }]}>{action.label}</Text>
        </Pressable>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  tile: {
    width: '47%',
    minHeight: 84,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: 'flex-start',
    justifyContent: 'flex-end',
  },
  emoji: { fontSize: 22, marginBottom: 8 },
});
