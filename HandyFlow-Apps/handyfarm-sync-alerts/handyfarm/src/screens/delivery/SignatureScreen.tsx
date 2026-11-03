import React, { useState } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { SignaturePad } from '../../components/SignaturePad';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useDeliveryStore } from '../../store/deliveryStore';
import type { SignatureStroke } from '../../types/delivery';

export function SignatureScreen({ route, navigation }: any) {
  const { deliveryId } = route.params;
  const setSignature = useDeliveryStore((s) => s.setSignature);
  const [strokes, setStrokes] = useState<SignatureStroke[]>([]);

  const hasSignature = strokes.length > 0;

  const handleAccept = () => {
    if (!hasSignature) return;
    setSignature(deliveryId, strokes);
    navigation.navigate('DeliveryNotePhoto', { deliveryId });
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>SIGNATURE</Text>
        <Text style={styles.subtitle}>Have the receiver sign below</Text>
      </View>

      <View style={styles.body}>
        <SignaturePad strokes={strokes} onChange={setStrokes} height={220} />
      </View>

      <View style={styles.footer}>
        <Pressable onPress={() => setStrokes([])} style={styles.clearButton}>
          <Text style={styles.clearButtonText}>Clear</Text>
        </Pressable>
        <Pressable
          disabled={!hasSignature}
          onPress={handleAccept}
          style={[styles.acceptButton, !hasSignature && styles.disabled]}
        >
          <Text style={styles.acceptButtonText}>Accept</Text>
        </Pressable>
      </View>
    </View>
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
  subtitle: { ...typography.caption, marginTop: 2 },
  body: { flex: 1, padding: spacing.lg, justifyContent: 'center' },
  footer: {
    flexDirection: 'row',
    gap: spacing.sm,
    padding: spacing.lg,
    backgroundColor: colors.surface,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  clearButton: {
    flex: 1,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  clearButtonText: { color: colors.textSecondary, fontWeight: '700' },
  acceptButton: {
    flex: 2,
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  disabled: { opacity: 0.4 },
  acceptButtonText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
});
