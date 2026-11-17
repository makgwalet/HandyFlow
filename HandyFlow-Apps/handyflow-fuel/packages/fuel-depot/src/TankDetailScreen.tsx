import React, { useRef, useState } from "react";
import { Text } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/api/client";
import { displayName } from "@/api/auth";
import { useAuth } from "@/auth/AuthContext";
import type { RootStackParamList } from "@/navigation/types";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import { alreadyRecorded, canRecordDip, dipBody, dipPageSchema, fillText, validateDip, varianceText } from "./tanks";
import { useTanks } from "./useTanks";

type Props = NativeStackScreenProps<RootStackParamList, "TankDetail">;
const path = (id: string) => `/api/v1/fuel/tanks/${id}/dip-readings`;

export function TankDetailScreen({ route }: Props) {
  const { tankId } = route.params;
  const { session } = useAuth();
  const { tanks } = useTanks();
  const qc = useQueryClient();
  const tank = tanks.find((t) => t.id === tankId);
  const [litres, setLitres] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);
  // Fixed when the form opens, so a retry after a lost reply is recognisable as the same reading.
  const readAt = useRef(new Date().toISOString());
  const attempted = useRef(false);

  const readings = useQuery({
    queryKey: ["fuel-depot-dips", tankId],
    retry: false,
    queryFn: async () => dipPageSchema.parse(await api<unknown>(`${path(tankId)}?size=5`)).content,
  });

  if (!tank) return <Screen title="Tank"><Text style={{ color: colors.muted }}>This tank is not on your list.</Text></Screen>;
  const mayRecord = canRecordDip(session?.permissions ?? []);

  async function save() {
    const problem = validateDip(litres, tank?.capacityLitres);
    if (problem) { setError(problem); return; }
    setError(null); setDone(null); setBusy(true);
    try {
      const body = dipBody(litres, readAt.current, session ? displayName(session) : "");
      if (attempted.current) {
        // The last try may have reached the server even though the reply was lost.
        try {
          const recent = dipPageSchema.parse(await api<unknown>(`${path(tankId)}?size=5`)).content;
          if (alreadyRecorded(recent, body.readAt, body.actualLitres)) { setDone("That reading was already saved."); await qc.invalidateQueries({ queryKey: ["fuel-depot-dips", tankId] }); return; }
        } catch { setError("Cannot check whether the last try was saved. Wait for signal and try again."); return; }
      }
      attempted.current = true;
      await api(path(tankId), { method: "POST", body });
      setDone("Dip reading saved.");
      setLitres("");
      readAt.current = new Date().toISOString();
      attempted.current = false;
      await qc.invalidateQueries({ queryKey: ["fuel-depot-dips", tankId] });
      await qc.invalidateQueries({ queryKey: ["fuel-depot-tanks"] });
    } catch (e) {
      setError(e instanceof ApiError && e.isNetwork
        ? "No signal. Dip readings cannot be saved without signal yet. Write it down and enter it when you have signal."
        : e instanceof Error ? e.message : "Could not save");
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
        </>
      ) : (
        <Text style={{ color: colors.muted }}>You can view this tank but not record dip readings.</Text>
      )}
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
