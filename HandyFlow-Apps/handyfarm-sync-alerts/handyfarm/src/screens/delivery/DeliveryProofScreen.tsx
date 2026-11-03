import React from 'react';
import { View, Text, Pressable, Share, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useDeliveryStore } from '../../store/deliveryStore';
import { useSyncStore } from '../../store/syncStore';

export function DeliveryProofScreen({ route, navigation }: any) {
  const { deliveryId } = route.params;
  const delivery = useDeliveryStore((s) => s.getById(deliveryId));
  const syncStatus = useSyncStore((s) => s.status);

  if (!delivery) return null;

  const offline = syncStatus === 'offline';
  const photoCount = [delivery.deliveryNotePhotoUri].filter(Boolean).length;

  const handleShare = () => {
    Share.share({
      message: `Delivery ${delivery.reference} completed — ${delivery.quantityDeliveredLitres ?? delivery.quantityLitres} L to ${delivery.customerName}. Receipt ${delivery.receiptNumber ?? ''}`,
    }).catch(() => {});
  };

  return (
    <View style={styles.container}>
      <View style={styles.iconWrap}>
        <Ionicons name="checkmark" size={36} color={colors.textOnDark} />
      </View>
      <Text style={styles.title}>DELIVERY COMPLETED</Text>

      <Text style={styles.amount}>
        {(delivery.quantityDeliveredLitres ?? delivery.quantityLitres).toLocaleString()} L
      </Text>
      <Text style={styles.customer}>
        {delivery.customerName}
        {delivery.siteName ? ` ${delivery.siteName}` : ''}
      </Text>

      <View style={styles.detailsCard}>
        <DetailRow label="Driver" value="You" />
        <DetailRow label="Receiver" value={delivery.receiverName ?? '—'} />
        <DetailRow
          label="Meter"
          value={
            delivery.meterStart != null && delivery.meterEnd != null
              ? `${delivery.meterStart} → ${delivery.meterEnd}`
              : '—'
          }
        />
        <DetailRow label="GPS" value={delivery.arrivalGps ? '✓' : '—'} />
        <DetailRow label="Signature" value={(delivery.signatureStrokes?.length ?? 0) > 0 ? '✓' : '—'} />
        <DetailRow label="Photos" value={String(photoCount)} />
        <DetailRow label="Receipt" value={delivery.receiptNumber ?? '—'} />
      </View>

      <View style={styles.statusRow}>
        <View style={[styles.statusDot, { backgroundColor: offline ? colors.textMuted : colors.success }]} />
        <Text style={styles.statusText}>{offline ? 'Pending sync' : 'Synced'}</Text>
      </View>

      <Pressable onPress={handleShare} style={styles.secondaryButton}>
        <Text style={styles.secondaryButtonText}>Share Receipt</Text>
      </Pressable>
      <Pressable onPress={() => navigation.getParent()?.goBack()} style={styles.doneButton}>
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
  container: { flex: 1, backgroundColor: colors.bg, alignItems: 'center', padding: spacing.xl, paddingTop: spacing.xxl },
  iconWrap: {
    width: 64,
    height: 64,
    borderRadius: radii.pill,
    backgroundColor: colors.success,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.md,
  },
  title: { ...typography.h3, letterSpacing: 0.5, marginBottom: spacing.md },
  amount: { fontSize: 36, fontWeight: '700', color: colors.textPrimary },
  customer: { ...typography.bodyStrong, marginBottom: spacing.lg },
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
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, marginBottom: spacing.xl },
  statusDot: { width: 8, height: 8, borderRadius: radii.pill },
  statusText: { ...typography.caption, fontWeight: '700' },
  secondaryButton: {
    width: '100%',
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: spacing.md,
    alignItems: 'center',
    marginBottom: spacing.sm,
  },
  secondaryButtonText: { color: colors.textSecondary, fontWeight: '700' },
  doneButton: {
    width: '100%',
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  doneButtonText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
});
