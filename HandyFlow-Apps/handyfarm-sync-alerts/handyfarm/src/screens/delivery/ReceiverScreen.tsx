import React, { useState } from 'react';
import { View, Text, TextInput, Pressable, KeyboardAvoidingView, Platform, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useDeliveryStore } from '../../store/deliveryStore';

export function ReceiverScreen({ route, navigation }: any) {
  const { deliveryId } = route.params;
  const setReceiver = useDeliveryStore((s) => s.setReceiver);

  const [name, setName] = useState('');
  const [idNumber, setIdNumber] = useState('');
  const [signedOnBehalf, setSignedOnBehalf] = useState(false);

  const canContinue = name.trim().length > 0;

  const handleNext = () => {
    if (!canContinue) return;
    setReceiver(deliveryId, name.trim(), idNumber.trim(), signedOnBehalf);
    navigation.navigate('Signature', { deliveryId });
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={styles.header}>
        <Text style={styles.title}>RECEIVER</Text>
      </View>

      <View style={styles.body}>
        <View style={styles.field}>
          <Text style={styles.label}>Name</Text>
          <TextInput
            value={name}
            onChangeText={setName}
            placeholder="Full name"
            placeholderTextColor={colors.textMuted}
            style={styles.input}
          />
        </View>

        <View style={styles.field}>
          <Text style={styles.label}>ID / Badge</Text>
          <TextInput
            value={idNumber}
            onChangeText={setIdNumber}
            placeholder="ID number or badge"
            placeholderTextColor={colors.textMuted}
            style={styles.input}
          />
        </View>

        <Pressable
          onPress={() => setSignedOnBehalf((v) => !v)}
          style={styles.checkboxRow}
        >
          <Ionicons
            name={signedOnBehalf ? 'checkbox' : 'square-outline'}
            size={20}
            color={signedOnBehalf ? colors.primaryDark : colors.textMuted}
          />
          <Text style={styles.checkboxLabel}>Signed on behalf of company</Text>
        </Pressable>
      </View>

      <View style={styles.footer}>
        <Pressable
          disabled={!canContinue}
          onPress={handleNext}
          style={[styles.next, !canContinue && styles.disabled]}
        >
          <Text style={styles.nextText}>Next</Text>
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  header: {
    padding: spacing.lg,
    paddingTop: spacing.xl,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  title: { ...typography.label, textTransform: 'uppercase' },
  body: { flex: 1, padding: spacing.lg, gap: spacing.lg },
  field: { gap: spacing.xs },
  label: { ...typography.caption },
  input: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    fontSize: 16,
    color: colors.textPrimary,
  },
  checkboxRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  checkboxLabel: { ...typography.body },
  footer: { padding: spacing.lg, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border },
  next: {
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  disabled: { opacity: 0.4 },
  nextText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
});
