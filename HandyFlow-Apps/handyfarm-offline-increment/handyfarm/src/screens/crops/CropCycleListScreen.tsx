import React from 'react';
import { FlatList, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useTheme } from '../../design-system/ThemeProvider';
import { useCropCyclesForFarm } from '../../hooks/agriculture/useCrops';
import { LoadingView, ErrorBanner } from '../../components/FormPrimitives';
import { ProductionStackParamList } from '../../navigation/ProductionStackNavigator';

type Props = NativeStackScreenProps<ProductionStackParamList, 'CropCycleList'>;

const STATUS_COLOR_KEY: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'textSecondary'> = {
  PLANNED: 'textSecondary',
  PLANTED: 'info',
  GROWING: 'success',
  HARVESTING: 'warning',
  HARVESTED: 'success',
  FAILED: 'danger',
  ABANDONED: 'danger',
};

export function CropCycleListScreen({ route, navigation }: Props) {
  const { farmId, farmName } = route.params;
  const { colors, spacing, typography, radius } = useTheme();
  const { data, isLoading, error } = useCropCyclesForFarm(farmId);

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }}>
      <View style={{ padding: spacing.lg, flex: 1 }}>
        <Text style={[typography.h1, { color: colors.textPrimary }]}>{farmName}</Text>
        <Text style={[typography.caption, { color: colors.textSecondary, marginBottom: spacing.lg }]}>Crop cycles</Text>

        {error && <ErrorBanner message="Couldn't load crop cycles. Check your connection and try again." />}

        {isLoading ? (
          <LoadingView />
        ) : (
          <FlatList
            data={data?.content ?? []}
            keyExtractor={(c) => c.id}
            ListEmptyComponent={
              <Text style={[typography.body, { color: colors.textSecondary }]}>No crop cycles on this farm yet.</Text>
            }
            renderItem={({ item: cycle }) => {
              const statusColor = colors[STATUS_COLOR_KEY[cycle.status] ?? 'textSecondary'];
              return (
                <Pressable
                  onPress={() =>
                    navigation.navigate('CropCycleProfile', {
                      cropCycleId: cycle.id,
                      cycleName: cycle.cycleName ?? cycle.variety ?? 'Crop cycle',
                    })
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
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Text style={[typography.h3, { color: colors.textPrimary }]}>
                      {cycle.cycleName ?? cycle.variety ?? 'Unnamed cycle'}
                    </Text>
                    <Text style={[typography.label, { color: statusColor }]}>{cycle.status}</Text>
                  </View>
                  <Text style={[typography.caption, { color: colors.textSecondary }]}>
                    {cycle.areaPlantedHectares} ha
                    {cycle.plantingDate ? ` · planted ${cycle.plantingDate}` : ' · not yet planted'}
                  </Text>
                </Pressable>
              );
            }}
          />
        )}
      </View>
    </SafeAreaView>
  );
}
