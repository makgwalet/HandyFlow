import React, { useRef, useState } from "react";
import { Pressable, Text } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useNavigation } from "@react-navigation/native";
import { useAuth } from "@/auth/AuthContext";
import type { RootStackParamList } from "@/navigation/types";
import { useOutbox } from "@/offline/OutboxProvider";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import { canReceiveFuel, emptyReceive, fillText, receiveBody, validateReceive, type ReceiveForm } from "./tanks";
import { ordersFor, usableSuppliers } from "./supply";
import { useSupply } from "./useSupply";
import { useTanks } from "./useTanks";

type Props = NativeStackScreenProps<RootStackParamList, "ReceiveFuel">;

/** Fuel delivered into a tank. Saved on the phone first and sent when there is signal, so a depot with no signal can still book it in. */
export function ReceiveFuelScreen({ route }: Props) {
  const { tankId } = route.params;
  const nav = useNavigation();
  const { session } = useAuth();
  const { tanks } = useTanks();
  const { enqueue } = useOutbox();
  const tank = tanks.find((t) => t.id === tankId);
  const supply = useSupply();
  const [form, setForm] = useState<ReceiveForm>(emptyReceive);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // The time the fuel arrived is when the form opened, not when signal came back.
  const receivedAt = useRef(new Date().toISOString());
  const set = (k: keyof ReceiveForm) => (v: string) => setForm((f) => ({ ...f, [k]: v }));

  if (!tank) return <Screen title="Receive fuel"><Text style={{ color: colors.muted }}>This tank is not on your list.</Text></Screen>;
  if (!canReceiveFuel(session?.permissions ?? [])) return <Screen title="Receive fuel"><Text style={{ color: colors.muted }}>You do not have permission to receive fuel.</Text></Screen>;

  async function save() {
    const problem = validateReceive(form, tank!);
    if (problem) { setError(problem); return; }
    setError(null); setBusy(true);
    try {
      await enqueue("RECEIVE_FUEL", { tankId, body: receiveBody(form, receivedAt.current) });
      nav.goBack();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save on this phone");
    } finally { setBusy(false); }
  }

  return (
    <Screen title={`Receive fuel: ${tank.name}`}>
      <Card><Text style={{ color: colors.muted }}>{tank.fuelType} · {fillText(tank)}</Text></Card>
      <Field label="Litres received" value={form.litres} onChangeText={set("litres")} keyboardType="decimal-pad" />
      <Field label="Price per litre (rand)" value={form.price} onChangeText={set("price")} keyboardType="decimal-pad" />
      <Text style={{ color: colors.text, fontSize: 16, fontWeight: "600" }}>Supplier (optional)</Text>
      {supply.unavailable && supply.suppliers.length === 0 ? <Text style={{ color: colors.muted }}>The supplier list needs signal once. Save the receipt now and add the supplier in the office if you must.</Text> : null}
      {choose(usableSuppliers(supply.suppliers).map((s) => ({ id: s.id, label: s.name })), form.supplierId, (id) => setForm((f) => ({ ...f, supplierId: id, purchaseOrderId: null })), "Supplier")}
      {form.supplierId ? (
        <>
          <Text style={{ color: colors.text, fontSize: 16, fontWeight: "600" }}>Purchase order (optional)</Text>
          {choose(ordersFor(supply.orders, form.supplierId, tank.fuelType).map((o) => ({ id: o.id, label: `${o.poNumber}${o.litresOutstanding != null ? ` · ${Math.round(o.litresOutstanding).toLocaleString("en-ZA")} L outstanding` : ""}` })), form.purchaseOrderId, (id) => setForm((f) => ({ ...f, purchaseOrderId: id })), "Order")}
        </>
      ) : null}
      <Field label="Delivery note number (optional)" value={form.deliveryNote} onChangeText={set("deliveryNote")} />
      <Field label="Supplier invoice number (optional)" value={form.invoiceRef} onChangeText={set("invoiceRef")} />
      <Field label="Freight in rand (optional)" value={form.freight} onChangeText={set("freight")} keyboardType="decimal-pad" />
      {error ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 15 }}>{error}</Text> : null}
      <Text style={{ color: colors.muted }}>Saved on this phone first. It is added to the tank's stock once it reaches the server.</Text>
      <Button label="Save receipt" onPress={save} loading={busy} />
    </Screen>
  );
}

function choose(options: { id: string; label: string }[], value: string | null, onPick: (id: string | null) => void, noun: string) {
  if (options.length === 0) return <Text style={{ color: colors.muted }}>No {noun.toLowerCase()} to choose from.</Text>;
  return (
    <>
      {options.map((o) => {
        const on = o.id === value;
        return (
          <Pressable key={o.id} accessibilityRole="radio" accessibilityState={{ selected: on }} accessibilityLabel={`${noun}: ${o.label}`} onPress={() => onPick(on ? null : o.id)}
            style={{ minHeight: 48, justifyContent: "center", paddingHorizontal: 14, borderRadius: 10, borderWidth: 1, borderColor: on ? colors.primary : colors.border }}>
            <Text style={{ color: colors.text, fontSize: 16, fontWeight: on ? "700" : "400" }}>{o.label}</Text>
          </Pressable>
        );
      })}
    </>
  );
}
