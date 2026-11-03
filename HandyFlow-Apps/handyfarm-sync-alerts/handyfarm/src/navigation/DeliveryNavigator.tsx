import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { DeliveryDetailScreen } from '../screens/delivery/DeliveryDetailScreen';
import { InTransitScreen } from '../screens/delivery/InTransitScreen';
import { ArrivalScreen } from '../screens/delivery/ArrivalScreen';
import { CompleteChecklistScreen } from '../screens/delivery/CompleteChecklistScreen';
import { ReceiverScreen } from '../screens/delivery/ReceiverScreen';
import { SignatureScreen } from '../screens/delivery/SignatureScreen';
import { DeliveryNotePhotoScreen } from '../screens/delivery/DeliveryNotePhotoScreen';
import { DeliveryCameraCaptureScreen } from '../screens/delivery/DeliveryCameraCaptureScreen';
import { DeliveryProofScreen } from '../screens/delivery/DeliveryProofScreen';
import { ReportProblemScreen } from '../screens/delivery/ReportProblemScreen';

const Stack = createNativeStackNavigator();

export function DeliveryNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="DeliveryDetail" component={DeliveryDetailScreen} />
      <Stack.Screen name="InTransit" component={InTransitScreen} />
      <Stack.Screen name="ReportProblem" component={ReportProblemScreen} />
      <Stack.Screen name="Arrival" component={ArrivalScreen} />
      <Stack.Screen name="CompleteChecklist" component={CompleteChecklistScreen} />
      <Stack.Screen name="Receiver" component={ReceiverScreen} />
      <Stack.Screen name="Signature" component={SignatureScreen} />
      <Stack.Screen name="DeliveryNotePhoto" component={DeliveryNotePhotoScreen} />
      <Stack.Screen
        name="DeliveryCameraCapture"
        component={DeliveryCameraCaptureScreen}
        options={{ presentation: 'fullScreenModal', animation: 'slide_from_bottom' }}
      />
      <Stack.Screen
        name="DeliveryProof"
        component={DeliveryProofScreen}
        options={{ gestureEnabled: false }}
      />
    </Stack.Navigator>
  );
}
