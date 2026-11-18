import React, { useEffect, useState } from "react";
import { Pressable, Text } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { api, ApiError } from "@/api/client";
import { useAuth } from "@/auth/AuthContext";
import type { RootStackParamList } from "@/navigation/types";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import { arrivalCheckSchema, blocks, isReleasable, RELEASE_REASONS, releaseSchema, windowHours, type ArrivalCheck } from "@driver/arrival";

type Props = NativeStackScreenProps<RootStackParamList, "ReleaseCheck">;

export const canReleaseArrivalCheck = (permissions: readonly string[]): boolean => permissions.includes("FUEL_ARRIVAL_CHECK_OVERRIDE");

const failText = (e: unknown) => (e instanceof ApiError && e.isNetwork ? "No signal. Nothing was released. Try again when you have signal." : e instanceof Error ? e.message : "Could not release");

/**
 * A supervisor releases a driver's stale arrival check. Online only, and the server decides: it refuses a blocked check,
 * a check that is not stale, a release past a failing safety rule, and a release by the person who made the check.
 */
export function ReleaseCheckScreen({ route }: Props) {
  const { loadId } = route.params;
  const { session } = useAuth();
  const [check, setCheck] = useState<ArrivalCheck | null | undefined>(undefined);
  const [reason, setReason] = useState<string>("");
  const [why, setWhy] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const may = canReleaseArrivalCheck(session?.permissions ?? []);

  useEffect(() => {
    if (!may) return;
    api<unknown>(`/api/v1/fuel/deliveries/${loadId}/arrival-check`)
      .then((raw) => setCheck(raw ? arrivalCheckSchema.parse(raw) : null))
      .catch((e) => { setError(failText(e)); setCheck(null); });
  }, [loadId, may]);

  if (!may) return <Screen title="Release arrival check"><Text style={{ color: colors.muted }}>You do not have permission to release an arrival check.</Text></Screen>;

  async function release() {
    const parsed = releaseSchema.safeParse({ reasonCode: reason, explanation: why });
    if (!parsed.success) { setError(parsed.error.issues[0]?.message ?? "Choose a reason and explain it."); return; }
    setError(null); setBusy(true);
    try {
      setCheck(arrivalCheckSchema.parse(await api<unknown>(`/api/v1/fuel/deliveries/${loadId}/arrival-check/release`, { method: "POST", body: parsed.data })));
      setDone(true);
    } catch (e) { setError(failText(e)); } finally { setBusy(false); }
  }

  const releasable = !!check && isReleasable(check);
  return (
    <Screen title="Release arrival check">
      {check === undefined ? <Text style={{ color: colors.muted }}>Loading…</Text> : null}
      {check === null && !error ? <Card><Text style={{ color: colors.text }}>No arrival check has been made on this load, so there is nothing to release.</Text></Card> : null}
      {check ? (
        <Card>
          <Text style={{ color: colors.text, fontSize: 17, fontWeight: "700" }}>{(check.status ?? "").toLowerCase() || "checked"}</Text>
          <Text style={{ color: colors.muted }}>Checked {new Date(check.checkedAt).toLocaleString()} · stays fresh {windowHours(check)} hours</Text>
          {blocks(check).map((f) => <Text key={f.code} style={{ color: "#FCA5A5" }}>{f.message}</Text>)}
          {!check.allowed ? <Text style={{ color: "#FCA5A5" }}>This check found something that blocks the delivery. It cannot be released. The driver must check again once it is fixed.</Text> : null}
          {check.allowed && !releasable && !done ? <Text style={{ color: colors.muted }}>This check is still good, or already released. There is nothing to release.</Text> : null}
        </Card>
      ) : null}
      {done ? <Text accessibilityRole="alert" style={{ color: "#86EFAC", fontSize: 15 }}>Released. The driver can offload until {check?.expiresAt ? new Date(check.expiresAt).toLocaleString() : "the window ends"}.</Text> : null}
      {releasable && !done ? (
        <>
          <Text accessibilityRole="header" style={{ color: colors.text, fontSize: 18, fontWeight: "700" }}>Reason</Text>
          {RELEASE_REASONS.map((r) => {
            const on = r.code === reason;
            return (
              <Pressable key={r.code} accessibilityRole="radio" accessibilityState={{ selected: on }} accessibilityLabel={r.label} onPress={() => setReason(r.code)}
                style={{ minHeight: 48, justifyContent: "center", paddingHorizontal: 14, borderRadius: 10, borderWidth: 1, borderColor: on ? colors.primary : colors.border }}>
                <Text style={{ color: colors.text, fontSize: 16, fontWeight: on ? "700" : "400" }}>{r.label}</Text>
              </Pressable>
            );
          })}
          <Field label="Explain the release" value={why} onChangeText={setWhy} multiline maxLength={1000} />
          <Button label="Release the check" onPress={() => void release()} loading={busy} />
        </>
      ) : null}
      {error ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 15 }}>{error}</Text> : null}
    </Screen>
  );
}
