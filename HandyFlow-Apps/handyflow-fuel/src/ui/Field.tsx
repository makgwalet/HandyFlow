import React from "react";
import { StyleSheet, Text, TextInput, type TextInputProps, View } from "react-native";
import { colors, MIN_TOUCH } from "./theme";

export function Field({ label, ...input }: { label: string } & TextInputProps) {
  return (
    <View style={styles.wrap}>
      <Text style={styles.label}>{label}</Text>
      <TextInput style={[styles.input, input.multiline && styles.multi]} accessibilityLabel={label} placeholderTextColor={colors.muted} {...input} />
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: 6 },
  label: { color: colors.muted, fontSize: 14 },
  input: { minHeight: MIN_TOUCH, borderRadius: 10, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.surface, color: colors.text, paddingHorizontal: 12, fontSize: 17 },
  multi: { minHeight: 96, paddingTop: 12, textAlignVertical: "top" },
});
