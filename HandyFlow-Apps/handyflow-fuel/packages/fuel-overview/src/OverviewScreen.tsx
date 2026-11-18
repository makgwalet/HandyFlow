import React from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { useAuth } from "@/auth/AuthContext";
import { canReleaseArrivalCheck } from "./ReleaseCheckScreen";
import { SafeAreaView } from "react-native-safe-area-context";
import { Card, styles as ui } from "@/ui/Screen";
import { SyncBanner } from "@/ui/SyncBanner";
import { colors } from "@/ui/theme";
import { attention, litres } from "./summary";
import { useSummary } from "./useSummary";

function Tile({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <Card>
      <Text style={{ color: colors.muted }}>{label}</Text>
      <Text style={{ color: colors.text, fontSize: 26, fontWeight: "700" }}>{value}</Text>
      {hint ? <Text style={{ color: colors.muted }}>{hint}</Text> : null}
    </Card>
  );
}

export function OverviewScreen() {
  const { summary: s, isLoading, error, savedAt, refetch } = useSummary();
  const [refreshing, setRefreshing] = React.useState(false);
  const nav = useNavigation<any>();
  const { session } = useAuth();
  const mayRelease = canReleaseArrivalCheck(session?.permissions ?? []);
  return (
    <SafeAreaView style={ui.root} edges={["bottom"]}>
      <SyncBanner />
      <ScrollView
        contentContainerStyle={ui.body}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={async () => { setRefreshing(true); await refetch(); setRefreshing(false); }} tintColor={colors.text} />}
      >
        <Text accessibilityRole="header" style={ui.title}>Overview</Text>
        {savedAt ? <Text style={{ color: "#FCD34D" }}>No signal. Showing the figures saved at {new Date(savedAt).toLocaleTimeString()}.</Text> : null}
        {error && !s ? <Card><Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 16 }}>{error}</Text></Card> : null}
        {isLoading ? <Text style={{ color: colors.muted }}>Loading…</Text> : null}
        {s ? (
          <View style={{ gap: 12 }}>
            {attention(s).map((a) => (
              <Card key={a.label}><Text accessibilityRole="alert" style={{ color: a.tone === "danger" ? "#FCA5A5" : "#FCD34D", fontSize: 16 }}>{a.label}</Text></Card>
            ))}
            <Tile label="Dispatched this month" value={litres(s.monthLitres)} hint={`${s.monthDispatches} dispatch${s.monthDispatches === 1 ? "" : "es"} · ${s.monthReceipts} receipt${s.monthReceipts === 1 ? "" : "s"}`} />
            <Tile label="Open deliveries" value={String(s.openDeliveries)} hint={`${s.inTransitDeliveries} on the road`} />
            {s.nextDeliveries.length > 0 ? (
              <Card>
                <Text style={{ color: colors.text, fontSize: 17, fontWeight: "700" }}>Next deliveries</Text>
                {s.nextDeliveries.map((d) => (
                  <Text key={d.id} style={{ color: colors.text }} onPress={mayRelease ? () => nav.navigate("ReleaseCheck", { loadId: d.id }) : undefined} accessibilityRole={mayRelease ? "button" : undefined}>
                    {d.fuelType ?? "Fuel"} · {litres(d.litresOrdered)} · {d.status.replace(/_/g, " ").toLowerCase()}{d.scheduledAt ? ` · ${new Date(d.scheduledAt).toLocaleDateString("en-ZA")}` : ""}
                  </Text>
                ))}
              </Card>
            ) : null}
          </View>
        ) : null}
      </ScrollView>
    </SafeAreaView>
  );
}
