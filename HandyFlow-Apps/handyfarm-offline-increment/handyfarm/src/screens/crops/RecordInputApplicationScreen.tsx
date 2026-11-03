import React, { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '../../design-system/ThemeProvider';
import { FormTextField, PrimaryButton, ErrorBanner } from '../../components/FormPrimitives';
import { recordInputApplicationSchema, RecordInputApplicationFormValues } from '../../validation/agricultureSchemas';
import { useRecordInputApplication } from '../../hooks/agriculture/useCrops';
import { ProductionStackParamList } from '../../navigation/ProductionStackNavigator';
import { ApiError } from '../../api/client';
import { InputType } from '../../domain-types/agriculture';

type Props = NativeStackScreenProps<ProductionStackParamList, 'RecordInputApplication'>;

const today = () => new Date().toISOString().slice(0, 10);
const INPUT_TYPES: InputType[] = ['FERTILISER', 'PESTICIDE', 'HERBICIDE', 'FUNGICIDE', 'IRRIGATION', 'OTHER'];

export function RecordInputApplicationScreen({ route, navigation }: Props) {
  const { cropCycleId, cycleName } = route.params;
  const { colors, spacing, typography, radius } = useTheme();
  const [inputType, setInputType] = useState<InputType>('FERTILISER');

  const { control, handleSubmit } = useForm<RecordInputApplicationFormValues>({
    resolver: zodResolver(recordInputApplicationSchema),
    defaultValues: { applicationDate: today(), inputType: 'FERTILISER', unitOfMeasure: 'kg' },
  });

  const mutation = useRecordInputApplication(cropCycleId);

  const onSubmit = handleSubmit((values) => {
    mutation.mutate(
      { ...values, inputType, laborHours: values.laborHours || undefined, cost: values.cost || undefined },
      { onSuccess: () => navigation.goBack() }
    );
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }}>
      <ScrollView contentContainerStyle={{ padding: spacing.lg }}>
        <Text style={[typography.h1, { color: colors.textPrimary, marginBottom: spacing.xs }]}>Input Application</Text>
        <Text style={[typography.body, { color: colors.textSecondary, marginBottom: spacing.lg }]}>{cycleName}</Text>

        {mutation.isError && (
          <ErrorBanner
            message={
              mutation.error instanceof ApiError
                ? mutation.error.message
                : "Couldn't save this application. It has not been saved."
            }
          />
        )}

        <Text style={[typography.label, { color: colors.textSecondary, marginBottom: spacing.sm }]}>TYPE</Text>
        <View style={{ flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.lg, flexWrap: 'wrap' }}>
          {INPUT_TYPES.map((t) => (
            <Pressable
              key={t}
              onPress={() => setInputType(t)}
              style={{
                paddingVertical: spacing.sm,
                paddingHorizontal: spacing.md,
                borderRadius: radius.pill,
                backgroundColor: inputType === t ? colors.accentSurface : colors.surface,
                borderWidth: 1,
                borderColor: colors.border,
              }}
            >
              <Text style={[typography.bodyStrong, { color: inputType === t ? colors.accent : colors.textSecondary }]}>
                {t.charAt(0) + t.slice(1).toLowerCase()}
              </Text>
            </Pressable>
          ))}
        </View>

        <FormTextField control={control} name="applicationDate" label="Date" placeholder="YYYY-MM-DD" />
        <FormTextField control={control} name="productUsed" label="Product used (optional)" />
        <FormTextField control={control} name="quantityApplied" label="Quantity applied" keyboardType="decimal-pad" />
        <FormTextField control={control} name="unitOfMeasure" label="Unit" placeholder="kg, L, etc." />
        <FormTextField control={control} name="applicationMethod" label="Method (optional)" />
        <FormTextField control={control} name="laborHours" label="Labor hours (optional)" keyboardType="decimal-pad" />
        <FormTextField control={control} name="cost" label="Cost (optional)" keyboardType="decimal-pad" />
        <FormTextField control={control} name="weatherConditions" label="Weather conditions (optional)" />
        <FormTextField control={control} name="notes" label="Notes (optional)" multiline />

        <View style={{ marginTop: spacing.md }}>
          <PrimaryButton label="Save" onPress={onSubmit} loading={mutation.isPending} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
