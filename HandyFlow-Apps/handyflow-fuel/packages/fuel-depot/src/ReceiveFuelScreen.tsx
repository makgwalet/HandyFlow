import React, { useRef, useState } from "react";
import { Text } from "react-native";
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
      <Field label="Delivery note number (optional)" value={form.deliveryNote} onChangeText={set("deliveryNote")} />
      <Field label="Supplier invoice number (optional)" value={form.invoiceRef} onChangeText={set("invoiceRef")} />
      <Field label="Freight in rand (optional)" value={form.freight} onChangeText={set("freight")} keyboardType="decimal-pad" />
      {error ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 15 }}>{error}</Text> : null}
      <Text style={{ color: colors.muted }}>Saved on this phone first. It is added to the tank's stock once it reaches the server.</Text>
      <Button label="Save receipt" onPress={save} loading={busy} />
    </Screen>
  );
}
