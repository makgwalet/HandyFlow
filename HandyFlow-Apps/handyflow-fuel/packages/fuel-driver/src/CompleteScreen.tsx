import React, { useState } from "react";
import { Pressable, Text } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { RootStackParamList } from "@/navigation/types";
import { useOutbox } from "@/offline/OutboxProvider";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import { completeBody, meterWarning, validateComplete, type CompleteForm } from "./loads";
import { useLoads } from "./useLoads";

type Props = NativeStackScreenProps<RootStackParamList, "CompleteLoad">;

export function CompleteScreen({ route, navigation }: Props) {
  const { loadId } = route.params;
  const { loads } = useLoads();
  const { enqueue } = useOutbox();
  const load = loads.find((l) => l.id === loadId);
  const [f, setF] = useState<CompleteForm>({ litres: load ? String(load.litresOrdered) : "", receiverName: "", receiverIdBadge: "", meterStart: "", meterEnd: "", onBehalf: false, onBehalfOf: "" });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = <K extends keyof CompleteForm>(k: K, v: CompleteForm[K]) => setF((p) => ({ ...p, [k]: v }));
  const warning = meterWarning(f);

  async function save() {
    const problem = validateComplete(f, load?.loadedLitres);
    if (problem) { setError(problem); return; }
    setError(null);
    setBusy(true);
    try {
      await enqueue("COMPLETE", { loadId, body: completeBody(f) });
      navigation.popTo("LoadDetail", { loadId });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save");
    } finally { setBusy(false); }
  }

  return (
    <Screen title="Record delivery">
      <Field label="Litres delivered" value={f.litres} onChangeText={(v) => set("litres", v)} keyboardType="decimal-pad" />
      <Field label="Received by (name)" value={f.receiverName} onChangeText={(v) => set("receiverName", v)} autoCapitalize="words" />
      <Field label="ID or site badge number (optional)" value={f.receiverIdBadge} onChangeText={(v) => set("receiverIdBadge", v)} autoCapitalize="characters" />
      <Field label="Pump meter start (optional)" value={f.meterStart} onChangeText={(v) => set("meterStart", v)} keyboardType="decimal-pad" />
      <Field label="Pump meter end (optional)" value={f.meterEnd} onChangeText={(v) => set("meterEnd", v)} keyboardType="decimal-pad" />
      {warning ? <Text style={{ color: "#FCD34D", fontSize: 15 }}>{warning}</Text> : null}
      <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: f.onBehalf }} onPress={() => set("onBehalf", !f.onBehalf)}>
        <Text style={{ color: colors.text, fontSize: 17 }}>{f.onBehalf ? "☑" : "☐"} Signed on behalf of the designated receiver</Text>
      </Pressable>
      {f.onBehalf ? <Field label="Designated receiver's name" value={f.onBehalfOf} onChangeText={(v) => set("onBehalfOf", v)} autoCapitalize="words" /> : null}
      {error ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 15 }}>{error}</Text> : null}
      <Button label="Save delivery" onPress={save} loading={busy} />
      <Text style={{ color: colors.muted, fontSize: 14 }}>Saved on this phone first, then sent. Safe to use without signal.</Text>
    </Screen>
  );
}
