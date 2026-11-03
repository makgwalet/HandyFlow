import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { WizardHeader } from '../../components/WizardHeader';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useReceiveFlowStore } from '../../store/receiveFlowStore';
import { MOCK_TANKS } from '../../data/mockData';

const QUICK_AMOUNTS = [1000, 2000, 5000, 10000];
const STEP = 100;

export function WaybillQuantityScreen({ navigation }: any) {
  const { tankId, waybillQuantityLitres, setWaybillQuantity } = useReceiveFlowStore();
  const tank = MOCK_TANKS.find((t) => t.id === tankId);
  if (!tank) return null;

  const spaceAvailable = tank.capacityLitres - tank.currentLitres;
  const exceedsSpace = waybillQuantityLitres > spaceAvailable;
  const canContinue = waybillQuantityLitres > 0 && !exceedsSpace;

  const clamp = (v: number) => Math.max(0, v);

  return (
    <View style={styles.container}>
      <WizardHeader title="Waybill Quantity" step={3} totalSteps={5} />
      <View style={styles.body}>
        <Text style={styles.tankLabel}>
          {tank.name} · Space available: {spaceAvailable.toLocaleString()} L
        </Text>

        <View style={styles.stepperRow}>
          <Pressable
            onPress={() => setWaybillQuantity(clamp(waybillQuantityLitres - STEP))}
            style={styles.stepperButton}
          >
            <Text style={styles.stepperGlyph}>−</Text>
          </Pressable>
          <View style={styles.amountBlock}>
            <Text style={styles.amountValue}>{waybillQuantityLitres.toLocaleString()}</Text>
            <Text style={styles.amountUnit}>LITRES</Text>
          </View>
          <Pressable
            onPress={() => setWaybillQuantity(waybillQuantityLitres + STEP)}
            style={styles.stepperButton}
          >
            <Text style={styles.stepperGlyph}>+</Text>
          </Pressable>
        </View>

        <View style={styles.quickRow}>
          {QUICK_AMOUNTS.map((amt) => (
            <Pressable key={amt} onPress={() => setWaybillQuantity(amt)} style={styles.quickChip}>
              <Text style={styles.quickChipText}>{amt.toLocaleString()} L</Text>
            </Pressable>
          ))}
        </View>

        <Text style={[styles.maxHint, exceedsSpace && styles.maxHintError]}>
          {exceedsSpace
            ? `Exceeds available space by ${(waybillQuantityLitres - spaceAvailable).toLocaleString()} L`
            : `Space available: ${spaceAvailable.toLocaleString()} L`}
        </Text>
      </View>

      <View style={styles.footer}>
        <Pressable
          disabled={!canContinue}
          onPress={() => navigation.navigate('DipReadings')}
          style={[styles.next, !canContinue && styles.disabled]}
        >
          <Text style={styles.nextText}>Next</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  body: { flex: 1, padding: spacing.lg, alignItems: 'center', paddingTop: spacing.xxl },
  tankLabel: { ...typography.caption, marginBottom: spacing.xxl, textAlign: 'center' },
  stepperRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.xl, marginBottom: spacing.xl },
  stepperButton: {
    width: 48,
    height: 48,
    borderRadius: radii.pill,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepperGlyph: { fontSize: 26, fontWeight: '700', color: colors.primaryDark },
  amountBlock: { alignItems: 'center', minWidth: 180 },
  amountValue: { fontSize: 44, fontWeight: '700', color: colors.textPrimary },
  amountUnit: { ...typography.label, marginTop: -4 },
  quickRow: { flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.xl, flexWrap: 'wrap', justifyContent: 'center' },
  quickChip: {
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderRadius: radii.pill,
    backgroundColor: colors.infoBg,
  },
  quickChipText: { color: colors.primaryDark, fontWeight: '700', fontSize: 13 },
  maxHint: { ...typography.caption, textAlign: 'center' },
  maxHintError: { color: colors.danger, fontWeight: '700' },
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
