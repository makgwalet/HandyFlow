import { z } from "zod";

const num = z.coerce.number();
const nnum = z.coerce.number().nullish();

export const pumpSchema = z.object({
  id: z.string(), name: z.string(), fuelType: z.string().nullish(), tankName: z.string().nullish(),
  lastMeter: nnum, pricePerLitre: nnum, active: z.boolean().default(true), onShift: z.boolean().default(false),
});
export type Pump = z.infer<typeof pumpSchema>;
export const pumpListSchema = z.array(pumpSchema);

export const readingSchema = z.object({
  pumpId: z.string(), pumpName: z.string().nullish(), fuelType: z.string().nullish(),
  openingMeter: nnum, closingMeter: nnum, testLitres: nnum, litresSold: nnum, salesValue: nnum,
});
export type Reading = z.infer<typeof readingSchema>;

export const shiftSchema = z.object({
  id: z.string(), shiftNumber: z.string().nullish(), attendantName: z.string(),
  status: z.string(), openedAt: z.string(), closedAt: z.string().nullish(),
  cash: nnum, card: nnum, account: nnum, other: nnum, fleetCard: nnum,
  expected: nnum, litresSold: nnum, variance: nnum, varianceReason: z.string().nullish(),
  adjustments: nnum, tolerance: nnum, reviewStatus: z.string().nullish(), reviewNote: z.string().nullish(),
  readings: z.array(readingSchema).default([]),
});
export type Shift = z.infer<typeof shiftSchema>;
export const shiftListSchema = z.array(shiftSchema);

export const adjustmentSchema = z.object({ id: z.string(), kind: z.string(), amount: num, reason: z.string(), status: z.string() });
export type Adjustment = z.infer<typeof adjustmentSchema>;
export const adjustmentListSchema = z.array(adjustmentSchema);

export const rand = (n: number | null | undefined): string => `R ${(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
export const lit = (n: number | null | undefined): string => `${(n ?? 0).toLocaleString("en-ZA", { maximumFractionDigits: 1 })} L`;

/** Open shifts first, then newest. */
export const sortShifts = (s: Shift[]): Shift[] =>
  [...s].sort((a, b) => Number(b.status === "OPEN") - Number(a.status === "OPEN") || b.openedAt.localeCompare(a.openedAt));

/** Supervisors approve or reject refunds, voids and discounts and review shifts that closed outside tolerance. The server checks again. */
export const canSupervise = (p: readonly string[]): boolean => p.includes("FUEL_SHIFT_SUPERVISE");
/** Reversing a closed shift is a manager's decision. */
export const canReverse = (p: readonly string[]): boolean => ["FUEL_MANAGE", "FUEL_FORECOURT_MANAGE"].some((x) => p.includes(x));
export const MIN_REASON = 5;
/** Null when the reason is good enough to send; the server limits are 5 to 300 characters (review, reject) and 5 to 500 (reverse). */
export function reasonProblem(reason: string, max = 300): string | null {
  const t = reason.trim();
  if (t.length < MIN_REASON) return `Give a reason of at least ${MIN_REASON} characters.`;
  if (t.length > max) return `Keep the reason to ${max} characters or fewer.`;
  return null;
}

export const canRunShifts = (p: readonly string[]): boolean => ["FUEL_FORECOURT", "FUEL_MANAGE", "FUEL_FORECOURT_MANAGE"].some((x) => p.includes(x));

const toNum = (t: string): number => Number(t.trim().replace(",", "."));
const isMoney = (t: string) => { const n = toNum(t); return t.trim() !== "" && Number.isFinite(n) && n >= 0; };

// ---- open ----
/** price is what this phone showed for the pump when the shift was opened; the server refuses the open if the pump price has changed since. */
export interface OpenRow { pumpId: string; meter: string; price?: number | null }
export function openRows(pumps: Pump[]): OpenRow[] {
  return pumps.filter((p) => p.active && !p.onShift).map((p) => ({ pumpId: p.id, meter: p.lastMeter != null ? String(p.lastMeter) : "", ...(p.pricePerLitre != null ? { price: p.pricePerLitre } : {}) }));
}
export function validateOpen(attendant: string, rows: OpenRow[]): string | null {
  if (!attendant.trim()) return "Enter the attendant's name.";
  if (rows.length === 0) return "No pumps are free to open a shift on.";
  if (rows.some((r) => !isMoney(r.meter))) return "Enter the opening meter for every pump, zero or more.";
  return null;
}
export const openBody = (attendant: string, rows: OpenRow[], notes: string) => ({
  attendantName: attendant.trim(), ...(notes.trim() ? { notes: notes.trim() } : {}),
  readings: rows.map((r) => ({ pumpId: r.pumpId, openingMeter: toNum(r.meter), ...(r.price != null && r.price > 0 ? { expectedPrice: r.price } : {}) })),
});

// ---- close ----
export interface CloseRow { pumpId: string; closing: string; test: string }
export interface Takings { cash: string; card: string; account: string; fleetCard: string; other: string }
export const emptyTakings: Takings = { cash: "", card: "", account: "", fleetCard: "", other: "" };

export const closeRows = (s: Shift): CloseRow[] => s.readings.map((r) => ({ pumpId: r.pumpId, closing: "", test: "" }));

export function validateClose(s: Shift, rows: CloseRow[], t: Takings): string | null {
  for (const r of rows) {
    const name = s.readings.find((x) => x.pumpId === r.pumpId)?.pumpName ?? "a pump";
    if (!isMoney(r.closing)) return `Enter the closing meter for ${name}.`;
    const opening = s.readings.find((x) => x.pumpId === r.pumpId)?.openingMeter;
    if (opening != null && toNum(r.closing) < opening) return `The closing meter for ${name} is below its opening meter of ${opening}. Check the reading.`;
    if (r.test.trim() && !isMoney(r.test)) return `Test litres for ${name} must be a number, zero or more.`;
  }
  for (const [k, label] of [["cash", "cash"], ["card", "card"], ["account", "account"], ["fleetCard", "fleet card"], ["other", "other"]] as const) {
    if (t[k].trim() && !isMoney(t[k])) return `Takings for ${label} must be a number, zero or more.`;
  }
  return null;
}

/** An empty takings box means nothing was taken that way, so it goes as 0. */
const orZero = (t: string) => (t.trim() === "" ? 0 : toNum(t));
export const closeBody = (rows: CloseRow[], t: Takings, varianceReason: string) => ({
  cash: orZero(t.cash), card: orZero(t.card), account: orZero(t.account), other: orZero(t.other), fleetCard: orZero(t.fleetCard),
  ...(varianceReason.trim() ? { varianceReason: varianceReason.trim() } : {}),
  readings: rows.map((r) => ({ pumpId: r.pumpId, closingMeter: toNum(r.closing), ...(r.test.trim() ? { testLitres: toNum(r.test) } : {}) })),
});

/** Litres and value the shift should have taken in, worked out on the phone from the meters so the attendant can see a gap before closing. */
export function expectedPreview(s: Shift, rows: CloseRow[], prices: Record<string, number>): { litres: number; value: number } {
  let litres = 0, value = 0;
  for (const r of rows) {
    const open = s.readings.find((x) => x.pumpId === r.pumpId)?.openingMeter ?? 0;
    const l = Math.max(0, toNum(r.closing || "0") - open - (r.test.trim() ? toNum(r.test) : 0));
    litres += l; value += l * (prices[r.pumpId] ?? 0);
  }
  return { litres, value };
}

// ---- adjustments ----
export const ADJUSTMENT_KINDS = ["REFUND", "VOID", "DISCOUNT"] as const;
export type AdjustmentKind = (typeof ADJUSTMENT_KINDS)[number];
export function validateAdjustment(amount: string, reason: string): string | null {
  const n = toNum(amount);
  if (!amount.trim() || !Number.isFinite(n) || n <= 0) return "Enter the amount, more than zero.";
  if (reason.trim().length < 5) return "Give a reason of at least 5 characters.";
  if (reason.trim().length > 300) return "The reason is too long (300 characters at most).";
  return null;
}
export const adjustmentBody = (kind: AdjustmentKind, amount: string, reason: string) => ({ kind, amount: toNum(amount), reason: reason.trim() });

/** How the shift's variance reads: over, short, or within tolerance. */
export function varianceLabel(s: Pick<Shift, "variance" | "tolerance">): { text: string; bad: boolean } {
  const v = s.variance ?? 0; const tol = s.tolerance ?? 0;
  if (Math.abs(v) <= tol || Math.abs(v) < 0.005) return { text: "Balanced", bad: false };
  return { text: `${v < 0 ? "Short" : "Over"} by ${rand(Math.abs(v))}`, bad: true };
}

// ---- a shift opened on this phone that has not reached the server yet ----

/** A SHIFT_OPEN still in the outbox. Its item id is the id the server will know the open by. */
export interface QueuedOpenLike { id: string; kind: string; recordedAt: string; payload: Record<string, unknown> }

export const queuedOpens = <T extends QueuedOpenLike>(pending: T[]): T[] => pending.filter((i) => i.kind === "SHIFT_OPEN");

/** What the Close screen needs, built from the saved open: the attendant, and each pump's opening meter. Not a server shift: no number, never final. */
export function shiftFromQueuedOpen(item: QueuedOpenLike, pumps: { id: string; name?: string | null; fuelType?: string | null }[]): Shift | null {
  const body = item.payload.body as { attendantName?: string; readings?: { pumpId: string; openingMeter: number }[] } | undefined;
  if (!body || !Array.isArray(body.readings)) return null;
  return shiftSchema.parse({
    id: item.id, shiftNumber: null, attendantName: body.attendantName ?? "", status: "OPEN", openedAt: item.recordedAt,
    readings: body.readings.map((r) => ({ pumpId: r.pumpId, pumpName: pumps.find((p) => p.id === r.pumpId)?.name ?? null, fuelType: pumps.find((p) => p.id === r.pumpId)?.fuelType ?? null, openingMeter: r.openingMeter })),
  });
}
