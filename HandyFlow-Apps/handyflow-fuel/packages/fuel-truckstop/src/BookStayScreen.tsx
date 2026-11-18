import React, { useState } from "react";
import { Text } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useOutbox } from "@/offline/OutboxProvider";
import { useAuth } from "@/auth/AuthContext";
import type { RootStackParamList } from "@/navigation/types";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";

import { canRunTruckStop, todayZA } from "./stays";
import { bookBody, validateBook, type BookForm } from "./tabs";

type Props = NativeStackScreenProps<RootStackParamList, "BookStay">;

/**
 * Book a stay at the desk. Saved on the phone and sent when there is signal. Availability, bays and the rate are the server's to judge, and the phone does not
 * claim a space is free. The server refuses a booking it cannot place.
 */
export function BookStayScreen({ navigation }: Props) {
  const { session } = useAuth();
  const { enqueue } = useOutbox();
  const today = todayZA();
  const [f, setF] = useState<BookForm>({ vehicleRegistration: "", driverName: "", driverPhone: "", arrivalDate: today, departureDate: "", note: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const set = <K extends keyof BookForm>(k: K, v: BookForm[K]) => setF((p) => ({ ...p, [k]: v }));
  if (!canRunTruckStop(session?.permissions ?? [])) return <Screen title="Book a stay"><Text style={{ color: colors.muted }}>You do not have permission to book a stay.</Text></Screen>;

  async function save() {
    const problem = validateBook(f, today);
    if (problem) { setError(problem); return; }
    setError(null); setBusy(true);
    try {
      await enqueue("STAY_BOOK", { body: bookBody(f) });
      navigation.goBack();
    } catch (e) { setError(e instanceof Error ? e.message : "Could not save on this phone"); } finally { setBusy(false); }
  }

  return (
    <Screen title="Book a stay">
      <Field label="Vehicle registration" value={f.vehicleRegistration} onChangeText={(v) => set("vehicleRegistration", v)} autoCapitalize="characters" />
      <Field label="Driver (optional)" value={f.driverName} onChangeText={(v) => set("driverName", v)} autoCapitalize="words" />
      <Field label="Driver phone (optional)" value={f.driverPhone} onChangeText={(v) => set("driverPhone", v)} keyboardType="phone-pad" />
      <Field label="Arrival date (yyyy-mm-dd)" value={f.arrivalDate} onChangeText={(v) => set("arrivalDate", v)} keyboardType="numbers-and-punctuation" />
      <Field label="Departure date (yyyy-mm-dd)" value={f.departureDate} onChangeText={(v) => set("departureDate", v)} keyboardType="numbers-and-punctuation" />
      <Field label="Note (optional)" value={f.note} onChangeText={(v) => set("note", v)} maxLength={300} />
      {error ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 15 }}>{error}</Text> : null}
      <Button label="Book the stay" onPress={() => void save()} loading={busy} />
      <Text style={{ color: colors.muted, fontSize: 14 }}>Saved on this phone and sent when there is signal. The phone cannot see which bays are free: the booking is only confirmed when the server has checked the bays and the rate, and it is refused if the yard is full.</Text>
    </Screen>
  );
}
