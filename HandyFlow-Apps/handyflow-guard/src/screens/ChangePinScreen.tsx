import React, { useState } from "react";
import { StyleSheet, Text, TextInput, View } from "react-native";
import { useAuth } from "@/auth/AuthContext";
import { PIN_PATTERN } from "@/api/guardAuth";
import { Button } from "@/ui/Button";
import { colors, spacing } from "@/ui/theme";

/** Shown before anything else when the server says the PIN must be changed. */
export function ChangePinScreen() {
  const { changePin } = useAuth();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [again, setAgain] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const ready = PIN_PATTERN.test(current) && PIN_PATTERN.test(next) && next === again && next !== current;

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      await changePin(current, next);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not change PIN");
    } finally {
      setBusy(false);
    }
  }

  const field = (label: string, value: string, set: (v: string) => void) => (
    <>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        style={styles.input}
        value={value}
        onChangeText={(t) => set(t.replace(/\D/g, "").slice(0, 6))}
        keyboardType="number-pad"
        secureTextEntry
        maxLength={6}
        accessibilityLabel={label}
      />
    </>
  );

  return (
    <View style={styles.root}>
      <Text style={styles.title}>Choose a new PIN</Text>
      <Text style={styles.help}>Your supervisor gave you a temporary PIN. Choose your own 6 digits. Do not reuse a recent PIN.</Text>
      {field("Current PIN", current, setCurrent)}
      {field("New PIN", next, setNext)}
      {field("New PIN again", again, setAgain)}
      {again.length === 6 && next !== again ? <Text style={styles.error}>The two new PINs do not match.</Text> : null}
      {error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null}
      <Button label="Save PIN" onPress={submit} loading={busy} disabled={!ready} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg, justifyContent: "center", padding: spacing.lg, gap: spacing.sm },
  title: { color: colors.text, fontSize: 24, fontWeight: "700" },
  help: { color: colors.muted, marginBottom: spacing.md },
  label: { color: colors.muted, fontSize: 14 },
  input: { backgroundColor: colors.surface, color: colors.text, borderColor: colors.border, borderWidth: 1, borderRadius: 12, minHeight: 52, paddingHorizontal: 14, fontSize: 18, marginBottom: spacing.sm },
  error: { color: colors.danger },
});
