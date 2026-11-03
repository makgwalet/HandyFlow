import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { WizardHeader } from '../../components/WizardHeader';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useDispenseFlowStore, HIGH_RISK_LITRES } from '../../store/dispenseFlowStore';
import { MOCK_TANKS } from '../../data/mockData';
import { MOCK_VEHICLES } from '../../data/mockVehicles';

const QUICK_AMOUNTS = [50, 100, 200, 500];
const STEP = 10;

export function EnterQuantityScreen({ navigation }: any) {
  const { tankId, vehicleId, quantityLitres, setQuantity } = useDispenseFlowStore();

  const tank = MOCK_TANKS.find((t) => t.id === tankId);
  const vehicle = MOCK_VEHICLES.find((v) => v.id === vehicleId);
  if (!tank || !vehicle) return null;

  const maxAllowed = Math.min(tank.currentLitres, vehicle.maxAllowedLitres);
  const exceedsMax = quantityLitres > maxAllowed;
  const canContinue = quantityLitres > 0 && !exceedsMax;

  const clamp = (v: number) => Math.max(0, v);

  const handleNext = () => {
    if (!canContinue) return;
    if (quantityLitres > HIGH_RISK_LITRES) {
      navigation.navigate('Authorisation');
    } else {
      navigation.navigate('MeterReading');
    }
  };

  return (
    <View style={styles.container}>
      <WizardHeader title="Enter Quantity" step={3} totalSteps={5} />
      <View style={styles.body}>
        <Text style={styles.tankLabel}>
          {tank.name} · Available: {tank.currentLitres.toLocaleString()} L
        </Text>

        <View style={styles.stepperRow}>
          <Pressable
            onPress={() => setQuantity(clamp(quantityLitres - STEP))}
            style={styles.stepperButton}
          >
            <Text style={styles.stepperGlyph}>−</Text>
          </Pressable>

          <View style={styles.amountBlock}>
            <Text style={styles.amountValue}>{quantityLitres}</Text>
            <Text style={styles.amountUnit}>LITRES</Text>
          </View>

          <Pressable
            onPress={() => setQuantity(clamp(quantityLitres + STEP))}
            style={styles.stepperButton}
          >
            <Text style={styles.stepperGlyph}>+</Text>
          </Pressable>
        </View>

        <View style={styles.quickRow}>
          {QUICK_AMOUNTS.map((amt) => (
            <Pressable
              key={amt}
              onPress={() => setQuantity(amt)}
              style={styles.quickChip}
            >
              <Text style={styles.quickChipText}>{amt} L</Text>
            </Pressable>
          ))}
        </View>

        <Text style={[styles.maxHint, exceedsMax && styles.maxHintError]}>
          Maximum allowed: {maxAllowed.toLocaleString()} L
        </Text>
        {quantityLitres > HIGH_RISK_LITRES && !exceedsMax && (
          <Text style={styles.highRiskHint}>
            Over {HIGH_RISK_LITRES} L — supervisor PIN will be required
          </Text>
        )}
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
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  body: { flex: 1, padding: spacing.lg, alignItems: 'center', paddingTop: spacing.xxl },
  tankLabel: { ...typography.caption, marginBottom: spacing.xxl },
  stepperRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xl,
    marginBottom: spacing.xl,
  },
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
  amountBlock: { alignItems: 'center', minWidth: 140 },
  amountValue: { fontSize: 56, fontWeight: '700', color: colors.textPrimary },
  amountUnit: { ...typography.label, marginTop: -4 },
  quickRow: { flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.xl },
  quickChip: {
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderRadius: radii.pill,
    backgroundColor: colors.infoBg,
  },
  quickChipText: { color: colors.primaryDark, fontWeight: '700', fontSize: 13 },
  maxHint: { ...typography.caption },
  maxHintError: { color: colors.danger, fontWeight: '700' },
  highRiskHint: { ...typography.caption, color: colors.warning, marginTop: spacing.xs, textAlign: 'center' },
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
