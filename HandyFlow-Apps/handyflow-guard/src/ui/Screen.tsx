import React from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { SyncBanner } from "./SyncBanner";
import { colors, spacing } from "./theme";

export function Screen({ title, children }: { title?: string; children: React.ReactNode }) {
  return (
    <SafeAreaView style={styles.root} edges={["top", "bottom"]}>
      <SyncBanner />
      <ScrollView contentContainerStyle={styles.body} keyboardShouldPersistTaps="handled">
        {title ? <Text accessibilityRole="header" style={styles.title}>{title}</Text> : null}
        {children}
      </ScrollView>
    </SafeAreaView>
  );
}

export function Card({ children }: { children: React.ReactNode }) {
  return <View style={styles.card}>{children}</View>;
}

export const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  body: { padding: spacing.md, gap: spacing.md },
  title: { color: colors.text, fontSize: 24, fontWeight: "700" },
  card: { backgroundColor: colors.surface, borderColor: colors.border, borderWidth: 1, borderRadius: 14, padding: spacing.md, gap: spacing.xs },
});
