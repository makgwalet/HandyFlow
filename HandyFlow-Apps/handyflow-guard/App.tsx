import React from "react";
import { ActivityIndicator, View } from "react-native";
import { StatusBar } from "expo-status-bar";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider, useAuth } from "@/auth/AuthContext";
import { LoginScreen } from "@/screens/LoginScreen";
import { ChangePinScreen } from "@/screens/ChangePinScreen";
import { HomeScreen } from "@/screens/HomeScreen";
import { colors } from "@/ui/theme";

const queryClient = new QueryClient();

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
  return <HomeScreen />;
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
