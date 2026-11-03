import React, { useRef, useState } from 'react';
import {
  View,
  Text,
  TextInput,
  Pressable,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { useAuthStore } from '../../store/authStore';
import { colors, radii, spacing, typography } from '../../theme/tokens';

const CODE_LENGTH = 6;

export function OTPScreen() {
  const { phone, error, verifyOtp, resendOtp } = useAuthStore();
  const [code, setCode] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const inputRef = useRef<TextInput>(null);

  const handleChange = async (value: string) => {
    const digits = value.replace(/[^0-9]/g, '').slice(0, CODE_LENGTH);
    setCode(digits);
    if (digits.length === CODE_LENGTH) {
      setSubmitting(true);
      await verifyOtp(digits);
      setSubmitting(false);
    }
  };

  return (
    <View style={styles.container}>
      <Pressable onPress={() => useAuthStore.setState({ phase: 'phone' })} hitSlop={12}>
        <Text style={styles.back}>← Back</Text>
      </Pressable>

      <Text style={styles.title}>Verify your identity</Text>
      <Text style={styles.subtitle}>Enter the 6-digit code sent to{'\n'}{phone}</Text>

      <Pressable onPress={() => inputRef.current?.focus()} style={styles.boxesRow}>
        {Array.from({ length: CODE_LENGTH }).map((_, i) => (
          <View key={i} style={[styles.box, code.length === i && styles.boxActive]}>
            <Text style={styles.boxText}>{code[i] ?? ''}</Text>
          </View>
        ))}
      </Pressable>

      <TextInput
        ref={inputRef}
        value={code}
        onChangeText={handleChange}
        keyboardType="number-pad"
        maxLength={CODE_LENGTH}
        style={styles.hiddenInput}
        autoFocus
      />

      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      <View style={styles.verifyRow}>
        {submitting ? (
          <ActivityIndicator color={colors.primaryDark} />
        ) : (
          <Pressable
            onPress={() => handleChange(code)}
            disabled={code.length !== CODE_LENGTH}
            style={[styles.primaryButton, code.length !== CODE_LENGTH && styles.disabled]}
          >
            <Text style={styles.primaryButtonText}>Verify</Text>
          </Pressable>
        )}
      </View>

      <Pressable onPress={resendOtp} style={styles.resend}>
        <Text style={styles.resendText}>Didn't receive a code? Resend</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg, padding: spacing.xl, justifyContent: 'center' },
  back: { ...typography.bodyStrong, color: colors.primaryDark, marginBottom: spacing.xl },
  title: { ...typography.h1, marginBottom: spacing.xs },
  subtitle: { ...typography.body, color: colors.textSecondary, marginBottom: spacing.xxl },
  boxesRow: { flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.lg },
  box: {
    flex: 1,
    height: 56,
    borderRadius: radii.md,
    borderWidth: 1.5,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  boxActive: { borderColor: colors.primary },
  boxText: { fontSize: 22, fontWeight: '700', color: colors.textPrimary },
  hiddenInput: { position: 'absolute', opacity: 0, height: 0, width: 0 },
  errorText: { color: colors.danger, marginBottom: spacing.md, fontSize: 13 },
  verifyRow: { marginBottom: spacing.lg, minHeight: 52, justifyContent: 'center' },
  primaryButton: {
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  disabled: { opacity: 0.5 },
  primaryButtonText: { color: colors.textOnDark, fontSize: 16, fontWeight: '700' },
  resend: { alignItems: 'center' },
  resendText: { ...typography.caption, color: colors.primaryDark, fontWeight: '600' },
});
