import React from "react";
import { Pressable, RefreshControl, ScrollView, Text, View } from "react-native";
import { useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { SafeAreaView } from "react-native-safe-area-context";
import type { RootStackParamList } from "@/navigation/types";
import { useOutbox } from "@/offline/OutboxProvider";
import { Card, styles as ui } from "@/ui/Screen";
import { SyncBanner } from "@/ui/SyncBanner";
import { colors, spacing } from "@/ui/theme";
import { addressText, loadSummary, queuedKinds } from "./loads";
import { useLoads } from "./useLoads";

export function MyLoadsScreen() {
  const nav = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const { loads, isLoading, error, savedAt, refetch } = useLoads();
  const { pending } = useOutbox();
  const [refreshing, setRefreshing] = React.useState(false);

  return (
    <SafeAreaView style={ui.root} edges={["bottom"]}>
      <SyncBanner />
      <ScrollView
        contentContainerStyle={ui.body}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={async () => { setRefreshing(true); await refetch(); setRefreshing(false); }} tintColor={colors.text} />}
      >
        <Text accessibilityRole="header" style={ui.title}>My loads</Text>
        {savedAt ? <Text style={{ color: "#FCD34D" }}>No signal. Showing the list saved at {new Date(savedAt).toLocaleTimeString()}.</Text> : null}
        {error && !savedAt && loads.length === 0 ? <Card><Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 16 }}>{error}</Text></Card> : null}
        {isLoading ? <Text style={{ color: colors.muted }}>Loading your loads…</Text> : null}
        {!isLoading && !error && loads.length === 0 ? <Card><Text style={{ color: colors.text, fontSize: 17 }}>No loads assigned to you right now.</Text></Card> : null}
        {loads.map((l) => {
          const queued = queuedKinds(pending, l.id);
          return (
            <Pressable key={l.id} accessibilityRole="button" accessibilityLabel={`${l.fuelType} ${l.litresOrdered} litres, ${addressText(l.deliveryAddress)}`} onPress={() => nav.navigate("LoadDetail", { loadId: l.id })}>
              <Card>
                <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
                  <Text style={{ color: colors.text, fontSize: 18, fontWeight: "700" }}>{l.fuelType}</Text>
                  <Text style={{ color: colors.text, fontSize: 18 }}>{l.litresOrdered.toLocaleString("en-ZA")} L</Text>
                </View>
                <Text style={{ color: colors.text, fontSize: 15 }}>{addressText(l.deliveryAddress)}</Text>
                <Text style={{ color: colors.muted, fontSize: 14, marginTop: spacing.xs }}>{loadSummary(l, queued)}</Text>
              </Card>
            </Pressable>
          );
        })}
      </ScrollView>
    </SafeAreaView>
  );
}
