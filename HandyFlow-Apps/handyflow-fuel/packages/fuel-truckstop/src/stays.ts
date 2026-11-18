import { z } from "zod";

const nnum = z.coerce.number().nullish();

export const staySchema = z.object({
  id: z.string(), vehicleRegistration: z.string(), driverName: z.string().nullish(), driverPhone: z.string().nullish(),
  arrivalDate: z.string(), departureDate: z.string(), actualDepartureDate: z.string().nullish(),
  status: z.string(), nights: z.number().default(0), amount: nnum, note: z.string().nullish(),
  overstaying: z.boolean().default(false), accountId: z.string().nullish(), invoiceState: z.string().nullish(),
  invoiceNumber: z.string().nullish(), invoiceError: z.string().nullish(), bayId: z.string().nullish(),
});
export type Stay = z.infer<typeof staySchema>;
export const stayListSchema = z.array(staySchema);

export const baySchema = z.object({ id: z.string(), name: z.string(), active: z.boolean().default(true) });
export type Bay = z.infer<typeof baySchema>;
export const bayListSchema = z.array(baySchema);

export const occupancySchema = z.object({ date: z.string(), inYard: z.number(), arrivals: z.number(), departures: z.number(), bays: z.number().nullish(), free: z.number().nullish() });
export type Occupancy = z.infer<typeof occupancySchema>;

export const requestSchema = z.object({
  id: z.string(), customerName: z.string().nullish(), vehicleRegistration: z.string(), driverName: z.string().nullish(),
  arrivalDate: z.string(), departureDate: z.string(), nights: z.number().default(0), status: z.string(), note: z.string().nullish(),
});
export type StayRequest = z.infer<typeof requestSchema>;
export const requestListSchema = z.array(requestSchema);

export const canRunTruckStop = (p: readonly string[]): boolean => p.includes("FUEL_MANAGE") || p.includes("FUEL_TRUCKSTOP_MANAGE");

/** Today in South African time as yyyy-mm-dd, the same day the server uses to judge "not due yet". */
export const todayZA = (now: Date = new Date()): string => new Date(now.getTime() + 2 * 3_600_000).toISOString().slice(0, 10);

export type Group = "overstaying" | "inYard" | "arrivingToday" | "upcoming" | "past";
export const GROUP_TITLE: Record<Group, string> = {
  overstaying: "Overstaying", inYard: "In the yard", arrivingToday: "Arriving today", upcoming: "Upcoming", past: "Recent",
};
export const GROUP_ORDER: Group[] = ["overstaying", "inYard", "arrivingToday", "upcoming", "past"];

export function groupOf(s: Stay, today: string): Group {
  if (s.status === "CHECKED_IN") return s.overstaying || s.departureDate < today ? "overstaying" : "inYard";
  if (s.status === "BOOKED") return s.arrivalDate <= today ? "arrivingToday" : "upcoming";
  return "past";
}

export function grouped(stays: Stay[], today: string): { group: Group; title: string; stays: Stay[] }[] {
  const out = GROUP_ORDER.map((g) => ({ group: g, title: GROUP_TITLE[g], stays: [] as Stay[] }));
  for (const s of stays) out.find((o) => o.group === groupOf(s, today))!.stays.push(s);
  for (const o of out) o.stays.sort((a, b) => (o.group === "past" ? b.arrivalDate.localeCompare(a.arrivalDate) : a.arrivalDate.localeCompare(b.arrivalDate)));
  return out.filter((o) => o.stays.length > 0);
}

export type StayAction = "checkIn" | "checkOut" | "noShow";
/** What can be done to a stay now. The server checks again (a truck is not checked in before its arrival date). */
export function actionsFor(s: Stay, today: string): StayAction[] {
  if (s.status === "BOOKED") return s.arrivalDate <= today ? ["checkIn", "noShow"] : [];
  if (s.status === "CHECKED_IN") return ["checkOut"];
  return [];
}
export const ACTION_LABEL: Record<StayAction, string> = { checkIn: "Check in", checkOut: "Check out", noShow: "Did not arrive" };
export const ACTION_PATH: Record<StayAction, string> = { checkIn: "check-in", checkOut: "check-out", noShow: "no-show" };

export const dayText = (iso: string): string => { const [y, m, d] = iso.split("-").map(Number); return new Date(y!, (m ?? 1) - 1, d).toLocaleDateString("en-ZA", { weekday: "short", day: "numeric", month: "short" }); };

export function validateDecline(reason: string): string | null {
  const r = reason.trim();
  if (r.length < 5) return "Give a reason of at least 5 characters.";
  if (r.length > 300) return "The reason is too long (300 characters at most).";
  return null;
}

/** Bays a stay can move to: active ones. */
export const activeBays = (bays: Bay[]): Bay[] => bays.filter((b) => b.active).sort((a, b) => a.name.localeCompare(b.name, "en", { numeric: true }));
