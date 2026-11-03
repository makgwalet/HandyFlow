import React, { useRef, useState } from 'react';
import { View, Text, TextInput, Pressable, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useAuthStore } from '../../store/authStore';
import { useDispenseFlowStore } from '../../store/dispenseFlowStore';
import { colors, radii, spacing, typography } from '../../theme/tokens';

const PIN_LENGTH = 4;

/**
 * Gate shown when quantity exceeds HIGH_RISK_LITRES (spec section 9's
 * tiered transaction policy). Any 4-digit PIN is accepted for now — see
 * BACKEND_GAPS.md "No operator PIN verification".
 */
export function AuthorisationScreen({ navigation }: any) {
  const user = useAuthStore((s) => s.user);
  const setPinVerified = useDispenseFlowStore((s) => s.setPinVerified);
  const [pin, setPin] = useState('');
  const inputRef = useRef<TextInput>(null);

  if (!user) return null;

  const handleChange = (value: string) => {
    const digits = value.replace(/[^0-9]/g, '').slice(0, PIN_LENGTH);
    setPin(digits);
    if (digits.length === PIN_LENGTH) {
      setPinVerified(true);
      navigation.navigate('Evidence');
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.eyebrow}>AUTHORISED OPERATOR</Text>
      <Text style={styles.name}>{user.name}</Text>

      <Text style={styles.label}>Enter PIN</Text>
      <Pressable onPress={() => inputRef.current?.focus()} style={styles.dotsRow}>
        {Array.from({ length: PIN_LENGTH }).map((_, i) => (
          <View key={i} style={[styles.dot, i < pin.length && styles.dotFilled]} />
        ))}
      </Pressable>
      <TextInput
        ref={inputRef}
        value={pin}
        onChangeText={handleChange}
        keyboardType="number-pad"
        maxLength={PIN_LENGTH}
        secureTextEntry
        autoFocus
        style={styles.hiddenInput}
      />

      <Pressable
        disabled={pin.length !== PIN_LENGTH}
        onPress={() => handleChange(pin)}
        style={[styles.confirm, pin.length !== PIN_LENGTH && styles.disabled]}
      >
        <Text style={styles.confirmText}>Confirm</Text>
      </Pressable>

      <View style={styles.fingerprintRow}>
        <Ionicons name="finger-print-outline" size={18} color={colors.textMuted} />
        <Text style={styles.fingerprintText}>Use fingerprint (not yet available)</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg, padding: spacing.xl, justifyContent: 'center', alignItems: 'center' },
  eyebrow: { ...typography.label, marginBottom: spacing.sm },
  name: { ...typography.h2, marginBottom: spacing.xxl },
  label: { ...typography.caption, marginBottom: spacing.md },
  dotsRow: { flexDirection: 'row', gap: spacing.md, marginBottom: spacing.xxl },
  dot: {
    width: 18,
    height: 18,
    borderRadius: radii.pill,
    borderWidth: 2,
    borderColor: colors.borderStrong,
  },
  dotFilled: { backgroundColor: colors.primaryDark, borderColor: colors.primaryDark },
  hiddenInput: { position: 'absolute', opacity: 0, height: 0, width: 0 },
  confirm: {
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.xxxl,
    alignItems: 'center',
    marginBottom: spacing.xl,
  },
  disabled: { opacity: 0.4 },
  confirmText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
  fingerprintRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs },
  fingerprintText: { ...typography.caption },
});
