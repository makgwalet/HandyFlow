import React from 'react';
import { FlatList, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useTheme } from '../../design-system/ThemeProvider';
import { useAnimal, useAnimalWeightHistory } from '../../hooks/agriculture/useLivestock';
import { LoadingView, ErrorBanner } from '../../components/FormPrimitives';
import { ProductionStackParamList } from '../../navigation/ProductionStackNavigator';

type Props = NativeStackScreenProps<ProductionStackParamList, 'AnimalProfile'>;

export function AnimalProfileScreen({ route, navigation }: Props) {
  const { animalId, tagOrBatch } = route.params;
  const { colors, spacing, typography, radius } = useTheme();
  const animal = useAnimal(animalId);
  const weightHistory = useAnimalWeightHistory(animalId);

  if (animal.isLoading) return <LoadingView />;
  if (animal.error || !animal.data) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.background, padding: spacing.lg }}>
        <ErrorBanner message="Couldn't load this animal. Check your connection and try again." />
      </SafeAreaView>
    );
  }

  const a = animal.data;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }}>
      <FlatList
        data={weightHistory.data?.content ?? []}
        keyExtractor={(w) => w.id}
        contentContainerStyle={{ padding: spacing.lg, paddingBottom: spacing.xxxl }}
        ListHeaderComponent={
          <View>
            <Text style={[typography.h1, { color: colors.textPrimary }]}>{tagOrBatch}</Text>
            <Text style={[typography.body, { color: colors.textSecondary, marginBottom: spacing.lg }]}>
              {a.breed ?? 'Unknown breed'} · {a.sex} · {a.status}
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
                <Text style={[typography.caption, { color: colors.textSecondary }]}>Current weight</Text>
                <Text style={[typography.h2, { color: colors.textPrimary }]}>
                  {a.currentWeightKg ? `${a.currentWeightKg} kg` : '—'}
                </Text>
              </View>
              <View style={{ flex: 1 }}>
                <Text style={[typography.caption, { color: colors.textSecondary }]}>Acquired</Text>
                <Text style={[typography.h2, { color: colors.textPrimary }]}>{a.acquisitionDate}</Text>
              </View>
            </View>

            <View style={{ flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.xl, flexWrap: 'wrap' }}>
              <QuickAction
                label="Record Weight"
                onPress={() => navigation.navigate('RecordWeight', { animalId, tagOrBatch })}
              />
              <QuickAction
                label="Treatment"
                onPress={() => navigation.navigate('RecordTreatment', { animalId, tagOrBatch })}
              />
              <QuickAction
                label="Mortality"
                onPress={() => navigation.navigate('RecordMortality', { animalId, tagOrBatch })}
              />
            </View>

            <Text style={[typography.label, { color: colors.textSecondary, marginBottom: spacing.sm }]}>
              WEIGHT HISTORY
            </Text>
            {weightHistory.isLoading && <LoadingView />}
          </View>
        }
        ListEmptyComponent={
          !weightHistory.isLoading ? (
            <Text style={[typography.body, { color: colors.textSecondary }]}>No weight records yet.</Text>
          ) : null
        }
        renderItem={({ item: w }) => (
          <View
            style={{
              flexDirection: 'row',
              justifyContent: 'space-between',
              paddingVertical: spacing.sm,
              borderBottomWidth: 1,
              borderBottomColor: colors.border,
            }}
          >
            <Text style={[typography.body, { color: colors.textPrimary }]}>{w.recordedDate}</Text>
            <Text style={[typography.bodyStrong, { color: colors.textPrimary }]}>{w.weightKg} kg</Text>
          </View>
        )}
      />
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
