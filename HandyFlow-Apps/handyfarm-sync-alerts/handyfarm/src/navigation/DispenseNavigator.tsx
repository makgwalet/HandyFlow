import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { SelectTankScreen } from '../screens/dispense/SelectTankScreen';
import { SelectVehicleScreen } from '../screens/dispense/SelectVehicleScreen';
import { EnterQuantityScreen } from '../screens/dispense/EnterQuantityScreen';
import { MeterReadingScreen } from '../screens/dispense/MeterReadingScreen';
import { AuthorisationScreen } from '../screens/dispense/AuthorisationScreen';
import { EvidenceScreen } from '../screens/dispense/EvidenceScreen';
import { CameraCaptureScreen } from '../screens/dispense/CameraCaptureScreen';
import { DispenseSuccessScreen } from '../screens/dispense/DispenseSuccessScreen';

const Stack = createNativeStackNavigator();

export function DispenseNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="SelectTank" component={SelectTankScreen} />
      <Stack.Screen name="SelectVehicle" component={SelectVehicleScreen} />
      <Stack.Screen name="EnterQuantity" component={EnterQuantityScreen} />
      <Stack.Screen name="MeterReading" component={MeterReadingScreen} />
      <Stack.Screen name="Authorisation" component={AuthorisationScreen} />
      <Stack.Screen name="Evidence" component={EvidenceScreen} />
      <Stack.Screen
        name="CameraCapture"
        component={CameraCaptureScreen}
        options={{ presentation: 'fullScreenModal', animation: 'slide_from_bottom' }}
      />
      <Stack.Screen
        name="Success"
        component={DispenseSuccessScreen}
        options={{ gestureEnabled: false }}
      />
    </Stack.Navigator>
  );
}
