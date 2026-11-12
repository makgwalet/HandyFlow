import React, { useState } from "react";
import { StyleSheet, Text, TextInput } from "react-native";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { acknowledgeIncident, decideSwap, getSiteOverview, getSwapsToDecide, resolveIncident } from "@/api/guardWork";
import { Button } from "@/ui/Button";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";

const time = (iso: string) => new Date(iso).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });

export function SupervisorScreen() {
  const qc = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [resolving, setResolving] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const q = useQuery({ queryKey: ["siteOverview"], queryFn: getSiteOverview, retry: false, refetchInterval: 60_000 });
  const swapsQ = useQuery({ queryKey: ["swapsToDecide"], queryFn: getSwapsToDecide, retry: false, refetchInterval: 60_000 });
  const acted = {
    onSuccess: () => {
      setError(null);
      void qc.invalidateQueries({ queryKey: ["siteOverview"] });
      void qc.invalidateQueries({ queryKey: ["swapsToDecide"] });
    },
    onError: (e: unknown) => setError(e instanceof Error ? e.message : "That did not work"),
  };
  const decide = useMutation({ mutationFn: (v: { id: string; approve: boolean }) => decideSwap(v.id, v.approve, ""), ...acted });
  const ack = useMutation({ mutationFn: (id: string) => acknowledgeIncident(id), ...acted });
  const resolve = useMutation({ mutationFn: (id: string) => resolveIncident(id, note), ...acted, onSuccess: () => { setResolving(null); setNote(""); acted.onSuccess(); } });
  if (q.isLoading) return <Screen title="Site overview"><Text style={s.muted}>Loading...</Text></Screen>;
  if (q.isError || !q.data) {
    return (
      <Screen title="Site overview">
        <Text accessibilityRole="alert" style={s.err}>{q.error instanceof Error ? q.error.message : "Could not load the overview"}</Text>
        <Button label="Try again" onPress={() => void q.refetch()} />
      </Screen>
    );
  }
  const o = q.data;
  return (
    <Screen title={o.siteName}>
      <Text style={s.muted}>Updated {time(o.generatedAt)}. Refreshes every minute while open.</Text>
      <Text style={s.head}>On duty ({o.onDuty.length})</Text>
      {o.onDuty.length === 0 ? <Text style={s.muted}>Nobody is on an active shift here.</Text> : null}
      {o.onDuty.map((g) => (
        <Card key={g.guardId}>
          <Text style={s.strong}>{g.name}</Text>
          <Text style={s.muted}>
            {time(g.shiftStart)} to {time(g.shiftEnd)}{g.overrunning ? ". Overrunning" : ""}
          </Text>
          <Text style={g.roundsMissed > 0 ? s.warn : s.muted}>
            Rounds: {g.roundsComplete} done, {g.roundsMissed} missed, {g.roundsTotal} in total
          </Text>
          <Text style={s.muted}>
            {g.lastScanAt ? `Last scan ${g.lastScanCheckpoint ?? ""} at ${time(g.lastScanAt)}` : "No scan yet"}
            {g.gpsState ? `. Position: ${g.gpsState.toLowerCase()}` : ""}
          </Text>
        </Card>
      ))}
      {error ? <Text accessibilityRole="alert" style={s.err}>{error}</Text> : null}
      <Text style={s.head}>Swaps waiting for you ({swapsQ.data?.length ?? 0})</Text>
      {(swapsQ.data ?? []).length === 0 ? <Text style={s.muted}>None waiting.</Text> : null}
      {(swapsQ.data ?? []).map((w) => (
        <Card key={w.id}>
          <Text style={s.strong}>{w.shiftSummary ?? "Shift"}</Text>
          <Text style={s.muted}>{w.requestingGuardName ?? "A guard"} to {w.proposedGuardName ?? "a colleague"}{w.reason ? `: ${w.reason}` : ""}</Text>
          <Button label="Approve" onPress={() => decide.mutate({ id: w.id, approve: true })} loading={decide.isPending && decide.variables?.id === w.id && decide.variables.approve} />
          <Button label="Refuse" tone="plain" onPress={() => decide.mutate({ id: w.id, approve: false })} loading={decide.isPending && decide.variables?.id === w.id && !decide.variables.approve} />
        </Card>
      ))}
      <Text style={s.head}>Open incidents, last 24 hours ({o.openIncidents.length})</Text>
      {o.openIncidents.length === 0 ? <Text style={s.muted}>None open.</Text> : null}
      {o.openIncidents.map((i) => (
        <Card key={i.id}>
          <Text style={s.strong}>{i.title}</Text>
          <Text style={s.muted}>{i.severity.toLowerCase()}, {i.status.toLowerCase()}, {time(i.createdAt)}</Text>
          {i.status === "OPEN" ? <Button label="Acknowledge" tone="plain" onPress={() => ack.mutate(i.id)} loading={ack.isPending && ack.variables === i.id} /> : null}
          {resolving === i.id ? (
            <>
              <TextInput style={s.input} value={note} onChangeText={setNote} multiline maxLength={1000} placeholder="How was it dealt with?" placeholderTextColor={colors.muted} accessibilityLabel="Resolution note" />
              <Button label="Save as resolved" onPress={() => resolve.mutate(i.id)} loading={resolve.isPending} disabled={note.trim().length < 3} />
            </>
          ) : (
            <Button label="Resolve" tone="plain" onPress={() => { setResolving(i.id); setNote(""); }} />
          )}
        </Card>
      ))}
      <Button label="Refresh now" tone="plain" onPress={() => void q.refetch()} loading={q.isFetching} />
    </Screen>
  );
}

const s = StyleSheet.create({
  head: { color: colors.text, fontWeight: "700", fontSize: 17 },
  strong: { color: colors.text, fontWeight: "700", fontSize: 16 },
  muted: { color: colors.muted },
  warn: { color: "#FBBF24" },
  err: { color: colors.danger },
  input: { backgroundColor: colors.surface, color: colors.text, borderColor: colors.border, borderWidth: 1, borderRadius: 12, minHeight: 80, padding: 12, fontSize: 16, textAlignVertical: "top" },
});
