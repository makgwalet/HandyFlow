import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { ShiftDashboardScreen } from '../screens/shift/ShiftDashboardScreen';
import { ShiftCloseScreen } from '../screens/shift/ShiftCloseScreen';
import { ShiftSummaryScreen } from '../screens/shift/ShiftSummaryScreen';
import { VarianceDetailScreen } from '../screens/shared/VarianceDetailScreen';

const Stack = createNativeStackNavigator();

export function ShiftNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="ShiftDashboard" component={ShiftDashboardScreen} />
      <Stack.Screen name="ShiftClose" component={ShiftCloseScreen} />
      <Stack.Screen name="ShiftSummary" component={ShiftSummaryScreen} options={{ gestureEnabled: false }} />
      <Stack.Screen name="VarianceDetail" component={VarianceDetailScreen} />
    </Stack.Navigator>
  );
}
