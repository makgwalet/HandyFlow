import React from "react";
import { Text } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { modesFor } from "@/access/modes";
import { useAuth } from "@/auth/AuthContext";
import type { RootStackParamList } from "@/navigation/types";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";

/** Placeholder for modes whose screens are not built. It says so plainly and points to the web app. */
export function ModePlannedScreen({ route }: NativeStackScreenProps<RootStackParamList, "ModePlanned">) {
  const { session } = useAuth();
  const mode = modesFor(session?.permissions ?? []).find((m) => m.id === route.params.mode);
  return (
    <Screen title={mode?.title ?? "Not available"}>
      <Card>
        <Text style={{ color: colors.text, fontSize: 17 }}>This part of the app is not built yet. Use HandyFlow on the web for now.</Text>
        {mode ? <Text style={{ color: colors.muted, fontSize: 15 }}>{mode.blurb}</Text> : null}
      </Card>
    </Screen>
  );
}
