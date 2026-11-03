import React from 'react';
import { Alert, Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useTheme } from '../../design-system/ThemeProvider';
import {
  useCompleteHarvest,
  useCropCycle,
  useHarvestRecords,
  useInputApplications,
  useMarkGrowing,
  useScoutingRecords,
  useStartHarvest,
} from '../../hooks/agriculture/useCrops';
import { LoadingView, ErrorBanner, PrimaryButton } from '../../components/FormPrimitives';
import { ProductionStackParamList } from '../../navigation/ProductionStackNavigator';
import { ApiError } from '../../api/client';
import { CropCycleStatus } from '../../domain-types/agriculture';

type Props = NativeStackScreenProps<ProductionStackParamList, 'CropCycleProfile'>;

// What the next transition is called from each status, and which hook drives it.
// PLANNED has no entry here — it routes to the RecordPlanting screen instead,
// since record-planting needs a date, unlike the other no-body transitions.
const NEXT_TRANSITION_LABEL: Partial<Record<CropCycleStatus, string>> = {
  PLANTED: 'Mark Growing',
  GROWING: 'Start Harvest',
  HARVESTING: 'Complete Harvest',
};

export function CropCycleProfileScreen({ route, navigation }: Props) {
  const { cropCycleId, cycleName } = route.params;
  const { colors, spacing, typography, radius } = useTheme();

  const cycle = useCropCycle(cropCycleId);
  const inputApplications = useInputApplications(cropCycleId);
  const scoutingRecords = useScoutingRecords(cropCycleId);
  const harvestRecords = useHarvestRecords(cropCycleId);
  const markGrowing = useMarkGrowing(cropCycleId);
  const startHarvest = useStartHarvest(cropCycleId);
  const completeHarvest = useCompleteHarvest(cropCycleId);

  if (cycle.isLoading) return <LoadingView />;
  if (cycle.error || !cycle.data) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.background, padding: spacing.lg }}>
        <ErrorBanner message="Couldn't load this crop cycle. Check your connection and try again." />
      </SafeAreaView>
    );
  }

  const c = cycle.data;
  const openScoutingCount = (scoutingRecords.data?.content ?? []).filter((s) => s.status === 'OPEN').length;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }}>
      <ScrollView contentContainerStyle={{ padding: spacing.lg, paddingBottom: spacing.xxxl }}>
        <Text style={[typography.h1, { color: colors.textPrimary }]}>{cycleName}</Text>
        <Text style={[typography.body, { color: colors.textSecondary, marginBottom: spacing.lg }]}>
          {c.status} · {c.areaPlantedHectares} ha
        </Text>

        <View
          style={{
            flexDirection: 'row',
            backgroundColor: colors.surface,
            borderColor: colors.border,
            borderWidth: 1,
            borderRadius: radius.md,
            padding: spacing.md,
            marginBottom: spacing.xl,
          }}
        >
          <View style={{ flex: 1 }}>
            <Text style={[typography.caption, { color: colors.textSecondary }]}>Planted</Text>
            <Text style={[typography.h3, { color: colors.textPrimary }]}>{c.plantingDate ?? '—'}</Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={[typography.caption, { color: colors.textSecondary }]}>Expected harvest</Text>
            <Text style={[typography.h3, { color: colors.textPrimary }]}>{c.expectedHarvestDate ?? '—'}</Text>
          </View>
        </View>

        {openScoutingCount > 0 && (
          <View
            style={{
              backgroundColor: colors.warning + '22',
              borderColor: colors.warning,
              borderWidth: 1,
              borderRadius: radius.md,
              padding: spacing.md,
              marginBottom: spacing.xl,
            }}
          >
            <Text style={[typography.bodyStrong, { color: colors.textPrimary }]}>
              {openScoutingCount} open scouting {openScoutingCount === 1 ? 'finding' : 'findings'}
            </Text>
          </View>
        )}

        {(markGrowing.isError || startHarvest.isError || completeHarvest.isError) && (
          <ErrorBanner
            message={
              [markGrowing.error, startHarvest.error, completeHarvest.error].find((e) => e instanceof ApiError)
                ?.message ?? "Couldn't update this cycle's status."
            }
          />
        )}

        {c.status === 'PLANNED' && (
          <View style={{ marginBottom: spacing.xl }}>
            <PrimaryButton
              label="Record Planting"
              onPress={() => navigation.navigate('RecordPlanting', { cropCycleId, cycleName })}
            />
          </View>
        )}
        {NEXT_TRANSITION_LABEL[c.status] && (
          <View style={{ marginBottom: spacing.xl }}>
            <PrimaryButton
              label={NEXT_TRANSITION_LABEL[c.status] as string}
              loading={markGrowing.isPending || startHarvest.isPending || completeHarvest.isPending}
              onPress={() => {
                Alert.alert(
                  NEXT_TRANSITION_LABEL[c.status] as string,
                  `Move this cycle from ${c.status} to the next stage?`,
                  [
                    { text: 'Cancel', style: 'cancel' },
                    {
                      text: 'Confirm',
                      onPress: () => {
                        if (c.status === 'PLANTED') markGrowing.mutate();
                        else if (c.status === 'GROWING') startHarvest.mutate();
                        else if (c.status === 'HARVESTING') completeHarvest.mutate();
                      },
                    },
                  ]
                );
              }}
            />
          </View>
        )}

        <View style={{ flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.xl, flexWrap: 'wrap' }}>
          <QuickAction
            label="Scout"
            onPress={() => navigation.navigate('RecordScouting', { cropCycleId, cycleName })}
          />
          <QuickAction
            label="Input Application"
            onPress={() => navigation.navigate('RecordInputApplication', { cropCycleId, cycleName })}
          />
          <QuickAction
            label="Harvest"
            onPress={() => navigation.navigate('RecordHarvest', { cropCycleId, cycleName })}
          />
        </View>

        <HistorySection title="HARVEST HISTORY" loading={harvestRecords.isLoading} empty="No harvests recorded yet.">
          {(harvestRecords.data?.content ?? []).map((h) => (
            <HistoryRow
              key={h.id}
              left={h.harvestDate}
              right={`${h.quantityHarvested} ${h.unitOfMeasure}${h.qualityGrade ? ` · ${h.qualityGrade}` : ''}`}
            />
          ))}
        </HistorySection>

        <HistorySection title="INPUT APPLICATIONS" loading={inputApplications.isLoading} empty="No input applications recorded yet.">
          {(inputApplications.data?.content ?? []).map((a) => (
            <HistoryRow
              key={a.id}
              left={`${a.applicationDate} · ${a.inputType}`}
              right={`${a.quantityApplied} ${a.unitOfMeasure}`}
            />
          ))}
        </HistorySection>

        <HistorySection title="SCOUTING HISTORY" loading={scoutingRecords.isLoading} empty="No scouting records yet.">
          {(scoutingRecords.data?.content ?? []).map((s) => (
            <HistoryRow
              key={s.id}
              left={`${s.scoutingDate} · ${s.observationType}`}
              right={`${s.severity}${s.status === 'OPEN' ? ' · OPEN' : ''}`}
            />
          ))}
        </HistorySection>
      </ScrollView>
    </SafeAreaView>
  );
}

function QuickAction({ label, onPress }: { label: string; onPress: () => void }) {
  const { colors, spacing, radius, typography } = useTheme();
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => ({
        backgroundColor: colors.accentSurface,
        borderRadius: radius.pill,
        paddingVertical: spacing.sm,
        paddingHorizontal: spacing.md,
        opacity: pressed ? 0.7 : 1,
      })}
    >
      <Text style={[typography.bodyStrong, { color: colors.accent }]}>{label}</Text>
    </Pressable>
  );
}

function HistorySection({
  title,
  loading,
  empty,
  children,
}: {
  title: string;
  loading: boolean;
  empty: string;
  children: React.ReactNode;
}) {
  const { colors, spacing, typography } = useTheme();
  const hasChildren = React.Children.count(children) > 0;
  return (
    <View style={{ marginBottom: spacing.xl }}>
      <Text style={[typography.label, { color: colors.textSecondary, marginBottom: spacing.sm }]}>{title}</Text>
      {loading ? (
        <LoadingView />
      ) : hasChildren ? (
        children
      ) : (
        <Text style={[typography.body, { color: colors.textSecondary }]}>{empty}</Text>
      )}
    </View>
  );
}

function HistoryRow({ left, right }: { left: string; right: string }) {
  const { colors, spacing, typography } = useTheme();
  return (
    <View
      style={{
        flexDirection: 'row',
        justifyContent: 'space-between',
        paddingVertical: spacing.sm,
        borderBottomWidth: 1,
        borderBottomColor: colors.border,
      }}
    >
      <Text style={[typography.body, { color: colors.textPrimary }]}>{left}</Text>
      <Text style={[typography.bodyStrong, { color: colors.textPrimary }]}>{right}</Text>
    </View>
  );
}
