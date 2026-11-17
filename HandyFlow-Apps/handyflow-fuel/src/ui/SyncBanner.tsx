import React from "react";
import { Pressable, StyleSheet, Text } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { useOutbox } from "@/offline/OutboxProvider";
import { colors, spacing } from "./theme";

/** Always visible when something is on the phone and not yet on the server. Tap to open the sync centre. */
export function SyncBanner() {
  const { pending, failed } = useOutbox();
  const nav = useNavigation<{ navigate: (name: "SyncCentre") => void }>();
  if (pending.length === 0 && failed.length === 0) return null;
  const bad = failed.length > 0;
  const text = bad
    ? `${failed.length} item${failed.length === 1 ? "" : "s"} could not be sent. Tap to see why.`
    : `${pending.length} waiting to send. Tap to see.`;
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={text} onPress={() => nav.navigate("SyncCentre")} style={[styles.bar, bad && styles.bad]}>
      <Text style={styles.text}>{text}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  bar: { backgroundColor: "#92400E", padding: spacing.sm, alignItems: "center" },
  bad: { backgroundColor: colors.danger },
  text: { color: colors.text, fontWeight: "600" },
});
