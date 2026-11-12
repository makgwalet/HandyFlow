import React, { useState } from "react";
import { StyleSheet, Text, TextInput } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  checkoutEquipment, getMyEquipment, RESOURCE_TYPES, RETURN_CONDITIONS, returnEquipment,
  type ResourceType, type ReturnCondition,
} from "@/api/guardWork";
import { Button } from "@/ui/Button";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import type { RootStackParamList } from "./types";

type Props = NativeStackScreenProps<RootStackParamList, "Equipment">;

const word = (v: string) => v.charAt(0) + v.slice(1).toLowerCase();

export function EquipmentScreen({ route }: Props) {
  const { sessionId } = route.params;
  const qc = useQueryClient();
  const [type, setType] = useState<ResourceType>("RADIO");
  const [ref, setRef] = useState("");
  const [error, setError] = useState<string | null>(null);
  const q = useQuery({ queryKey: ["myEquipment"], queryFn: getMyEquipment });

  const changed = {
    onSuccess: () => {
      setError(null);
      void qc.invalidateQueries({ queryKey: ["myEquipment"] });
    },
    onError: (e: unknown) => setError(e instanceof Error ? e.message : "That did not work"),
  };
  const checkout = useMutation({ mutationFn: () => checkoutEquipment(sessionId, type, ref), ...changed, onSuccess: () => { setRef(""); changed.onSuccess(); } });
  const giveBack = useMutation({ mutationFn: (v: { id: string; c: ReturnCondition }) => returnEquipment(v.id, v.c, ""), ...changed });

  const items = q.data ?? [];
  return (
    <Screen title="Equipment">
      {error ? <Text accessibilityRole="alert" style={s.err}>{error}</Text> : null}
      <Text style={s.head}>With you now</Text>
      {q.isError ? <Text style={s.err}>Could not load your equipment.</Text> : null}
      {items.length === 0 && !q.isLoading ? <Text style={s.muted}>Nothing signed out to you.</Text> : null}
      {items.map((i) => (
        <Card key={i.id}>
          <Text style={s.strong}>{i.resourceRef} ({word(i.resourceType)})</Text>
          <Text style={s.muted}>Signed out {new Date(i.checkedOutAt).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}. Return it as:</Text>
          {RETURN_CONDITIONS.map((c) => (
            <Button key={c} label={c === "GOOD" ? "Good condition" : c === "DAMAGED" ? "Damaged" : "Missing"} tone={c === "GOOD" ? "primary" : "plain"}
              loading={giveBack.isPending && giveBack.variables?.id === i.id && giveBack.variables.c === c}
              onPress={() => giveBack.mutate({ id: i.id, c })} />
          ))}
        </Card>
      ))}

      <Text style={s.head}>Sign out an item</Text>
      {RESOURCE_TYPES.map((t) => <Button key={t} label={word(t)} tone={t === type ? "primary" : "plain"} onPress={() => setType(t)} />)}
      <TextInput style={s.input} value={ref} onChangeText={setRef} maxLength={100} placeholder="Name or number, e.g. Radio R-014" placeholderTextColor={colors.muted} accessibilityLabel="Item name or number" />
      <Button label="Sign out" onPress={() => checkout.mutate()} loading={checkout.isPending} disabled={ref.trim().length < 2} />
      <Text style={s.muted}>Firearms are issued by the armoury, not from this app.</Text>
    </Screen>
  );
}

const s = StyleSheet.create({
  head: { color: colors.text, fontWeight: "700", fontSize: 17 },
  strong: { color: colors.text, fontWeight: "700", fontSize: 16 },
  muted: { color: colors.muted },
  err: { color: colors.danger },
  input: { backgroundColor: colors.surface, color: colors.text, borderColor: colors.border, borderWidth: 1, borderRadius: 12, minHeight: 52, paddingHorizontal: 14, fontSize: 17 },
});
