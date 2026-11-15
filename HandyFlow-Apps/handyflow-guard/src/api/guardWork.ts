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
  if (item.kind === "GATE_ARRIVAL") {
    await api<unknown>("/api/v1/guard/gate/entries", { method: "POST", body: item.payload });
    return;
  }
  if (item.kind === "GATE_EXIT") {
    const p = item.payload as GateRef & { deviceHardwareId: string };
    await api<unknown>(gatePath(p, "exit"), { method: "POST", body: { deviceHardwareId: p.deviceHardwareId, recordedAt: item.recordedAt } });
    return;
  }
  if (item.kind === "GATE_PHOTO") {
    const p = item.payload as GateRef & { docType: string; localUri: string };
    const form = new FormData();
    form.append("file", { uri: p.localUri, name: `${item.id}.jpg`, type: "image/jpeg" } as unknown as Blob);
    form.append("docType", p.docType);
    form.append("photoRef", item.id);
    await api<unknown>(gatePath(p, "attachments"), { method: "POST", form });
    await removePhoto(p.localUri);
    return;
  }
  if (item.kind === "ACK") {
    const p = item.payload as { orderId: string; version: number; deviceHardwareId: string };
    await api<unknown>(`/api/v1/guard/my-post/orders/${p.orderId}/acknowledge`, {
      method: "POST",
      body: { deviceHardwareId: p.deviceHardwareId, acknowledgedOffline: true, acknowledgedAt: item.recordedAt, version: p.version },
    });
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

export const orderSchema = z.object({
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

export const myPostSchema = z.object({
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

const colleagueSchema = z.object({ id: z.string(), name: z.string() });
export type Colleague = z.infer<typeof colleagueSchema>;

/** Guards on the same rosters as me, names only. */
export async function getSwapColleagues(): Promise<Colleague[]> {
  return z.array(colleagueSchema).parse(await api<unknown>("/api/v1/guard/shifts/swaps/colleagues"));
}

/** Ask a named colleague to take a shift. The server will not approve a request that names nobody, so the app always names one. */
export async function requestSwap(originalShiftId: string, proposedGuardId: string, reason: string): Promise<Swap> {
  return swapSchema.parse(
    await api<unknown>("/api/v1/guard/shifts/swaps", {
      method: "POST",
      body: { originalShiftId, proposedGuardId, reason: reason.trim() || null },
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

// ── Gate log ──────────────────────────────────────────────────────────────────

export const accessPointSchema = z.object({ id: z.string(), name: z.string() });
export type AccessPoint = z.infer<typeof accessPointSchema>;

export const gateEntrySchema = z.object({
  id: z.string(),
  accessPointName: z.string().nullish(),
  entryType: z.string(),
  personName: z.string(),
  company: z.string().nullish(),
  vehicleRegistration: z.string().nullish(),
  hostName: z.string().nullish(),
  loggedInAt: z.string(),
  status: z.string(),
});
export type GateEntry = z.infer<typeof gateEntrySchema>;

export const ENTRY_TYPES = ["VISITOR", "CONTRACTOR", "DELIVERY", "STAFF_VEHICLE", "OTHER"] as const;
export type EntryType = (typeof ENTRY_TYPES)[number];

export async function getAccessPoints(): Promise<AccessPoint[]> {
  return z.array(accessPointSchema).parse(await api<unknown>("/api/v1/guard/gate/access-points"));
}

export async function getOnSite(): Promise<GateEntry[]> {
  return z.array(gateEntrySchema).parse(await api<unknown>("/api/v1/guard/gate/on-site"));
}

const clean = (v: string) => v.trim() || null;

/** `clientRef` is made once per arrival form, so a double tap or a timed-out retry logs the visitor once. */
export async function logArrival(input: {
  clientRef: string;
  deviceHardwareId: string;
  accessPointId: string;
  entryType: EntryType;
  personName: string;
  idNumber: string;
  company: string;
  hostName: string;
  purpose: string;
  vehicleRegistration: string;
}): Promise<GateEntry> {
  return gateEntrySchema.parse(
    await api<unknown>("/api/v1/guard/gate/entries", {
      method: "POST",
      body: {
        clientRef: input.clientRef,
        deviceHardwareId: input.deviceHardwareId,
        accessPointId: input.accessPointId,
        entryType: input.entryType,
        personName: input.personName.trim(),
        idNumber: clean(input.idNumber),
        company: clean(input.company),
        hostName: clean(input.hostName),
        purpose: clean(input.purpose),
        vehicleRegistration: clean(input.vehicleRegistration)?.toUpperCase() ?? null,
        idScanConfidence: clean(input.idNumber) ? "MANUAL_ENTRY" : null,
      },
    }),
  );
}

export function logDeparture(entryId: string, deviceHardwareId: string): Promise<unknown> {
  return api<unknown>(`/api/v1/guard/gate/entries/${entryId}/exit`, { method: "POST", body: { deviceHardwareId } });
}

type GateRef = { entryId?: string | null; entryRef?: string | null };
const gatePath = (p: GateRef, tail: "exit" | "attachments") =>
  p.entryId ? `/api/v1/guard/gate/entries/${p.entryId}/${tail}` : `/api/v1/guard/gate/entries/by-ref/${p.entryRef}/${tail}`;

export const GATE_PHOTO_TYPES = [
  { docType: "ID_DOCUMENT", label: "ID photo" },
  { docType: "VEHICLE_DISC", label: "Vehicle disc" },
  { docType: "GENERAL_PHOTO", label: "Other photo" },
] as const;
export type GatePhotoType = (typeof GATE_PHOTO_TYPES)[number]["docType"];

/** Uploads a photo for an entry at the guard's own site, then deletes the phone's copy. The file stays if the upload fails. */
export async function attachGatePhoto(entryId: string, docType: GatePhotoType, localUri: string, photoRef: string): Promise<void> {
  const form = new FormData();
  form.append("file", { uri: localUri, name: `${photoRef}.jpg`, type: "image/jpeg" } as unknown as Blob);
  form.append("docType", docType);
  form.append("photoRef", photoRef); // a retried upload (reply lost) is attached once
  await api<unknown>(`/api/v1/guard/gate/entries/${entryId}/attachments`, { method: "POST", form });
  await removePhoto(localUri);
}

// ── Site supervisor ───────────────────────────────────────────────────────────

const siteOverviewSchema = z.object({
  siteId: z.string(),
  siteName: z.string(),
  generatedAt: z.string(),
  onDuty: z.array(
    z.object({
      guardId: z.string(),
      name: z.string(),
      shiftStart: z.string(),
      shiftEnd: z.string(),
      overrunning: z.boolean(),
      gpsState: z.string().nullish(),
      lastScanAt: z.string().nullish(),
      lastScanCheckpoint: z.string().nullish(),
      roundsTotal: z.number(),
      roundsComplete: z.number(),
      roundsMissed: z.number(),
    }),
  ),
  openIncidents: z.array(z.object({ id: z.string(), title: z.string(), severity: z.string(), status: z.string(), createdAt: z.string() })),
});
export type SiteOverview = z.infer<typeof siteOverviewSchema>;

/** Only a guard a manager made a site supervisor gets this; anyone else gets 403 and the button never shows. */
export async function getSiteOverview(): Promise<SiteOverview> {
  return siteOverviewSchema.parse(await api<unknown>("/api/v1/guard/supervisor/site"));
}

export async function getSwapsToDecide(): Promise<Swap[]> {
  return z.array(swapSchema).parse(await api<unknown>("/api/v1/guard/supervisor/swaps"));
}

export function decideSwap(id: string, approve: boolean, reason: string): Promise<unknown> {
  return api<unknown>(`/api/v1/guard/supervisor/swaps/${id}/${approve ? "approve" : "reject"}`, {
    method: "POST",
    body: { reason: reason.trim() || null },
  });
}

export function acknowledgeIncident(id: string): Promise<unknown> {
  return api<unknown>(`/api/v1/guard/supervisor/incidents/${id}/acknowledge`, { method: "POST" });
}

export function resolveIncident(id: string, note: string): Promise<unknown> {
  return api<unknown>(`/api/v1/guard/supervisor/incidents/${id}/resolve`, { method: "POST", body: { note: note.trim() || null } });
}
