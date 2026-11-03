import React from 'react';
import { Pressable, Text, View, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radii, spacing, typography } from '../theme/tokens';

interface QuickActionButtonProps {
  label: string;
  icon: keyof typeof Ionicons.glyphMap;
  onPress?: () => void;
  tone?: 'default' | 'warning';
}

export function QuickActionButton({
  label,
  icon,
  onPress,
  tone = 'default',
}: QuickActionButtonProps) {
  const iconColor = tone === 'warning' ? colors.warning : colors.primary;
  const iconBg = tone === 'warning' ? colors.warningBg : colors.infoBg;

  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [styles.button, pressed && styles.pressed]}
    >
      <View style={[styles.iconWrap, { backgroundColor: iconBg }]}>
        <Ionicons name={icon} size={22} color={iconColor} />
      </View>
      <Text style={styles.label} numberOfLines={2}>
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    flexBasis: '48%',
    flexGrow: 1,
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: spacing.lg,
    paddingHorizontal: spacing.md,
    alignItems: 'flex-start',
    gap: spacing.sm,
  },
  pressed: {
    opacity: 0.7,
  },
  iconWrap: {
    width: 40,
    height: 40,
    borderRadius: radii.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: {
    ...typography.bodyStrong,
  },
});
