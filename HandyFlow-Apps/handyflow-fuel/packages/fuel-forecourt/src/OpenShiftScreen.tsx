import React, { useEffect, useState } from "react";
import { Text } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { useOutbox } from "@/offline/OutboxProvider";
import { displayName } from "@/api/auth";
import { useAuth } from "@/auth/AuthContext";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import { usePumps } from "./hooks";
import { canRunShifts, openBody, openRows, validateOpen, type OpenRow } from "./shifts";

export function OpenShiftScreen() {
  const nav = useNavigation();
  const { session } = useAuth();
  const { pumps, isLoading, error: loadError } = usePumps();
  const { enqueue } = useOutbox();
  const [attendant, setAttendant] = useState(session ? displayName(session) : "");
  const [notes, setNotes] = useState("");
  const [rows, setRows] = useState<OpenRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => { if (rows === null && pumps.length > 0) setRows(openRows(pumps)); }, [pumps, rows]);

  if (!canRunShifts(session?.permissions ?? [])) return <Screen title="Open a shift"><Text style={{ color: colors.muted }}>You do not have permission to open a shift.</Text></Screen>;
  const free = rows ?? [];

  async function save() {
    const problem = validateOpen(attendant, free);
    if (problem) { setError(problem); return; }
    setError(null); setBusy(true);
    try {
      await enqueue("SHIFT_OPEN", { body: openBody(attendant, free, notes) });
      nav.goBack();
    } catch (e) { setError(e instanceof Error ? e.message : "Could not save on this phone"); } finally { setBusy(false); }
  }

  return (
    <Screen title="Open a shift">
      <Field label="Attendant" value={attendant} onChangeText={setAttendant} />
      {isLoading ? <Text style={{ color: colors.muted }}>Loading pumps…</Text> : null}
      {loadError ? <Card><Text accessibilityRole="alert" style={{ color: "#FCA5A5" }}>{loadError}</Text></Card> : null}
      {!isLoading && !loadError && free.length === 0 ? <Card><Text style={{ color: colors.text }}>Every active pump is already on an open shift.</Text></Card> : null}
      {free.map((r) => {
        const p = pumps.find((x) => x.id === r.pumpId);
        return <Field key={r.pumpId} label={`Opening meter: ${p?.name ?? "pump"}${p?.fuelType ? ` (${p.fuelType})` : ""}`} value={r.meter} keyboardType="decimal-pad"
          onChangeText={(v) => setRows((rs) => (rs ?? []).map((x) => (x.pumpId === r.pumpId ? { ...x, meter: v } : x)))} />;
      })}
      <Field label="Notes (optional)" value={notes} onChangeText={setNotes} />
      {error ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 15 }}>{error}</Text> : null}
      <Text style={{ color: colors.muted }}>Saved on this phone first and sent when there is signal. The shift is not open on the server until it is accepted. If a pump is already on another shift, or a meter is below the last reading, the server will refuse it and say why.</Text>
      <Button label="Open shift" onPress={save} loading={busy} disabled={free.length === 0} />
    </Screen>
  );
}
