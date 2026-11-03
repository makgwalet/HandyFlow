import React, { useEffect } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useAuthStore } from '../../store/authStore';
import { useDispenseFlowStore } from '../../store/dispenseFlowStore';
import { useSyncStore } from '../../store/syncStore';
import { MOCK_TANKS } from '../../data/mockData';
import { MOCK_VEHICLES } from '../../data/mockVehicles';

export function DispenseSuccessScreen({ route, navigation }: any) {
  const clientRequestId: string = route.params?.clientRequestId ?? '';
  const user = useAuthStore((s) => s.user);
  const flow = useDispenseFlowStore();
  const syncStatus = useSyncStore((s) => s.status);
  const reset = useDispenseFlowStore((s) => s.reset);

  const tank = MOCK_TANKS.find((t) => t.id === flow.tankId);
  const vehicle = MOCK_VEHICLES.find((v) => v.id === flow.vehicleId);

  const offline = syncStatus === 'offline';

  useEffect(() => {
    return () => reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!user || !tank || !vehicle) return null;

  const handleDone = () => {
    navigation.getParent()?.goBack();
  };

  return (
    <View style={styles.container}>
      <View style={[styles.iconWrap, offline && styles.iconWrapOffline]}>
        <Ionicons
          name={offline ? 'cloud-offline-outline' : 'checkmark'}
          size={36}
          color={colors.textOnDark}
        />
      </View>
      <Text style={styles.title}>{offline ? 'SAVED OFFLINE' : 'FUEL DISPENSED'}</Text>
      {offline && (
        <Text style={styles.offlineNote}>
          Transaction securely stored on this device.{'\n'}Waiting for network...
        </Text>
      )}

      <View style={styles.amountRow}>
        <Text style={styles.amount}>{flow.quantityLitres} L</Text>
        <Text style={styles.fuelType}>{tank.fuelType}</Text>
      </View>
      <Text style={styles.registration}>{vehicle.registration}</Text>

      <View style={styles.detailsCard}>
        <DetailRow label="Tank" value={tank.name} />
        <DetailRow label="Odometer" value={`${flow.odometer} km`} />
        <DetailRow label="Operator" value={user.name} />
        <DetailRow
          label="Time"
          value={new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
        />
        <DetailRow label="GPS" value={flow.gps ? 'Captured' : '—'} />
        <DetailRow label="Transaction" value={`FD-${clientRequestId.slice(0, 8).toUpperCase()}`} />
      </View>

      <View style={styles.statusRow}>
        <View style={[styles.statusDot, { backgroundColor: offline ? colors.textMuted : colors.success }]} />
        <Text style={styles.statusText}>{offline ? 'Pending sync' : 'Synced'}</Text>
      </View>

      <Pressable onPress={handleDone} style={styles.doneButton}>
        <Text style={styles.doneButtonText}>Done</Text>
      </Pressable>
    </View>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.detailRow}>
      <Text style={styles.detailLabel}>{label}</Text>
      <Text style={styles.detailValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg, alignItems: 'center', padding: spacing.xl, paddingTop: spacing.xxxl },
  iconWrap: {
    width: 64,
    height: 64,
    borderRadius: radii.pill,
    backgroundColor: colors.success,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.md,
  },
  iconWrapOffline: { backgroundColor: colors.textMuted },
  title: { ...typography.h3, letterSpacing: 0.5, marginBottom: spacing.xs },
  offlineNote: { ...typography.caption, textAlign: 'center', marginBottom: spacing.md },
  amountRow: { flexDirection: 'row', alignItems: 'baseline', gap: spacing.xs, marginTop: spacing.md },
  amount: { fontSize: 36, fontWeight: '700', color: colors.textPrimary },
  fuelType: { ...typography.body, color: colors.textSecondary },
  registration: { ...typography.bodyStrong, marginBottom: spacing.lg },
  detailsCard: {
    width: '100%',
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    gap: spacing.sm,
    marginBottom: spacing.lg,
  },
  detailRow: { flexDirection: 'row', justifyContent: 'space-between' },
  detailLabel: { ...typography.caption },
  detailValue: { ...typography.bodyStrong, fontSize: 14 },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, marginBottom: spacing.xxl },
  statusDot: { width: 8, height: 8, borderRadius: radii.pill },
  statusText: { ...typography.caption, fontWeight: '700' },
  doneButton: {
    width: '100%',
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  doneButtonText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
});
