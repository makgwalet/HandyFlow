import React from "react";
import { StyleSheet, Text } from "react-native";
import { useQuery } from "@tanstack/react-query";
import { getSiteOverview } from "@/api/guardWork";
import { Button } from "@/ui/Button";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";

const time = (iso: string) => new Date(iso).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });

export function SupervisorScreen() {
  const q = useQuery({ queryKey: ["siteOverview"], queryFn: getSiteOverview, retry: false, refetchInterval: 60_000 });
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
      <Text style={s.head}>Open incidents, last 24 hours ({o.openIncidents.length})</Text>
      {o.openIncidents.length === 0 ? <Text style={s.muted}>None open.</Text> : null}
      {o.openIncidents.map((i) => (
        <Card key={i.id}>
          <Text style={s.strong}>{i.title}</Text>
          <Text style={s.muted}>{i.severity.toLowerCase()}, {i.status.toLowerCase()}, {time(i.createdAt)}</Text>
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
});
