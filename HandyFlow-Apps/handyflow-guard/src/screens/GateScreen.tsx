import React, { useEffect, useRef, useState } from "react";
import { StyleSheet, Text, TextInput } from "react-native";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as Crypto from "expo-crypto";
import { getDeviceId } from "@/auth/deviceStore";
import { useAuth } from "@/auth/AuthContext";
import { ApiError } from "@/api/client";
import {
  attachGatePhoto, ENTRY_TYPES, GATE_PHOTO_TYPES, getAccessPoints, getOnSite, logArrival, logDeparture,
  type AccessPoint, type EntryType, type GateEntry, type GatePhotoType,
} from "@/api/guardWork";
import { loadGateCache, saveAccessPoints, saveOnSite } from "@/gateCache";
import { useOutbox } from "@/offline/OutboxProvider";
import { gateArrivalItem, gateExitItem, gatePhotoItem, type EntryRef } from "@/offline/payloads";
import { takePhoto } from "@/photos";
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

interface Loaded<T> { data: T; savedAt: string | null }

export function GateScreen() {
  const qc = useQueryClient();
  const { session } = useAuth();
  const guardId = session?.guardId ?? "";
  const { enqueue, context, pending } = useOutbox();
  const [gateId, setGateId] = useState<string | null>(null);
  const [type, setType] = useState<EntryType>("VISITOR");
  const [name, setName] = useState("");
  const [idNumber, setIdNumber] = useState("");
  const [company, setCompany] = useState("");
  const [host, setHost] = useState("");
  const [purpose, setPurpose] = useState("");
  const [reg, setReg] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const clientRef = useRef(Crypto.randomUUID()); // one per arrival form

  // Online: the live list, remembered on the phone. No signal: the remembered copy, labelled with when it was saved.
  const gates = useQuery({
    queryKey: ["accessPoints", guardId],
    retry: false,
    queryFn: async (): Promise<Loaded<AccessPoint[]>> => {
      try {
        const data = await getAccessPoints();
        void saveAccessPoints(guardId, data);
        return { data, savedAt: null };
      } catch (e) {
        const cached = e instanceof ApiError && e.isNetwork ? await loadGateCache(guardId) : null;
        if (cached?.accessPoints) return { data: cached.accessPoints, savedAt: cached.savedAt };
        throw e;
      }
    },
  });
  const onSite = useQuery({
    queryKey: ["onSite", guardId],
    retry: false,
    queryFn: async (): Promise<Loaded<GateEntry[]>> => {
      try {
        const data = await getOnSite();
        void saveOnSite(guardId, data);
        return { data, savedAt: null };
      } catch (e) {
        const cached = e instanceof ApiError && e.isNetwork ? await loadGateCache(guardId) : null;
        if (cached?.onSite) return { data: cached.onSite, savedAt: cached.savedAt };
        throw e;
      }
    },
  });
  const gateList = gates.data?.data ?? [];
  const gate = gateId ?? gateList[0]?.id ?? null;

  // When something waiting has been sent (the queue got shorter), read the list again.
  const waiting = pending.length;
  useEffect(() => {
    void qc.invalidateQueries({ queryKey: ["onSite"] });
  }, [waiting, qc]);

  const queuedFor = (kind: string, ref: string) =>
    pending.filter((i) => i.kind === kind && (i.payload["entryId"] === ref || i.payload["entryRef"] === ref));
  const departureSaved = (ref: string) => queuedFor("GATE_EXIT", ref).length > 0;
  const photosSaved = (ref: string) => queuedFor("GATE_PHOTO", ref).length;

  const arrive = useMutation({
    mutationFn: async (): Promise<"sent" | "queued"> => {
      const input = {
        deviceHardwareId: await getDeviceId(), accessPointId: gate as string, entryType: type, personName: name,
        idNumber, company, hostName: host, purpose, vehicleRegistration: reg,
      };
      try {
        await logArrival({ clientRef: clientRef.current, ...input });
        return "sent";
      } catch (e) {
        if (e instanceof ApiError && e.isNetwork) {
          // Saved under the form's own reference, so if the server did get the first try it recognises this one.
          await enqueue(gateArrivalItem(input, { ...context(), newId: () => clientRef.current }));
          return "queued";
        }
        throw e;
      }
    },
    onSuccess: (r) => {
      setError(null);
      setNote(r === "queued" ? "Saved on this phone. The visitor will be logged as soon as there is signal." : null);
      setName(""); setIdNumber(""); setCompany(""); setHost(""); setPurpose(""); setReg("");
      clientRef.current = Crypto.randomUUID();
      void qc.invalidateQueries({ queryKey: ["onSite"] });
    },
    onError: (e) => setError(e instanceof Error ? e.message : "Could not log the arrival"),
  });

  const photo = useMutation({
    mutationFn: async (v: { ref: EntryRef; docType: GatePhotoType }): Promise<"sent" | "queued"> => {
      const uri = await takePhoto();
      if (!uri) throw new Error("No photo taken. Allow the camera in Settings if you want to add one.");
      const photoRef = Crypto.randomUUID();
      const keep = async () => {
        await enqueue(gatePhotoItem({ ...v.ref, docType: v.docType, localUri: uri }, { ...context(), newId: () => photoRef }));
        return "queued" as const;
      };
      const id = v.ref.entryId;
      if (id === undefined) return keep(); // the arrival itself has not reached the server yet
      try {
        await attachGatePhoto(id, v.docType, uri, photoRef);
        return "sent";
      } catch (e) {
        if (e instanceof ApiError && e.isNetwork) return keep();
        throw e;
      }
    },
    onSuccess: (r) => { setError(null); setNote(r === "queued" ? "Photo saved on this phone. It will be sent when there is signal." : "Photo saved."); },
    onError: (e) => { setNote(null); setError(e instanceof Error ? e.message : "Could not save the photo"); },
  });

  const depart = useMutation({
    mutationFn: async (ref: EntryRef): Promise<"sent" | "queued"> => {
      const device = await getDeviceId();
      const keep = async () => {
        await enqueue(gateExitItem({ ...ref, deviceHardwareId: device }, context()));
        return "queued" as const;
      };
      const id = ref.entryId;
      if (id === undefined) return keep();
      try {
        await logDeparture(id, device);
        return "sent";
      } catch (e) {
        if (e instanceof ApiError && e.isNetwork) return keep();
        throw e;
      }
    },
    onSuccess: (r) => {
      setError(null);
      if (r === "queued") setNote("Departure saved on this phone, with the time you logged it. It will be sent when there is signal.");
      void qc.invalidateQueries({ queryKey: ["onSite"] });
    },
    onError: (e) => setError(e instanceof Error ? e.message : "Could not log the departure"),
  });

  if (gates.isError) {
    return (
      <Screen title="Gate log">
        <Text accessibilityRole="alert" style={s.err}>{gates.error instanceof Error ? gates.error.message : "Could not load the gates"}</Text>
        <Text style={s.muted}>Start your shift first, and check your signal. Open this screen once with signal so the gates can be saved on the phone.</Text>
        <Button label="Try again" onPress={() => void gates.refetch()} />
      </Screen>
    );
  }

  const list = onSite.data?.data ?? [];
  const savedAt = onSite.data?.savedAt ?? gates.data?.savedAt ?? null;
  const waitingArrivals = pending.filter((i) => i.kind === "GATE_ARRIVAL");

  const actions = (ref: EntryRef, key: string) => {
    const refKey = ref.entryId ?? ref.entryRef;
    return (
      <>
        {GATE_PHOTO_TYPES.map((t) => (
          <Button key={t.docType} label={t.label} tone="plain" onPress={() => photo.mutate({ ref, docType: t.docType })}
            loading={photo.isPending && photo.variables?.docType === t.docType && (photo.variables.ref.entryId ?? photo.variables.ref.entryRef) === refKey} />
        ))}
        {photosSaved(refKey) > 0 ? <Text style={s.muted}>{photosSaved(refKey)} photo(s) saved on this phone, waiting to send.</Text> : null}
        {departureSaved(refKey) ? (
          <Text style={s.muted}>Departure saved on this phone, waiting to send.</Text>
        ) : (
          <Button key={key} label="Log departure" tone="plain" onPress={() => depart.mutate(ref)}
            loading={depart.isPending && (depart.variables?.entryId ?? depart.variables?.entryRef) === refKey} />
        )}
      </>
    );
  };

  return (
    <Screen title="Gate log">
      {savedAt ? <Text style={s.muted}>No signal. This list was saved on this phone at {new Date(savedAt).toLocaleString()}; people may have come or gone since.</Text> : null}
      {error ? <Text accessibilityRole="alert" style={s.err}>{error}</Text> : null}
      {note ? <Text style={s.muted}>{note}</Text> : null}
      <Text style={s.head}>On site now ({list.length + waitingArrivals.length})</Text>
      {list.length + waitingArrivals.length === 0 && !onSite.isLoading ? <Text style={s.muted}>Nobody logged in.</Text> : null}
      {waitingArrivals.map((i) => (
        <Card key={i.id}>
          <Text style={s.strong}>{String(i.payload["personName"])}{i.payload["company"] ? `, ${String(i.payload["company"])}` : ""}</Text>
          <Text style={s.muted}>Saved on this phone at {time(i.recordedAt)}. Waiting to be sent.</Text>
          {actions({ entryRef: i.id }, i.id)}
        </Card>
      ))}
      {list.map((e) => (
        <Card key={e.id}>
          <Text style={s.strong}>{e.personName}{e.company ? `, ${e.company}` : ""}</Text>
          <Text style={s.muted}>{word(e.entryType)} in at {time(e.loggedInAt)}{e.vehicleRegistration ? `, ${e.vehicleRegistration}` : ""}{e.status === "OVERSTAYED" ? ". Overstayed" : ""}</Text>
          {actions({ entryId: e.id }, e.id)}
        </Card>
      ))}

      <Text style={s.head}>Log an arrival</Text>
      {gateList.length > 1 ? gateList.map((g) => <Button key={g.id} label={g.name} tone={g.id === gate ? "primary" : "plain"} onPress={() => setGateId(g.id)} />) : null}
      {ENTRY_TYPES.map((t) => <Button key={t} label={word(t)} tone={t === type ? "primary" : "plain"} onPress={() => setType(t)} />)}
      <Field label="Name" value={name} onChange={setName} />
      <Field label="ID number (optional)" value={idNumber} onChange={setIdNumber} autoCapitalize="characters" />
      <Field label="Company (optional)" value={company} onChange={setCompany} />
      <Field label="Visiting (optional)" value={host} onChange={setHost} />
      <Field label="Reason (optional)" value={purpose} onChange={setPurpose} autoCapitalize="none" />
      <Field label="Vehicle registration (optional)" value={reg} onChange={setReg} autoCapitalize="characters" />
      <Button label="Log arrival" onPress={() => arrive.mutate()} loading={arrive.isPending} disabled={!gate || name.trim().length < 2} />
      <Text style={s.muted}>Works without signal: arrivals, departures and photos are saved on the phone with the time you logged them and sent later. ID numbers are masked once saved.</Text>
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
