import React, { useEffect, useState } from "react";
import { Pressable, Text } from "react-native";
import { api } from "@/api/client";
import { isOn, prefsSchema, PUSH_TYPES, type Prefs } from "@/pushPrefs";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";

const PATH = "/api/v1/fuel/push/preferences";

/** Which alerts this phone may show you. Switching one off only mutes it for you: it never changes who is told or what anyone may do. */
export function PushPreferencesScreen() {
  const [prefs, setPrefs] = useState<Prefs | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    api<unknown>(PATH).then((r) => { if (live) setPrefs(prefsSchema.parse(r)); }).catch((e) => { if (live) setError(e instanceof Error ? e.message : "Could not load"); });
    return () => { live = false; };
  }, []);

  async function flip(type: string) {
    if (!prefs) return;
    const next = !isOn(prefs, type);
    setError(null); setBusy(type);
    try { setPrefs(prefsSchema.parse(await api<unknown>(PATH, { method: "PUT", body: { enabled: { [type]: next } } }))); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not save. Needs signal."); } finally { setBusy(null); }
  }

  return (
    <Screen title="Alerts I get">
      <Text style={{ color: colors.muted }}>Choose which alerts show on this phone. This needs signal. An alert only tells you something: it never lets you do anything you could not do before.</Text>
      {error ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 15 }}>{error}</Text> : null}
      {!prefs && !error ? <Text style={{ color: colors.muted }}>Loading…</Text> : null}
      {prefs ? PUSH_TYPES.map((t) => {
        const on = isOn(prefs, t.type);
        return (
          <Pressable key={t.type} accessibilityRole="switch" accessibilityLabel={t.label} accessibilityState={{ checked: on, disabled: busy === t.type }} disabled={!!busy} onPress={() => void flip(t.type)}>
            <Card>
              <Text style={{ color: colors.text, fontSize: 17, fontWeight: "600" }}>{t.label}: {on ? "on" : "off"}</Text>
              <Text style={{ color: colors.muted }}>{t.hint}</Text>
            </Card>
          </Pressable>
        );
      }) : null}
    </Screen>
  );
}
