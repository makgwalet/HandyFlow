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
import { useYard } from "./hooks";
import { canRunTruckStop, dayText, grouped, todayZA } from "./stays";

export function YardScreen() {
  const nav = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const { session } = useAuth();
  const { stays, occupancy: o, isLoading, error, savedAt, refetch } = useYard();
  const [refreshing, setRefreshing] = React.useState(false);
  const today = todayZA();
  return (
    <SafeAreaView style={ui.root} edges={["bottom"]}>
      <SyncBanner />
      <ScrollView contentContainerStyle={ui.body}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={async () => { setRefreshing(true); await refetch(); setRefreshing(false); }} tintColor={colors.text} />}>
        <Text accessibilityRole="header" style={ui.title}>Truck stop</Text>
        {canRunTruckStop(session?.permissions ?? []) ? <Button label="Stay requests" tone="plain" onPress={() => nav.navigate("StayRequests")} /> : null}
        {savedAt ? <Text style={{ color: "#FCD34D" }}>No signal. Showing the yard saved at {new Date(savedAt).toLocaleTimeString()}. Check-in and check-out need signal.</Text> : null}
        {error && stays.length === 0 ? <Card><Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 16 }}>{error}</Text></Card> : null}
        {isLoading ? <Text style={{ color: colors.muted }}>Loading…</Text> : null}
        {o ? (
          <Card>
            <Text style={{ color: colors.text, fontSize: 18, fontWeight: "700" }}>{o.inYard} in the yard{o.bays != null ? ` of ${o.bays} bays` : ""}</Text>
            <Text style={{ color: colors.muted }}>{o.arrivals} arriving · {o.departures} leaving{o.free != null ? ` · ${o.free} free` : ""}</Text>
          </Card>
        ) : null}
        {!isLoading && !error && stays.length === 0 ? <Card><Text style={{ color: colors.text, fontSize: 17 }}>No stays.</Text></Card> : null}
        {grouped(stays, today).map((g) => (
          <React.Fragment key={g.group}>
            <Text accessibilityRole="header" style={{ color: g.group === "overstaying" ? "#FCA5A5" : colors.text, fontSize: 18, fontWeight: "700" }}>{g.title} ({g.stays.length})</Text>
            {g.stays.map((s) => (
              <Pressable key={s.id} accessibilityRole="button" accessibilityLabel={`${s.vehicleRegistration}, ${g.title}`} onPress={() => nav.navigate("StayDetail", { stayId: s.id })}>
                <Card>
                  <Text style={{ color: colors.text, fontSize: 18, fontWeight: "700" }}>{s.vehicleRegistration}{s.driverName ? ` · ${s.driverName}` : ""}</Text>
                  <Text style={{ color: colors.muted }}>{dayText(s.arrivalDate)} to {dayText(s.departureDate)} · {s.nights} night{s.nights === 1 ? "" : "s"}</Text>
                </Card>
              </Pressable>
            ))}
          </React.Fragment>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}
