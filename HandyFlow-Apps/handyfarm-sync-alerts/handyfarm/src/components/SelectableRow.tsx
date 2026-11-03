import React from 'react';
import { Pressable, View, Text, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radii, spacing, typography } from '../theme/tokens';

interface SelectableRowProps {
  title: string;
  subtitle?: string;
  selected: boolean;
  onPress: () => void;
  icon?: keyof typeof Ionicons.glyphMap;
}

export function SelectableRow({ title, subtitle, selected, onPress, icon }: SelectableRowProps) {
  return (
    <Pressable
      onPress={onPress}
      style={[styles.row, selected && styles.rowSelected]}
    >
      {icon ? (
        <View style={[styles.iconWrap, selected && styles.iconWrapSelected]}>
          <Ionicons name={icon} size={18} color={selected ? colors.primaryDark : colors.textSecondary} />
        </View>
      ) : (
        <View style={[styles.radio, selected && styles.radioSelected]}>
          {selected && <View style={styles.radioDot} />}
        </View>
      )}
      <View style={{ flex: 1 }}>
        <Text style={styles.title}>{title}</Text>
        {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
      </View>
      {icon && selected && (
        <Ionicons name="checkmark-circle" size={20} color={colors.primaryDark} />
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    padding: spacing.md,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    marginBottom: spacing.sm,
  },
  rowSelected: { borderColor: colors.primary, backgroundColor: colors.infoBg },
  radio: {
    width: 20,
    height: 20,
    borderRadius: radii.pill,
    borderWidth: 2,
    borderColor: colors.borderStrong,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioSelected: { borderColor: colors.primaryDark },
  radioDot: { width: 10, height: 10, borderRadius: radii.pill, backgroundColor: colors.primaryDark },
  iconWrap: {
    width: 36,
    height: 36,
    borderRadius: radii.md,
    backgroundColor: colors.bg,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconWrapSelected: { backgroundColor: colors.surface },
  title: { ...typography.bodyStrong },
  subtitle: { ...typography.caption },
});
