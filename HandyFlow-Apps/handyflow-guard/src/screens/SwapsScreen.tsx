import React, { useState } from "react";
import { StyleSheet, Text, TextInput } from "react-native";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/auth/AuthContext";
import { acceptSwap, cancelSwap, getMyShifts, getMySwaps, requestSwap, type Swap } from "@/api/guardWork";
import { Button } from "@/ui/Button";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";

const OPEN = ["PENDING", "PROPOSED_ACCEPTED"];

const fmt = (iso: string) =>
  new Date(iso).toLocaleString(undefined, { weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

const STATUS_WORDS: Record<string, string> = {
  PENDING: "Waiting for a colleague or your supervisor",
  PROPOSED_ACCEPTED: "A colleague agreed. Waiting for your supervisor",
  APPROVED: "Approved",
  REJECTED: "Refused",
  CANCELLED: "Cancelled",
};

export function SwapsScreen() {
  const { session } = useAuth();
  const me = session?.guardId ?? "";
  const qc = useQueryClient();
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const swapsQ = useQuery({ queryKey: ["mySwaps"], queryFn: getMySwaps });
  const shiftsQ = useQuery({ queryKey: ["myShifts"], queryFn: getMyShifts });

  const swaps = swapsQ.data ?? [];
  const taken = new Set(swaps.filter((w) => OPEN.includes(w.status)).map((w) => w.originalShiftId));
  const swappable = (shiftsQ.data ?? []).filter((x) => x.status === "SCHEDULED" && !taken.has(x.id) && new Date(x.startAt) > new Date());

  const done = {
    onSuccess: () => {
      setError(null);
      void qc.invalidateQueries({ queryKey: ["mySwaps"] });
    },
    onError: (e: unknown) => setError(e instanceof Error ? e.message : "That did not work"),
  };
  const request = useMutation({ mutationFn: (shiftId: string) => requestSwap(shiftId, reason), ...done, onSuccess: () => { setReason(""); done.onSuccess(); } });
  const accept = useMutation({ mutationFn: (id: string) => acceptSwap(id), ...done });
  const cancel = useMutation({ mutationFn: (id: string) => cancelSwap(id), ...done });

  const mineToCancel = (w: Swap) => w.requestingGuardId === me && OPEN.includes(w.status);
  const mineToAccept = (w: Swap) => w.proposedGuardId === me && w.requestingGuardId !== me && w.status === "PENDING";

  return (
    <Screen title="Shift swaps">
      {error ? <Text accessibilityRole="alert" style={s.err}>{error}</Text> : null}
      <Text style={s.head}>Ask to swap a shift</Text>
      <TextInput style={s.input} value={reason} onChangeText={setReason} maxLength={500} placeholder="Reason (optional)" placeholderTextColor={colors.muted} accessibilityLabel="Reason" />
      {swappable.length === 0 ? <Text style={s.muted}>{shiftsQ.isLoading ? "Loading..." : "No upcoming shifts that can be swapped."}</Text> : null}
      {swappable.map((x) => (
        <Button key={x.id} tone="plain" label={`${x.siteName}, ${fmt(x.startAt)}`} loading={request.isPending && request.variables === x.id} onPress={() => request.mutate(x.id)} />
      ))}
      <Text style={s.muted}>Your supervisor decides. The shift stays yours until it is approved.</Text>

      <Text style={s.head}>Your requests</Text>
      {swapsQ.isError ? <Text style={s.err}>Could not load your swaps.</Text> : null}
      {swaps.length === 0 && !swapsQ.isLoading ? <Text style={s.muted}>None yet.</Text> : null}
      {swaps.map((w) => (
        <Card key={w.id}>
          <Text style={s.strong}>{w.shiftSummary ?? "Shift"}</Text>
          <Text style={s.muted}>
            {w.requestingGuardId === me ? "You asked" : `${w.requestingGuardName ?? "A colleague"} asked you`}: {STATUS_WORDS[w.status] ?? w.status}
          </Text>
          {w.rejectionReason ? <Text style={s.muted}>Reason given: {w.rejectionReason}</Text> : null}
          {mineToAccept(w) ? <Button label="Accept" onPress={() => accept.mutate(w.id)} loading={accept.isPending && accept.variables === w.id} /> : null}
          {mineToCancel(w) ? <Button label="Cancel request" tone="plain" onPress={() => cancel.mutate(w.id)} loading={cancel.isPending && cancel.variables === w.id} /> : null}
        </Card>
      ))}
    </Screen>
  );
}

const s = StyleSheet.create({
  head: { color: colors.text, fontWeight: "700", fontSize: 17 },
  strong: { color: colors.text, fontWeight: "700", fontSize: 16 },
  muted: { color: colors.muted },
  err: { color: colors.danger },
  input: { backgroundColor: colors.surface, color: colors.text, borderColor: colors.border, borderWidth: 1, borderRadius: 12, minHeight: 52, paddingHorizontal: 14, fontSize: 17 },
});
