import React, { useState } from 'react';
import { View, Text, TextInput, Pressable, ScrollView, KeyboardAvoidingView, Platform, StyleSheet } from 'react-native';
import { WizardHeader } from '../../components/WizardHeader';
import { SelectableRow } from '../../components/SelectableRow';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useReceiveFlowStore } from '../../store/receiveFlowStore';
import { MOCK_SUPPLIERS } from '../../data/mockSuppliers';

export function SupplierDetailsScreen({ navigation }: any) {
  const { supplierId, waybillNumber, tankerRegistration, driverName, setSupplierDetails } =
    useReceiveFlowStore();

  const [localSupplierId, setLocalSupplierId] = useState(supplierId);
  const [localWaybill, setLocalWaybill] = useState(waybillNumber);
  const [localReg, setLocalReg] = useState(tankerRegistration);
  const [localDriver, setLocalDriver] = useState(driverName);

  const canContinue =
    !!localSupplierId && localWaybill.trim().length > 0 && localReg.trim().length > 0;

  const handleNext = () => {
    if (!canContinue || !localSupplierId) return;
    setSupplierDetails({
      supplierId: localSupplierId,
      waybillNumber: localWaybill.trim(),
      tankerRegistration: localReg.trim(),
      driverName: localDriver.trim(),
    });
    navigation.navigate('WaybillQuantity');
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <WizardHeader title="Delivery Details" step={2} totalSteps={5} />
      <ScrollView style={styles.body} contentContainerStyle={{ paddingBottom: spacing.xl }}>
        <Text style={styles.sectionLabel}>Supplier</Text>
        {MOCK_SUPPLIERS.map((s) => (
          <SelectableRow
            key={s.id}
            title={s.name}
            selected={localSupplierId === s.id}
            onPress={() => setLocalSupplierId(s.id)}
          />
        ))}

        <View style={styles.field}>
          <Text style={styles.label}>Waybill / delivery note number</Text>
          <TextInput
            value={localWaybill}
            onChangeText={setLocalWaybill}
            placeholder="e.g. WB-88213"
            placeholderTextColor={colors.textMuted}
            style={styles.input}
          />
        </View>

        <View style={styles.field}>
          <Text style={styles.label}>Tanker registration</Text>
          <TextInput
            value={localReg}
            onChangeText={setLocalReg}
            placeholder="e.g. TRK 402 GP"
            placeholderTextColor={colors.textMuted}
            style={styles.input}
          />
        </View>

        <View style={styles.field}>
          <Text style={styles.label}>Driver name (optional)</Text>
          <TextInput
            value={localDriver}
            onChangeText={setLocalDriver}
            placeholder="Delivery driver"
            placeholderTextColor={colors.textMuted}
            style={styles.input}
          />
        </View>
      </ScrollView>

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
  body: { flex: 1, padding: spacing.lg },
  sectionLabel: { ...typography.label, textTransform: 'uppercase', marginBottom: spacing.sm },
  field: { marginTop: spacing.md, gap: spacing.xs },
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
