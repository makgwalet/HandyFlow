import React, { useCallback, useEffect, useState } from "react";
import { Text } from "react-native";
import { z } from "zod";
import { api } from "@/api/client";
import { Button } from "@/ui/Button";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";

const conflictSchema = z.object({
  id: z.string(), kind: z.string(), reason: z.string(), createdAt: z.string(), capturedAt: z.string().nullish(), status: z.string().default("OPEN"),
});
type Conflict = z.infer<typeof conflictSchema>;

export const CONFLICT_LABEL: Record<string, string> = {
  SHIFT_OPEN: "Open shift", SHIFT_CLOSE: "Close shift and cash-up", STAY_BOOK: "Book stay", STAY_CHECK_IN: "Check truck in", STAY_CHECK_OUT: "Check truck out",
};

/** Who may see the server's list of refused queued writes. The server checks it again. */
export const canSeeConflicts = (perms: readonly string[]): boolean =>
  perms.some((p) => ["FUEL_MANAGE", "FUEL_FORECOURT_MANAGE", "FUEL_TRUCKSTOP_MANAGE", "FUEL_SHIFT_SUPERVISE"].includes(p));

/** Queued writes the server refused when they arrived: what, why, and when. Dealing with one only clears it from this list. */
export function OfflineConflictsScreen() {
  const [items, setItems] = useState<Conflict[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    try { setItems(z.array(conflictSchema).parse(await api<unknown>("/api/v1/fuel/offline/conflicts"))); setError(null); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not load"); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  async function resolve(id: string) {
    setBusy(id);
    try { await api(`/api/v1/fuel/offline/conflicts/${id}/resolve`, { method: "POST", body: {} }); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not save. Needs signal."); } finally { setBusy(null); }
  }

  return (
    <Screen title="Refused offline writes">
      <Text style={{ color: colors.muted }}>These were saved on a phone while it had no signal, and the server refused them when they arrived. Nothing here changed stock or money. Check what really happened, fix it in the app or the office, then mark it as dealt with.</Text>
      {error ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 15 }}>{error}</Text> : null}
      {items && items.length === 0 ? <Card><Text style={{ color: colors.text, fontSize: 17 }}>Nothing refused.</Text></Card> : null}
      {(items ?? []).map((c) => (
        <Card key={c.id}>
          <Text style={{ color: colors.text, fontSize: 17, fontWeight: "600" }}>{CONFLICT_LABEL[c.kind] ?? c.kind}</Text>
          <Text style={{ color: "#FCA5A5" }}>{c.reason}</Text>
          <Text style={{ color: colors.muted }}>{c.capturedAt ? `Captured ${new Date(c.capturedAt).toLocaleString()}. ` : ""}Reached the server {new Date(c.createdAt).toLocaleString()}.</Text>
          <Button label="Mark as dealt with" tone="plain" loading={busy === c.id} disabled={!!busy} onPress={() => void resolve(c.id)} />
        </Card>
      ))}
    </Screen>
  );
}
