import { z } from "zod";
import type { OutboxItem } from "@/offline/outbox";

/** The part of the backend DeliveryResponse the driver app uses. Extra fields are ignored; a missing required one fails loudly. */
export const loadSchema = z.object({
  id: z.string(),
  status: z.string(),
  fuelType: z.string(),
  litresOrdered: z.number(),
  litresDelivered: z.number().nullish(),
  loadedLitres: z.number().nullish(),
  deliveryAddress: z.record(z.string(), z.string()).nullish(),
  vehicleReg: z.string().nullish(),
  scheduledAt: z.string().nullish(),
  windowStart: z.string().nullish(),
  windowEnd: z.string().nullish(),
  failedAttempts: z.number().default(0),
  routeSequence: z.number().nullish(),
  receiptNumber: z.string().nullish(),
  sealNumbers: z.string().nullish(),
});
export type Load = z.infer<typeof loadSchema>;
export const loadListSchema = z.array(loadSchema);

export const FAILURE_REASONS: { code: string; label: string }[] = [
  { code: "SITE_CLOSED", label: "Site closed" },
  { code: "NO_ACCESS", label: "No access to the site" },
  { code: "RECEIVER_ABSENT", label: "Nobody to receive the fuel" },
  { code: "REFUSED", label: "Customer refused the delivery" },
  { code: "SAFETY", label: "Safety concern" },
  { code: "VEHICLE_FAULT", label: "Vehicle fault" },
  { code: "OTHER", label: "Other (describe)" },
];
export const reasonLabel = (code: string): string => FAILURE_REASONS.find((r) => r.code === code)?.label ?? code;

export const EVIDENCE_KINDS = [
  { code: "PHOTO", label: "Photo" },
  { code: "SIGNATURE", label: "Signed delivery note" },
  { code: "DOCUMENT", label: "Document" },
] as const;

export type LoadStage = "READY" | "ON_THE_ROAD" | "DONE" | "OTHER";

export function stageOf(l: Pick<Load, "status">): LoadStage {
  if (l.status === "SCHEDULED") return "READY";
  if (l.status === "IN_TRANSIT") return "ON_THE_ROAD";
  if (l.status === "DELIVERED") return "DONE";
  return "OTHER";
}

export const stageText = (l: Pick<Load, "status">): string =>
  ({ READY: "Ready to go", ON_THE_ROAD: "On the road", DONE: "Delivered", OTHER: l.status.replace(/_/g, " ").toLowerCase() })[stageOf(l)];

/** "08:00 to 12:00", "from 08:00", "by 12:00", or null when there is no window. */
export function windowText(start?: string | null, end?: string | null, fmt: (iso: string) => string = hhmm): string | null {
  if (start && end) return `${fmt(start)} to ${fmt(end)}`;
  if (start) return `from ${fmt(start)}`;
  if (end) return `by ${fmt(end)}`;
  return null;
}
export function hhmm(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

export function addressText(a?: Record<string, string> | null): string {
  if (!a) return "No address";
  return ["street", "line1", "address", "suburb", "city", "town", "province"].map((k) => a[k]).filter(Boolean).join(", ") || Object.values(a).filter(Boolean).join(", ") || "No address";
}

/** Today's work first: loads that are on the road, then the route order, then scheduled time. Finished loads go last. */
export function sortLoads(loads: Load[]): Load[] {
  const rank = (l: Load) => ({ ON_THE_ROAD: 0, READY: 1, OTHER: 2, DONE: 3 })[stageOf(l)];
  return [...loads].sort((a, b) =>
    rank(a) - rank(b) ||
    (a.routeSequence ?? 1e9) - (b.routeSequence ?? 1e9) ||
    (a.scheduledAt ?? "").localeCompare(b.scheduledAt ?? ""));
}

// ── Work waiting in the outbox ──────────────────────────────────────────────

const LOAD_KINDS = ["DISPATCH", "COMPLETE", "FAILED_ATTEMPT", "EVIDENCE"] as const;
export type QueuedKind = (typeof LOAD_KINDS)[number];

/** The kinds of write saved on the phone for one load and not yet sent, so the screen can say "saved, waiting to send" instead of offering the same action twice. */
export function queuedKinds(items: OutboxItem[], loadId: string): Set<QueuedKind> {
  return new Set(items.filter((i) => i.payload.loadId === loadId).map((i) => i.kind as QueuedKind));
}

export type LoadActions = { start: boolean; complete: boolean; failed: boolean; evidence: boolean };

/**
 * What the driver may do now. The phone's own queue counts: a trip already started offline is not offered again,
 * and a load already closed out offline (delivered or failed) cannot be closed twice.
 */
export function actionsFor(l: Pick<Load, "status">, queued: Set<QueuedKind>): LoadActions {
  const stage = stageOf(l);
  const closed = queued.has("COMPLETE") || queued.has("FAILED_ATTEMPT");
  const started = stage === "ON_THE_ROAD" || queued.has("DISPATCH");
  return {
    start: stage === "READY" && !queued.has("DISPATCH"),
    complete: started && stage !== "DONE" && !closed,
    failed: started && stage !== "DONE" && !closed,
    evidence: stage !== "OTHER" || queued.size > 0,
  };
}

/** One line for a card: window, route stop, failed attempts, and anything saved on the phone. */
export function loadSummary(l: Load, queued: Set<QueuedKind>): string {
  const parts: string[] = [stageText(l)];
  const w = windowText(l.windowStart, l.windowEnd);
  if (w) parts.push(`window ${w}`);
  if (l.routeSequence) parts.push(`stop ${l.routeSequence}`);
  if (l.failedAttempts > 0) parts.push(`${l.failedAttempts} failed attempt${l.failedAttempts === 1 ? "" : "s"}`);
  if (queued.has("COMPLETE")) parts.push("delivery saved, waiting to send");
  else if (queued.has("FAILED_ATTEMPT")) parts.push("failed delivery saved, waiting to send");
  else if (queued.has("DISPATCH")) parts.push("start saved, waiting to send");
  return parts.join(" · ");
}

// ── Forms ───────────────────────────────────────────────────────────────────

export interface CompleteForm {
  litres: string;
  receiverName: string;
  receiverIdBadge: string;
  meterStart: string;
  meterEnd: string;
  onBehalf: boolean;
  onBehalfOf: string;
}

const num = (s: string): number | null => {
  const t = s.trim().replace(",", ".");
  if (!t) return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : NaN;
};

/** Null when the delivery can be saved, otherwise the message to show. The server checks again. */
export function validateComplete(f: CompleteForm, loadedLitres?: number | null): string | null {
  const litres = num(f.litres);
  if (litres === null || Number.isNaN(litres) || litres <= 0) return "Enter the litres delivered. It must be more than zero.";
  if (loadedLitres != null && litres > loadedLitres) return `Litres delivered cannot be more than the ${loadedLitres} L loaded.`;
  if (!f.receiverName.trim()) return "Enter the name of the person who received the fuel.";
  if (f.onBehalf && !f.onBehalfOf.trim()) return "Enter the name of the receiver you signed on behalf of.";
  const a = num(f.meterStart), b = num(f.meterEnd);
  if ((a !== null && Number.isNaN(a)) || (b !== null && Number.isNaN(b))) return "Meter readings must be numbers.";
  if ((a === null) !== (b === null)) return "Enter both meter readings, or neither.";
  if (a !== null && b !== null && b <= a) return "The end meter reading must be higher than the start reading.";
  return null;
}

/** A warning, not a block: the pump meter and the litres typed disagree by more than 1%. */
export function meterWarning(f: Pick<CompleteForm, "litres" | "meterStart" | "meterEnd">): string | null {
  const litres = num(f.litres), a = num(f.meterStart), b = num(f.meterEnd);
  if (litres === null || a === null || b === null || [litres, a, b].some(Number.isNaN)) return null;
  const metered = b - a;
  if (metered <= 0 || litres <= 0) return null;
  return Math.abs(metered - litres) / metered > 0.01 ? `The meter shows ${round1(metered)} L but you entered ${round1(litres)} L. Check both before saving.` : null;
}
const round1 = (n: number) => Math.round(n * 10) / 10;

export const completeBody = (f: CompleteForm) => ({
  litresDelivered: num(f.litres),
  receiverName: f.receiverName.trim(),
  receiverIdBadge: f.receiverIdBadge.trim() || null,
  meterReadingStart: num(f.meterStart),
  meterReadingEnd: num(f.meterEnd),
  signedOnBehalf: f.onBehalf,
  onBehalfOf: f.onBehalf ? f.onBehalfOf.trim() : null,
});

export interface FailedForm { reason: string; note: string; litresReturned: string }

export function validateFailure(f: FailedForm, loadedLitres?: number | null): string | null {
  if (!f.reason) return "Pick a reason.";
  if (f.reason === "OTHER" && !f.note.trim()) return "Describe what happened.";
  const n = num(f.litresReturned);
  if (n !== null) {
    if (Number.isNaN(n) || n < 0) return "Litres returned must be zero or more.";
    if (n > 0 && loadedLitres == null) return "No loading was recorded, so litres cannot be returned.";
    if (loadedLitres != null && n > loadedLitres) return `Litres returned cannot be more than the ${loadedLitres} L loaded.`;
  }
  return null;
}

export const failedBody = (f: FailedForm) => ({
  reasonCode: f.reason,
  note: f.note.trim() || null,
  litresReturned: num(f.litresReturned),
});
