import { z } from "zod";

/** The part of the backend TankResponse the depot screens use. */
export const tankSchema = z.object({
  id: z.string(),
  name: z.string(),
  fuelType: z.string(),
  capacityLitres: z.number(),
  currentLitres: z.number(),
  fillPercentage: z.number().nullish(),
  low: z.boolean().default(false),
  location: z.string().nullish(),
});
export type Tank = z.infer<typeof tankSchema>;
export const tankListSchema = z.array(tankSchema);

export const dipReadingSchema = z.object({
  id: z.string(),
  readAt: z.string(),
  actualLitres: z.number(),
  calculatedLitres: z.number().nullish(),
  varianceLitres: z.number().nullish(),
  negativeVariance: z.boolean().default(false),
  readBy: z.string().nullish(),
});
export type DipReading = z.infer<typeof dipReadingSchema>;
/** Spring page: the list is in `content`. */
export const dipPageSchema = z.object({ content: z.array(dipReadingSchema) });

const fmtL = (n: number) => `${Math.round(n).toLocaleString("en-ZA")} L`;

export function fillText(t: Pick<Tank, "currentLitres" | "capacityLitres" | "fillPercentage">): string {
  const pct = t.fillPercentage ?? (t.capacityLitres > 0 ? (t.currentLitres / t.capacityLitres) * 100 : 0);
  return `${fmtL(t.currentLitres)} of ${fmtL(t.capacityLitres)} (${Math.round(pct)}%)`;
}

/** Low tanks first, then by name. */
export function sortTanks(tanks: Tank[]): Tank[] {
  return [...tanks].sort((a, b) => Number(b.low) - Number(a.low) || a.name.localeCompare(b.name));
}

/** The server enforces this too (permission to record a dip). The app only decides whether to show the button. */
export const canRecordDip = (permissions: readonly string[]): boolean =>
  permissions.includes("FUEL_MANAGE") || permissions.includes("FUEL_OPERATIONS_MANAGE");

/** Null when the reading can be saved, otherwise the message to show. A reading cannot be above the tank's capacity. */
export function validateDip(litres: string, capacityLitres?: number | null): string | null {
  const t = litres.trim().replace(",", ".");
  if (!t) return "Enter the litres you measured.";
  const n = Number(t);
  if (!Number.isFinite(n) || n < 0) return "Litres must be a number, zero or more.";
  if (capacityLitres != null && n > capacityLitres) return `That is more than the tank's ${fmtL(capacityLitres)} capacity. Check the reading.`;
  return null;
}

export const dipBody = (litres: string, readAtIso: string, readBy: string) => ({
  actualLitres: Number(litres.trim().replace(",", ".")),
  readAt: readAtIso,
  readBy,
});

export function varianceText(r: Pick<DipReading, "varianceLitres" | "negativeVariance">): string {
  if (r.varianceLitres == null) return "No variance calculated";
  const v = r.varianceLitres;
  if (Math.abs(v) < 0.5) return "Matches the book stock";
  return `${v < 0 ? "Short" : "Over"} by ${fmtL(Math.abs(v))}`;
}

/** The server enforces this too. Receiving fuel needs the same permission as a dip reading. */
export const canReceiveFuel = canRecordDip;

export interface ReceiveForm { litres: string; price: string; deliveryNote: string; invoiceRef: string; freight: string }
export const emptyReceive: ReceiveForm = { litres: "", price: "", deliveryNote: "", invoiceRef: "", freight: "" };

const toNum = (t: string) => Number(t.trim().replace(",", "."));

/**
 * Null when the receipt can be saved, otherwise the message to show. Fuel cannot be received past the room left in the tank
 * (the server refuses it too, but an offline phone should say so now, not hours later).
 */
export function validateReceive(f: ReceiveForm, tank: Pick<Tank, "capacityLitres" | "currentLitres">): string | null {
  if (!f.litres.trim()) return "Enter the litres received.";
  const l = toNum(f.litres);
  if (!Number.isFinite(l) || l <= 0) return "Litres received must be more than zero.";
  const room = tank.capacityLitres - tank.currentLitres;
  if (l > room + 0.001) return `That is more than the ${fmtL(Math.max(room, 0))} of room left in the tank. Check the litres.`;
  if (!f.price.trim()) return "Enter the price per litre.";
  const p = toNum(f.price);
  if (!Number.isFinite(p) || p <= 0) return "Price per litre must be more than zero.";
  if (f.freight.trim()) {
    const fr = toNum(f.freight);
    if (!Number.isFinite(fr) || fr < 0) return "Freight must be a number, zero or more.";
  }
  return null;
}

/** The request body without clientRequestId; the outbox adds its item id when it sends. */
export function receiveBody(f: ReceiveForm, receivedAtIso: string) {
  return {
    litresReceived: toNum(f.litres),
    pricePerLitre: toNum(f.price),
    receivedAt: receivedAtIso,
    ...(f.deliveryNote.trim() ? { deliveryNote: f.deliveryNote.trim() } : {}),
    ...(f.invoiceRef.trim() ? { invoiceRef: f.invoiceRef.trim() } : {}),
    ...(f.freight.trim() ? { freightAmount: toNum(f.freight) } : {}),
  };
}
