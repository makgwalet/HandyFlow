import React, { useRef, useState } from "react";
import { StyleSheet, Text, TextInput } from "react-native";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as Crypto from "expo-crypto";
import { getDeviceId } from "@/auth/deviceStore";
import { ENTRY_TYPES, getAccessPoints, getOnSite, logArrival, logDeparture, type EntryType } from "@/api/guardWork";
import { Button } from "@/ui/Button";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";

const word = (v: string) => v.charAt(0) + v.slice(1).toLowerCase().replace("_", " ");
const time = (iso: string) => new Date(iso).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });

function Field(p: { label: string; value: string; onChange: (v: string) => void; autoCapitalize?: "none" | "characters" | "words" }) {
  return (
    <>
      <Text style={s.muted}>{p.label}</Text>
      <TextInput style={s.input} value={p.value} onChangeText={p.onChange} autoCapitalize={p.autoCapitalize ?? "words"} accessibilityLabel={p.label} maxLength={120} />
    </>
  );
}

export function GateScreen() {
  const qc = useQueryClient();
  const [gateId, setGateId] = useState<string | null>(null);
  const [type, setType] = useState<EntryType>("VISITOR");
  const [name, setName] = useState("");
  const [idNumber, setIdNumber] = useState("");
  const [company, setCompany] = useState("");
  const [host, setHost] = useState("");
  const [purpose, setPurpose] = useState("");
  const [reg, setReg] = useState("");
  const [error, setError] = useState<string | null>(null);
  const clientRef = useRef(Crypto.randomUUID()); // one per arrival form

  const gates = useQuery({ queryKey: ["accessPoints"], queryFn: getAccessPoints });
  const onSite = useQuery({ queryKey: ["onSite"], queryFn: getOnSite });
  const gate = gateId ?? gates.data?.[0]?.id ?? null;

  const arrive = useMutation({
    mutationFn: async () =>
      logArrival({
        clientRef: clientRef.current, deviceHardwareId: await getDeviceId(), accessPointId: gate as string,
        entryType: type, personName: name, idNumber, company, hostName: host, purpose, vehicleRegistration: reg,
      }),
    onSuccess: () => {
      setError(null);
      setName(""); setIdNumber(""); setCompany(""); setHost(""); setPurpose(""); setReg("");
      clientRef.current = Crypto.randomUUID();
      void qc.invalidateQueries({ queryKey: ["onSite"] });
    },
    onError: (e) => setError(e instanceof Error ? e.message : "Could not log the arrival"),
  });
  const depart = useMutation({
    mutationFn: async (id: string) => logDeparture(id, await getDeviceId()),
    onSuccess: () => {
      setError(null);
      void qc.invalidateQueries({ queryKey: ["onSite"] });
    },
    onError: (e) => setError(e instanceof Error ? e.message : "Could not log the departure"),
  });

  if (gates.isError) {
    return (
      <Screen title="Gate log">
        <Text accessibilityRole="alert" style={s.err}>{gates.error instanceof Error ? gates.error.message : "Could not load the gates"}</Text>
        <Text style={s.muted}>Start your shift first, and check your signal.</Text>
        <Button label="Try again" onPress={() => void gates.refetch()} />
      </Screen>
    );
  }
  const list = onSite.data ?? [];
  return (
    <Screen title="Gate log">
      {error ? <Text accessibilityRole="alert" style={s.err}>{error}</Text> : null}
      <Text style={s.head}>On site now ({list.length})</Text>
      {list.length === 0 && !onSite.isLoading ? <Text style={s.muted}>Nobody logged in.</Text> : null}
      {list.map((e) => (
        <Card key={e.id}>
          <Text style={s.strong}>{e.personName}{e.company ? `, ${e.company}` : ""}</Text>
          <Text style={s.muted}>{word(e.entryType)} in at {time(e.loggedInAt)}{e.vehicleRegistration ? `, ${e.vehicleRegistration}` : ""}{e.status === "OVERSTAYED" ? ". Overstayed" : ""}</Text>
          <Button label="Log departure" tone="plain" onPress={() => depart.mutate(e.id)} loading={depart.isPending && depart.variables === e.id} />
        </Card>
      ))}

      <Text style={s.head}>Log an arrival</Text>
      {(gates.data ?? []).length > 1 ? (gates.data ?? []).map((g) => <Button key={g.id} label={g.name} tone={g.id === gate ? "primary" : "plain"} onPress={() => setGateId(g.id)} />) : null}
      {ENTRY_TYPES.map((t) => <Button key={t} label={word(t)} tone={t === type ? "primary" : "plain"} onPress={() => setType(t)} />)}
      <Field label="Name" value={name} onChange={setName} />
      <Field label="ID number (optional)" value={idNumber} onChange={setIdNumber} autoCapitalize="characters" />
      <Field label="Company (optional)" value={company} onChange={setCompany} />
      <Field label="Visiting (optional)" value={host} onChange={setHost} />
      <Field label="Reason (optional)" value={purpose} onChange={setPurpose} autoCapitalize="none" />
      <Field label="Vehicle registration (optional)" value={reg} onChange={setReg} autoCapitalize="characters" />
      <Button label="Log arrival" onPress={() => arrive.mutate()} loading={arrive.isPending} disabled={!gate || name.trim().length < 2} />
      <Text style={s.muted}>Needs signal. ID numbers are masked once saved.</Text>
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
