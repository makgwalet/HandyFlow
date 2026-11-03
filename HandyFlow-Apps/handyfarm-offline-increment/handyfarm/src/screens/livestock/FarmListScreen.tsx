import React from 'react';
import { FlatList, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useTheme } from '../../design-system/ThemeProvider';
import { useFarms } from '../../hooks/agriculture/useFarms';
import { LoadingView, ErrorBanner } from '../../components/FormPrimitives';
import { ProductionStackParamList } from '../../navigation/ProductionStackNavigator';
import { ApiError } from '../../api/client';

type Props = NativeStackScreenProps<ProductionStackParamList, 'FarmList'>;

export function FarmListScreen({ navigation }: Props) {
  const { colors, spacing, typography, radius } = useTheme();
  const { data, isLoading, error } = useFarms();

  if (isLoading) return <LoadingView />;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }}>
      <View style={{ padding: spacing.lg, flex: 1 }}>
        <Text style={[typography.h1, { color: colors.textPrimary, marginBottom: spacing.lg }]}>Your farms</Text>

        {error && (
          <ErrorBanner
            message={
              error instanceof ApiError
                ? `Couldn't load farms: ${error.message}`
                : "Couldn't load farms. Check your connection."
            }
          />
        )}

        <FlatList
          data={data?.content ?? []}
          keyExtractor={(farm) => farm.id}
          ListEmptyComponent={
            !error ? (
              <Text style={[typography.body, { color: colors.textSecondary }]}>
                No farms yet. Farm creation isn't wired into this screen yet — add one from the web app for now.
              </Text>
            ) : null
          }
          renderItem={({ item: farm }) => (
            <Pressable
              onPress={() => navigation.navigate('FarmHub', { farmId: farm.id, farmName: farm.name })}
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
              <Text style={[typography.h3, { color: colors.textPrimary }]}>{farm.name}</Text>
              <Text style={[typography.caption, { color: colors.textSecondary }]}>
                {farm.farmType}
                {farm.totalHectares ? ` · ${farm.totalHectares} ha` : ''}
                {farm.region ? ` · ${farm.region}` : ''}
              </Text>
            </Pressable>
          )}
        />
      </View>
    </SafeAreaView>
  );
}
