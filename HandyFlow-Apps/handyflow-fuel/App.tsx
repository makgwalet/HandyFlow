import React from "react";
import { ActivityIndicator, View } from "react-native";
import { StatusBar } from "expo-status-bar";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { DarkTheme, NavigationContainer } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { CompleteScreen, FailedAttemptScreen, LoadDetailScreen, MyLoadsScreen } from "@driver/index";
import { AuthProvider, useAuth } from "@/auth/AuthContext";
import { OutboxProvider } from "@/offline/OutboxProvider";
import { HomeScreen } from "@/screens/HomeScreen";
import { LoginScreen } from "@/screens/LoginScreen";
import { ModePlannedScreen } from "@/screens/ModePlannedScreen";
import { SyncCentreScreen } from "@/screens/SyncCentreScreen";
import type { RootStackParamList } from "@/navigation/types";
import { colors } from "@/ui/theme";

const queryClient = new QueryClient();
const Stack = createNativeStackNavigator<RootStackParamList>();
const navTheme = { ...DarkTheme, colors: { ...DarkTheme.colors, background: colors.bg, card: colors.surface, text: colors.text, border: colors.border } };

function SignedIn() {
  return (
    <OutboxProvider>
      <NavigationContainer theme={navTheme}>
        <Stack.Navigator>
          <Stack.Screen name="Home" component={HomeScreen} options={{ title: "HandyFlow Fuel", headerBackVisible: false }} />
          <Stack.Screen name="ModePlanned" component={ModePlannedScreen} options={{ title: "Coming soon" }} />
          <Stack.Screen name="SyncCentre" component={SyncCentreScreen} options={{ title: "Sync centre" }} />
          <Stack.Screen name="DriverLoads" component={MyLoadsScreen} options={{ title: "My loads" }} />
          <Stack.Screen name="LoadDetail" component={LoadDetailScreen} options={{ title: "Load" }} />
          <Stack.Screen name="CompleteLoad" component={CompleteScreen} options={{ title: "Record delivery" }} />
          <Stack.Screen name="FailedAttempt" component={FailedAttemptScreen} options={{ title: "Could not deliver" }} />
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
