import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { ReceiveSelectTankScreen } from '../screens/receive/ReceiveSelectTankScreen';
import { SupplierDetailsScreen } from '../screens/receive/SupplierDetailsScreen';
import { WaybillQuantityScreen } from '../screens/receive/WaybillQuantityScreen';
import { DipReadingsScreen } from '../screens/receive/DipReadingsScreen';
import { ReceiveCameraCaptureScreen } from '../screens/receive/ReceiveCameraCaptureScreen';
import { ReceiveReconciliationScreen } from '../screens/receive/ReceiveReconciliationScreen';
import { ReceiveVarianceReasonScreen } from '../screens/receive/ReceiveVarianceReasonScreen';
import { ReceiveSuccessScreen } from '../screens/receive/ReceiveSuccessScreen';
import { VarianceDetailScreen } from '../screens/shared/VarianceDetailScreen';

const Stack = createNativeStackNavigator();

export function ReceiveNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="ReceiveSelectTank" component={ReceiveSelectTankScreen} />
      <Stack.Screen name="SupplierDetails" component={SupplierDetailsScreen} />
      <Stack.Screen name="WaybillQuantity" component={WaybillQuantityScreen} />
      <Stack.Screen name="DipReadings" component={DipReadingsScreen} />
      <Stack.Screen
        name="ReceiveCameraCapture"
        component={ReceiveCameraCaptureScreen}
        options={{ presentation: 'fullScreenModal', animation: 'slide_from_bottom' }}
      />
      <Stack.Screen name="ReceiveReconciliation" component={ReceiveReconciliationScreen} />
      <Stack.Screen name="ReceiveVarianceReason" component={ReceiveVarianceReasonScreen} />
      <Stack.Screen name="Success" component={ReceiveSuccessScreen} options={{ gestureEnabled: false }} />
      <Stack.Screen name="VarianceDetail" component={VarianceDetailScreen} />
    </Stack.Navigator>
  );
}
