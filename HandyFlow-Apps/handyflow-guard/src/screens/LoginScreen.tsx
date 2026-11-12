import React, { useEffect, useState } from "react";
import { KeyboardAvoidingView, Platform, StyleSheet, Text, TextInput, View } from "react-native";
import { useAuth } from "@/auth/AuthContext";
import { getDeviceId } from "@/auth/deviceStore";
import { PIN_PATTERN } from "@/api/guardAuth";
import { Button } from "@/ui/Button";
import { colors, spacing } from "@/ui/theme";

export function LoginScreen() {
  const { signIn } = useAuth();
  const [identifier, setIdentifier] = useState("");
  const [pin, setPin] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [deviceId, setDeviceId] = useState<string | null>(null);
  useEffect(() => { void getDeviceId().then(setDeviceId).catch(() => undefined); }, []);

  const ready = identifier.trim().length >= 3 && PIN_PATTERN.test(pin);

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      await signIn(identifier, pin);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Sign in failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <KeyboardAvoidingView style={styles.root} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <Text style={styles.title}>HandyFlow Guard</Text>
      <View style={styles.form}>
        <Text style={styles.label}>Phone number or employee code</Text>
        <TextInput
          style={styles.input}
          value={identifier}
          onChangeText={setIdentifier}
          autoCapitalize="characters"
          autoCorrect={false}
          accessibilityLabel="Phone number or employee code"
          placeholderTextColor={colors.muted}
        />
        <Text style={styles.label}>6-digit PIN</Text>
        <TextInput
          style={styles.input}
          value={pin}
          onChangeText={(t) => setPin(t.replace(/\D/g, "").slice(0, 6))}
          keyboardType="number-pad"
          secureTextEntry
          maxLength={6}
          accessibilityLabel="6-digit PIN"
          placeholderTextColor={colors.muted}
        />
        {error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null}
        <Button label="Sign in" onPress={submit} loading={busy} disabled={!ready} />
        {deviceId ? (
          <Text selectable style={styles.device}>Phone ID (your supervisor needs this to enrol the phone): {deviceId}</Text>
        ) : null}
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg, justifyContent: "center", padding: spacing.lg },
  title: { color: colors.text, fontSize: 28, fontWeight: "700", marginBottom: spacing.xl, textAlign: "center" },
  form: { gap: spacing.sm },
  label: { color: colors.muted, fontSize: 14 },
  input: { backgroundColor: colors.surface, color: colors.text, borderColor: colors.border, borderWidth: 1, borderRadius: 12, minHeight: 52, paddingHorizontal: 14, fontSize: 18, marginBottom: spacing.sm },
  device: { color: colors.muted, fontSize: 12, marginTop: spacing.md },
  error: { color: colors.danger, marginBottom: spacing.sm },
});
