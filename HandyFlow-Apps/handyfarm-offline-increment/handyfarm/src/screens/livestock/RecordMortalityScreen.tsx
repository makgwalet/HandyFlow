import React from 'react';
import { ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '../../design-system/ThemeProvider';
import { FormTextField, PrimaryButton, ErrorBanner } from '../../components/FormPrimitives';
import { recordMortalitySchema, RecordMortalityFormValues } from '../../validation/agricultureSchemas';
import { useRecordMortality } from '../../hooks/agriculture/useLivestock';
import { ProductionStackParamList } from '../../navigation/ProductionStackNavigator';
import { ApiError } from '../../api/client';

type Props = NativeStackScreenProps<ProductionStackParamList, 'RecordMortality'>;

const today = () => new Date().toISOString().slice(0, 10);

export function RecordMortalityScreen({ route, navigation }: Props) {
  const { animalId, groupId, tagOrBatch } = route.params;
  const { colors, spacing, typography } = useTheme();
  const isGroup = !!groupId;

  const { control, handleSubmit } = useForm<RecordMortalityFormValues>({
    resolver: zodResolver(recordMortalitySchema),
    defaultValues: { mortalityDate: today(), countLost: isGroup ? undefined : 1, causeCategory: '' },
  });

  const mutation = useRecordMortality({ animalId, groupId });

  const onSubmit = handleSubmit((values) => {
    mutation.mutate(
      { ...values, estimatedValueLoss: values.estimatedValueLoss || undefined },
      { onSuccess: () => navigation.goBack() }
    );
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }}>
      <ScrollView contentContainerStyle={{ padding: spacing.lg }}>
        <Text style={[typography.h1, { color: colors.textPrimary, marginBottom: spacing.xs }]}>Record Mortality</Text>
        <Text style={[typography.body, { color: colors.textSecondary, marginBottom: spacing.lg }]}>{tagOrBatch}</Text>
        {!isGroup && (
          <Text style={[typography.caption, { color: colors.textSecondary, marginBottom: spacing.lg }]}>
            This will set the animal's status to Deceased.
          </Text>
        )}

        {mutation.isError && (
          <ErrorBanner
            message={
              mutation.error instanceof ApiError
                ? mutation.error.message
                : "Couldn't save this mortality record. It has not been saved."
            }
          />
        )}

        <FormTextField control={control} name="mortalityDate" label="Date" placeholder="YYYY-MM-DD" />
        {isGroup && (
          <FormTextField control={control} name="countLost" label="Count lost" keyboardType="numeric" />
        )}
        <FormTextField control={control} name="causeCategory" label="Cause category" placeholder="e.g. Disease, Predation, Injury" />
        <FormTextField control={control} name="causeDetail" label="Cause detail (optional)" multiline />
        <FormTextField
          control={control}
          name="estimatedValueLoss"
          label="Estimated value loss (optional)"
          keyboardType="decimal-pad"
        />
        <FormTextField control={control} name="notes" label="Notes (optional)" multiline />

        <View style={{ marginTop: spacing.md }}>
          <PrimaryButton label="Save" onPress={onSubmit} loading={mutation.isPending} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
