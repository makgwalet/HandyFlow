import React, { useState } from "react";
import { Pressable, RefreshControl, ScrollView, Text } from "react-native";
import { useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { SafeAreaView } from "react-native-safe-area-context";
import { api } from "@/api/client";
import { useAuth } from "@/auth/AuthContext";
import type { RootStackParamList } from "@/navigation/types";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { Card, styles as ui } from "@/ui/Screen";
import { SyncBanner } from "@/ui/SyncBanner";
import { colors } from "@/ui/theme";
import { failText } from "./hooks";
import { canRunTruckStop } from "./stays";
import { TAB_BASE, useRefreshTabs, useTabs } from "./tabHooks";
import { rands, tabSchema, validateOpenTab } from "./tabs";

/** Open driver tabs, and a way to open one. Online only: a tab is a bill, and the phone does not keep a second copy of it. */
export function TabsScreen() {
  const nav = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const { session } = useAuth();
  const { tabs, isLoading, error, refetch } = useTabs("OPEN");
  const refresh = useRefreshTabs();
  const [refreshing, setRefreshing] = useState(false);
  const [reg, setReg] = useState("");
  const [driver, setDriver] = useState("");
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const may = canRunTruckStop(session?.permissions ?? []);

  async function open() {
    const problem = validateOpenTab(reg);
    if (problem) { setFormError(problem); return; }
    setFormError(null); setBusy(true);
    try {
      const t = tabSchema.parse(await api<unknown>(TAB_BASE, { method: "POST", body: { vehicleRegistration: reg.trim().toUpperCase(), driverName: driver.trim() || null } }));
      setReg(""); setDriver(""); await refresh();
      nav.navigate("TabDetail", { tabId: t.id });
    } catch (e) { setFormError(failText(e)); } finally { setBusy(false); }
  }

  return (
    <SafeAreaView style={ui.root} edges={["bottom"]}>
      <SyncBanner />
      <ScrollView contentContainerStyle={ui.body}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={async () => { setRefreshing(true); await refetch(); setRefreshing(false); }} tintColor={colors.text} />}>
        <Text accessibilityRole="header" style={ui.title}>Driver tabs</Text>
        {may ? (
          <Card>
            <Text style={{ color: colors.text, fontWeight: "700" }}>Open a tab</Text>
            <Field label="Vehicle registration" value={reg} onChangeText={setReg} autoCapitalize="characters" />
            <Field label="Driver (optional)" value={driver} onChangeText={setDriver} autoCapitalize="words" />
            {formError ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5" }}>{formError}</Text> : null}
            <Button label="Open tab" onPress={() => void open()} loading={busy} />
          </Card>
        ) : null}
        {error ? <Card><Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 16 }}>{error}</Text></Card> : null}
        {isLoading ? <Text style={{ color: colors.muted }}>Loading…</Text> : null}
        {!isLoading && !error && tabs.length === 0 ? <Card><Text style={{ color: colors.text, fontSize: 17 }}>No open tabs.</Text></Card> : null}
        {tabs.map((t) => (
          <Pressable key={t.id} accessibilityRole="button" accessibilityLabel={`Tab ${t.vehicleRegistration}`} onPress={() => nav.navigate("TabDetail", { tabId: t.id })}>
            <Card>
              <Text style={{ color: colors.text, fontSize: 18, fontWeight: "700" }}>{t.vehicleRegistration}{t.driverName ? ` · ${t.driverName}` : ""}</Text>
              <Text style={{ color: colors.muted }}>{t.lines.length} item{t.lines.length === 1 ? "" : "s"} · {rands(t.total)}{t.accountId ? " · on an account" : ""}</Text>
            </Card>
          </Pressable>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}
