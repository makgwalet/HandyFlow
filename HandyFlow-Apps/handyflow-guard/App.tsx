import React from "react";
import { ActivityIndicator, View } from "react-native";
import { StatusBar } from "expo-status-bar";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider, useAuth } from "@/auth/AuthContext";
import { LoginScreen } from "@/screens/LoginScreen";
import { ChangePinScreen } from "@/screens/ChangePinScreen";
import { NavigationContainer, DarkTheme } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { OutboxProvider } from "@/offline/OutboxProvider";
import { HomeScreen } from "@/screens/HomeScreen";
import { PatrolScreen } from "@/screens/PatrolScreen";
import { ScanScreen } from "@/screens/ScanScreen";
import { IncidentScreen } from "@/screens/IncidentScreen";
import { ClockOutScreen } from "@/screens/ClockOutScreen";
import type { RootStackParamList } from "@/screens/types";
import { colors } from "@/ui/theme";

const queryClient = new QueryClient();
const Stack = createNativeStackNavigator<RootStackParamList>();
const navTheme = { ...DarkTheme, colors: { ...DarkTheme.colors, background: colors.bg, card: colors.surface, text: colors.text, border: colors.border } };

function SignedIn() {
  return (
    <OutboxProvider>
      <NavigationContainer theme={navTheme}>
        <Stack.Navigator>
          <Stack.Screen name="Home" component={HomeScreen} options={{ title: "HandyFlow Guard", headerBackVisible: false }} />
          <Stack.Screen name="Patrol" component={PatrolScreen} options={{ title: "Patrol" }} />
          <Stack.Screen name="Scan" component={ScanScreen} options={{ title: "Scan" }} />
          <Stack.Screen name="Incident" component={IncidentScreen} options={{ title: "Incident" }} />
          <Stack.Screen name="ClockOut" component={ClockOutScreen} options={{ title: "End shift" }} />
        </Stack.Navigator>
      </NavigationContainer>
    </OutboxProvider>
  );
}

function Root() {
  const { status, session } = useAuth();
  if (status === "loading") {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg, justifyContent: "center" }}>
        <ActivityIndicator color={colors.text} />
      </View>
    );
  }
  if (status === "signedOut") return <LoginScreen />;
  if (session?.mustChangePIN) return <ChangePinScreen />;
  return <SignedIn />;
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
