import React from "react";
import { Text } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { RootStackParamList } from "@/navigation/types";
import { Button } from "@/ui/Button";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import { blocks, notes, offloadGate, reviews } from "./arrival";
import { useArrival } from "./useArrival";

type Props = NativeStackScreenProps<RootStackParamList, "ArrivalCheck">;

export function ArrivalCheckScreen({ route }: Props) {
  const { check, busy, error, run } = useArrival(route.params.loadId);
  const gate = offloadGate(check);
  return (
    <Screen title="Arrival check">
      <Text style={{ color: colors.muted, fontSize: 15 }}>Checks your tanker, your approval and the product against the site's rules. Needs signal.</Text>
      <Button label={check ? "Check again" : "Run the check"} onPress={run} loading={busy} />
      {error ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 15 }}>{error}</Text> : null}
      {check ? (
        <>
          <Card>
            <Text style={{ color: gate.ok ? "#86EFAC" : "#FCA5A5", fontSize: 20, fontWeight: "700" }}>{gate.ok ? "You may offload" : "Do not offload"}</Text>
            <Text style={{ color: colors.muted }}>Checked {new Date(check.checkedAt).toLocaleString()}</Text>
            {gate.reason ? <Text style={{ color: "#FCD34D" }}>{gate.reason}</Text> : null}
          </Card>
          {blocks(check).map((f) => <Card key={f.code + f.message}><Text style={{ color: "#FCA5A5", fontWeight: "700" }}>Stops this delivery</Text><Text style={{ color: colors.text }}>{f.message}</Text></Card>)}
          {reviews(check).map((f) => <Card key={f.code + f.message}><Text style={{ color: "#FCD34D", fontWeight: "700" }}>The office may need to review</Text><Text style={{ color: colors.text }}>{f.message}</Text></Card>)}
          {notes(check).map((f) => <Card key={f.code + f.message}><Text style={{ color: colors.text }}>{f.message}</Text></Card>)}
        </>
      ) : null}
    </Screen>
  );
}
