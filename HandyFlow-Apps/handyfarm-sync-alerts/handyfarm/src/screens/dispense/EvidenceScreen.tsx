import React, { useEffect } from 'react';
import { View, Text, Pressable, Image, StyleSheet, ActivityIndicator } from 'react-native';
import * as Location from 'expo-location';
import { Ionicons } from '@expo/vector-icons';
import { WizardHeader } from '../../components/WizardHeader';
import { Card } from '../../components/Card';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useAuthStore } from '../../store/authStore';
import { useDispenseFlowStore } from '../../store/dispenseFlowStore';
import { useTransactionQueueStore } from '../../store/transactionQueueStore';
import { useSyncStore } from '../../store/syncStore';
import { MOCK_TANKS } from '../../data/mockData';
import { MOCK_VEHICLES } from '../../data/mockVehicles';

export function EvidenceScreen({ navigation }: any) {
  const user = useAuthStore((s) => s.user);
  const flow = useDispenseFlowStore();
  const enqueue = useTransactionQueueStore((s) => s.enqueue);
  const syncStatus = useSyncStore((s) => s.status);

  const tank = MOCK_TANKS.find((t) => t.id === flow.tankId);
  const vehicle = MOCK_VEHICLES.find((v) => v.id === flow.vehicleId);

  useEffect(() => {
    if (flow.gpsStatus === 'idle') captureGps();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const captureGps = async () => {
    flow.setGps('capturing');
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        flow.setGps('denied');
        return;
      }
      const position = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });
      flow.setGps('captured', {
        lat: position.coords.latitude,
        lng: position.coords.longitude,
        accuracy: position.coords.accuracy,
      });
    } catch {
      flow.setGps('error');
    }
  };

  if (!user || !tank || !vehicle) return null;

  const canSubmit = flow.gpsStatus === 'captured' && !!flow.photoUri;

  const handleSubmit = () => {
    if (!canSubmit) return;

    const payload = {
      tankId: tank.id,
      vehicleId: vehicle.id,
      quantityLitres: flow.quantityLitres,
      odometer: flow.odometer,
      pumpMeterStart: flow.pumpMeterStart,
      pumpMeterEnd: flow.pumpMeterEnd,
      pinVerified: flow.pinVerified,
      location: flow.gps,
      photoUri: flow.photoUri,
      authorisedBy: user.id,
      capturedAt: new Date().toISOString(),
    };

    const item = enqueue('FUEL_DISPATCH', user.id, payload);

    navigation.replace('Success', { clientRequestId: item.clientRequestId });
  };

  return (
    <View style={styles.container}>
      <WizardHeader title="Capture Evidence" step={5} totalSteps={5} />
      <View style={styles.body}>
        <Text style={styles.sectionLabel}>Photo</Text>
        {flow.photoUri ? (
          <Pressable onPress={() => navigation.navigate('CameraCapture')}>
            <Image source={{ uri: flow.photoUri }} style={styles.photoPreview} />
            <View style={styles.retakeBadge}>
              <Ionicons name="camera-outline" size={14} color={colors.textOnDark} />
              <Text style={styles.retakeText}>Retake</Text>
            </View>
          </Pressable>
        ) : (
          <Pressable
            onPress={() => navigation.navigate('CameraCapture')}
            style={styles.photoPlaceholder}
          >
            <Ionicons name="camera-outline" size={28} color={colors.primary} />
            <Text style={styles.photoPlaceholderText}>Take Photo</Text>
          </Pressable>
        )}

        <Text style={styles.sectionLabel}>Location</Text>
        <Card style={styles.gpsCard}>
          {flow.gpsStatus === 'capturing' && (
            <>
              <ActivityIndicator color={colors.primary} />
              <Text style={styles.gpsText}>Capturing GPS...</Text>
            </>
          )}
          {flow.gpsStatus === 'captured' && flow.gps && (
            <>
              <Ionicons name="checkmark-circle" size={20} color={colors.success} />
              <View>
                <Text style={styles.gpsText}>Captured</Text>
                <Text style={styles.gpsCoords}>
                  {flow.gps.lat.toFixed(5)}, {flow.gps.lng.toFixed(5)}
                </Text>
              </View>
            </>
          )}
          {flow.gpsStatus === 'denied' && (
            <>
              <Ionicons name="warning-outline" size={20} color={colors.danger} />
              <View style={{ flex: 1 }}>
                <Text style={[styles.gpsText, { color: colors.danger }]}>Permission denied</Text>
                <Pressable onPress={captureGps}>
                  <Text style={styles.retryLink}>Grant access & retry</Text>
                </Pressable>
              </View>
            </>
          )}
          {flow.gpsStatus === 'error' && (
            <>
              <Ionicons name="warning-outline" size={20} color={colors.warning} />
              <View style={{ flex: 1 }}>
                <Text style={styles.gpsText}>Couldn't get a location</Text>
                <Pressable onPress={captureGps}>
                  <Text style={styles.retryLink}>Try again</Text>
                </Pressable>
              </View>
            </>
          )}
        </Card>

        <Text style={styles.sectionLabel}>Authorised By</Text>
        <Card style={styles.authCard}>
          <Text style={styles.authName}>{user.name}</Text>
          <Ionicons name="checkmark-circle" size={20} color={colors.success} />
        </Card>
      </View>

      <View style={styles.footer}>
        <Pressable
          disabled={!canSubmit}
          onPress={handleSubmit}
          style={[styles.submit, !canSubmit && styles.disabled]}
        >
          <Text style={styles.submitText}>
            {syncStatus === 'offline' ? 'Save Offline' : 'Dispense'}
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  body: { flex: 1, padding: spacing.lg },
  sectionLabel: { ...typography.label, textTransform: 'uppercase', marginBottom: spacing.sm, marginTop: spacing.md },
  photoPlaceholder: {
    height: 140,
    borderRadius: radii.lg,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: colors.borderStrong,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.xs,
  },
  photoPlaceholderText: { ...typography.bodyStrong, color: colors.primary },
  photoPreview: { height: 160, borderRadius: radii.lg },
  retakeBadge: {
    position: 'absolute',
    bottom: spacing.sm,
    right: spacing.sm,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: 'rgba(10,25,41,0.7)',
    borderRadius: radii.pill,
    paddingVertical: 4,
    paddingHorizontal: spacing.sm,
  },
  retakeText: { color: colors.textOnDark, fontSize: 12, fontWeight: '600' },
  gpsCard: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  gpsText: { ...typography.bodyStrong },
  gpsCoords: { ...typography.caption },
  retryLink: { ...typography.caption, color: colors.primaryDark, fontWeight: '700', marginTop: 2 },
  authCard: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  authName: { ...typography.bodyStrong },
  footer: { padding: spacing.lg, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border },
  submit: {
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  disabled: { opacity: 0.4 },
  submitText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
});
