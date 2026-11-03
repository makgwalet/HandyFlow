import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { MainNavigator } from './MainNavigator';
import { DispenseNavigator } from './DispenseNavigator';
import { DipNavigator } from './DipNavigator';
import { DeliveryNavigator } from './DeliveryNavigator';
import { ReceiveNavigator } from './ReceiveNavigator';
import { ShiftNavigator } from './ShiftNavigator';
import { SyncCentreScreen } from '../screens/sync/SyncCentreScreen';

const Stack = createNativeStackNavigator();

export function AppNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="Tabs" component={MainNavigator} />
      <Stack.Screen
        name="Dispense"
        component={DispenseNavigator}
        options={{ presentation: 'modal' }}
      />
      <Stack.Screen name="Dip" component={DipNavigator} options={{ presentation: 'modal' }} />
      <Stack.Screen
        name="Delivery"
        component={DeliveryNavigator}
        options={{ presentation: 'modal' }}
      />
      <Stack.Screen
        name="Receive"
        component={ReceiveNavigator}
        options={{ presentation: 'modal' }}
      />
      <Stack.Screen name="Shift" component={ShiftNavigator} options={{ presentation: 'modal' }} />
      <Stack.Screen
        name="SyncCentre"
        component={SyncCentreScreen}
        options={{ presentation: 'modal' }}
      />
    </Stack.Navigator>
  );
}
