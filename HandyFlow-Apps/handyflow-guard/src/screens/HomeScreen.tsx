import React, { useState } from "react";
import { StyleSheet, Text, View } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/auth/AuthContext";
import { getDeviceId } from "@/auth/deviceStore";
import { clockIn, getCurrentSession, getMyShifts, type Shift } from "@/api/guardWork";
import { getPosition } from "@/location";
import { Button } from "@/ui/Button";
import { useOutbox } from "@/offline/OutboxProvider";
import { PanicButton } from "@/ui/PanicButton";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import type { RootStackParamList } from "./types";

type Props = NativeStackScreenProps<RootStackParamList, "Home">;

const fmt = (iso: string) =>
  new Date(iso).toLocaleString(undefined, { weekday: "short", hour: "2-digit", minute: "2-digit" });

export function HomeScreen({ navigation }: Props) {
  const { session, signOut } = useAuth();
  const qc = useQueryClient();
  const { pendingCount } = useOutbox();
  const [error, setError] = useState<string | null>(null);

  const deviceQ = useQuery({ queryKey: ["deviceId"], queryFn: getDeviceId, staleTime: Infinity });
  const shiftsQ = useQuery({ queryKey: ["myShifts"], queryFn: getMyShifts });
  const sessionQ = useQuery({
    queryKey: ["currentSession", deviceQ.data],
    queryFn: () => getCurrentSession(deviceQ.data as string),
    enabled: !!deviceQ.data,
  });

  const open = sessionQ.data ?? null;
  const shifts = shiftsQ.data ?? [];
  const current: Shift | undefined = open?.shiftId ? shifts.find((s) => s.id === open.shiftId) : undefined;
  const next: Shift | undefined = shifts.find((s) => s.status === "SCHEDULED") ?? shifts[0];

  const clockInM = useMutation({
    mutationFn: async () => {
      const position = await getPosition();
      return clockIn({
        deviceHardwareId: deviceQ.data as string,
        latitude: position?.latitude ?? null,
        longitude: position?.longitude ?? null,
        accuracyMetres: position?.accuracy ?? null,
      });
    },
    onSuccess: () => {
      setError(null);
      void qc.invalidateQueries({ queryKey: ["currentSession"] });
      void qc.invalidateQueries({ queryKey: ["myShifts"] });
    },
    onError: (e) => setError(e instanceof Error ? e.message : "Could not clock in"),
  });

  return (
    <Screen title={`Hello, ${session?.fullName ?? ""}`}>
      <Card>
        {open ? (
          <>
            <Text style={s.strong}>On duty{current ? ` at ${current.siteName}` : ""}</Text>
            {current ? <Text style={s.muted}>{fmt(current.startAt)} to {fmt(current.endAt)}</Text> : <Text style={s.muted}>No shift is linked to this session.</Text>}
          </>
        ) : next ? (
          <>
            <Text style={s.strong}>Next shift: {next.siteName}</Text>
            <Text style={s.muted}>{fmt(next.startAt)} to {fmt(next.endAt)}</Text>
          </>
        ) : (
          <Text style={s.muted}>{shiftsQ.isLoading ? "Loading your shifts..." : "No shifts in the next 7 days."}</Text>
        )}
        {error ? <Text accessibilityRole="alert" style={s.err}>{error}</Text> : null}
        {shiftsQ.isError ? <Text style={s.err}>Could not load your shifts. Pull down or try again.</Text> : null}
      </Card>

      {open ? (
        <View style={s.stack}>
          <Button label="Patrol rounds" onPress={() => navigation.navigate("Patrol")} />
          <Button label="My post orders" tone="plain" onPress={() => navigation.navigate("MyPost")} />
          <Button label="Equipment" tone="plain" onPress={() => navigation.navigate("Equipment", { sessionId: open.id })} />
          <Button label="Scan checkpoint" onPress={() => navigation.navigate("Scan", { shiftId: open.shiftId ?? null })} />
          <Button
            label="Report an incident"
            tone="plain"
            disabled={!current}
            onPress={() => current && navigation.navigate("Incident", { siteId: current.siteId, shiftId: open.shiftId ?? null })}
          />
          <Button label="End shift" tone="plain" onPress={() => navigation.navigate("ClockOut", { sessionId: open.id })} />
        </View>
      ) : (
        <Button label="Start shift" onPress={() => clockInM.mutate()} loading={clockInM.isPending} disabled={!deviceQ.data} />
      )}

      <Button label="Shift swaps" tone="plain" onPress={() => navigation.navigate("Swaps")} />
      <PanicButton />
      {pendingCount > 0 ? (
        <Text style={s.muted}>Sign out is available once your {pendingCount} waiting item(s) are sent, so nothing is lost.</Text>
      ) : null}
      <Button label="Sign out" tone="plain" disabled={pendingCount > 0} onPress={() => void signOut()} />
    </Screen>
  );
}

const s = StyleSheet.create({
  strong: { color: colors.text, fontSize: 18, fontWeight: "700" },
  muted: { color: colors.muted },
  err: { color: colors.danger },
  stack: { gap: 12 },
});
