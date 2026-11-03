import React from 'react';
import { ScrollView, Text, View, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '../../design-system/ThemeProvider';
import { FormTextField, PrimaryButton, ErrorBanner } from '../../components/FormPrimitives';
import { recordWeightSchema, RecordWeightFormValues } from '../../validation/agricultureSchemas';
import { useRecordWeight } from '../../hooks/agriculture/useLivestock';
import { ProductionStackParamList } from '../../navigation/ProductionStackNavigator';
import { ApiError } from '../../api/client';

type Props = NativeStackScreenProps<ProductionStackParamList, 'RecordWeight'>;

const today = () => new Date().toISOString().slice(0, 10);

export function RecordWeightScreen({ route, navigation }: Props) {
  const { animalId, groupId, tagOrBatch } = route.params;
  const { colors, spacing, typography } = useTheme();
  const isGroup = !!groupId;

  const { control, handleSubmit, formState } = useForm<RecordWeightFormValues>({
    resolver: zodResolver(recordWeightSchema),
    defaultValues: { recordedDate: today(), weightKg: undefined, sampleSize: undefined, notes: '' },
  });

  const mutation = useRecordWeight({ animalId, groupId });

  const onSubmit = handleSubmit((values) => {
    mutation.mutate(
      {
        recordedDate: values.recordedDate,
        weightKg: values.weightKg,
        sampleSize: isGroup ? values.sampleSize : undefined,
        notes: values.notes || undefined,
      },
      {
        onSuccess: (result) => {
          if (result.status === 'queued') {
            Alert.alert('Saved on device', 'You appear to be offline. This weight will sync automatically once connection is available.', [
              { text: 'OK', onPress: () => navigation.goBack() },
            ]);
          } else {
            navigation.goBack();
          }
        },
      }
    );
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }}>
      <ScrollView contentContainerStyle={{ padding: spacing.lg }}>
        <Text style={[typography.h1, { color: colors.textPrimary, marginBottom: spacing.xs }]}>Record Weight</Text>
        <Text style={[typography.body, { color: colors.textSecondary, marginBottom: spacing.xl }]}>{tagOrBatch}</Text>

        {mutation.isError && (
          <ErrorBanner
            message={
              mutation.error instanceof ApiError
                ? mutation.error.message
                : "Couldn't save this weight record. It has not been saved."
            }
          />
        )}

        <FormTextField control={control} name="recordedDate" label="Date" placeholder="YYYY-MM-DD" />
        <FormTextField
          control={control}
          name="weightKg"
          label={isGroup ? 'Average weight (kg)' : 'Weight (kg)'}
          placeholder="0.0"
          keyboardType="decimal-pad"
        />
        {isGroup && (
          <FormTextField
            control={control}
            name="sampleSize"
            label="Sample size"
            placeholder="e.g. 50 birds weighed"
            keyboardType="numeric"
          />
        )}
        <FormTextField control={control} name="notes" label="Notes (optional)" multiline />

        <View style={{ marginTop: spacing.md }}>
          <PrimaryButton label="Save Weight" onPress={onSubmit} loading={mutation.isPending} disabled={!formState.isValid && formState.isSubmitted} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
