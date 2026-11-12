import React, { useState } from "react";
import { StyleSheet, Switch, Text, TextInput, View } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { clockOut } from "@/api/guardWork";
import { useOutbox } from "@/offline/OutboxProvider";
import { Button } from "@/ui/Button";
import { Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import type { RootStackParamList } from "./types";

type Props = NativeStackScreenProps<RootStackParamList, "ClockOut">;

export function ClockOutScreen({ route, navigation }: Props) {
  const { sessionId } = route.params;
  const qc = useQueryClient();
  const { pendingCount, syncNow } = useOutbox();
  const [notes, setNotes] = useState("");
  const [returned, setReturned] = useState(true);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  const m = useMutation({
    mutationFn: async () => {
      await syncNow(); // send anything still waiting before the shift closes
      return clockOut(sessionId, { handoverNotes: notes, resourcesReturned: returned, incompletePatrolReason: reason });
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["currentSession"] });
      void qc.invalidateQueries({ queryKey: ["myShifts"] });
      navigation.popToTop();
    },
    onError: (e) => setError(e instanceof Error ? e.message : "Could not end the shift"),
  });

  return (
    <Screen title="End shift">
      {pendingCount > 0 ? <Text style={s.warn}>{pendingCount} item(s) still waiting to send. They will be sent first.</Text> : null}
      <Text style={s.label}>Handover notes for the next guard</Text>
      <TextInput style={[s.input, s.multi]} value={notes} onChangeText={setNotes} multiline accessibilityLabel="Handover notes" />
      <View style={s.row}>
        <Text style={s.label}>I returned all equipment</Text>
        <Switch value={returned} onValueChange={setReturned} accessibilityLabel="Equipment returned" />
      </View>
      <Text style={s.label}>If you missed patrol rounds, say why</Text>
      <TextInput style={s.input} value={reason} onChangeText={setReason} accessibilityLabel="Reason for missed rounds" />
      {error ? <Text accessibilityRole="alert" style={s.err}>{error}</Text> : null}
      <Button label="End shift" tone="danger" onPress={() => m.mutate()} loading={m.isPending} />
    </Screen>
  );
}

const s = StyleSheet.create({
  label: { color: colors.muted, fontSize: 14 },
  warn: { color: "#FBBF24" },
  err: { color: colors.danger },
  row: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  input: { backgroundColor: colors.surface, color: colors.text, borderColor: colors.border, borderWidth: 1, borderRadius: 12, minHeight: 52, paddingHorizontal: 14, fontSize: 17 },
  multi: { minHeight: 100, textAlignVertical: "top", paddingTop: 12 },
});
