import React from "react";
import { Pressable, RefreshControl, ScrollView, Text, View } from "react-native";
import { useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { SafeAreaView } from "react-native-safe-area-context";
import type { RootStackParamList } from "@/navigation/types";
import { Card, styles as ui } from "@/ui/Screen";
import { SyncBanner } from "@/ui/SyncBanner";
import { colors } from "@/ui/theme";
import { fillText } from "./tanks";
import { useTanks } from "./useTanks";

export function TanksScreen() {
  const nav = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const { tanks, isLoading, error, savedAt, refetch } = useTanks();
  const [refreshing, setRefreshing] = React.useState(false);
  return (
    <SafeAreaView style={ui.root} edges={["bottom"]}>
      <SyncBanner />
      <ScrollView
        contentContainerStyle={ui.body}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={async () => { setRefreshing(true); await refetch(); setRefreshing(false); }} tintColor={colors.text} />}
      >
        <Text accessibilityRole="header" style={ui.title}>Tanks</Text>
        {savedAt ? <Text style={{ color: "#FCD34D" }}>No signal. Showing the list saved at {new Date(savedAt).toLocaleTimeString()}.</Text> : null}
        {error && !savedAt && tanks.length === 0 ? <Card><Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 16 }}>{error}</Text></Card> : null}
        {isLoading ? <Text style={{ color: colors.muted }}>Loading tanks…</Text> : null}
        {!isLoading && !error && tanks.length === 0 ? <Card><Text style={{ color: colors.text, fontSize: 17 }}>No tanks to show.</Text></Card> : null}
        {tanks.map((t) => (
          <Pressable key={t.id} accessibilityRole="button" accessibilityLabel={`${t.name}, ${t.fuelType}${t.low ? ", low" : ""}`} onPress={() => nav.navigate("TankDetail", { tankId: t.id })}>
            <Card>
              <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
                <Text style={{ color: colors.text, fontSize: 18, fontWeight: "700" }}>{t.name}</Text>
                {t.low ? <Text style={{ color: "#FCA5A5", fontWeight: "700" }}>LOW</Text> : null}
              </View>
              <Text style={{ color: colors.muted }}>{t.fuelType}{t.location ? ` · ${t.location}` : ""}</Text>
              <Text style={{ color: colors.text, fontSize: 15 }}>{fillText(t)}</Text>
            </Card>
          </Pressable>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}
