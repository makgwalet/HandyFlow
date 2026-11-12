import { z } from "zod";
import { api } from "./client";
import type { OutboxItem } from "@/offline/outbox";

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
  const path =
    item.kind === "SCAN" ? "/api/v1/guard/checkpoints/scan"
    : item.kind === "INCIDENT" ? "/api/v1/guard/incidents"
    : "/api/v1/guard/duress";
  await api<unknown>(path, { method: "POST", body: item.payload });
}
