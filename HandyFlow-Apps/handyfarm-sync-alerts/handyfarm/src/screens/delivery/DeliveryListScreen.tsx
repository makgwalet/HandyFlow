import React from 'react';
import { View, Text, Pressable, FlatList, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Card } from '../../components/Card';
import { DeliveryStatusBadge } from '../../components/DeliveryStatusBadge';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useDeliveryStore } from '../../store/deliveryStore';
import { routeForDelivery } from '../../utils/deliveryRouting';
import type { Delivery } from '../../types/delivery';

const CTA_LABEL: Record<Delivery['status'], string> = {
  SCHEDULED: 'Start Delivery',
  IN_TRANSIT: 'Continue',
  ARRIVED: 'Complete Delivery',
  COMPLETED: 'View Receipt',
  PROBLEM: 'View Details',
};

export function DeliveryListScreen({ navigation }: any) {
  const deliveries = useDeliveryStore((s) => s.deliveries);

  const openDelivery = (delivery: Delivery) => {
    const route = routeForDelivery(delivery);
    navigation.navigate('Delivery', { screen: route.screen, params: route.params });
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>My Deliveries</Text>
      </View>
      <FlatList
        data={deliveries}
        keyExtractor={(d) => d.id}
        contentContainerStyle={styles.list}
        renderItem={({ item }) => (
          <Pressable onPress={() => openDelivery(item)}>
            <Card style={styles.card}>
              <View style={styles.cardTop}>
                <Text style={styles.reference}>#{item.reference}</Text>
                <DeliveryStatusBadge status={item.status} />
              </View>
              <Text style={styles.customer}>
                {item.customerName}
                {item.siteName ? ` - ${item.siteName}` : ''}
              </Text>
              <Text style={styles.quantity}>
                {item.quantityLitres.toLocaleString()} L {item.fuelType}
              </Text>

              <View style={styles.metaRow}>
                <View style={styles.metaItem}>
                  <Ionicons name="navigate-outline" size={14} color={colors.textSecondary} />
                  <Text style={styles.metaText}>{item.distanceKm} km</Text>
                </View>
                <View style={styles.metaItem}>
                  <Ionicons name="time-outline" size={14} color={colors.textSecondary} />
                  <Text style={styles.metaText}>Scheduled {item.scheduledTime}</Text>
                </View>
              </View>

              <Pressable onPress={() => openDelivery(item)} style={styles.ctaButton}>
                <Text style={styles.ctaText}>{CTA_LABEL[item.status]}</Text>
              </Pressable>
            </Card>
          </Pressable>
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  header: { padding: spacing.lg, paddingBottom: spacing.sm },
  title: { ...typography.h2 },
  list: { paddingHorizontal: spacing.lg, paddingBottom: spacing.xxxl, gap: spacing.md },
  card: { marginBottom: spacing.md },
  cardTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.xs },
  reference: { ...typography.caption, fontWeight: '700' },
  customer: { ...typography.bodyStrong, fontSize: 16, marginBottom: 2 },
  quantity: { ...typography.body, color: colors.textSecondary, marginBottom: spacing.sm },
  metaRow: { flexDirection: 'row', gap: spacing.lg, marginBottom: spacing.md },
  metaItem: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  metaText: { ...typography.caption },
  ctaButton: {
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.sm,
    alignItems: 'center',
  },
  ctaText: { color: colors.textOnDark, fontWeight: '700', fontSize: 14 },
});
