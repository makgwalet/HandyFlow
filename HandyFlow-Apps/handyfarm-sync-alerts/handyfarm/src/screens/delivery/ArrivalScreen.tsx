import React, { useEffect, useState } from 'react';
import { View, Text, TextInput, Pressable, ActivityIndicator, StyleSheet } from 'react-native';
import * as Location from 'expo-location';
import { Ionicons } from '@expo/vector-icons';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useDeliveryStore } from '../../store/deliveryStore';
import { haversineMeters } from '../../utils/geo';

const GEOFENCE_RADIUS_METERS = 150;

type GpsStatus = 'capturing' | 'captured' | 'denied' | 'error';

export function ArrivalScreen({ route, navigation }: any) {
  const { deliveryId } = route.params;
  const delivery = useDeliveryStore((s) => s.getById(deliveryId));
  const setArrival = useDeliveryStore((s) => s.setArrival);

  const [status, setStatus] = useState<GpsStatus>('capturing');
  const [distance, setDistance] = useState<number | null>(null);
  const [coords, setCoords] = useState<{ lat: number; lng: number } | null>(null);
  const [overrideReason, setOverrideReason] = useState('');

  useEffect(() => {
    capture();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const capture = async () => {
    if (!delivery) return;
    setStatus('capturing');
    try {
      const { status: permStatus } = await Location.requestForegroundPermissionsAsync();
      if (permStatus !== 'granted') {
        setStatus('denied');
        return;
      }
      const position = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.High,
      });
      const lat = position.coords.latitude;
      const lng = position.coords.longitude;
      setCoords({ lat, lng });
      setDistance(
        haversineMeters(lat, lng, delivery.destination.lat, delivery.destination.lng)
      );
      setStatus('captured');
    } catch {
      setStatus('error');
    }
  };

  if (!delivery) return null;

  const inside = distance !== null && distance <= GEOFENCE_RADIUS_METERS;
  const canConfirm = status === 'captured' && coords && (inside || overrideReason.trim().length > 0);

  const handleConfirm = () => {
    if (!canConfirm || !coords || distance === null) return;
    setArrival(
      delivery.id,
      { lat: coords.lat, lng: coords.lng, distanceMeters: distance },
      inside ? undefined : overrideReason.trim()
    );
    navigation.replace('CompleteChecklist', { deliveryId: delivery.id });
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>ARRIVAL CONFIRMATION</Text>
      <Text style={styles.subtitle}>Are you at:</Text>
      <Text style={styles.destination}>{delivery.destination.name}</Text>

      {status === 'capturing' && (
        <View style={styles.center}>
          <ActivityIndicator color={colors.primary} />
          <Text style={styles.capturingText}>Checking your location...</Text>
        </View>
      )}

      {status === 'captured' && inside && (
        <View style={styles.okBlock}>
          <Ionicons name="checkmark-circle" size={22} color={colors.success} />
          <Text style={styles.okText}>Inside delivery geofence</Text>
          <Text style={styles.distanceText}>{Math.round(distance!)} m</Text>
        </View>
      )}

      {status === 'captured' && !inside && (
        <View style={styles.warnBlock}>
          <Ionicons name="warning-outline" size={22} color={colors.warning} />
          <Text style={styles.warnTitle}>LOCATION CHECK</Text>
          <Text style={styles.warnText}>
            You appear to be {(distance! / 1000).toFixed(1)} km from the delivery location.
          </Text>
          <TextInput
            value={overrideReason}
            onChangeText={setOverrideReason}
            placeholder="Reason for continuing (required)"
            placeholderTextColor={colors.textMuted}
            style={styles.reasonInput}
            multiline
          />
        </View>
      )}

      {(status === 'denied' || status === 'error') && (
        <View style={styles.warnBlock}>
          <Ionicons name="warning-outline" size={22} color={colors.danger} />
          <Text style={styles.warnTitle}>
            {status === 'denied' ? 'Location permission denied' : "Couldn't get a location"}
          </Text>
          <Pressable onPress={capture}>
            <Text style={styles.retryLink}>Try again</Text>
          </Pressable>
        </View>
      )}

      <View style={{ flex: 1 }} />

      <Pressable
        disabled={!canConfirm}
        onPress={handleConfirm}
        style={[styles.confirmButton, !canConfirm && styles.disabled]}
      >
        <Text style={styles.confirmButtonText}>
          {inside ? 'Confirm Arrival' : 'Continue with reason'}
        </Text>
      </Pressable>
      <Pressable onPress={() => navigation.goBack()} style={styles.cancelButton}>
        <Text style={styles.cancelButtonText}>Cancel</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg, padding: spacing.xl, paddingTop: spacing.xxxl },
  title: { ...typography.label, textTransform: 'uppercase' },
  subtitle: { ...typography.body, color: colors.textSecondary, marginTop: spacing.md },
  destination: { ...typography.h2, marginBottom: spacing.xl },
  center: { alignItems: 'center', gap: spacing.sm, paddingVertical: spacing.xl },
  capturingText: { ...typography.caption },
  okBlock: { alignItems: 'center', gap: spacing.xs, paddingVertical: spacing.lg },
  okText: { ...typography.bodyStrong, color: colors.success },
  distanceText: { ...typography.caption },
  warnBlock: { alignItems: 'center', gap: spacing.xs, paddingVertical: spacing.lg },
  warnTitle: { ...typography.bodyStrong, color: colors.warning, letterSpacing: 0.5 },
  warnText: { ...typography.body, textAlign: 'center', color: colors.textSecondary, marginBottom: spacing.md },
  reasonInput: {
    width: '100%',
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.md,
    padding: spacing.md,
    minHeight: 70,
    textAlignVertical: 'top',
    color: colors.textPrimary,
  },
  retryLink: { ...typography.caption, color: colors.primaryDark, fontWeight: '700' },
  confirmButton: {
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
    marginBottom: spacing.sm,
  },
  disabled: { opacity: 0.4 },
  confirmButtonText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
  cancelButton: { alignItems: 'center', paddingVertical: spacing.sm },
  cancelButtonText: { color: colors.textSecondary, fontWeight: '600' },
});
