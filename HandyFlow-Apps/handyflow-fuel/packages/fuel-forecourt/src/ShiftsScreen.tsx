import React from "react";
import { Pressable, RefreshControl, ScrollView, Text } from "react-native";
import { useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { SafeAreaView } from "react-native-safe-area-context";
import { useAuth } from "@/auth/AuthContext";
import type { RootStackParamList } from "@/navigation/types";
import { Button } from "@/ui/Button";
import { Card, styles as ui } from "@/ui/Screen";
import { SyncBanner } from "@/ui/SyncBanner";
import { colors } from "@/ui/theme";
import { useOutbox } from "@/offline/OutboxProvider";
import { useShifts } from "./hooks";
import { canRunShifts, queuedOpens, rand, sortShifts, varianceLabel } from "./shifts";

export function ShiftsScreen() {
  const nav = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const { session } = useAuth();
  const { shifts, isLoading, error, savedAt, refetch } = useShifts();
  const { pending } = useOutbox();
  const opens = queuedOpens(pending);
  const [refreshing, setRefreshing] = React.useState(false);
  const may = canRunShifts(session?.permissions ?? []);
  return (
    <SafeAreaView style={ui.root} edges={["bottom"]}>
      <SyncBanner />
      <ScrollView contentContainerStyle={ui.body}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={async () => { setRefreshing(true); await refetch(); setRefreshing(false); }} tintColor={colors.text} />}>
        <Text accessibilityRole="header" style={ui.title}>Shifts</Text>
        {may ? <Button label="Open a shift" onPress={() => nav.navigate("OpenShift")} /> : null}
        {savedAt ? <Text style={{ color: "#FCD34D" }}>No signal. Showing the list saved at {new Date(savedAt).toLocaleTimeString()}. Opening and closing a shift needs signal.</Text> : null}
        {error && shifts.length === 0 ? <Card><Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 16 }}>{error}</Text></Card> : null}
        {isLoading ? <Text style={{ color: colors.muted }}>Loading shifts…</Text> : null}
        {!isLoading && !error && shifts.length === 0 ? <Card><Text style={{ color: colors.text, fontSize: 17 }}>No shifts yet.</Text></Card> : null}
        {opens.map((o) => {
          const who = (o.payload.body as { attendantName?: string } | undefined)?.attendantName ?? "Shift";
          const closing = pending.some((i) => i.kind === "SHIFT_CLOSE" && i.payload.openRequestId === o.id);
          return (
            <Pressable key={o.id} accessibilityRole="button" accessibilityLabel={`Shift opened on this phone by ${who}, waiting to send`} disabled={closing} onPress={() => nav.navigate("CloseShift", { openRequestId: o.id })}>
              <Card>
                <Text style={{ color: colors.text, fontSize: 18, fontWeight: "700" }}>{who}</Text>
                <Text style={{ color: "#FCD34D" }}>Opened on this phone at {new Date(o.recordedAt).toLocaleTimeString()} · waiting to send · not on the server yet</Text>
                <Text style={{ color: colors.muted }}>{closing ? "Close saved, waiting to send." : "Tap to close it."}</Text>
              </Card>
            </Pressable>
          );
        })}
        {sortShifts(shifts).map((s) => {
          const v = varianceLabel(s);
          return (
            <Pressable key={s.id} accessibilityRole="button" accessibilityLabel={`Shift ${s.shiftNumber ?? ""} ${s.attendantName}, ${s.status}`} onPress={() => nav.navigate("ShiftDetail", { shiftId: s.id })}>
              <Card>
                <Text style={{ color: colors.text, fontSize: 18, fontWeight: "700" }}>{s.attendantName}{s.shiftNumber ? ` · ${s.shiftNumber}` : ""}</Text>
                <Text style={{ color: s.status === "OPEN" ? "#86EFAC" : colors.muted }}>{s.status === "OPEN" ? "Open" : s.status === "CLOSED" ? "Closed" : "Reversed"} · opened {new Date(s.openedAt).toLocaleString()}</Text>
                {s.status === "CLOSED" ? <Text style={{ color: v.bad ? "#FCA5A5" : colors.muted }}>{rand(s.expected)} expected · {v.text}{s.reviewStatus && s.reviewStatus !== "NONE" ? ` · review ${s.reviewStatus.toLowerCase()}` : ""}</Text> : null}
              </Card>
            </Pressable>
          );
        })}
      </ScrollView>
    </SafeAreaView>
  );
}
