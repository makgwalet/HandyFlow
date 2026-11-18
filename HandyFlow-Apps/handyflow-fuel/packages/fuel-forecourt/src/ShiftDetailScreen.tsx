import React from "react";
import { Text } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useAuth } from "@/auth/AuthContext";
import type { RootStackParamList } from "@/navigation/types";
import { Button } from "@/ui/Button";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import { useAdjustments, useShift } from "./hooks";
import { canRunShifts, lit, rand, varianceLabel } from "./shifts";

type Props = NativeStackScreenProps<RootStackParamList, "ShiftDetail">;

export function ShiftDetailScreen({ route }: Props) {
  const { shiftId } = route.params;
  const nav = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const { session } = useAuth();
  const { shift: s, isLoading, error } = useShift(shiftId);
  const { adjustments } = useAdjustments(shiftId);
  if (isLoading || !s) return <Screen title="Shift"><Text style={{ color: error ? "#FCA5A5" : colors.muted }}>{error ?? "Loading…"}</Text></Screen>;
  const may = canRunShifts(session?.permissions ?? []);
  const v = varianceLabel(s);
  return (
    <Screen title={`${s.attendantName}${s.shiftNumber ? ` · ${s.shiftNumber}` : ""}`}>
      <Card>
        <Text style={{ color: colors.text, fontSize: 17 }}>{s.status === "OPEN" ? "Open" : s.status === "CLOSED" ? "Closed" : "Reversed"}</Text>
        <Text style={{ color: colors.muted }}>Opened {new Date(s.openedAt).toLocaleString()}{s.closedAt ? ` · closed ${new Date(s.closedAt).toLocaleString()}` : ""}</Text>
      </Card>
      {s.readings.map((r) => (
        <Card key={r.pumpId}>
          <Text style={{ color: colors.text, fontWeight: "700" }}>{r.pumpName}{r.fuelType ? ` · ${r.fuelType}` : ""}</Text>
          <Text style={{ color: colors.muted }}>Opening {r.openingMeter ?? "-"}{r.closingMeter != null ? ` · closing ${r.closingMeter}` : ""}{r.litresSold != null ? ` · ${lit(r.litresSold)} sold` : ""}</Text>
        </Card>
      ))}
      {s.status === "CLOSED" ? (
        <Card>
          <Text style={{ color: colors.text }}>Expected {rand(s.expected)} · cash {rand(s.cash)} · card {rand(s.card)} · account {rand(s.account)} · fleet card {rand(s.fleetCard)} · other {rand(s.other)}</Text>
          {(s.adjustments ?? 0) > 0 ? <Text style={{ color: colors.muted }}>Approved refunds, voids and discounts: {rand(s.adjustments)}</Text> : null}
          <Text style={{ color: v.bad ? "#FCA5A5" : "#86EFAC", fontWeight: "700" }}>{v.text}</Text>
          {s.varianceReason ? <Text style={{ color: colors.muted }}>Reason: {s.varianceReason}</Text> : null}
          {s.reviewStatus && s.reviewStatus !== "NONE" ? <Text style={{ color: "#FCD34D" }}>Review: {s.reviewStatus.toLowerCase()}{s.reviewNote ? `: ${s.reviewNote}` : ""}</Text> : null}
        </Card>
      ) : null}
      {adjustments.length > 0 ? (
        <Card>
          <Text style={{ color: colors.text, fontWeight: "700" }}>Refunds, voids and discounts</Text>
          {adjustments.map((a) => <Text key={a.id} style={{ color: colors.muted }}>{a.kind.toLowerCase()} {rand(a.amount)} · {a.status.toLowerCase()} · {a.reason}</Text>)}
        </Card>
      ) : null}
      {may && s.status === "OPEN" ? (
        <>
          <Button label="Record refund, void or discount" tone="plain" onPress={() => nav.navigate("Adjustment", { shiftId })} />
          <Button label="Close this shift" onPress={() => nav.navigate("CloseShift", { shiftId })} />
        </>
      ) : null}
    </Screen>
  );
}
