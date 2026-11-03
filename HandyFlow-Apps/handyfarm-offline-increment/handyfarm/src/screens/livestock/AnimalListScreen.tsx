import React, { useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useTheme } from '../../design-system/ThemeProvider';
import { useAnimalsForFarm, useGroupsForFarm } from '../../hooks/agriculture/useLivestock';
import { LoadingView, ErrorBanner } from '../../components/FormPrimitives';
import { ProductionStackParamList } from '../../navigation/ProductionStackNavigator';
import { Animal, AnimalGroup } from '../../domain-types/agriculture';

type Props = NativeStackScreenProps<ProductionStackParamList, 'AnimalList'>;

type Row = { kind: 'animal'; item: Animal } | { kind: 'group'; item: AnimalGroup };

export function AnimalListScreen({ route, navigation }: Props) {
  const { farmId, farmName } = route.params;
  const { colors, spacing, typography, radius } = useTheme();
  const [tab, setTab] = useState<'animals' | 'groups'>('animals');

  const animals = useAnimalsForFarm(farmId);
  const groups = useGroupsForFarm(farmId);

  const isLoading = tab === 'animals' ? animals.isLoading : groups.isLoading;
  const error = tab === 'animals' ? animals.error : groups.error;
  const rows: Row[] =
    tab === 'animals'
      ? (animals.data?.content ?? []).map((item) => ({ kind: 'animal', item }))
      : (groups.data?.content ?? []).map((item) => ({ kind: 'group', item }));

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }}>
      <View style={{ padding: spacing.lg, flex: 1 }}>
        <Text style={[typography.h1, { color: colors.textPrimary }]}>{farmName}</Text>
        <Text style={[typography.caption, { color: colors.textSecondary, marginBottom: spacing.lg }]}>Livestock</Text>

        <View style={{ flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.lg }}>
          {(['animals', 'groups'] as const).map((t) => (
            <Pressable
              key={t}
              onPress={() => setTab(t)}
              style={{
                paddingVertical: spacing.sm,
                paddingHorizontal: spacing.md,
                borderRadius: radius.pill,
                backgroundColor: tab === t ? colors.accentSurface : colors.surface,
                borderWidth: StyleSheet.hairlineWidth,
                borderColor: colors.border,
              }}
            >
              <Text style={[typography.bodyStrong, { color: tab === t ? colors.accent : colors.textSecondary }]}>
                {t === 'animals' ? 'Individual animals' : 'Groups / batches'}
              </Text>
            </Pressable>
          ))}
        </View>

        {error && <ErrorBanner message="Couldn't load livestock. Check your connection and try again." />}

        {isLoading ? (
          <LoadingView />
        ) : (
          <FlatList
            data={rows}
            keyExtractor={(row) => row.item.id}
            ListEmptyComponent={
              <Text style={[typography.body, { color: colors.textSecondary }]}>
                No {tab === 'animals' ? 'individually-tracked animals' : 'groups'} registered on this farm yet.
              </Text>
            }
            renderItem={({ item: row }) => (
              <Pressable
                onPress={() =>
                  row.kind === 'animal'
                    ? navigation.navigate('AnimalProfile', { animalId: row.item.id, tagOrBatch: row.item.tagNumber })
                    : navigation.navigate('GroupProfile', { groupId: row.item.id, tagOrBatch: row.item.batchNumber })
                }
                style={({ pressed }) => ({
                  backgroundColor: colors.surface,
                  borderColor: colors.border,
                  borderWidth: 1,
                  borderRadius: radius.md,
                  padding: spacing.md,
                  marginBottom: spacing.sm,
                  opacity: pressed ? 0.7 : 1,
                })}
              >
                {row.kind === 'animal' ? (
                  <>
                    <Text style={[typography.h3, { color: colors.textPrimary }]}>
                      {row.item.tagNumber}
                      {row.item.name ? ` — ${row.item.name}` : ''}
                    </Text>
                    <Text style={[typography.caption, { color: colors.textSecondary }]}>
                      {row.item.breed ?? 'Unknown breed'} · {row.item.sex}
                      {row.item.currentWeightKg ? ` · ${row.item.currentWeightKg} kg` : ''}
                    </Text>
                  </>
                ) : (
                  <>
                    <Text style={[typography.h3, { color: colors.textPrimary }]}>{row.item.batchNumber}</Text>
                    <Text style={[typography.caption, { color: colors.textSecondary }]}>
                      {row.item.currentCount} head
                      {row.item.averageWeightKg ? ` · avg ${row.item.averageWeightKg} kg` : ''}
                    </Text>
                  </>
                )}
              </Pressable>
            )}
          />
        )}
      </View>
    </SafeAreaView>
  );
}
