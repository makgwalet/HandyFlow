import React from 'react';
import { FlatList, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useTheme } from '../../design-system/ThemeProvider';
import { useGroup, useGroupWeightHistory } from '../../hooks/agriculture/useLivestock';
import { LoadingView, ErrorBanner } from '../../components/FormPrimitives';
import { ProductionStackParamList } from '../../navigation/ProductionStackNavigator';

type Props = NativeStackScreenProps<ProductionStackParamList, 'GroupProfile'>;

export function GroupProfileScreen({ route, navigation }: Props) {
  const { groupId, tagOrBatch } = route.params;
  const { colors, spacing, typography, radius } = useTheme();
  const group = useGroup(groupId);
  const weightHistory = useGroupWeightHistory(groupId);

  if (group.isLoading) return <LoadingView />;
  if (group.error || !group.data) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.background, padding: spacing.lg }}>
        <ErrorBanner message="Couldn't load this group. Check your connection and try again." />
      </SafeAreaView>
    );
  }

  const g = group.data;

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
              {g.breed ?? 'Unknown breed'} · {g.status}
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
                <Text style={[typography.caption, { color: colors.textSecondary }]}>Current count</Text>
                <Text style={[typography.h2, { color: colors.textPrimary }]}>{g.currentCount}</Text>
              </View>
              <View style={{ flex: 1 }}>
                <Text style={[typography.caption, { color: colors.textSecondary }]}>Avg weight</Text>
                <Text style={[typography.h2, { color: colors.textPrimary }]}>
                  {g.averageWeightKg ? `${g.averageWeightKg} kg` : '—'}
                </Text>
              </View>
            </View>

            <View style={{ flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.xl, flexWrap: 'wrap' }}>
              <QuickAction
                label="Record Weight"
                onPress={() => navigation.navigate('RecordWeight', { groupId, tagOrBatch })}
              />
              <QuickAction
                label="Treatment"
                onPress={() => navigation.navigate('RecordTreatment', { groupId, tagOrBatch })}
              />
              <QuickAction
                label="Mortality"
                onPress={() => navigation.navigate('RecordMortality', { groupId, tagOrBatch })}
              />
            </View>

            <Text style={[typography.label, { color: colors.textSecondary, marginBottom: spacing.sm }]}>
              WEIGHT HISTORY (SAMPLES)
            </Text>
            {weightHistory.isLoading && <LoadingView />}
          </View>
        }
        ListEmptyComponent={
          !weightHistory.isLoading ? (
            <Text style={[typography.body, { color: colors.textSecondary }]}>No weight samples yet.</Text>
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
            <Text style={[typography.body, { color: colors.textPrimary }]}>
              {w.recordedDate}
              {w.sampleSize ? ` · n=${w.sampleSize}` : ''}
            </Text>
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
