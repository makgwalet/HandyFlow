import React from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text } from "react-native";
import { colors, MIN_TOUCH } from "./theme";

interface Props {
  label: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  tone?: "primary" | "danger" | "plain";
}

export function Button({ label, onPress, loading, disabled, tone = "primary" }: Props) {
  const off = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled: !!off, busy: !!loading }}
      onPress={onPress}
      disabled={off}
      style={[styles.base, tone === "danger" && styles.danger, tone === "plain" && styles.plain, off && styles.off]}
    >
      {loading ? <ActivityIndicator color={colors.text} /> : <Text style={styles.label}>{label}</Text>}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: { minHeight: MIN_TOUCH, borderRadius: 12, backgroundColor: colors.primary, alignItems: "center", justifyContent: "center", paddingHorizontal: 16 },
  danger: { backgroundColor: colors.danger },
  plain: { backgroundColor: "transparent", borderWidth: 1, borderColor: colors.border },
  off: { opacity: 0.5 },
  label: { color: colors.text, fontSize: 17, fontWeight: "600" },
});
