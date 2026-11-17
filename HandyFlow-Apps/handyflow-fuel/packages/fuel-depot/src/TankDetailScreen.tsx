import React, { useState } from "react";
import { Text } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useQuery } from "@tanstack/react-query";
import { useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { api } from "@/api/client";
import { displayName } from "@/api/auth";
import { useAuth } from "@/auth/AuthContext";
import type { RootStackParamList } from "@/navigation/types";
import { useOutbox } from "@/offline/OutboxProvider";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import { canReceiveFuel, canRecordDip, dipBody, dipPageSchema, fillText, validateDip, varianceText } from "./tanks";
import { useTanks } from "./useTanks";

type Props = NativeStackScreenProps<RootStackParamList, "TankDetail">;
const path = (id: string) => `/api/v1/fuel/tanks/${id}/dip-readings`;

export function TankDetailScreen({ route }: Props) {
  const { tankId } = route.params;
  const { session } = useAuth();
  const { tanks } = useTanks();
  const { enqueue, pending } = useOutbox();
  const nav = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const tank = tanks.find((t) => t.id === tankId);
  const [litres, setLitres] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  const readings = useQuery({
    queryKey: ["fuel-depot-dips", tankId],
    retry: false,
    queryFn: async () => dipPageSchema.parse(await api<unknown>(`${path(tankId)}?size=5`)).content,
  });

  if (!tank) return <Screen title="Tank"><Text style={{ color: colors.muted }}>This tank is not on your list.</Text></Screen>;
  const mayRecord = canRecordDip(session?.permissions ?? []);
  const waiting = pending.filter((i) => (i.kind === "DIP_READING" || i.kind === "RECEIVE_FUEL") && i.payload.tankId === tankId).length;

  async function save() {
    const problem = validateDip(litres, tank?.capacityLitres);
    if (problem) { setError(problem); return; }
    setError(null); setDone(null); setBusy(true);
    try {
      // The time of the dip is fixed now, not when signal returns. The queue's item id is the clientRequestId, so a resend cannot double-save.
      await enqueue("DIP_READING", { tankId, body: dipBody(litres, new Date().toISOString(), session ? displayName(session) : "") });
      setDone("Dip reading saved on this phone. It is sent when there is signal.");
      setLitres("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save on this phone");
    } finally { setBusy(false); }
  }

  return (
    <Screen title={tank.name}>
      <Card>
        <Text style={{ color: colors.text, fontSize: 17 }}>{tank.fuelType}</Text>
        <Text style={{ color: colors.muted }}>{fillText(tank)}</Text>
        {tank.low ? <Text style={{ color: "#FCA5A5", fontWeight: "700" }}>Low</Text> : null}
      </Card>
      {mayRecord ? (
        <>
          <Field label="Litres measured (dip)" value={litres} onChangeText={setLitres} keyboardType="decimal-pad" />
          {error ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 15 }}>{error}</Text> : null}
          {done ? <Text accessibilityRole="alert" style={{ color: "#86EFAC", fontSize: 15 }}>{done}</Text> : null}
          <Button label="Save dip reading" onPress={save} loading={busy} />
          {canReceiveFuel(session?.permissions ?? []) ? <Button label="Receive fuel" tone="plain" onPress={() => nav.navigate("ReceiveFuel", { tankId })} /> : null}
        </>
      ) : (
        <Text style={{ color: colors.muted }}>You can view this tank but not record dip readings.</Text>
      )}
      {waiting > 0 ? <Text style={{ color: "#FCD34D" }}>{waiting} {waiting === 1 ? "entry" : "entries"} for this tank saved on the phone, waiting to send.</Text> : null}
      <Text accessibilityRole="header" style={{ color: colors.text, fontSize: 18, fontWeight: "700" }}>Recent readings</Text>
      {readings.isLoading ? <Text style={{ color: colors.muted }}>Loading…</Text> : null}
      {readings.error ? <Text style={{ color: colors.muted }}>Recent readings need signal.</Text> : null}
      {(readings.data ?? []).map((r) => (
        <Card key={r.id}>
          <Text style={{ color: colors.text }}>{Math.round(r.actualLitres).toLocaleString("en-ZA")} L · {new Date(r.readAt).toLocaleString()}</Text>
          <Text style={{ color: r.negativeVariance ? "#FCA5A5" : colors.muted }}>{varianceText(r)}{r.readBy ? ` · ${r.readBy}` : ""}</Text>
        </Card>
      ))}
    </Screen>
  );
}
