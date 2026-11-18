import React, { useState } from "react";
import { Linking, Pressable, Text } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { api } from "@/api/client";
import { useAuth } from "@/auth/AuthContext";
import type { RootStackParamList } from "@/navigation/types";
import { Button } from "@/ui/Button";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import { failText, useBays, useRefreshYard, useYard } from "./hooks";
import { ACTION_LABEL, ACTION_PATH, actionsFor, activeBays, canRunTruckStop, dayText, todayZA, type Stay, type StayAction } from "./stays";

type Props = NativeStackScreenProps<RootStackParamList, "StayDetail">;
const base = "/api/v1/fuel/truck-stop/stays";

export function StayDetailScreen({ route }: Props) {
  const { stayId } = route.params;
  const { session } = useAuth();
  const { stays } = useYard();
  const { bays } = useBays();
  const refresh = useRefreshYard();
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [override, setOverride] = useState<Stay | null>(null);
  const s = override && override.id === stayId ? override : stays.find((x) => x.id === stayId);
  if (!s) return <Screen title="Stay"><Text style={{ color: colors.muted }}>This stay is not on the list.</Text></Screen>;
  const may = canRunTruckStop(session?.permissions ?? []);
  const actions = may ? actionsFor(s, todayZA()) : [];
  const bayName = bays.find((b) => b.id === s.bayId)?.name;

  async function run(key: string, call: () => Promise<Stay>) {
    setError(null); setMessage(null); setBusy(key);
    try { const out = await call(); setOverride(out); await refresh(); setMessage(key === "bay" ? "Bay saved." : "Saved."); }
    catch (e) { setError(failText(e)); } finally { setBusy(null); }
  }
  const act = (a: StayAction) => run(a, () => api<Stay>(`${base}/${stayId}/${ACTION_PATH[a]}`, { method: "POST" }));
  const setBay = (bayId: string | null) => run("bay", () => api<Stay>(`${base}/${stayId}/bay`, { method: "PUT", body: { bayId } }));

  return (
    <Screen title={s.vehicleRegistration}>
      <Card>
        <Text style={{ color: colors.text, fontSize: 17 }}>{s.status.replace(/_/g, " ").toLowerCase()}{s.overstaying ? " · overstaying" : ""}</Text>
        <Text style={{ color: colors.muted }}>{dayText(s.arrivalDate)} to {dayText(s.departureDate)} · {s.nights} night{s.nights === 1 ? "" : "s"}</Text>
        {s.driverName ? <Text style={{ color: colors.muted }}>Driver: {s.driverName}</Text> : null}
        <Text style={{ color: colors.muted }}>{s.accountId ? "Charged to the customer's account" : "Pays at the gate"}{s.amount != null ? ` · R ${s.amount.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : ""}</Text>
        {s.invoiceNumber ? <Text style={{ color: colors.muted }}>Invoice {s.invoiceNumber}</Text> : null}
        {s.invoiceState === "FAILED" ? <Text style={{ color: "#FCA5A5" }}>Invoice not raised: {s.invoiceError ?? "see the office"}</Text> : null}
        {s.note ? <Text style={{ color: colors.muted }}>Note: {s.note}</Text> : null}
      </Card>
      {s.driverPhone ? <Button label={`Call ${s.driverName ?? "driver"}`} tone="plain" onPress={() => void Linking.openURL(`tel:${s.driverPhone!.replace(/[^\d+]/g, "")}`)} /> : null}
      {error ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 15 }}>{error}</Text> : null}
      {message ? <Text accessibilityRole="alert" style={{ color: "#86EFAC", fontSize: 15 }}>{message}</Text> : null}
      {actions.map((a) => <Button key={a} label={ACTION_LABEL[a]} tone={a === "noShow" ? "plain" : "primary"} loading={busy === a} disabled={!!busy} onPress={() => void act(a)} />)}
      {may && (s.status === "BOOKED" || s.status === "CHECKED_IN") ? (
        <>
          <Text accessibilityRole="header" style={{ color: colors.text, fontSize: 18, fontWeight: "700" }}>Bay{bayName ? `: ${bayName}` : ": none"}</Text>
          {activeBays(bays).map((b) => {
            const on = b.id === s.bayId;
            return (
              <Pressable key={b.id} accessibilityRole="radio" accessibilityState={{ selected: on }} accessibilityLabel={`Bay ${b.name}`} disabled={!!busy} onPress={() => void setBay(on ? null : b.id)}
                style={{ minHeight: 48, justifyContent: "center", paddingHorizontal: 14, borderRadius: 10, borderWidth: 1, borderColor: on ? colors.primary : colors.border }}>
                <Text style={{ color: colors.text, fontSize: 16, fontWeight: on ? "700" : "400" }}>{b.name}</Text>
              </Pressable>
            );
          })}
        </>
      ) : null}
    </Screen>
  );
}
