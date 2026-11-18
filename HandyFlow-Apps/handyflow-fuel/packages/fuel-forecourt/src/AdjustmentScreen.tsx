import React, { useState } from "react";
import { Pressable, Text } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useNavigation } from "@react-navigation/native";
import { api } from "@/api/client";
import type { RootStackParamList } from "@/navigation/types";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import { failText, useRefreshShifts } from "./hooks";
import { ADJUSTMENT_KINDS, adjustmentBody, validateAdjustment, type AdjustmentKind } from "./shifts";

type Props = NativeStackScreenProps<RootStackParamList, "Adjustment">;
const LABEL: Record<AdjustmentKind, string> = { REFUND: "Refund", VOID: "Void", DISCOUNT: "Discount" };

/** A refund, void or discount on an open shift. A supervisor approves it; until then it does not change the shift's expected takings. */
export function AdjustmentScreen({ route }: Props) {
  const { shiftId } = route.params;
  const nav = useNavigation();
  const refresh = useRefreshShifts();
  const [kind, setKind] = useState<AdjustmentKind>("REFUND");
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function save() {
    const problem = validateAdjustment(amount, reason);
    if (problem) { setError(problem); return; }
    setError(null); setBusy(true);
    try {
      await api(`/api/v1/fuel/forecourt/shifts/${shiftId}/adjustments`, { method: "POST", body: adjustmentBody(kind, amount, reason) });
      await refresh(shiftId);
      nav.goBack();
    } catch (e) { setError(failText(e)); } finally { setBusy(false); }
  }

  return (
    <Screen title="Refund, void or discount">
      {ADJUSTMENT_KINDS.map((k) => (
        <Pressable key={k} accessibilityRole="radio" accessibilityState={{ selected: kind === k }} accessibilityLabel={LABEL[k]} onPress={() => setKind(k)}
          style={{ minHeight: 48, justifyContent: "center", paddingHorizontal: 14, borderRadius: 10, borderWidth: 1, borderColor: kind === k ? colors.primary : colors.border }}>
          <Text style={{ color: colors.text, fontSize: 16, fontWeight: kind === k ? "700" : "400" }}>{LABEL[k]}</Text>
        </Pressable>
      ))}
      <Field label="Amount (rand)" value={amount} onChangeText={setAmount} keyboardType="decimal-pad" />
      <Field label="Reason" value={reason} onChangeText={setReason} />
      {error ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 15 }}>{error}</Text> : null}
      <Button label="Save for approval" onPress={save} loading={busy} />
    </Screen>
  );
}
