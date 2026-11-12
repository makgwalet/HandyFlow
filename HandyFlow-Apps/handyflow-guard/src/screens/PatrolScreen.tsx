import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { useQuery } from "@tanstack/react-query";
import { getMyRounds } from "@/api/guardWork";
import { ApiError } from "@/api/client";
import { Button } from "@/ui/Button";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";

const time = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" }) : "";

export function PatrolScreen() {
  const q = useQuery({ queryKey: ["myRounds"], queryFn: getMyRounds, refetchInterval: 20_000 });
  const message =
    q.error instanceof ApiError && q.error.status === 409 ? q.error.message : q.isError ? "Could not load your rounds." : null;

  return (
    <Screen title="Patrol rounds">
      {message ? <Text accessibilityRole="alert" style={s.err}>{message}</Text> : null}
      {q.isLoading ? <Text style={s.muted}>Loading...</Text> : null}
      {q.data?.length === 0 ? <Text style={s.muted}>No rounds are planned for this shift.</Text> : null}
      {q.data?.map((r) => (
        <Card key={r.id}>
          <Text style={s.strong}>Round {r.roundNumber}: {r.status.replace("_", " ").toLowerCase()}</Text>
          <Text style={s.muted}>{time(r.expectedStartAt)}{r.expectedEndAt ? ` to ${time(r.expectedEndAt)}` : ""} · {r.checkpointsScanned} of {r.checkpointsExpected} scanned</Text>
          <View style={s.list}>
            {r.checkpoints.map((c) => (
              <Text key={c.checkpointId} style={c.scanned ? s.done : s.todo}>
                {c.scanned ? "✓" : "○"} {c.sequence}. {c.name}
              </Text>
            ))}
          </View>
        </Card>
      ))}
      <Button label="Refresh" tone="plain" onPress={() => void q.refetch()} loading={q.isFetching} />
    </Screen>
  );
}

const s = StyleSheet.create({
  strong: { color: colors.text, fontSize: 17, fontWeight: "700" },
  muted: { color: colors.muted },
  err: { color: colors.danger },
  list: { marginTop: 6, gap: 4 },
  done: { color: colors.ok, fontSize: 16 },
  todo: { color: colors.text, fontSize: 16 },
});
