import React from "react";
import { ActivityIndicator, View } from "react-native";
import { StatusBar } from "expo-status-bar";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { DarkTheme, NavigationContainer } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { ArrivalCheckScreen, SignatureScreen, CompleteScreen, FailedAttemptScreen, LoadDetailScreen, MyLoadsScreen } from "@driver/index";
import { ReceiveFuelScreen, TankDetailScreen, TanksScreen } from "@depot/index";
import { OverviewScreen } from "@overview/index";
import { StayDetailScreen, StayRequestsScreen, YardScreen } from "@truckstop/index";
import { AdjustmentScreen, CloseShiftScreen, OpenShiftScreen, ShiftDetailScreen, ShiftsScreen } from "@forecourt/index";
import { AuthProvider, useAuth } from "@/auth/AuthContext";
import { PushBridge, navigationRef } from "@/PushBridge";
import { OutboxProvider } from "@/offline/OutboxProvider";
import { HomeScreen } from "@/screens/HomeScreen";
import { LoginScreen } from "@/screens/LoginScreen";
import { ModePlannedScreen } from "@/screens/ModePlannedScreen";
import { AccessScreen } from "@/screens/AccessScreen";
import { SyncCentreScreen } from "@/screens/SyncCentreScreen";
import type { RootStackParamList } from "@/navigation/types";
import { colors } from "@/ui/theme";

const queryClient = new QueryClient();
const Stack = createNativeStackNavigator<RootStackParamList>();
const navTheme = { ...DarkTheme, colors: { ...DarkTheme.colors, background: colors.bg, card: colors.surface, text: colors.text, border: colors.border } };

function SignedIn() {
  return (
    <OutboxProvider>
      <NavigationContainer theme={navTheme} ref={navigationRef}>
        <PushBridge />
        <Stack.Navigator>
          <Stack.Screen name="Home" component={HomeScreen} options={{ title: "HandyFlow Fuel", headerBackVisible: false }} />
          <Stack.Screen name="ModePlanned" component={ModePlannedScreen} options={{ title: "Coming soon" }} />
          <Stack.Screen name="Access" component={AccessScreen} options={{ title: "My access" }} />
          <Stack.Screen name="SyncCentre" component={SyncCentreScreen} options={{ title: "Sync centre" }} />
          <Stack.Screen name="DriverLoads" component={MyLoadsScreen} options={{ title: "My loads" }} />
          <Stack.Screen name="LoadDetail" component={LoadDetailScreen} options={{ title: "Load" }} />
          <Stack.Screen name="CompleteLoad" component={CompleteScreen} options={{ title: "Record delivery" }} />
          <Stack.Screen name="ArrivalCheck" component={ArrivalCheckScreen} options={{ title: "Arrival check" }} />
          <Stack.Screen name="Signature" component={SignatureScreen} options={{ title: "Signature" }} />
          <Stack.Screen name="FailedAttempt" component={FailedAttemptScreen} options={{ title: "Could not deliver" }} />
          <Stack.Screen name="DepotTanks" component={TanksScreen} options={{ title: "Tanks" }} />
          <Stack.Screen name="ReceiveFuel" component={ReceiveFuelScreen} options={{ title: "Receive fuel" }} />
          <Stack.Screen name="ForecourtShifts" component={ShiftsScreen} options={{ title: "Shifts" }} />
          <Stack.Screen name="OpenShift" component={OpenShiftScreen} options={{ title: "Open a shift" }} />
          <Stack.Screen name="ShiftDetail" component={ShiftDetailScreen} options={{ title: "Shift" }} />
          <Stack.Screen name="CloseShift" component={CloseShiftScreen} options={{ title: "Close shift" }} />
          <Stack.Screen name="Adjustment" component={AdjustmentScreen} options={{ title: "Refund, void or discount" }} />
          <Stack.Screen name="TruckStopYard" component={YardScreen} options={{ title: "Truck stop" }} />
          <Stack.Screen name="StayDetail" component={StayDetailScreen} options={{ title: "Stay" }} />
          <Stack.Screen name="StayRequests" component={StayRequestsScreen} options={{ title: "Stay requests" }} />
          <Stack.Screen name="Overview" component={OverviewScreen} options={{ title: "Overview" }} />
          <Stack.Screen name="TankDetail" component={TankDetailScreen} options={{ title: "Tank" }} />
        </Stack.Navigator>
      </NavigationContainer>
    </OutboxProvider>
  );
}

function Root() {
  const { status } = useAuth();
  if (status === "loading") {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg, justifyContent: "center" }}>
        <ActivityIndicator color={colors.text} />
      </View>
    );
  }
  return status === "signedOut" ? <LoginScreen /> : <SignedIn />;
}

export default function App() {
  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <StatusBar style="light" />
          <Root />
        </AuthProvider>
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}
