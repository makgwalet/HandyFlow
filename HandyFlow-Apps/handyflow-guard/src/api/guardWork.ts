import { z } from "zod";
import { api } from "./client";
import type { OutboxItem } from "@/offline/outbox";
import { removePhoto } from "@/photos";

export const shiftSchema = z.object({
  id: z.string(),
  siteId: z.string(),
  siteName: z.string(),
  startAt: z.string(),
  endAt: z.string(),
  status: z.string(),
  actualStartAt: z.string().nullish(),
});
export type Shift = z.infer<typeof shiftSchema>;

export const roundSchema = z.object({
  id: z.string(),
  roundNumber: z.number(),
  status: z.string(),
  expectedStartAt: z.string(),
  expectedEndAt: z.string().nullish(),
  startedAt: z.string().nullish(),
  completedAt: z.string().nullish(),
  checkpointsExpected: z.number(),
  checkpointsScanned: z.number(),
  checkpoints: z.array(
    z.object({ checkpointId: z.string(), name: z.string(), sequence: z.number(), scanned: z.boolean() }),
  ),
});
export type Round = z.infer<typeof roundSchema>;

/** The open session the server holds for this guard on this device, or null. Only the fields the app uses. */
export const currentSessionSchema = z
  .object({ id: z.string(), shiftId: z.string().nullish(), startedAt: z.string() })
  .nullable();
export type CurrentSession = z.infer<typeof currentSessionSchema>;

export const openedSessionSchema = z.object({
  sessionId: z.string(),
  shiftId: z.string().nullish(),
  siteId: z.string().nullish(),
  shiftSummary: z.string().nullish(),
  startedAt: z.string(),
});

export async function getMyShifts(): Promise<Shift[]> {
  return z.array(shiftSchema).parse(await api<unknown>("/api/v1/guard/me/shifts"));
}

export async function getMyRounds(): Promise<Round[]> {
  return z.array(roundSchema).parse(await api<unknown>("/api/v1/guard/me/rounds"));
}

export async function getCurrentSession(deviceHardwareId: string): Promise<CurrentSession> {
  const raw = await api<unknown>(`/api/v1/guard/sessions/current?deviceHardwareId=${encodeURIComponent(deviceHardwareId)}`);
  return currentSessionSchema.parse(raw ?? null);
}

/** Clock in. Sends only the device and where the phone is. The server decides who the guard is and whether they are on site. */
export async function clockIn(input: {
  deviceHardwareId: string;
  latitude: number | null;
  longitude: number | null;
  accuracyMetres: number | null;
}) {
  return openedSessionSchema.parse(
    await api<unknown>("/api/v1/guard/sessions/open", { method: "POST", body: input }),
  );
}

export function clockOut(
  sessionId: string,
  input: { handoverNotes: string; resourcesReturned: boolean; incompletePatrolReason: string },
): Promise<unknown> {
  return api<unknown>(`/api/v1/guard/sessions/${sessionId}/close`, {
    method: "POST",
    body: {
      handoverNotes: input.handoverNotes.trim() || null,
      resourcesReturned: input.resourcesReturned,
      incompletePatrolReason: input.incompletePatrolReason.trim() || null,
    },
  });
}

/** Sends one queued write to its endpoint. The body already carries clientRef and recordedAt. */
export async function sendOutboxItem(item: OutboxItem): Promise<void> {
  if (item.kind === "EVIDENCE") {
    await sendPhoto(item);
    return;
  }
  const path =
    item.kind === "SCAN" ? "/api/v1/guard/checkpoints/scan"
    : item.kind === "INCIDENT" ? "/api/v1/guard/incidents"
    : "/api/v1/guard/duress";
  await api<unknown>(path, { method: "POST", body: item.payload });
}

/** Uploads one queued photo, then deletes the phone's copy. A failed upload keeps the file for the retry. */
async function sendPhoto(item: OutboxItem): Promise<void> {
  const p = item.payload as { incidentRef: string; localUri: string; label: string | null };
  const form = new FormData();
  form.append("file", { uri: p.localUri, name: `${item.id}.jpg`, type: "image/jpeg" } as unknown as Blob);
  form.append("photoRef", item.id);
  if (p.label) form.append("label", p.label);
  await api<unknown>(`/api/v1/guard/incidents/by-ref/${p.incidentRef}/evidence`, { method: "POST", form });
  await removePhoto(p.localUri);
}

// ── My post (site orders) ─────────────────────────────────────────────────────

const orderSchema = z.object({
  id: z.string(),
  version: z.number(),
  postName: z.string().nullish(),
  instructions: z.string().nullish(),
  duties: z.string().nullish(),
  emergencyProcedures: z.string().nullish(),
  restrictedAreas: z.string().nullish(),
  accessRules: z.string().nullish(),
  contacts: z.array(z.object({ id: z.string(), name: z.string(), role: z.string().nullish(), phone: z.string().nullish() })).default([]),
});
export type PostOrder = z.infer<typeof orderSchema>;

const myPostSchema = z.object({
  siteId: z.string(),
  siteLevelOrder: orderSchema.nullish(),
  siteLevelNeedsAcknowledgment: z.boolean(),
  posts: z.array(z.object({ postId: z.string(), postName: z.string(), hasActiveOrder: z.boolean() })),
});
export type MyPost = z.infer<typeof myPostSchema>;

/** Needs an open session: the server picks the site from it. */
export async function getMyPost(): Promise<MyPost> {
  return myPostSchema.parse(await api<unknown>("/api/v1/guard/my-post"));
}

export async function getPostOrder(postId: string): Promise<PostOrder> {
  return orderSchema.parse(await api<unknown>(`/api/v1/guard/my-post/posts/${postId}`));
}

/** Says which version was read, so a newer one published meanwhile is refused rather than wrongly acknowledged. */
export function acknowledgeOrder(order: { id: string; version: number }, deviceHardwareId: string): Promise<unknown> {
  return api<unknown>(`/api/v1/guard/my-post/orders/${order.id}/acknowledge`, {
    method: "POST",
    body: { deviceHardwareId, acknowledgedOffline: false, version: order.version },
  });
}

// ── Shift swaps ───────────────────────────────────────────────────────────────

const swapSchema = z.object({
  id: z.string(),
  originalShiftId: z.string(),
  shiftSummary: z.string().nullish(),
  requestingGuardId: z.string(),
  requestingGuardName: z.string().nullish(),
  proposedGuardId: z.string().nullish(),
  proposedGuardName: z.string().nullish(),
  status: z.string(),
  reason: z.string().nullish(),
  rejectionReason: z.string().nullish(),
});
export type Swap = z.infer<typeof swapSchema>;

export async function getMySwaps(): Promise<Swap[]> {
  const page = z.object({ content: z.array(swapSchema) }).parse(await api<unknown>("/api/v1/guard/shifts/swaps/my-swaps?size=30"));
  return page.content;
}

/** An open request: any available guard, the supervisor decides. Naming a colleague needs a colleague list the server does not offer guards yet. */
export async function requestSwap(originalShiftId: string, reason: string): Promise<Swap> {
  return swapSchema.parse(
    await api<unknown>("/api/v1/guard/shifts/swaps", {
      method: "POST",
      body: { originalShiftId, proposedGuardId: null, reason: reason.trim() || null },
    }),
  );
}

export async function acceptSwap(id: string): Promise<Swap> {
  return swapSchema.parse(await api<unknown>(`/api/v1/guard/shifts/swaps/${id}/accept`, { method: "POST" }));
}

export async function cancelSwap(id: string): Promise<Swap> {
  return swapSchema.parse(await api<unknown>(`/api/v1/guard/shifts/swaps/${id}`, { method: "DELETE" }));
}

// ── Equipment in my custody ───────────────────────────────────────────────────

const custodySchema = z.object({
  id: z.string(),
  resourceType: z.string(),
  resourceRef: z.string(),
  checkedOutAt: z.string(),
});
export type Custody = z.infer<typeof custodySchema>;

/** Firearms are issued through the armoury process, so the app does not offer them. */
export const RESOURCE_TYPES = ["RADIO", "KEY", "VEHICLE", "OTHER"] as const;
export type ResourceType = (typeof RESOURCE_TYPES)[number];
export const RETURN_CONDITIONS = ["GOOD", "DAMAGED", "MISSING"] as const;
export type ReturnCondition = (typeof RETURN_CONDITIONS)[number];

export async function getMyEquipment(): Promise<Custody[]> {
  return z.array(custodySchema).parse(await api<unknown>("/api/v1/guard/sessions/resources/mine"));
}

export async function checkoutEquipment(sessionId: string, type: ResourceType, ref: string): Promise<Custody> {
  return custodySchema.parse(
    await api<unknown>(`/api/v1/guard/sessions/${sessionId}/resources/checkout`, {
      method: "POST",
      body: { resourceType: type, resourceRef: ref.trim(), notes: null },
    }),
  );
}

export function returnEquipment(custodyId: string, condition: ReturnCondition, notes: string): Promise<unknown> {
  return api<unknown>(`/api/v1/guard/sessions/resources/${custodyId}/return`, {
    method: "POST",
    body: { condition, notes: notes.trim() || null },
  });
}
