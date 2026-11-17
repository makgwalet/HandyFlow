import React from "react";
import { Pressable, Text } from "react-native";
import { useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { displayName } from "@/api/auth";
import { hasFuelAccess, modesFor, type Mode } from "@/access/modes";
import { useAuth } from "@/auth/AuthContext";
import type { RootStackParamList } from "@/navigation/types";
import { Button } from "@/ui/Button";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";

type Nav = NativeStackNavigationProp<RootStackParamList>;

export function HomeScreen() {
  const { session, signOut } = useAuth();
  const nav = useNavigation<Nav>();
  const perms = session?.permissions ?? [];
  const modes = modesFor(perms);

  function open(m: Mode) {
    if (m.id === "driver") nav.navigate("DriverLoads");
    else if (m.id === "depot") nav.navigate("DepotTanks");
    else nav.navigate("ModePlanned", { mode: m.id });
  }

  return (
    <Screen title={session ? displayName(session) : "HandyFlow Fuel"}>
      {!hasFuelAccess(perms) || modes.length === 0 ? (
        <Card>
          <Text style={{ color: colors.text, fontSize: 17 }}>This sign-in has no Fuel access. Ask your administrator to give you a Fuel role.</Text>
        </Card>
      ) : (
        modes.map((m) => (
          <Pressable key={m.id} accessibilityRole="button" accessibilityLabel={m.available ? m.title : `${m.title}, not available yet`} onPress={() => open(m)}>
            <Card>
              <Text style={{ color: colors.text, fontSize: 20, fontWeight: "700" }}>{m.title}</Text>
              <Text style={{ color: colors.muted, fontSize: 15 }}>{m.available ? m.blurb : `${m.blurb} Coming soon.`}</Text>
            </Card>
          </Pressable>
        ))
      )}
      <Button label="Sync centre" tone="plain" onPress={() => nav.navigate("SyncCentre")} />
      <Button label="Sign out" tone="plain" onPress={() => void signOut()} />
    </Screen>
  );
}
