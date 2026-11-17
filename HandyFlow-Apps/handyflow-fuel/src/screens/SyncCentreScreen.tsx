import React from "react";
import { Text } from "react-native";
import { useOutbox } from "@/offline/OutboxProvider";
import type { OutboxItem } from "@/offline/outbox";
import { Button } from "@/ui/Button";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";

export const KIND_LABEL: Record<OutboxItem["kind"], string> = {
  DISPATCH: "Start trip",
  COMPLETE: "Delivery",
  FAILED_ATTEMPT: "Failed delivery",
  EVIDENCE: "Photo",
};

export function SyncCentreScreen() {
  const { pending, failed, syncNow, retry, discard } = useOutbox();
  return (
    <Screen title="Sync centre">
      <Button label={pending.length ? `Send ${pending.length} now` : "Check for waiting items"} onPress={() => void syncNow()} />
      {pending.length === 0 && failed.length === 0 ? (
        <Card><Text style={{ color: colors.text, fontSize: 17 }}>Everything on this phone has been sent.</Text></Card>
      ) : null}
      {pending.map((i) => (
        <Card key={i.id}>
          <Text style={{ color: colors.text, fontSize: 17, fontWeight: "600" }}>{KIND_LABEL[i.kind]}</Text>
          <Text style={{ color: colors.muted }}>Saved {new Date(i.recordedAt).toLocaleString()}. Waiting to send{i.attempts ? `, tried ${i.attempts} time${i.attempts === 1 ? "" : "s"}` : ""}.</Text>
          {i.lastError ? <Text style={{ color: "#FCD34D" }}>{i.lastError}</Text> : null}
        </Card>
      ))}
      {failed.map((i) => (
        <Card key={i.id}>
          <Text style={{ color: colors.text, fontSize: 17, fontWeight: "600" }}>{KIND_LABEL[i.kind]}: refused</Text>
          <Text style={{ color: "#FCA5A5" }}>{i.lastError ?? "The server refused this."}</Text>
          <Button label="Try again" tone="plain" onPress={() => void retry(i.id)} />
          <Button label="Discard" tone="danger" onPress={() => void discard(i.id)} />
        </Card>
      ))}
    </Screen>
  );
}
