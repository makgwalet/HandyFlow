import React, { useState } from "react";
import { StyleSheet, Text, View } from "react-native";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getDeviceId } from "@/auth/deviceStore";
import { acknowledgeOrder, getMyPost, getPostOrder, type PostOrder } from "@/api/guardWork";
import { Button } from "@/ui/Button";
import { Card, Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";

function Section({ title, text }: { title: string; text?: string | null }) {
  if (!text?.trim()) return null;
  return (
    <View style={s.sec}>
      <Text style={s.head}>{title}</Text>
      <Text style={s.body}>{text}</Text>
    </View>
  );
}

function OrderCard({ order, needsAck, onAcknowledge, busy }: { order: PostOrder; needsAck: boolean; onAcknowledge: () => void; busy: boolean }) {
  return (
    <Card>
      <Text style={s.title}>{order.postName ?? "Site orders"} (version {order.version})</Text>
      <Section title="Instructions" text={order.instructions} />
      <Section title="Duties" text={order.duties} />
      <Section title="Emergency procedures" text={order.emergencyProcedures} />
      <Section title="Restricted areas" text={order.restrictedAreas} />
      <Section title="Access rules" text={order.accessRules} />
      {order.contacts.length > 0 ? (
        <View style={s.sec}>
          <Text style={s.head}>Who to call</Text>
          {order.contacts.map((c) => (
            <Text key={c.id} style={s.body}>{c.name}{c.role ? ` (${c.role})` : ""}{c.phone ? `: ${c.phone}` : ""}</Text>
          ))}
        </View>
      ) : null}
      {needsAck ? (
        <Button label="I have read and understood this" onPress={onAcknowledge} loading={busy} />
      ) : (
        <Text style={s.ok}>You have acknowledged this version.</Text>
      )}
    </Card>
  );
}

export function MyPostScreen() {
  const qc = useQueryClient();
  const [postId, setPostId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const mine = useQuery({ queryKey: ["myPost"], queryFn: getMyPost });
  const postOrder = useQuery({ queryKey: ["postOrder", postId], queryFn: () => getPostOrder(postId as string), enabled: !!postId });

  const ack = useMutation({
    mutationFn: async (order: PostOrder) => acknowledgeOrder(order, await getDeviceId()),
    onSuccess: () => {
      setError(null);
      void qc.invalidateQueries({ queryKey: ["myPost"] });
    },
    onError: (e) => {
      setError(e instanceof Error ? e.message : "Could not record your acknowledgement");
      // A newer version may have been published: read again.
      void qc.invalidateQueries({ queryKey: ["myPost"] });
      void qc.invalidateQueries({ queryKey: ["postOrder"] });
    },
  });

  if (mine.isLoading) return <Screen title="My post"><Text style={s.body}>Loading...</Text></Screen>;
  if (mine.isError || !mine.data) {
    return (
      <Screen title="My post">
        <Text accessibilityRole="alert" style={s.err}>{mine.error instanceof Error ? mine.error.message : "Could not load your post orders"}</Text>
        <Text style={s.body}>Start your shift first, and check your signal.</Text>
        <Button label="Try again" onPress={() => void mine.refetch()} />
      </Screen>
    );
  }
  const data = mine.data;
  return (
    <Screen title="My post">
      {error ? <Text accessibilityRole="alert" style={s.err}>{error}</Text> : null}
      {data.siteLevelOrder ? (
        <OrderCard order={data.siteLevelOrder} needsAck={data.siteLevelNeedsAcknowledgment} busy={ack.isPending}
          onAcknowledge={() => data.siteLevelOrder && ack.mutate(data.siteLevelOrder)} />
      ) : <Text style={s.body}>No site-wide orders have been published for this site.</Text>}
      {data.posts.length > 0 ? <Text style={s.head}>Your post</Text> : null}
      {data.posts.map((p) => (
        <Button key={p.postId} label={p.postName + (p.hasActiveOrder ? "" : " (no orders)")} tone={p.postId === postId ? "primary" : "plain"}
          disabled={!p.hasActiveOrder} onPress={() => setPostId(p.postId)} />
      ))}
      {postId && postOrder.data ? (
        <OrderCard order={postOrder.data} needsAck busy={ack.isPending} onAcknowledge={() => postOrder.data && ack.mutate(postOrder.data)} />
      ) : null}
    </Screen>
  );
}

const s = StyleSheet.create({
  title: { color: colors.text, fontSize: 18, fontWeight: "700" },
  head: { color: colors.text, fontWeight: "700", fontSize: 16 },
  body: { color: colors.text, fontSize: 16 },
  sec: { gap: 2, marginBottom: 8 },
  ok: { color: colors.muted },
  err: { color: colors.danger },
});
