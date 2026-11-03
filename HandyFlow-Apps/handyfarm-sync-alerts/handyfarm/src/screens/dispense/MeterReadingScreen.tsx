import React, { useEffect } from 'react';
import { View, Text, TextInput, Pressable, StyleSheet, KeyboardAvoidingView, Platform } from 'react-native';
import { WizardHeader } from '../../components/WizardHeader';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useDispenseFlowStore } from '../../store/dispenseFlowStore';
import { MOCK_VEHICLES, PUMP_METER_CURRENT } from '../../data/mockVehicles';

export function MeterReadingScreen({ navigation }: any) {
  const { vehicleId, tankId, quantityLitres, odometer, pumpMeterEnd, setMeterReading } =
    useDispenseFlowStore();

  const vehicle = MOCK_VEHICLES.find((v) => v.id === vehicleId);
  const pumpMeterStart = tankId ? PUMP_METER_CURRENT[tankId] ?? 0 : 0;

  // Pre-fill sensible defaults once, first time this screen is reached.
  useEffect(() => {
    if (!odometer && vehicle) {
      const suggestedEnd = (pumpMeterStart + quantityLitres).toFixed(1);
      setMeterReading(String(vehicle.lastOdometer), pumpMeterStart, suggestedEnd);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!vehicle) return null;

  const odometerNum = parseInt(odometer, 10);
  const odometerValid = !Number.isNaN(odometerNum) && odometerNum >= vehicle.lastOdometer;
  const pumpEndNum = parseFloat(pumpMeterEnd);
  const pumpValid = !Number.isNaN(pumpEndNum) && pumpEndNum >= pumpMeterStart;
  const canContinue = odometerValid && pumpValid;

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <WizardHeader title="Meter Reading" step={4} totalSteps={5} />
      <View style={styles.body}>
        <View style={styles.field}>
          <Text style={styles.label}>Current pump reading</Text>
          <Text style={styles.readonlyValue}>{pumpMeterStart.toFixed(1)}</Text>
        </View>

        <View style={styles.field}>
          <Text style={styles.label}>New pump reading</Text>
          <TextInput
            value={pumpMeterEnd}
            onChangeText={(v) => setMeterReading(odometer, pumpMeterStart, v)}
            keyboardType="decimal-pad"
            style={styles.input}
          />
          {!pumpValid && (
            <Text style={styles.error}>Must be at least {pumpMeterStart.toFixed(1)}</Text>
          )}
        </View>

        <View style={styles.field}>
          <Text style={styles.label}>Odometer (km)</Text>
          <TextInput
            value={odometer}
            onChangeText={(v) => setMeterReading(v, pumpMeterStart, pumpMeterEnd)}
            keyboardType="number-pad"
            style={styles.input}
          />
          {!odometerValid && (
            <Text style={styles.error}>
              Must be at least {vehicle.lastOdometer.toLocaleString()} km (last known reading)
            </Text>
          )}
        </View>
      </View>

      <View style={styles.footer}>
        <Pressable
          disabled={!canContinue}
          onPress={() => navigation.navigate('Evidence')}
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
  body: { flex: 1, padding: spacing.lg, gap: spacing.lg },
  field: { gap: spacing.xs },
  label: { ...typography.caption },
  readonlyValue: {
    ...typography.h3,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
  },
  input: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    fontSize: 18,
    fontWeight: '600',
    color: colors.textPrimary,
  },
  error: { color: colors.danger, fontSize: 12 },
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
