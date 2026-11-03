import React, { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '../../design-system/ThemeProvider';
import { FormTextField, PrimaryButton, ErrorBanner } from '../../components/FormPrimitives';
import { recordScoutingSchema, RecordScoutingFormValues } from '../../validation/agricultureSchemas';
import { useRecordScouting } from '../../hooks/agriculture/useCrops';
import { ProductionStackParamList } from '../../navigation/ProductionStackNavigator';
import { ApiError } from '../../api/client';
import { ObservationType, ScoutingSeverity } from '../../domain-types/agriculture';

type Props = NativeStackScreenProps<ProductionStackParamList, 'RecordScouting'>;

const today = () => new Date().toISOString().slice(0, 10);
const OBSERVATION_TYPES: ObservationType[] = ['PEST', 'DISEASE', 'WEED', 'NUTRIENT_DEFICIENCY', 'WEATHER_DAMAGE', 'GENERAL'];
const SEVERITIES: ScoutingSeverity[] = ['LOW', 'MEDIUM', 'HIGH'];

export function RecordScoutingScreen({ route, navigation }: Props) {
  const { cropCycleId, cycleName } = route.params;
  const { colors, spacing, typography, radius } = useTheme();
  const [observationType, setObservationType] = useState<ObservationType>('GENERAL');
  const [severity, setSeverity] = useState<ScoutingSeverity>('LOW');

  const { control, handleSubmit } = useForm<RecordScoutingFormValues>({
    resolver: zodResolver(recordScoutingSchema),
    defaultValues: { scoutingDate: today(), observationType: 'GENERAL', description: '' },
  });

  const mutation = useRecordScouting(cropCycleId);

  const onSubmit = handleSubmit((values) => {
    mutation.mutate({ ...values, observationType, severity }, { onSuccess: () => navigation.goBack() });
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }}>
      <ScrollView contentContainerStyle={{ padding: spacing.lg }}>
        <Text style={[typography.h1, { color: colors.textPrimary, marginBottom: spacing.xs }]}>Scout</Text>
        <Text style={[typography.body, { color: colors.textSecondary, marginBottom: spacing.sm }]}>{cycleName}</Text>
        <Text style={[typography.caption, { color: colors.textSecondary, marginBottom: spacing.lg }]}>
          GPS tagging and photo attachment aren't wired up in this screen yet — see the project's offline/camera
          architecture docs.
        </Text>

        {mutation.isError && (
          <ErrorBanner
            message={
              mutation.error instanceof ApiError
                ? mutation.error.message
                : "Couldn't save this observation. It has not been saved."
            }
          />
        )}

        <ChipRow
          label="OBSERVATION TYPE"
          options={OBSERVATION_TYPES}
          selected={observationType}
          onSelect={setObservationType}
        />
        <ChipRow label="SEVERITY" options={SEVERITIES} selected={severity} onSelect={setSeverity} />

        <FormTextField control={control} name="scoutingDate" label="Date" placeholder="YYYY-MM-DD" />
        <FormTextField control={control} name="description" label="Description" multiline />
        <FormTextField control={control} name="recommendedAction" label="Recommended action (optional)" multiline />
        <FormTextField control={control} name="followUpDate" label="Follow-up date (optional)" placeholder="YYYY-MM-DD" />
        <FormTextField control={control} name="notes" label="Notes (optional)" multiline />

        <View style={{ marginTop: spacing.md }}>
          <PrimaryButton label="Save" onPress={onSubmit} loading={mutation.isPending} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function ChipRow<T extends string>({
  label,
  options,
  selected,
  onSelect,
}: {
  label: string;
  options: T[];
  selected: T;
  onSelect: (v: T) => void;
}) {
  const { colors, spacing, radius, typography } = useTheme();
  return (
    <View style={{ marginBottom: spacing.lg }}>
      <Text style={[typography.label, { color: colors.textSecondary, marginBottom: spacing.sm }]}>{label}</Text>
      <View style={{ flexDirection: 'row', gap: spacing.sm, flexWrap: 'wrap' }}>
        {options.map((opt) => (
          <Pressable
            key={opt}
            onPress={() => onSelect(opt)}
            style={{
              paddingVertical: spacing.sm,
              paddingHorizontal: spacing.md,
              borderRadius: radius.pill,
              backgroundColor: selected === opt ? colors.accentSurface : colors.surface,
              borderWidth: 1,
              borderColor: colors.border,
            }}
          >
            <Text style={[typography.bodyStrong, { color: selected === opt ? colors.accent : colors.textSecondary }]}>
              {opt.charAt(0) + opt.slice(1).toLowerCase().replace(/_/g, ' ')}
            </Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}
