import React from "react";
import { Linking, Text } from "react-native";
import Constants from "expo-constants";
import { displayName } from "@/api/auth";
import { emergencyContactOf, telUrl } from "@/api/access";
import { modesFor } from "@/access/modes";
import { useAccess } from "@/access/useAccess";
import { useAuth } from "@/auth/AuthContext";
import { Button } from "@/ui/Button";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";

const DIVISION_LABEL: Record<string, string> = {
  WHOLESALE_SUPPLY: "Wholesale supply", DISTRIBUTION: "Distribution", RETAIL_FORECOURT: "Retail forecourt", TRUCK_STOP: "Truck stop",
};
const label = (d: string) => DIVISION_LABEL[d] ?? d;

/** Says plainly what this sign-in can open and why something is missing, so a driver is not left guessing. */
export function AccessScreen() {
  const { session, subscriptionInactive } = useAuth();
  const { access, isLoading, error } = useAccess();
  const modes = modesFor(session?.permissions ?? [], access);
  const contact = emergencyContactOf(access);
  return (
    <Screen title="My access">
      <Card>
        <Text style={{ color: colors.text, fontSize: 18, fontWeight: "700" }}>{session ? displayName(session) : ""}</Text>
        <Text style={{ color: colors.muted }}>{session?.email}</Text>
        <Text style={{ color: colors.muted }}>App version {Constants.expoConfig?.version ?? "?"}</Text>
      </Card>
      {subscriptionInactive ? <Card><Text style={{ color: "#FCD34D" }}>Your company's Fuel subscription is not active. Saving still works; sending waits until it is.</Text></Card> : null}
      <Card>
        <Text style={{ color: colors.text, fontSize: 17, fontWeight: "600" }}>You can open</Text>
        {modes.length === 0 ? <Text style={{ color: colors.muted }}>Nothing. This sign-in has no Fuel role, or your company does not use the matching division. Ask your administrator.</Text> : null}
        {modes.map((m) => <Text key={m.id} style={{ color: colors.text }}>{m.title}{m.available ? "" : " (coming soon)"}</Text>)}
      </Card>
      {isLoading ? <Text style={{ color: colors.muted }}>Checking your access…</Text> : null}
      {error && !access ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5" }}>{error}</Text> : null}
      {access ? (
        <Card>
          <Text style={{ color: colors.text, fontSize: 17, fontWeight: "600" }}>Your company uses</Text>
          <Text style={{ color: colors.muted }}>{access.legacyMode ? "Every division (not set up by division yet)" : access.activeDivisions.map(label).join(", ") || "No division switched on"}</Text>
          {access.scope.restricted ? <Text style={{ color: colors.muted }}>Your access is limited to {access.scope.divisions.map(label).join(", ") || "certain depots"}.</Text> : null}
        </Card>
      ) : null}
      {contact ? <Button label={`Call ${contact.name}`} tone="plain" onPress={() => void Linking.openURL(telUrl(contact.phone))} /> : null}
    </Screen>
  );
}
