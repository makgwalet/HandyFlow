import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radii, spacing, typography } from '../theme/tokens';

interface WizardHeaderProps {
  title: string;
  step: number; // 1-indexed
  totalSteps: number;
  onClose?: () => void;
}

export function WizardHeader({ title, step, totalSteps, onClose }: WizardHeaderProps) {
  const navigation = useNavigation<any>();

  return (
    <View style={styles.container}>
      <View style={styles.topRow}>
        <Pressable
          onPress={() => (navigation.canGoBack() ? navigation.goBack() : onClose?.())}
          hitSlop={12}
        >
          <Ionicons name="arrow-back" size={22} color={colors.textPrimary} />
        </Pressable>
        <Text style={styles.title}>{title}</Text>
        <Pressable onPress={onClose} hitSlop={12}>
          <Ionicons name="close" size={22} color={colors.textSecondary} />
        </Pressable>
      </View>

      <View style={styles.dots}>
        {Array.from({ length: totalSteps }).map((_, i) => {
          const idx = i + 1;
          const active = idx === step;
          const done = idx < step;
          return (
            <React.Fragment key={idx}>
              <View
                style={[
                  styles.dot,
                  active && styles.dotActive,
                  done && styles.dotDone,
                ]}
              >
                <Text style={[styles.dotText, (active || done) && styles.dotTextActive]}>
                  {idx}
                </Text>
              </View>
              {idx < totalSteps && <View style={styles.connector} />}
            </React.Fragment>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
    paddingBottom: spacing.lg,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  topRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.lg,
  },
  title: { ...typography.h3 },
  dots: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center' },
  dot: {
    width: 26,
    height: 26,
    borderRadius: radii.pill,
    backgroundColor: colors.bg,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dotActive: { backgroundColor: colors.primaryDark, borderColor: colors.primaryDark },
  dotDone: { backgroundColor: colors.primaryLight, borderColor: colors.primaryLight },
  dotText: { fontSize: 12, fontWeight: '700', color: colors.textMuted },
  dotTextActive: { color: colors.textOnDark },
  connector: { width: 16, height: 1, backgroundColor: colors.border },
});
