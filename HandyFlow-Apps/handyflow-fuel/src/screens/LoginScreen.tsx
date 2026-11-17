import React, { useEffect, useState } from "react";
import { KeyboardAvoidingView, Platform, StyleSheet, Text, View } from "react-native";
import { useAuth } from "@/auth/AuthContext";
import { loadTenantSlug } from "@/auth/deviceStore";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { colors, spacing } from "@/ui/theme";

export function LoginScreen() {
  const { signIn, expired } = useAuth();
  const [tenantSlug, setTenantSlug] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => { void loadTenantSlug().then((s) => s && setTenantSlug(s)).catch(() => undefined); }, []);

  const ready = tenantSlug.trim().length > 0 && email.includes("@") && password.length > 0;

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      await signIn({ tenantSlug, email, password });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Sign in failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <KeyboardAvoidingView style={styles.root} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <Text style={styles.title}>HandyFlow Fuel</Text>
      {expired ? <Text accessibilityRole="alert" style={styles.notice}>Your sign-in expired. Sign in again. Anything saved on this phone will be sent.</Text> : null}
      <View style={styles.form}>
        <Field label="Company code" value={tenantSlug} onChangeText={setTenantSlug} autoCapitalize="none" autoCorrect={false} />
        <Field label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" autoCorrect={false} keyboardType="email-address" />
        <Field label="Password" value={password} onChangeText={setPassword} secureTextEntry autoCapitalize="none" />
        {error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null}
        <Button label="Sign in" onPress={submit} loading={busy} disabled={!ready} />
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg, justifyContent: "center", padding: spacing.lg, gap: spacing.lg },
  title: { color: colors.text, fontSize: 30, fontWeight: "800" },
  form: { gap: spacing.md },
  error: { color: "#FCA5A5", fontSize: 15 },
  notice: { color: "#FCD34D", fontSize: 15 },
});
