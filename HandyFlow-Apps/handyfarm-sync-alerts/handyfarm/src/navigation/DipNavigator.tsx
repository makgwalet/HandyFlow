import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { DipSelectTankScreen } from '../screens/dip/DipSelectTankScreen';
import { PhysicalReadingScreen } from '../screens/dip/PhysicalReadingScreen';
import { DipCameraCaptureScreen } from '../screens/dip/DipCameraCaptureScreen';
import { ReconciliationScreen } from '../screens/dip/ReconciliationScreen';
import { VarianceReasonScreen } from '../screens/dip/VarianceReasonScreen';
import { DipSuccessScreen } from '../screens/dip/DipSuccessScreen';
import { VarianceDetailScreen } from '../screens/shared/VarianceDetailScreen';

const Stack = createNativeStackNavigator();

export function DipNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="DipSelectTank" component={DipSelectTankScreen} />
      <Stack.Screen name="PhysicalReading" component={PhysicalReadingScreen} />
      <Stack.Screen
        name="DipCameraCapture"
        component={DipCameraCaptureScreen}
        options={{ presentation: 'fullScreenModal', animation: 'slide_from_bottom' }}
      />
      <Stack.Screen name="Reconciliation" component={ReconciliationScreen} />
      <Stack.Screen name="VarianceReason" component={VarianceReasonScreen} />
      <Stack.Screen name="Success" component={DipSuccessScreen} options={{ gestureEnabled: false }} />
      <Stack.Screen name="VarianceDetail" component={VarianceDetailScreen} />
    </Stack.Navigator>
  );
}
