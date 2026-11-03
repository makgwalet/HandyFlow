import React from 'react';
import { ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTheme } from '../../design-system/ThemeProvider';
import { FormTextField, PrimaryButton, ErrorBanner } from '../../components/FormPrimitives';
import { useRecordPlanting } from '../../hooks/agriculture/useCrops';
import { ProductionStackParamList } from '../../navigation/ProductionStackNavigator';
import { ApiError } from '../../api/client';

type Props = NativeStackScreenProps<ProductionStackParamList, 'RecordPlanting'>;

const today = () => new Date().toISOString().slice(0, 10);

// Mirrors RecordPlantingRequest's inspectable fields — seedInventoryItemId
// omitted deliberately, since there's no inventory picker UI yet.
const schema = z.object({
  plantingDate: z.string().min(1, 'Date is required'),
  seedQuantity: z.coerce.number().positive().optional(),
  seedSource: z.string().optional(),
});
type FormValues = z.infer<typeof schema>;

export function RecordPlantingScreen({ route, navigation }: Props) {
  const { cropCycleId, cycleName } = route.params;
  const { colors, spacing, typography } = useTheme();

  const { control, handleSubmit } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { plantingDate: today() },
  });

  const mutation = useRecordPlanting(cropCycleId);

  const onSubmit = handleSubmit((values) => {
    mutation.mutate(values, { onSuccess: () => navigation.goBack() });
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }}>
      <ScrollView contentContainerStyle={{ padding: spacing.lg }}>
        <Text style={[typography.h1, { color: colors.textPrimary, marginBottom: spacing.xs }]}>Record Planting</Text>
        <Text style={[typography.body, { color: colors.textSecondary, marginBottom: spacing.lg }]}>{cycleName}</Text>

        {mutation.isError && (
          <ErrorBanner
            message={mutation.error instanceof ApiError ? mutation.error.message : "Couldn't record planting."}
          />
        )}

        <FormTextField control={control} name="plantingDate" label="Planting date" placeholder="YYYY-MM-DD" />
        <FormTextField control={control} name="seedQuantity" label="Seed quantity (optional)" keyboardType="decimal-pad" />
        <FormTextField control={control} name="seedSource" label="Seed source (optional)" />

        <View style={{ marginTop: spacing.md }}>
          <PrimaryButton label="Record Planting" onPress={onSubmit} loading={mutation.isPending} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
