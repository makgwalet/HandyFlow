import React, { useState } from 'react';
import { View, Text, TextInput, Pressable, KeyboardAvoidingView, Platform, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useDeliveryStore } from '../../store/deliveryStore';

function CheckRow({ label, checked }: { label: string; checked: boolean }) {
  return (
    <View style={styles.checkRow}>
      <Ionicons
        name={checked ? 'checkbox' : 'square-outline'}
        size={20}
        color={checked ? colors.success : colors.textMuted}
      />
      <Text style={styles.checkLabel}>{label}</Text>
    </View>
  );
}

export function CompleteChecklistScreen({ route, navigation }: any) {
  const { deliveryId } = route.params;
  const delivery = useDeliveryStore((s) => s.getById(deliveryId));
  const setMeterReadings = useDeliveryStore((s) => s.setMeterReadings);

  const [meterStart, setMeterStart] = useState(delivery?.meterStart?.toString() ?? '');
  const [meterEnd, setMeterEnd] = useState(delivery?.meterEnd?.toString() ?? '');

  if (!delivery) return null;

  const startNum = parseFloat(meterStart);
  const endNum = parseFloat(meterEnd);
  const metersValid = !Number.isNaN(startNum) && !Number.isNaN(endNum) && endNum > startNum;
  const quantityDelivered = metersValid ? endNum - startNum : 0;

  const handleComplete = () => {
    if (!metersValid) return;
    setMeterReadings(delivery.id, startNum, endNum);
    navigation.navigate('Receiver', { deliveryId });
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={styles.header}>
        <Text style={styles.title}>COMPLETE DELIVERY</Text>
        <Text style={styles.customer}>
          {delivery.customerName}
          {delivery.siteName ? ` — ${delivery.siteName}` : ''}
        </Text>
        <Text style={styles.quantity}>{delivery.quantityLitres.toLocaleString()} L {delivery.fuelType}</Text>
      </View>

      <View style={styles.body}>
        <CheckRow label="Arrived at site" checked />
        <CheckRow label="GPS captured" checked={!!delivery.arrivalGps} />
        <CheckRow label="Vehicle identified" checked />

        <View style={styles.meterRow}>
          <View style={styles.meterField}>
            <Text style={styles.meterLabel}>Meter start</Text>
            <TextInput
              value={meterStart}
              onChangeText={setMeterStart}
              keyboardType="decimal-pad"
              style={styles.meterInput}
            />
          </View>
          <View style={styles.meterField}>
            <Text style={styles.meterLabel}>Meter end</Text>
            <TextInput
              value={meterEnd}
              onChangeText={setMeterEnd}
              keyboardType="decimal-pad"
              style={styles.meterInput}
            />
          </View>
        </View>
        {metersValid && (
          <Text style={styles.quantityDelivered}>
            Quantity delivered: {quantityDelivered.toLocaleString()} L
          </Text>
        )}

        <View style={styles.spacer} />
        <CheckRow label="Receiver details" checked={false} />
        <CheckRow label="Receiver signature" checked={false} />
        <CheckRow label="Delivery note photo" checked={false} />
      </View>

      <View style={styles.footer}>
        <Pressable
          disabled={!metersValid}
          onPress={handleComplete}
          style={[styles.completeButton, !metersValid && styles.disabled]}
        >
          <Text style={styles.completeButtonText}>Complete Delivery</Text>
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
  customer: { ...typography.h2, marginTop: spacing.xs },
  quantity: { ...typography.body, color: colors.textSecondary },
  body: { flex: 1, padding: spacing.lg },
  checkRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, paddingVertical: spacing.xs },
  checkLabel: { ...typography.body },
  meterRow: { flexDirection: 'row', gap: spacing.md, marginTop: spacing.lg, marginBottom: spacing.sm },
  meterField: { flex: 1, gap: spacing.xs },
  meterLabel: { ...typography.caption },
  meterInput: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    fontSize: 16,
    fontWeight: '600',
    color: colors.textPrimary,
  },
  quantityDelivered: { ...typography.caption, color: colors.success, fontWeight: '700', marginBottom: spacing.sm },
  spacer: { height: spacing.md },
  footer: { padding: spacing.lg, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border },
  completeButton: {
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  disabled: { opacity: 0.4 },
  completeButtonText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
});
