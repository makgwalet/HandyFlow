import React, { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '../../design-system/ThemeProvider';
import { FormTextField, PrimaryButton, ErrorBanner } from '../../components/FormPrimitives';
import { recordTreatmentSchema, RecordTreatmentFormValues } from '../../validation/agricultureSchemas';
import { useRecordHealthEvent } from '../../hooks/agriculture/useLivestock';
import { ProductionStackParamList } from '../../navigation/ProductionStackNavigator';
import { ApiError } from '../../api/client';
import { HealthEventType } from '../../domain-types/agriculture';

type Props = NativeStackScreenProps<ProductionStackParamList, 'RecordTreatment'>;

const today = () => new Date().toISOString().slice(0, 10);
const EVENT_TYPES: HealthEventType[] = ['TREATMENT', 'VACCINATION', 'DEWORMING'];

export function RecordTreatmentScreen({ route, navigation }: Props) {
  const { animalId, groupId, tagOrBatch } = route.params;
  const { colors, spacing, typography, radius } = useTheme();
  const [eventType, setEventType] = useState<HealthEventType>('TREATMENT');

  const { control, handleSubmit } = useForm<RecordTreatmentFormValues>({
    resolver: zodResolver(recordTreatmentSchema),
    defaultValues: { eventType: 'TREATMENT', eventDate: today(), description: '' },
  });

  const mutation = useRecordHealthEvent({ animalId, groupId });

  const onSubmit = handleSubmit((values) => {
    mutation.mutate(
      { ...values, eventType, cost: values.cost || undefined, withdrawalPeriodDays: values.withdrawalPeriodDays || undefined },
      { onSuccess: () => navigation.goBack() }
    );
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }}>
      <ScrollView contentContainerStyle={{ padding: spacing.lg }}>
        <Text style={[typography.h1, { color: colors.textPrimary, marginBottom: spacing.xs }]}>
          Record Treatment
        </Text>
        <Text style={[typography.body, { color: colors.textSecondary, marginBottom: spacing.lg }]}>{tagOrBatch}</Text>

        {mutation.isError && (
          <ErrorBanner
            message={
              mutation.error instanceof ApiError
                ? mutation.error.message
                : "Couldn't save this health event. It has not been saved."
            }
          />
        )}

        <Text style={[typography.label, { color: colors.textSecondary, marginBottom: spacing.sm }]}>TYPE</Text>
        <View style={{ flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.lg }}>
          {EVENT_TYPES.map((t) => (
            <Pressable
              key={t}
              onPress={() => setEventType(t)}
              style={{
                paddingVertical: spacing.sm,
                paddingHorizontal: spacing.md,
                borderRadius: radius.pill,
                backgroundColor: eventType === t ? colors.accentSurface : colors.surface,
                borderWidth: 1,
                borderColor: colors.border,
              }}
            >
              <Text style={[typography.bodyStrong, { color: eventType === t ? colors.accent : colors.textSecondary }]}>
                {t.charAt(0) + t.slice(1).toLowerCase()}
              </Text>
            </Pressable>
          ))}
        </View>

        <FormTextField control={control} name="eventDate" label="Date" placeholder="YYYY-MM-DD" />
        <FormTextField control={control} name="description" label="Description" placeholder="What was done" multiline />
        <FormTextField control={control} name="productUsed" label="Product used (optional)" />
        <FormTextField control={control} name="dosage" label="Dosage (optional)" />
        <FormTextField control={control} name="veterinarian" label="Veterinarian (optional)" />
        <FormTextField
          control={control}
          name="withdrawalPeriodDays"
          label="Withdrawal period, days (optional)"
          keyboardType="numeric"
        />
        <FormTextField control={control} name="nextDueDate" label="Next due date (optional)" placeholder="YYYY-MM-DD" />
        <FormTextField control={control} name="notes" label="Notes (optional)" multiline />

        <View style={{ marginTop: spacing.md }}>
          <PrimaryButton label="Save" onPress={onSubmit} loading={mutation.isPending} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
