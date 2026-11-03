import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { FarmListScreen } from '../screens/livestock/FarmListScreen';
import { FarmHubScreen } from '../screens/livestock/FarmHubScreen';
import { AnimalListScreen } from '../screens/livestock/AnimalListScreen';
import { AnimalProfileScreen } from '../screens/livestock/AnimalProfileScreen';
import { GroupProfileScreen } from '../screens/livestock/GroupProfileScreen';
import { RecordWeightScreen } from '../screens/livestock/RecordWeightScreen';
import { RecordTreatmentScreen } from '../screens/livestock/RecordTreatmentScreen';
import { RecordMortalityScreen } from '../screens/livestock/RecordMortalityScreen';
import { CropCycleListScreen } from '../screens/crops/CropCycleListScreen';
import { CropCycleProfileScreen } from '../screens/crops/CropCycleProfileScreen';
import { RecordPlantingScreen } from '../screens/crops/RecordPlantingScreen';
import { RecordInputApplicationScreen } from '../screens/crops/RecordInputApplicationScreen';
import { RecordScoutingScreen } from '../screens/crops/RecordScoutingScreen';
import { RecordHarvestScreen } from '../screens/crops/RecordHarvestScreen';
import { useTheme } from '../design-system/ThemeProvider';

export type ProductionStackParamList = {
  FarmList: undefined;
  FarmHub: { farmId: string; farmName: string };

  // Livestock
  AnimalList: { farmId: string; farmName: string };
  AnimalProfile: { animalId: string; tagOrBatch: string };
  GroupProfile: { groupId: string; tagOrBatch: string };
  RecordWeight: { animalId?: string; groupId?: string; tagOrBatch: string };
  RecordTreatment: { animalId?: string; groupId?: string; tagOrBatch: string };
  RecordMortality: { animalId?: string; groupId?: string; tagOrBatch: string };

  // Crops
  CropCycleList: { farmId: string; farmName: string };
  CropCycleProfile: { cropCycleId: string; cycleName: string };
  RecordPlanting: { cropCycleId: string; cycleName: string };
  RecordInputApplication: { cropCycleId: string; cycleName: string };
  RecordScouting: { cropCycleId: string; cycleName: string };
  RecordHarvest: { cropCycleId: string; cycleName: string };
};

const Stack = createNativeStackNavigator<ProductionStackParamList>();

export function ProductionStackNavigator() {
  const { colors } = useTheme();
  return (
    <Stack.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: colors.surface },
        headerTintColor: colors.textPrimary,
        headerTitleStyle: { color: colors.textPrimary },
        contentStyle: { backgroundColor: colors.background },
      }}
    >
      <Stack.Screen name="FarmList" component={FarmListScreen} options={{ title: 'Farms' }} />
      <Stack.Screen name="FarmHub" component={FarmHubScreen} options={{ title: 'Farm' }} />

      <Stack.Screen name="AnimalList" component={AnimalListScreen} options={{ title: 'Livestock' }} />
      <Stack.Screen name="AnimalProfile" component={AnimalProfileScreen} options={{ title: 'Animal' }} />
      <Stack.Screen name="GroupProfile" component={GroupProfileScreen} options={{ title: 'Group' }} />
      <Stack.Screen
        name="RecordWeight"
        component={RecordWeightScreen}
        options={{ title: 'Record Weight', presentation: 'modal' }}
      />
      <Stack.Screen
        name="RecordTreatment"
        component={RecordTreatmentScreen}
        options={{ title: 'Record Treatment', presentation: 'modal' }}
      />
      <Stack.Screen
        name="RecordMortality"
        component={RecordMortalityScreen}
        options={{ title: 'Record Mortality', presentation: 'modal' }}
      />

      <Stack.Screen name="CropCycleList" component={CropCycleListScreen} options={{ title: 'Crops' }} />
      <Stack.Screen name="CropCycleProfile" component={CropCycleProfileScreen} options={{ title: 'Crop Cycle' }} />
      <Stack.Screen
        name="RecordPlanting"
        component={RecordPlantingScreen}
        options={{ title: 'Record Planting', presentation: 'modal' }}
      />
      <Stack.Screen
        name="RecordInputApplication"
        component={RecordInputApplicationScreen}
        options={{ title: 'Input Application', presentation: 'modal' }}
      />
      <Stack.Screen
        name="RecordScouting"
        component={RecordScoutingScreen}
        options={{ title: 'Scout', presentation: 'modal' }}
      />
      <Stack.Screen
        name="RecordHarvest"
        component={RecordHarvestScreen}
        options={{ title: 'Record Harvest', presentation: 'modal' }}
      />
    </Stack.Navigator>
  );
}
