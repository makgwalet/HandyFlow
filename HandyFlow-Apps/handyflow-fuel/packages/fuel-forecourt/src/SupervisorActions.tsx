import React, { useState } from "react";
import { Text } from "react-native";
import { api } from "@/api/client";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { Card } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import { failText, SUPERVISOR_BASE, useRefreshShifts } from "./hooks";
import { canReverse, canSupervise, rand, reasonProblem, type Adjustment, type Shift } from "./shifts";

type Ask = { kind: "review" | "reverse" | "reject"; id: string } | null;

/**
 * What a supervisor or manager does on a shift: review one that closed outside tolerance, approve or reject a pending refund,
 * void or discount, reverse a closed shift. Online only; the server decides and enforces the permission, so a person without it
 * sees nothing here and could not do it by other means. Each needs a reason that is stored.
 */
export function SupervisorActions({ shift, adjustments, permissions }: { shift: Shift; adjustments: Adjustment[]; permissions: readonly string[] }) {
  const refresh = useRefreshShifts();
  const [ask, setAsk] = useState<Ask>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);
  const supervise = canSupervise(permissions);
  const reverse = canReverse(permissions);
  const pending = adjustments.filter((a) => a.status === "PENDING");
  const reviewable = supervise && shift.status === "CLOSED" && shift.reviewStatus === "PENDING_REVIEW";
  const reversible = reverse && shift.status === "CLOSED";
  if (!reviewable && !reversible && !(supervise && pending.length > 0)) return null;

  async function send(key: string, path: string, body?: object) {
    setError(null); setDone(null); setBusy(key);
    try { await api(`${SUPERVISOR_BASE}${path}`, { method: "POST", ...(body ? { body } : {}) }); setAsk(null); setReason(""); setDone("Saved."); await refresh(shift.id); }
    catch (e) { setError(failText(e)); } finally { setBusy(null); }
  }

  function confirm() {
    if (!ask) return;
    const problem = reasonProblem(reason, ask.kind === "reverse" ? 500 : 300);
    if (problem) { setError(problem); return; }
    if (ask.kind === "review") void send("review", `/shifts/${shift.id}/review`, { reason: reason.trim() });
    else if (ask.kind === "reverse") void send("reverse", `/shifts/${shift.id}/reverse`, { reason: reason.trim() });
    else void send(`reject-${ask.id}`, `/adjustments/${ask.id}/reject`, { reason: reason.trim() });
  }

  return (
    <>
      {supervise && pending.length > 0 ? (
        <Card>
          <Text accessibilityRole="header" style={{ color: colors.text, fontWeight: "700" }}>Waiting for your decision</Text>
          {pending.map((a) => (
            <React.Fragment key={a.id}>
              <Text style={{ color: colors.text }}>{a.kind.toLowerCase()} {rand(a.amount)} · {a.reason}</Text>
              <Button label={`Approve ${a.kind.toLowerCase()} ${rand(a.amount)}`} loading={busy === `approve-${a.id}`} disabled={!!busy} onPress={() => void send(`approve-${a.id}`, `/adjustments/${a.id}/approve`)} />
              <Button label={`Reject ${a.kind.toLowerCase()} ${rand(a.amount)}`} tone="plain" disabled={!!busy} onPress={() => { setAsk({ kind: "reject", id: a.id }); setReason(""); setError(null); }} />
            </React.Fragment>
          ))}
        </Card>
      ) : null}
      {reviewable ? <Button label="Review this shift" tone="plain" disabled={!!busy} onPress={() => { setAsk({ kind: "review", id: shift.id }); setReason(""); setError(null); }} /> : null}
      {reversible ? <Button label="Reverse this shift" tone="danger" disabled={!!busy} onPress={() => { setAsk({ kind: "reverse", id: shift.id }); setReason(""); setError(null); }} /> : null}
      {ask ? (
        <Card>
          <Text style={{ color: colors.text, fontWeight: "700" }}>{ask.kind === "review" ? "Review: what did you find?" : ask.kind === "reject" ? "Why are you rejecting it?" : "Why is this shift being reversed?"}</Text>
          {ask.kind === "reverse" ? <Text style={{ color: "#FCD34D" }}>A reversed shift no longer counts in the takings. This is recorded against you.</Text> : null}
          <Field label="Reason" value={reason} onChangeText={setReason} multiline maxLength={ask.kind === "reverse" ? 500 : 300} />
          <Button label="Confirm" onPress={confirm} loading={!!busy} />
          <Button label="Cancel" tone="plain" onPress={() => { setAsk(null); setError(null); }} />
        </Card>
      ) : null}
      {error ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 15 }}>{error}</Text> : null}
      {done ? <Text accessibilityRole="alert" style={{ color: "#86EFAC", fontSize: 15 }}>{done}</Text> : null}
    </>
  );
}
