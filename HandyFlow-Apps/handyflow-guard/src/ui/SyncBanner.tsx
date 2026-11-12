import React from "react";
import { Pressable, StyleSheet, Text } from "react-native";
import { useOutbox } from "@/offline/OutboxProvider";
import { colors, spacing } from "./theme";

/** Always visible: tells the guard what is still on the phone and what the server refused. */
export function SyncBanner() {
  const { pendingCount, failed, syncNow } = useOutbox();
  if (pendingCount === 0 && failed.length === 0) return null;
  const bad = failed.length > 0;
  const text = bad
    ? `${failed.length} item${failed.length === 1 ? "" : "s"} could not be sent. Tell your supervisor.`
    : `${pendingCount} waiting to send. Tap to try now.`;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={text}
      onPress={() => void syncNow()}
      style={[styles.bar, bad && styles.bad]}
    >
      <Text style={styles.text}>{text}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  bar: { backgroundColor: "#92400E", padding: spacing.sm, alignItems: "center" },
  bad: { backgroundColor: colors.danger },
  text: { color: colors.text, fontWeight: "600" },
});
