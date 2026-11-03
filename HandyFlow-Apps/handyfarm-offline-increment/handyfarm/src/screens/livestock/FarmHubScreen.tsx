import React from 'react';
import { Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useTheme } from '../../design-system/ThemeProvider';
import { ProductionStackParamList } from '../../navigation/ProductionStackNavigator';

type Props = NativeStackScreenProps<ProductionStackParamList, 'FarmHub'>;

export function FarmHubScreen({ route, navigation }: Props) {
  const { farmId, farmName } = route.params;
  const { colors, spacing, typography, radius } = useTheme();

  function HubCard({ icon, title, subtitle, onPress }: { icon: string; title: string; subtitle: string; onPress: () => void }) {
    return (
      <Pressable
        onPress={onPress}
        style={({ pressed }) => ({
          backgroundColor: colors.surface,
          borderColor: colors.border,
          borderWidth: 1,
          borderRadius: radius.md,
          padding: spacing.lg,
          marginBottom: spacing.md,
          opacity: pressed ? 0.7 : 1,
        })}
      >
        <Text style={{ fontSize: 28, marginBottom: spacing.sm }}>{icon}</Text>
        <Text style={[typography.h2, { color: colors.textPrimary }]}>{title}</Text>
        <Text style={[typography.caption, { color: colors.textSecondary }]}>{subtitle}</Text>
      </Pressable>
    );
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }}>
      <View style={{ padding: spacing.lg, flex: 1 }}>
        <Text style={[typography.h1, { color: colors.textPrimary, marginBottom: spacing.xl }]}>{farmName}</Text>

        <HubCard
          icon="🐄"
          title="Livestock"
          subtitle="Individual animals and groups"
          onPress={() => navigation.navigate('AnimalList', { farmId, farmName })}
        />
        <HubCard
          icon="🌾"
          title="Crops"
          subtitle="Crop cycles, scouting, harvest"
          onPress={() => navigation.navigate('CropCycleList', { farmId, farmName })}
        />
      </View>
    </SafeAreaView>
  );
}
