import React, { useEffect, useMemo, useState } from "react";
import { Text } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useNavigation } from "@react-navigation/native";
import { api } from "@/api/client";
import { useAuth } from "@/auth/AuthContext";
import type { RootStackParamList } from "@/navigation/types";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import { failText, usePumps, useRefreshShifts, useShift } from "./hooks";
import { canRunShifts, closeBody, closeRows, emptyTakings, expectedPreview, lit, rand, validateClose, type CloseRow, type Takings } from "./shifts";

type Props = NativeStackScreenProps<RootStackParamList, "CloseShift">;

export function CloseShiftScreen({ route }: Props) {
  const { shiftId } = route.params;
  const nav = useNavigation();
  const { session } = useAuth();
  const { shift, isLoading, error: loadError } = useShift(shiftId);
  const { pumps } = usePumps();
  const refresh = useRefreshShifts();
  const [rows, setRows] = useState<CloseRow[] | null>(null);
  const [t, setT] = useState<Takings>(emptyTakings);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => { if (shift && rows === null) setRows(closeRows(shift)); }, [shift, rows]);
  const prices = useMemo(() => Object.fromEntries(pumps.map((p) => [p.id, p.pricePerLitre ?? 0])), [pumps]);

  if (!canRunShifts(session?.permissions ?? [])) return <Screen title="Close shift"><Text style={{ color: colors.muted }}>You do not have permission to close a shift.</Text></Screen>;
  if (isLoading || !shift) return <Screen title="Close shift"><Text style={{ color: loadError ? "#FCA5A5" : colors.muted }}>{loadError ?? "Loading…"}</Text></Screen>;
  if (shift.status !== "OPEN") return <Screen title="Close shift"><Text style={{ color: colors.muted }}>This shift is already {shift.status.toLowerCase()}.</Text></Screen>;
  const cur = rows ?? [];
  const preview = expectedPreview(shift, cur, prices);
  const setRow = (id: string, k: "closing" | "test") => (v: string) => setRows((rs) => (rs ?? []).map((r) => (r.pumpId === id ? { ...r, [k]: v } : r)));
  const setTake = (k: keyof Takings) => (v: string) => setT((x) => ({ ...x, [k]: v }));

  async function save() {
    const problem = validateClose(shift!, cur, t);
    if (problem) { setError(problem); return; }
    setError(null); setBusy(true);
    try {
      await api(`/api/v1/fuel/forecourt/shifts/${shiftId}/close`, { method: "POST", body: closeBody(cur, t, reason) });
      await refresh(shiftId);
      nav.goBack();
    } catch (e) { setError(failText(e)); } finally { setBusy(false); }
  }

  return (
    <Screen title={`Close shift: ${shift.attendantName}`}>
      {shift.readings.map((r) => (
        <React.Fragment key={r.pumpId}>
          <Field label={`Closing meter: ${r.pumpName ?? "pump"} (opened at ${r.openingMeter ?? 0})`} value={cur.find((x) => x.pumpId === r.pumpId)?.closing ?? ""} keyboardType="decimal-pad" onChangeText={setRow(r.pumpId, "closing")} />
          <Field label={`Test litres: ${r.pumpName ?? "pump"} (optional)`} value={cur.find((x) => x.pumpId === r.pumpId)?.test ?? ""} keyboardType="decimal-pad" onChangeText={setRow(r.pumpId, "test")} />
        </React.Fragment>
      ))}
      <Card><Text style={{ color: colors.text }}>From the meters: {lit(preview.litres)}{preview.value > 0 ? ` · about ${rand(preview.value)}` : ""}</Text></Card>
      <Field label="Cash taken (rand)" value={t.cash} keyboardType="decimal-pad" onChangeText={setTake("cash")} />
      <Field label="Card taken (rand)" value={t.card} keyboardType="decimal-pad" onChangeText={setTake("card")} />
      <Field label="Account sales (rand)" value={t.account} keyboardType="decimal-pad" onChangeText={setTake("account")} />
      <Field label="Fleet card (rand)" value={t.fleetCard} keyboardType="decimal-pad" onChangeText={setTake("fleetCard")} />
      <Field label="Other (rand)" value={t.other} keyboardType="decimal-pad" onChangeText={setTake("other")} />
      <Field label="Reason for any difference (optional)" value={reason} onChangeText={setReason} />
      {error ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 15 }}>{error}</Text> : null}
      <Text style={{ color: colors.muted }}>Closing needs signal. The server works out the expected takings and the variance.</Text>
      <Button label="Close shift" onPress={save} loading={busy} />
    </Screen>
  );
}
