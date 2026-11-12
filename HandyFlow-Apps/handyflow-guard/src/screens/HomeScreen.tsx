import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { useAuth } from "@/auth/AuthContext";
import { Button } from "@/ui/Button";
import { colors, spacing } from "@/ui/theme";

/** Placeholder. Shift, patrol, scan, panic and incident screens land here next. */
export function HomeScreen() {
  const { session, signOut } = useAuth();
  return (
    <View style={styles.root}>
      <Text style={styles.hello}>Hello, {session?.fullName}</Text>
      <Text style={styles.muted}>Shift, patrol and panic screens come next.</Text>
      <Button label="Sign out" tone="plain" onPress={() => void signOut()} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg, justifyContent: "center", padding: spacing.lg, gap: spacing.md },
  hello: { color: colors.text, fontSize: 24, fontWeight: "700" },
  muted: { color: colors.muted },
});
