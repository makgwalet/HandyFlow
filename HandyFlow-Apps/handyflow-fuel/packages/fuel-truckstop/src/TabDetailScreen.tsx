import React, { useState } from "react";
import { Pressable, Text } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { api } from "@/api/client";
import { useAuth } from "@/auth/AuthContext";
import type { RootStackParamList } from "@/navigation/types";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import { failText } from "./hooks";
import { canRunTruckStop } from "./stays";
import { parseTab, TAB_BASE, useOfferings, useRefreshTabs, useTab } from "./tabHooks";
import { quantityOf, rands, tabActions, TILL_METHODS, validateQuantity, validateVoid, type Tab } from "./tabs";

type Props = NativeStackScreenProps<RootStackParamList, "TabDetail">;

/**
 * One driver tab: add services, take it off, then settle it at the till or charge it to the customer's account, or void it
 * with a reason. Online only. The price of a line is never typed here: the server reads it when the line is added. Payment
 * itself stays with the till and the invoicing integration; this records how it was settled.
 */
export function TabDetailScreen({ route }: Props) {
  const { tabId } = route.params;
  const { session } = useAuth();
  const { tab: loaded, isLoading, error: loadError } = useTab(tabId);
  const { offerings } = useOfferings();
  const refresh = useRefreshTabs();
  const [current, setCurrent] = useState<Tab | null>(null);
  const [offeringId, setOfferingId] = useState<string>("");
  const [qty, setQty] = useState("1");
  const [voiding, setVoiding] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const t = current && current.id === tabId ? current : loaded;
  if (!t) return <Screen title="Tab"><Text style={{ color: loadError ? "#FCA5A5" : colors.muted }}>{loadError ?? (isLoading ? "Loading…" : "This tab is not on the list.")}</Text></Screen>;
  const may = canRunTruckStop(session?.permissions ?? []);
  const actions = may ? tabActions(t) : [];

  async function run(key: string, call: () => Promise<unknown>, done: string) {
    setError(null); setMessage(null); setBusy(key);
    try { setCurrent(parseTab(await call())); setMessage(done); await refresh(); }
    catch (e) { setError(failText(e)); } finally { setBusy(null); }
  }

  function addLine() {
    const problem = validateQuantity(qty);
    if (!offeringId) { setError("Choose the service."); return; }
    if (problem) { setError(problem); return; }
    void run("add", () => api(`${TAB_BASE}/${tabId}/lines`, { method: "POST", body: { offeringId, quantity: quantityOf(qty) } }), "Added.");
  }

  function confirmVoid() {
    const problem = validateVoid(reason);
    if (problem) { setError(problem); return; }
    void run("void", () => api(`${TAB_BASE}/${tabId}/void`, { method: "POST", body: { reason: reason.trim() } }), "Tab voided.").then(() => setVoiding(false));
  }

  return (
    <Screen title={t.vehicleRegistration}>
      <Card>
        <Text style={{ color: colors.text, fontSize: 17 }}>{t.status === "OPEN" ? "Open" : t.status === "SETTLED" ? "Settled" : "Voided"}{t.paymentMethod ? ` · ${t.paymentMethod.toLowerCase()}` : ""}</Text>
        {t.driverName ? <Text style={{ color: colors.muted }}>Driver: {t.driverName}</Text> : null}
        <Text style={{ color: colors.muted }}>{t.accountId ? "Charged to the customer's account" : "Paid at the till"}</Text>
        {t.voidReason ? <Text style={{ color: colors.muted }}>Voided: {t.voidReason}</Text> : null}
        {t.invoiceNumber ? <Text style={{ color: colors.muted }}>Invoice {t.invoiceNumber}</Text> : null}
        {t.invoiceState === "FAILED" ? <Text style={{ color: "#FCA5A5" }}>Invoice not raised: {t.invoiceError ?? "see the office"}</Text> : null}
      </Card>
      {t.lines.length === 0 ? <Card><Text style={{ color: colors.muted }}>No services on this tab yet.</Text></Card> : null}
      {t.lines.map((l) => (
        <Card key={l.id}>
          <Text style={{ color: colors.text }}>{l.description}</Text>
          <Text style={{ color: colors.muted }}>{l.quantity} × {rands(l.unitPrice)} = {rands(l.amount)}</Text>
          {actions.includes("addLine") ? <Button label={`Remove ${l.description}`} tone="plain" disabled={!!busy} onPress={() => void run(`rm-${l.id}`, () => api(`${TAB_BASE}/${tabId}/lines/${l.id}`, { method: "DELETE" }), "Removed.")} /> : null}
        </Card>
      ))}
      <Card><Text style={{ color: colors.text, fontSize: 18, fontWeight: "700" }}>Total {rands(t.total)} ex VAT</Text></Card>
      {error ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 15 }}>{error}</Text> : null}
      {message ? <Text accessibilityRole="alert" style={{ color: "#86EFAC", fontSize: 15 }}>{message}</Text> : null}
      {actions.includes("addLine") ? (
        <>
          <Text accessibilityRole="header" style={{ color: colors.text, fontSize: 18, fontWeight: "700" }}>Add a service</Text>
          {offerings.map((o) => {
            const on = o.id === offeringId;
            return (
              <Pressable key={o.id} accessibilityRole="radio" accessibilityState={{ selected: on }} accessibilityLabel={o.serviceName} onPress={() => setOfferingId(o.id)}
                style={{ minHeight: 48, justifyContent: "center", paddingHorizontal: 14, borderRadius: 10, borderWidth: 1, borderColor: on ? colors.primary : colors.border }}>
                <Text style={{ color: colors.text, fontSize: 16, fontWeight: on ? "700" : "400" }}>{o.serviceName}{o.price != null ? ` · ${rands(o.price)}` : ""}</Text>
              </Pressable>
            );
          })}
          <Field label="Quantity" value={qty} onChangeText={setQty} keyboardType="decimal-pad" />
          <Button label="Add to tab" tone="plain" onPress={addLine} loading={busy === "add"} disabled={!!busy} />
        </>
      ) : null}
      {actions.includes("settle") ? (
        <>
          <Text accessibilityRole="header" style={{ color: colors.text, fontSize: 18, fontWeight: "700" }}>Settled at the till</Text>
          {TILL_METHODS.map((m) => <Button key={m.code} label={`Paid by ${m.label}`} loading={busy === `settle-${m.code}`} disabled={!!busy}
            onPress={() => void run(`settle-${m.code}`, () => api(`${TAB_BASE}/${tabId}/settle`, { method: "POST", body: { paymentMethod: m.code } }), "Settled.")} />)}
        </>
      ) : null}
      {actions.includes("charge") ? <Button label="Charge to the customer's account" loading={busy === "charge"} disabled={!!busy} onPress={() => void run("charge", () => api(`${TAB_BASE}/${tabId}/charge`, { method: "POST" }), "Charged to the account.")} /> : null}
      {actions.includes("void") && !voiding ? <Button label="Void this tab" tone="danger" disabled={!!busy} onPress={() => { setVoiding(true); setError(null); }} /> : null}
      {voiding ? (
        <Card>
          <Text style={{ color: colors.text, fontWeight: "700" }}>Why is this tab being voided?</Text>
          <Field label="Reason" value={reason} onChangeText={setReason} multiline maxLength={300} />
          <Button label="Confirm void" tone="danger" onPress={confirmVoid} loading={busy === "void"} />
          <Button label="Cancel" tone="plain" onPress={() => { setVoiding(false); setError(null); }} />
        </Card>
      ) : null}
    </Screen>
  );
}
