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

/** After a lost reply the reading may already be on the server. Same time and litres means it is: do not save it twice. */
export function alreadyRecorded(readings: DipReading[], readAtIso: string, litres: number): boolean {
  const at = Date.parse(readAtIso);
  return readings.some((r) => Date.parse(r.readAt) === at && Math.abs(r.actualLitres - litres) < 0.001);
}
