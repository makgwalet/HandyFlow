import React from 'react';
import { View, Text, Pressable, ScrollView, StyleSheet } from 'react-native';
import { WizardHeader } from '../../components/WizardHeader';
import { SelectableRow } from '../../components/SelectableRow';
import { Card } from '../../components/Card';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useAuthStore } from '../../store/authStore';
import { useShiftStore } from '../../store/shiftStore';
import { useShiftTotals } from '../../hooks/useShiftTotals';
import { MOCK_TANKS } from '../../data/mockData';

function shiftLabel(startedAt: string) {
  const h = new Date(startedAt).getHours();
  if (h < 12) return 'Morning Shift';
  if (h < 18) return 'Afternoon Shift';
  return 'Night Shift';
}

export function ShiftDashboardScreen({ navigation }: any) {
  const user = useAuthStore((s) => s.user);
  const activeShift = useShiftStore((s) => s.activeShift);
  const startShift = useShiftStore((s) => s.startShift);
  const [selectedTankId, setSelectedTankId] = React.useState<string | null>(null);

  if (!user) return null;

  if (!activeShift) {
    const tank = MOCK_TANKS.find((t) => t.id === selectedTankId);
    return (
      <View style={styles.container}>
        <WizardHeader title="Start Shift" step={1} totalSteps={1} onClose={() => navigation.getParent()?.goBack()} />
        <ScrollView style={styles.body}>
          <Text style={styles.label}>Select tank to operate</Text>
          {MOCK_TANKS.map((t) => (
            <SelectableRow
              key={t.id}
              title={t.name}
              subtitle={`Opening level: ${t.currentLitres.toLocaleString()} L`}
              selected={selectedTankId === t.id}
              onPress={() => setSelectedTankId(t.id)}
            />
          ))}
        </ScrollView>
        <View style={styles.footer}>
          <Pressable
            disabled={!tank}
            onPress={() => tank && startShift(tank.id, tank.currentLitres, user.id, user.name)}
            style={[styles.startButton, !tank && styles.disabled]}
          >
            <Text style={styles.startButtonText}>Start Shift</Text>
          </Pressable>
        </View>
      </View>
    );
  }

  const tank = MOCK_TANKS.find((t) => t.id === activeShift.tankId);
  const { received, dispensed } = useShiftTotals(activeShift.tankId, activeShift.startedAt);
  const expected = activeShift.openingLevel + received - dispensed;

  if (!tank) return null;

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.dashboardContent}>
      <Pressable onPress={() => navigation.getParent()?.goBack()} hitSlop={12}>
        <Text style={styles.close}>Close</Text>
      </Pressable>

      <Text style={styles.shiftTitle}>{shiftLabel(activeShift.startedAt)}</Text>
      <Text style={styles.operator}>Operator: {activeShift.operatorName}</Text>
      <Text style={styles.tank}>{tank.name}</Text>

      <Card style={styles.card}>
        <Row label="Opening" value={`${activeShift.openingLevel.toLocaleString()} L`} />
        <Divider />
        <Row label="Received" value={`+ ${received.toLocaleString()} L`} valueColor={colors.success} />
        <Divider />
        <Row label="Dispensed" value={`- ${dispensed.toLocaleString()} L`} valueColor={colors.danger} />
        <Divider />
        <Row label="Expected" value={`${expected.toLocaleString()} L`} strong />
      </Card>

      <Text style={styles.hint}>
        Received/Dispensed totals come from every FUEL_RECEIPT and FUEL_DISPATCH on this tank
        since the shift started.
      </Text>

      <Pressable
        onPress={() => navigation.navigate('ShiftClose', { tankId: tank.id })}
        style={styles.closeShiftButton}
      >
        <Text style={styles.closeShiftButtonText}>Close Shift</Text>
      </Pressable>
    </ScrollView>
  );
}

function Row({
  label,
  value,
  valueColor,
  strong,
}: {
  label: string;
  value: string;
  valueColor?: string;
  strong?: boolean;
}) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Text
        style={[
          styles.rowValue,
          strong && { fontSize: 18 },
          valueColor ? { color: valueColor } : null,
        ]}
      >
        {value}
      </Text>
    </View>
  );
}

function Divider() {
  return <View style={styles.divider} />;
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  body: { flex: 1, padding: spacing.lg },
  label: { ...typography.caption, marginBottom: spacing.md },
  footer: { padding: spacing.lg, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border },
  startButton: {
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  disabled: { opacity: 0.4 },
  startButtonText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },

  dashboardContent: { padding: spacing.lg, paddingTop: spacing.xl, paddingBottom: spacing.xxxl },
  close: { ...typography.bodyStrong, color: colors.primaryDark, marginBottom: spacing.lg },
  shiftTitle: { ...typography.label, textTransform: 'uppercase' },
  operator: { ...typography.h2, marginTop: spacing.xs },
  tank: { ...typography.body, color: colors.textSecondary, marginBottom: spacing.lg },
  card: { marginBottom: spacing.md },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: spacing.sm },
  rowLabel: { ...typography.body, color: colors.textSecondary },
  rowValue: { ...typography.bodyStrong, fontSize: 16 },
  divider: { height: 1, backgroundColor: colors.border },
  hint: { ...typography.caption, marginBottom: spacing.xl },
  closeShiftButton: {
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  closeShiftButtonText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
});
