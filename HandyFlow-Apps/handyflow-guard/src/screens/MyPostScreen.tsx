import React, { useState } from "react";
import { StyleSheet, Text, View } from "react-native";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getDeviceId } from "@/auth/deviceStore";
import { useAuth } from "@/auth/AuthContext";
import { ApiError } from "@/api/client";
import { acknowledgeOrder, getMyPost, getPostOrder, type MyPost, type PostOrder } from "@/api/guardWork";
import { useOutbox } from "@/offline/OutboxProvider";
import { acknowledgeItem } from "@/offline/payloads";
import { loadCache, saveMyPost, saveOrder } from "@/postOrderCache";
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

function OrderCard({ order, needsAck, queued, onAcknowledge, busy }: { order: PostOrder; needsAck: boolean; queued: boolean; onAcknowledge: () => void; busy: boolean }) {
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
      {queued ? (
        <Text style={s.ok}>Saved on this phone. It will be sent as soon as there is signal.</Text>
      ) : needsAck ? (
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
  const { session } = useAuth();
  const guardId = session?.guardId ?? "";
  const { enqueue, context, pending } = useOutbox();
  const queuedIds = new Set(pending.filter((i) => i.kind === "ACK").map((i) => String(i.payload["orderId"])));

  // Online: the live copy, remembered on the phone. No signal: the remembered copy, labelled with when it was saved.
  const mine = useQuery({
    queryKey: ["myPost", guardId],
    queryFn: async (): Promise<{ data: MyPost; savedAt: string | null }> => {
      try {
        const data = await getMyPost();
        void saveMyPost(guardId, data);
        return { data, savedAt: null };
      } catch (e) {
        if (e instanceof ApiError && e.isNetwork) {
          const cached = await loadCache(guardId);
          if (cached?.myPost) return { data: cached.myPost, savedAt: cached.savedAt };
        }
        throw e;
      }
    },
    retry: false,
  });
  const postOrder = useQuery({
    queryKey: ["postOrder", guardId, postId],
    queryFn: async (): Promise<{ order: PostOrder; savedAt: string | null }> => {
      const id = postId as string;
      try {
        const order = await getPostOrder(id);
        void saveOrder(guardId, id, order);
        return { order, savedAt: null };
      } catch (e) {
        if (e instanceof ApiError && e.isNetwork) {
          const cached = (await loadCache(guardId))?.orders[id];
          if (cached) return { order: cached, savedAt: (await loadCache(guardId))?.savedAt ?? null };
        }
        throw e;
      }
    },
    enabled: !!postId,
    retry: false,
  });

  const ack = useMutation({
    mutationFn: async (order: PostOrder) => {
      const deviceId = await getDeviceId();
      try {
        await acknowledgeOrder(order, deviceId);
      } catch (e) {
        // No signal: keep the acknowledgement of exactly this version on the phone and send it later.
        if (e instanceof ApiError && e.isNetwork) {
          await enqueue(acknowledgeItem({ orderId: order.id, version: order.version, deviceHardwareId: deviceId }, context()));
          return;
        }
        throw e;
      }
    },
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
  const savedNote = (at: string | null) =>
    at ? <Text style={s.warn}>No signal. This is the copy saved on this phone at {new Date(at).toLocaleString()}. A newer version may exist.</Text> : null;

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
  const data = mine.data.data;
  return (
    <Screen title="My post">
      {savedNote(mine.data.savedAt)}
      {error ? <Text accessibilityRole="alert" style={s.err}>{error}</Text> : null}
      {data.siteLevelOrder ? (
        <OrderCard order={data.siteLevelOrder} needsAck={data.siteLevelNeedsAcknowledgment} queued={queuedIds.has(data.siteLevelOrder.id)} busy={ack.isPending}
          onAcknowledge={() => data.siteLevelOrder && ack.mutate(data.siteLevelOrder)} />
      ) : <Text style={s.body}>No site-wide orders have been published for this site.</Text>}
      {data.posts.length > 0 ? <Text style={s.head}>Your post</Text> : null}
      {data.posts.map((p) => (
        <Button key={p.postId} label={p.postName + (p.hasActiveOrder ? "" : " (no orders)")} tone={p.postId === postId ? "primary" : "plain"}
          disabled={!p.hasActiveOrder} onPress={() => setPostId(p.postId)} />
      ))}
      {postId && postOrder.data ? (
        <>
          {savedNote(postOrder.data.savedAt)}
          <OrderCard order={postOrder.data.order} needsAck queued={queuedIds.has(postOrder.data.order.id)} busy={ack.isPending}
            onAcknowledge={() => postOrder.data && ack.mutate(postOrder.data.order)} />
        </>
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
  warn: { color: colors.muted, fontStyle: "italic" },
});
