import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  Pressable,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  ActivityIndicator,
} from 'react-native';
import { useAuthStore } from '../../store/authStore';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { ROLE_LABEL, type Role } from '../../types/domain';

const ROLES: Role[] = ['OPERATOR', 'DRIVER', 'FUEL_MANAGER'];

export function LoginScreen() {
  const [phone, setPhone] = useState('+27 82 123 4567');
  const [submitting, setSubmitting] = useState(false);
  const { selectedRole, setSelectedRole, requestOtp } = useAuthStore();

  const handleContinue = async () => {
    if (!phone.trim() || submitting) return;
    setSubmitting(true);
    await requestOtp(phone.trim());
    setSubmitting(false);
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={styles.header}>
        <Text style={styles.title}>Welcome back</Text>
        <Text style={styles.subtitle}>Enter your employee or driver number to continue</Text>
      </View>

      <View style={styles.field}>
        <Text style={styles.fieldLabel}>Phone number</Text>
        <TextInput
          value={phone}
          onChangeText={setPhone}
          keyboardType="phone-pad"
          placeholder="+27 82 123 4567"
          placeholderTextColor={colors.textMuted}
          style={styles.input}
        />
      </View>

      <Pressable
        onPress={handleContinue}
        disabled={submitting}
        style={({ pressed }) => [
          styles.primaryButton,
          pressed && styles.pressed,
          submitting && styles.disabled,
        ]}
      >
        {submitting ? (
          <ActivityIndicator color={colors.textOnDark} />
        ) : (
          <Text style={styles.primaryButtonText}>Continue</Text>
        )}
      </Pressable>

      <View style={styles.devPanel}>
        <Text style={styles.devLabel}>PREVIEW ROLE (dev only — no backend yet)</Text>
        <View style={styles.segmented}>
          {ROLES.map((role) => {
            const active = role === selectedRole;
            return (
              <Pressable
                key={role}
                onPress={() => setSelectedRole(role)}
                style={[styles.segment, active && styles.segmentActive]}
              >
                <Text style={[styles.segmentText, active && styles.segmentTextActive]}>
                  {ROLE_LABEL[role]}
                </Text>
              </Pressable>
            );
          })}
        </View>
        <Text style={styles.devHint}>
          Any 6-digit code works on the next screen. This picker goes away once Identity/OTP
          is wired up.
        </Text>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg, padding: spacing.xl, justifyContent: 'center' },
  header: { marginBottom: spacing.xxl },
  title: { ...typography.h1, marginBottom: spacing.xs },
  subtitle: { ...typography.body, color: colors.textSecondary },
  field: { marginBottom: spacing.lg },
  fieldLabel: { ...typography.caption, marginBottom: spacing.xs },
  input: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.md,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    fontSize: 16,
    color: colors.textPrimary,
  },
  primaryButton: {
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 52,
  },
  pressed: { opacity: 0.85 },
  disabled: { opacity: 0.6 },
  primaryButtonText: { color: colors.textOnDark, fontSize: 16, fontWeight: '700' },
  devPanel: {
    marginTop: spacing.xxl,
    padding: spacing.lg,
    borderRadius: radii.lg,
    borderWidth: 1,
    borderStyle: 'dashed',
    borderColor: colors.borderStrong,
    backgroundColor: colors.surface,
  },
  devLabel: { ...typography.label, marginBottom: spacing.sm },
  segmented: { flexDirection: 'row', gap: spacing.xs, marginBottom: spacing.sm },
  segment: {
    flex: 1,
    paddingVertical: spacing.sm,
    borderRadius: radii.sm,
    alignItems: 'center',
    backgroundColor: colors.bg,
  },
  segmentActive: { backgroundColor: colors.primaryDark },
  segmentText: { fontSize: 12, fontWeight: '700', color: colors.textSecondary },
  segmentTextActive: { color: colors.textOnDark },
  devHint: { ...typography.caption, fontSize: 12 },
});
