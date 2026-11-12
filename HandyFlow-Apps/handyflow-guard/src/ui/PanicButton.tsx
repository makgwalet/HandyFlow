import React, { useState } from "react";
import { Pressable, StyleSheet, Text } from "react-native";
import * as Haptics from "expo-haptics";
import { getPosition } from "@/location";
import { duressItem } from "@/offline/payloads";
import { useOutbox } from "@/offline/OutboxProvider";
import { colors } from "./theme";

/**
 * Hold for 1.5 seconds. The alert is saved on the phone first and sent at once; with no signal it goes the moment signal
 * returns, and the screen says so plainly. The position is best effort and never delays the alert for long.
 */
export function PanicButton() {
  const { enqueue, context } = useOutbox();
  const [state, setState] = useState<"idle" | "saved" | "error">("idle");

  async function raise() {
    try {
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
      const position = await getPosition(2500);
      await enqueue(duressItem({ position }, context()));
      setState("saved");
    } catch {
      setState("error");
    }
  }

  return (
    <>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Panic. Press and hold for 1.5 seconds to raise an alarm."
        delayLongPress={1500}
        onLongPress={() => void raise()}
        style={styles.button}
      >
        <Text style={styles.label}>PANIC</Text>
        <Text style={styles.hint}>Press and hold</Text>
      </Pressable>
      {state === "saved" ? <Text style={styles.ok}>Alarm raised. Control room is being told.</Text> : null}
      {state === "error" ? <Text style={styles.err}>Could not save the alarm. Use your radio or call now.</Text> : null}
    </>
  );
}

const styles = StyleSheet.create({
  button: { backgroundColor: colors.danger, borderRadius: 16, minHeight: 88, alignItems: "center", justifyContent: "center" },
  label: { color: colors.text, fontSize: 26, fontWeight: "800" },
  hint: { color: colors.text, opacity: 0.85 },
  ok: { color: colors.ok, textAlign: "center", marginTop: 8 },
  err: { color: colors.danger, textAlign: "center", marginTop: 8, fontWeight: "700" },
});
