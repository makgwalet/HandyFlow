import React from 'react';
import { View, Text, Pressable, Image, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useAuthStore } from '../../store/authStore';
import { useDeliveryStore } from '../../store/deliveryStore';
import { useTransactionQueueStore } from '../../store/transactionQueueStore';

export function DeliveryNotePhotoScreen({ route, navigation }: any) {
  const { deliveryId } = route.params;
  const user = useAuthStore((s) => s.user);
  const delivery = useDeliveryStore((s) => s.getById(deliveryId));
  const completeDelivery = useDeliveryStore((s) => s.completeDelivery);
  const enqueue = useTransactionQueueStore((s) => s.enqueue);

  if (!delivery || !user) return null;

  const handleFinish = () => {
    if (!delivery.deliveryNotePhotoUri) return;

    const completed = completeDelivery(delivery.id);

    enqueue('DELIVERY_COMPLETE', user.id, {
      deliveryId: delivery.id,
      meterStart: delivery.meterStart,
      meterEnd: delivery.meterEnd,
      quantityDeliveredLitres: delivery.quantityDeliveredLitres,
      receiverName: delivery.receiverName,
      receiverIdNumber: delivery.receiverIdNumber,
      signedOnBehalf: delivery.signedOnBehalf,
      hasSignature: (delivery.signatureStrokes?.length ?? 0) > 0,
      deliveryNotePhotoUri: delivery.deliveryNotePhotoUri,
      arrivalGps: delivery.arrivalGps,
      completedAt: completed.completedAt,
    });

    navigation.replace('DeliveryProof', { deliveryId: delivery.id });
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>DELIVERY NOTE PHOTO</Text>
        <Text style={styles.subtitle}>Capture a photo of the signed delivery note</Text>
      </View>

      <View style={styles.body}>
        {delivery.deliveryNotePhotoUri ? (
          <Pressable
            onPress={() => navigation.navigate('DeliveryCameraCapture', { deliveryId })}
          >
            <Image source={{ uri: delivery.deliveryNotePhotoUri }} style={styles.photoPreview} />
          </Pressable>
        ) : (
          <Pressable
            onPress={() => navigation.navigate('DeliveryCameraCapture', { deliveryId })}
            style={styles.photoPlaceholder}
          >
            <Ionicons name="camera-outline" size={28} color={colors.primary} />
            <Text style={styles.photoPlaceholderText}>Take Photo</Text>
          </Pressable>
        )}
      </View>

      <View style={styles.footer}>
        <Pressable
          disabled={!delivery.deliveryNotePhotoUri}
          onPress={handleFinish}
          style={[styles.finishButton, !delivery.deliveryNotePhotoUri && styles.disabled]}
        >
          <Text style={styles.finishButtonText}>Complete Delivery</Text>
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
  subtitle: { ...typography.caption, marginTop: 2 },
  body: { flex: 1, padding: spacing.lg, justifyContent: 'center' },
  photoPlaceholder: {
    height: 200,
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
  photoPreview: { height: 220, borderRadius: radii.lg },
  footer: { padding: spacing.lg, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border },
  finishButton: {
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  disabled: { opacity: 0.4 },
  finishButtonText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
});
