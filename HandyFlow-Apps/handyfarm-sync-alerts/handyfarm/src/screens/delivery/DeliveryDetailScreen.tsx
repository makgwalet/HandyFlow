import React from 'react';
import { View, Text, Pressable, ScrollView, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { DeliveryStatusBadge } from '../../components/DeliveryStatusBadge';
import { Card } from '../../components/Card';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useDeliveryStore } from '../../store/deliveryStore';

export function DeliveryDetailScreen({ route, navigation }: any) {
  const { deliveryId } = route.params;
  const delivery = useDeliveryStore((s) => s.getById(deliveryId));
  const startDelivery = useDeliveryStore((s) => s.startDelivery);

  if (!delivery) return null;

  const handleStart = () => {
    startDelivery(delivery.id);
    navigation.replace('InTransit', { deliveryId: delivery.id });
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Pressable onPress={() => navigation.getParent()?.goBack()} hitSlop={12}>
        <Ionicons name="arrow-back" size={22} color={colors.textPrimary} />
      </Pressable>

      <Text style={styles.reference}>DELIVERY #{delivery.reference}</Text>
      <Text style={styles.customer}>
        {delivery.customerName}
        {delivery.siteName ? ` - ${delivery.siteName}` : ''}
      </Text>
      <DeliveryStatusBadge status={delivery.status} />

      <Text style={styles.quantity}>
        {delivery.quantityLitres.toLocaleString()} L {delivery.fuelType}
      </Text>

      <Card style={styles.card}>
        <View style={styles.row}>
          <Ionicons name="location-outline" size={18} color={colors.textSecondary} />
          <View>
            <Text style={styles.rowLabel}>Pickup</Text>
            <Text style={styles.rowValue}>{delivery.pickup.name}</Text>
          </View>
        </View>
        <View style={styles.divider} />
        <View style={styles.row}>
          <Ionicons name="flag-outline" size={18} color={colors.textSecondary} />
          <View>
            <Text style={styles.rowLabel}>Destination</Text>
            <Text style={styles.rowValue}>{delivery.destination.name}</Text>
          </View>
        </View>
      </Card>

      {delivery.status === 'SCHEDULED' && (
        <Pressable onPress={handleStart} style={styles.startButton}>
          <Text style={styles.startButtonText}>Start Delivery</Text>
        </Pressable>
      )}

      {delivery.status === 'PROBLEM' && delivery.problem && (
        <Card style={[styles.card, { borderColor: colors.danger }]}>
          <Text style={[styles.rowLabel, { color: colors.danger }]}>Problem Reported</Text>
          <Text style={styles.rowValue}>{delivery.problem.reason}</Text>
          {!!delivery.problem.notes && <Text style={styles.notes}>{delivery.problem.notes}</Text>}
        </Card>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg, gap: spacing.sm, paddingBottom: spacing.xxxl },
  reference: { ...typography.label, textTransform: 'uppercase', marginTop: spacing.md },
  customer: { ...typography.h2 },
  quantity: { ...typography.h3, marginTop: spacing.sm },
  card: { marginTop: spacing.md, gap: spacing.sm },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, paddingVertical: spacing.xs },
  rowLabel: { ...typography.caption },
  rowValue: { ...typography.bodyStrong },
  divider: { height: 1, backgroundColor: colors.border },
  notes: { ...typography.caption, marginTop: spacing.xs },
  startButton: {
    marginTop: spacing.xl,
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  startButtonText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
});
