import React, { useState } from "react";
import { RefreshControl, ScrollView, Text } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { api } from "@/api/client";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { Card, styles as ui } from "@/ui/Screen";
import { SyncBanner } from "@/ui/SyncBanner";
import { colors } from "@/ui/theme";
import { failText, usePendingRequests, useRefreshYard } from "./hooks";
import { dayText, validateDecline } from "./stays";

const base = "/api/v1/fuel/truck-stop/stay-requests";

/** Driver stay requests waiting for a decision. Approving books the stay through the normal rules; a refusal leaves it pending, with the reason. */
export function StayRequestsScreen() {
  const { requests, isLoading, error, refetch } = usePendingRequests();
  const refresh = useRefreshYard();
  const [refreshing, setRefreshing] = useState(false);
  const [declining, setDeclining] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [msg, setMsg] = useState<Record<string, string>>({});

  async function decide(id: string, kind: "approve" | "decline") {
    if (kind === "decline") {
      const problem = validateDecline(reason);
      if (problem) { setMsg((m) => ({ ...m, [id]: problem })); return; }
    }
    setBusy(id); setMsg((m) => ({ ...m, [id]: "" }));
    try {
      await api(`${base}/${id}/${kind}`, { method: "POST", body: kind === "decline" ? { reason: reason.trim() } : {} });
      setDeclining(null); setReason("");
      await refresh();
    } catch (e) { setMsg((m) => ({ ...m, [id]: failText(e) })); } finally { setBusy(null); }
  }

  return (
    <SafeAreaView style={ui.root} edges={["bottom"]}>
      <SyncBanner />
      <ScrollView contentContainerStyle={ui.body}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={async () => { setRefreshing(true); await refetch(); setRefreshing(false); }} tintColor={colors.text} />}>
        <Text accessibilityRole="header" style={ui.title}>Stay requests</Text>
        {error ? <Card><Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 16 }}>{error}</Text></Card> : null}
        {isLoading ? <Text style={{ color: colors.muted }}>Loading…</Text> : null}
        {!isLoading && !error && requests.length === 0 ? <Card><Text style={{ color: colors.text, fontSize: 17 }}>No requests waiting.</Text></Card> : null}
        {requests.map((r) => (
          <Card key={r.id}>
            <Text style={{ color: colors.text, fontSize: 18, fontWeight: "700" }}>{r.vehicleRegistration}{r.customerName ? ` · ${r.customerName}` : ""}</Text>
            <Text style={{ color: colors.muted }}>{dayText(r.arrivalDate)} to {dayText(r.departureDate)} · {r.nights} night{r.nights === 1 ? "" : "s"}{r.driverName ? ` · ${r.driverName}` : ""}</Text>
            {r.note ? <Text style={{ color: colors.muted }}>Note: {r.note}</Text> : null}
            {msg[r.id] ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5" }}>{msg[r.id]}</Text> : null}
            {declining === r.id ? (
              <>
                <Field label="Reason for declining" value={reason} onChangeText={setReason} />
                <Button label="Send decline" tone="danger" loading={busy === r.id} onPress={() => void decide(r.id, "decline")} />
                <Button label="Cancel" tone="plain" onPress={() => { setDeclining(null); setReason(""); }} />
              </>
            ) : (
              <>
                <Button label={`Approve ${r.vehicleRegistration}`} loading={busy === r.id} disabled={!!busy} onPress={() => void decide(r.id, "approve")} />
                <Button label={`Decline ${r.vehicleRegistration}`} tone="plain" disabled={!!busy} onPress={() => { setDeclining(r.id); setReason(""); }} />
              </>
            )}
          </Card>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}
