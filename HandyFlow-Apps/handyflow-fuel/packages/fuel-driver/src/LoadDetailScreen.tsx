import React, { useState } from "react";
import { Text } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import * as ImagePicker from "expo-image-picker";
import * as Location from "expo-location";
import { keepFile } from "@/files";
import type { RootStackParamList } from "@/navigation/types";
import { useOutbox } from "@/offline/OutboxProvider";
import { Button } from "@/ui/Button";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import { offloadGate } from "./arrival";
import { useArrival } from "./useArrival";
import { actionsFor, addressText, loadSummary, queuedKinds, windowText } from "./loads";
import { useLoads } from "./useLoads";

type Props = NativeStackScreenProps<RootStackParamList, "LoadDetail">;

/** Best-effort position for a photo. Never blocks the photo: with no permission or no fix the photo is saved without one. */
async function tryPosition(): Promise<{ latitude: number; longitude: number; accuracyM: number | null } | null> {
  try {
    const perm = await Location.requestForegroundPermissionsAsync();
    if (perm.status !== "granted") return null;
    const p = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
    return { latitude: p.coords.latitude, longitude: p.coords.longitude, accuracyM: p.coords.accuracy ?? null };
  } catch {
    return null;
  }
}

export function LoadDetailScreen({ route, navigation }: Props) {
  const { loadId } = route.params;
  const { loads, isLoading } = useLoads();
  const { pending, enqueue } = useOutbox();
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<string | null>(null);
  const { check } = useArrival(loadId);
  const load = loads.find((l) => l.id === loadId);

  if (!load) {
    return <Screen title="Load"><Text style={{ color: colors.muted }}>{isLoading ? "Loading…" : "This load is no longer on your list."}</Text></Screen>;
  }

  const queued = queuedKinds(pending, load.id);
  const can = actionsFor(load, queued);
  const w = windowText(load.windowStart, load.windowEnd);
  const gate = offloadGate(check);

  async function start() {
    setBusy(true);
    try {
      await enqueue("DISPATCH", { loadId });
      setNote("Trip started. It will reach the office as soon as there is signal.");
    } finally { setBusy(false); }
  }

  async function photo() {
    setBusy(true);
    setNote(null);
    try {
      const cam = await ImagePicker.requestCameraPermissionsAsync();
      if (cam.status !== "granted") { setNote("Camera permission is needed to take a photo."); return; }
      const shot = await ImagePicker.launchCameraAsync({ mediaTypes: ["images"], quality: 0.6 });
      if (shot.canceled || !shot.assets[0]) return;
      const a = shot.assets[0];
      const pos = await tryPosition();
      const kept = await keepFile(a.uri, a.fileName ?? `delivery-${Date.now()}.jpg`); // the camera's cache can be emptied before the upload runs
      await enqueue("EVIDENCE", {
        loadId, kind: "PHOTO", uri: kept, name: a.fileName ?? `delivery-${Date.now()}.jpg`, mimeType: a.mimeType ?? "image/jpeg",
        latitude: pos?.latitude ?? null, longitude: pos?.longitude ?? null, accuracyM: pos?.accuracyM ?? null,
      });
      setNote("Photo saved. It will upload when there is signal.");
    } finally { setBusy(false); }
  }

  return (
    <Screen title={`${load.fuelType} · ${load.litresOrdered.toLocaleString("en-ZA")} L`}>
      <Card>
        <Text style={{ color: colors.text, fontSize: 17 }}>{addressText(load.deliveryAddress)}</Text>
        <Text style={{ color: colors.muted }}>{loadSummary(load, queued)}</Text>
        {w ? <Text style={{ color: colors.muted }}>Delivery window {w}</Text> : null}
        {load.vehicleReg ? <Text style={{ color: colors.muted }}>Vehicle {load.vehicleReg}</Text> : null}
        {load.loadedLitres != null ? <Text style={{ color: colors.muted }}>Loaded {load.loadedLitres.toLocaleString("en-ZA")} L{load.sealNumbers ? ` · seals ${load.sealNumbers}` : ""}</Text> : null}
        {load.receiptNumber ? <Text style={{ color: colors.muted }}>Receipt {load.receiptNumber}</Text> : null}
      </Card>
      {note ? <Text accessibilityRole="alert" style={{ color: "#86EFAC" }}>{note}</Text> : null}
      {can.start ? <Button label="Start trip" onPress={start} loading={busy} /> : null}
      {can.complete && !gate.ok ? (
        <>
          <Text style={{ color: "#FCD34D", fontSize: 15 }}>{gate.reason}</Text>
          <Button label="Arrival check" onPress={() => navigation.navigate("ArrivalCheck", { loadId })} />
        </>
      ) : null}
      {can.complete && gate.ok ? <Button label="Record delivery" onPress={() => navigation.navigate("CompleteLoad", { loadId })} /> : null}
      {can.complete && gate.ok ? <Button label="Receiver's signature" tone="plain" onPress={() => navigation.navigate("Signature", { loadId })} /> : null}
      {can.evidence ? <Button label="Take a photo" tone="plain" onPress={photo} loading={busy} /> : null}
      {can.failed ? <Button label="Could not deliver" tone="danger" onPress={() => navigation.navigate("FailedAttempt", { loadId })} /> : null}
    </Screen>
  );
}
