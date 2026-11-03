import React from 'react';
import { View, Text, Pressable, Linking, Platform, Alert, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useDeliveryStore } from '../../store/deliveryStore';

export function InTransitScreen({ route, navigation }: any) {
  const { deliveryId } = route.params;
  const delivery = useDeliveryStore((s) => s.getById(deliveryId));

  if (!delivery) return null;

  const openNavigation = () => {
    const { lat, lng, name } = delivery.destination;
    const label = encodeURIComponent(name);
    const url = Platform.select({
      ios: `maps:0,0?q=${label}@${lat},${lng}`,
      android: `geo:${lat},${lng}?q=${lat},${lng}(${label})`,
      default: `https://maps.google.com/?q=${lat},${lng}`,
    })!;
    Linking.openURL(url).catch(() =>
      Alert.alert('Could not open maps', 'No maps app is available on this device.')
    );
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>DELIVERY IN TRANSIT</Text>
        <Text style={styles.customer}>
          {delivery.customerName}
          {delivery.siteName ? ` — ${delivery.siteName}` : ''}
        </Text>
        <Text style={styles.quantity}>{delivery.quantityLitres.toLocaleString()} L</Text>
      </View>

      <View style={styles.body}>
        <View style={styles.infoRow}>
          <Text style={styles.infoLabel}>Started</Text>
          <Text style={styles.infoValue}>
            {delivery.startedAt
              ? new Date(delivery.startedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
              : '—'}
          </Text>
        </View>
        <View style={styles.gpsRow}>
          <View style={styles.gpsDot} />
          <Text style={styles.gpsText}>Tracking</Text>
        </View>

        <View style={styles.divider} />

        <Text style={styles.destinationLabel}>Destination</Text>
        <Text style={styles.destinationValue}>{delivery.destination.name}</Text>

        <Pressable onPress={openNavigation} style={styles.navButton}>
          <Ionicons name="navigate" size={18} color={colors.primaryDark} />
          <Text style={styles.navButtonText}>Open Navigation</Text>
        </Pressable>
      </View>

      <View style={styles.footer}>
        <Pressable
          onPress={() => navigation.navigate('ReportProblem', { deliveryId })}
          style={styles.reportButton}
        >
          <Text style={styles.reportButtonText}>Report Problem</Text>
        </Pressable>
        <Pressable
          onPress={() => navigation.navigate('Arrival', { deliveryId })}
          style={styles.arrivedButton}
        >
          <Text style={styles.arrivedButtonText}>Arrived</Text>
        </Pressable>
      </View>
    </View>
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
  quantity: { ...typography.h3, color: colors.textSecondary },
  body: { flex: 1, padding: spacing.lg },
  infoRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: spacing.sm },
  infoLabel: { ...typography.caption },
  infoValue: { ...typography.bodyStrong },
  gpsRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, marginBottom: spacing.lg },
  gpsDot: { width: 8, height: 8, borderRadius: radii.pill, backgroundColor: colors.info },
  gpsText: { ...typography.caption, fontWeight: '700', color: colors.info },
  divider: { height: 1, backgroundColor: colors.border, marginBottom: spacing.lg },
  destinationLabel: { ...typography.caption, marginBottom: 2 },
  destinationValue: { ...typography.h3, marginBottom: spacing.lg },
  navButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.sm,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.primary,
    paddingVertical: spacing.md,
  },
  navButtonText: { color: colors.primaryDark, fontWeight: '700', fontSize: 15 },
  footer: {
    padding: spacing.lg,
    backgroundColor: colors.surface,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    gap: spacing.sm,
  },
  reportButton: {
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  reportButtonText: { color: colors.textSecondary, fontWeight: '700' },
  arrivedButton: {
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  arrivedButtonText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
});
