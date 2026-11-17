import React, { useState } from "react";
import { Pressable, Text } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { RootStackParamList } from "@/navigation/types";
import { useOutbox } from "@/offline/OutboxProvider";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import { FAILURE_REASONS, failedBody, validateFailure, type FailedForm } from "./loads";
import { useLoads } from "./useLoads";

type Props = NativeStackScreenProps<RootStackParamList, "FailedAttempt">;

export function FailedAttemptScreen({ route, navigation }: Props) {
  const { loadId } = route.params;
  const { loads } = useLoads();
  const { enqueue } = useOutbox();
  const load = loads.find((l) => l.id === loadId);
  const [f, setF] = useState<FailedForm>({ reason: "", note: "", litresReturned: "" });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function save() {
    const problem = validateFailure(f, load?.loadedLitres);
    if (problem) { setError(problem); return; }
    setError(null);
    setBusy(true);
    try {
      await enqueue("FAILED_ATTEMPT", { loadId, body: failedBody(f) });
      navigation.popTo("LoadDetail", { loadId });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save");
    } finally { setBusy(false); }
  }

  return (
    <Screen title="Could not deliver">
      {FAILURE_REASONS.map((r) => (
        <Pressable key={r.code} accessibilityRole="radio" accessibilityState={{ selected: f.reason === r.code }} onPress={() => setF((p) => ({ ...p, reason: r.code }))}>
          <Text style={{ color: colors.text, fontSize: 18, paddingVertical: 8 }}>{f.reason === r.code ? "●" : "○"} {r.label}</Text>
        </Pressable>
      ))}
      <Field label="Note" value={f.note} onChangeText={(v) => setF((p) => ({ ...p, note: v }))} multiline />
      <Field label="Litres coming back (optional)" value={f.litresReturned} onChangeText={(v) => setF((p) => ({ ...p, litresReturned: v }))} keyboardType="decimal-pad" />
      {error ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 15 }}>{error}</Text> : null}
      <Button label="Save failed delivery" tone="danger" onPress={save} loading={busy} />
    </Screen>
  );
}
