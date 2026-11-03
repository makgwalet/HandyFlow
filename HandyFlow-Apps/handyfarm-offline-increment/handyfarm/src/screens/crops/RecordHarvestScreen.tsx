import React from 'react';
import { ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '../../design-system/ThemeProvider';
import { FormTextField, PrimaryButton, ErrorBanner } from '../../components/FormPrimitives';
import { recordHarvestSchema, RecordHarvestFormValues } from '../../validation/agricultureSchemas';
import { useRecordHarvest } from '../../hooks/agriculture/useCrops';
import { ProductionStackParamList } from '../../navigation/ProductionStackNavigator';
import { ApiError } from '../../api/client';

type Props = NativeStackScreenProps<ProductionStackParamList, 'RecordHarvest'>;

const today = () => new Date().toISOString().slice(0, 10);

export function RecordHarvestScreen({ route, navigation }: Props) {
  const { cropCycleId, cycleName } = route.params;
  const { colors, spacing, typography } = useTheme();

  const { control, handleSubmit } = useForm<RecordHarvestFormValues>({
    resolver: zodResolver(recordHarvestSchema),
    defaultValues: { harvestDate: today(), unitOfMeasure: 'kg' },
  });

  const mutation = useRecordHarvest(cropCycleId);

  const onSubmit = handleSubmit((values) => {
    mutation.mutate(
      { ...values, moistureContent: values.moistureContent || undefined, laborHours: values.laborHours || undefined },
      { onSuccess: () => navigation.goBack() }
    );
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }}>
      <ScrollView contentContainerStyle={{ padding: spacing.lg }}>
        <Text style={[typography.h1, { color: colors.textPrimary, marginBottom: spacing.xs }]}>Record Harvest</Text>
        <Text style={[typography.body, { color: colors.textSecondary, marginBottom: spacing.sm }]}>{cycleName}</Text>
        <Text style={[typography.caption, { color: colors.textSecondary, marginBottom: spacing.lg }]}>
          Multiple harvest records per cycle are expected for multi-pick crops — this doesn't close the cycle.
        </Text>

        {mutation.isError && (
          <ErrorBanner
            message={
              mutation.error instanceof ApiError
                ? mutation.error.message
                : "Couldn't save this harvest. It has not been saved."
            }
          />
        )}

        <FormTextField control={control} name="harvestDate" label="Date" placeholder="YYYY-MM-DD" />
        <FormTextField control={control} name="quantityHarvested" label="Quantity harvested" keyboardType="decimal-pad" />
        <FormTextField control={control} name="unitOfMeasure" label="Unit" placeholder="kg, tonnes, crates, etc." />
        <FormTextField control={control} name="qualityGrade" label="Quality grade (optional)" />
        <FormTextField control={control} name="moistureContent" label="Moisture % (optional)" keyboardType="decimal-pad" />
        <FormTextField control={control} name="storageLocation" label="Storage location (optional)" />
        <FormTextField control={control} name="laborHours" label="Labor hours (optional)" keyboardType="decimal-pad" />
        <FormTextField control={control} name="notes" label="Notes (optional)" multiline />

        <View style={{ marginTop: spacing.md }}>
          <PrimaryButton label="Save Harvest" onPress={onSubmit} loading={mutation.isPending} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
