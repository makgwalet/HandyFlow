import React, { useEffect, useMemo, useState } from "react";
import { Text } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useNavigation } from "@react-navigation/native";
import { useAuth } from "@/auth/AuthContext";
import type { RootStackParamList } from "@/navigation/types";
import { useOutbox } from "@/offline/OutboxProvider";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import { usePumps, useShift } from "./hooks";
import { closeWaiting } from "./send";
import { canRunShifts, closeBody, closeRows, emptyTakings, expectedPreview, lit, queuedOpens, rand, shiftFromQueuedOpen, validateClose, type CloseRow, type Shift, type Takings } from "./shifts";

type Props = NativeStackScreenProps<RootStackParamList, "CloseShift">;
type Target = { shiftId?: string; openRequestId?: string };

/** Closes a shift the server knows by its id, or one opened on this phone that has not been sent yet, by the id its open was queued under. */
export function CloseShiftScreen({ route }: Props) {
  const { shiftId, openRequestId } = route.params;
  return openRequestId ? <CloseQueuedOpen openRequestId={openRequestId} /> : <CloseServerShift shiftId={shiftId ?? ""} />;
}

function CloseServerShift({ shiftId }: { shiftId: string }) {
  const { shift, isLoading, error: loadError } = useShift(shiftId);
  const { pending } = useOutbox();
  const { session } = useAuth();
  if (!canRunShifts(session?.permissions ?? [])) return <Screen title="Close shift"><Text style={{ color: colors.muted }}>You do not have permission to close a shift.</Text></Screen>;
  if (isLoading || !shift) return <Screen title="Close shift"><Text style={{ color: loadError ? "#FCA5A5" : colors.muted }}>{loadError ?? "Loading…"}</Text></Screen>;
  if (closeWaiting(pending, { shiftId })) return <Waiting />;
  if (shift.status !== "OPEN") return <Screen title="Close shift"><Text style={{ color: colors.muted }}>This shift is already {shift.status.toLowerCase()}.</Text></Screen>;
  return <CloseForm shift={shift} target={{ shiftId }} />;
}

function CloseQueuedOpen({ openRequestId }: { openRequestId: string }) {
  const { pending } = useOutbox();
  const { pumps } = usePumps();
  const { session } = useAuth();
  const item = queuedOpens(pending).find((i) => i.id === openRequestId);
  const shift = useMemo(() => (item ? shiftFromQueuedOpen(item, pumps) : null), [item, pumps]);
  if (!canRunShifts(session?.permissions ?? [])) return <Screen title="Close shift"><Text style={{ color: colors.muted }}>You do not have permission to close a shift.</Text></Screen>;
  if (closeWaiting(pending, { openRequestId })) return <Waiting />;
  if (!shift) return <Screen title="Close shift"><Card><Text style={{ color: colors.text, fontSize: 17 }}>This shift has been sent to the server.</Text><Text style={{ color: colors.muted }}>Close it from the shift list once it appears there.</Text></Card></Screen>;
  return <CloseForm shift={shift} target={{ openRequestId }} queuedOpen />;
}

const Waiting = () => (
  <Screen title="Close shift"><Card><Text style={{ color: colors.text, fontSize: 17 }}>The close for this shift is saved on this phone and waiting to send.</Text><Text style={{ color: colors.muted }}>It is not final, and the takings are not booked, until the server accepts it.</Text></Card></Screen>
);

function CloseForm({ shift, target, queuedOpen }: { shift: Shift; target: Target; queuedOpen?: boolean }) {
  const nav = useNavigation();
  const { enqueue } = useOutbox();
  const { pumps } = usePumps();
  const [rows, setRows] = useState<CloseRow[] | null>(null);
  const [t, setT] = useState<Takings>(emptyTakings);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => { if (rows === null) setRows(closeRows(shift)); }, [shift, rows]);
  const prices = useMemo(() => Object.fromEntries(pumps.map((p) => [p.id, p.pricePerLitre ?? 0])), [pumps]);
  const cur = rows ?? [];
  const preview = expectedPreview(shift, cur, prices);
  const setRow = (id: string, k: "closing" | "test") => (v: string) => setRows((rs) => (rs ?? []).map((r) => (r.pumpId === id ? { ...r, [k]: v } : r)));
  const setTake = (k: keyof Takings) => (v: string) => setT((x) => ({ ...x, [k]: v }));

  async function save() {
    const problem = validateClose(shift, cur, t);
    if (problem) { setError(problem); return; }
    setError(null); setBusy(true);
    try {
      await enqueue("SHIFT_CLOSE", { ...target, body: closeBody(cur, t, reason) });
      nav.goBack();
    } catch (e) { setError(e instanceof Error ? e.message : "Could not save on this phone"); } finally { setBusy(false); }
  }

  return (
    <Screen title={`Close shift: ${shift.attendantName}`}>
      {queuedOpen ? <Card><Text style={{ color: "#FCD34D" }}>This shift was opened on this phone and has not reached the server yet. The close is sent after the open, in order.</Text></Card> : null}
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
      <Text style={{ color: colors.muted }}>Saved on this phone first and sent when there is signal. The takings are not final until the server accepts them: it works out the expected takings and the variance, and may send the shift for review.</Text>
      <Button label="Close shift" onPress={save} loading={busy} />
    </Screen>
  );
}
