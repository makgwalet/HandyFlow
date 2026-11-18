import { z } from "zod";

const num = z.coerce.number();

export const lineSchema = z.object({ id: z.string(), offeringId: z.string().nullish(), description: z.string(), quantity: num, unitPrice: num, amount: num });
export const tabSchema = z.object({
  id: z.string(), vehicleRegistration: z.string(), driverName: z.string().nullish(), accountId: z.string().nullish(),
  status: z.string(), paymentMethod: z.string().nullish(), voidReason: z.string().nullish(),
  invoiceState: z.string().nullish(), invoiceNumber: z.string().nullish(), invoiceError: z.string().nullish(),
  lines: z.array(lineSchema).default([]), total: num.default(0),
});
export type Tab = z.infer<typeof tabSchema>;
export const tabListSchema = z.array(tabSchema);

export const offeringSchema = z.object({ id: z.string(), serviceName: z.string(), price: num.nullish(), active: z.boolean().default(true), serviceMissing: z.boolean().default(false) });
export type Offering = z.infer<typeof offeringSchema>;
export const offeringListSchema = z.array(offeringSchema);

/** The ways a tab is settled at the till. Charging to an account is a separate action for a tab that has one. */
export const TILL_METHODS: { code: string; label: string }[] = [
  { code: "CASH", label: "Cash" }, { code: "CARD", label: "Card" }, { code: "EFT", label: "EFT" }, { code: "OTHER", label: "Other" },
];

export const rands = (n: number | null | undefined): string => `R ${(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

export type TabAction = "addLine" | "settle" | "charge" | "void";
/** What can be done to a tab now. The server checks again (a tab with no lines cannot be settled). */
export function tabActions(t: Tab): TabAction[] {
  if (t.status !== "OPEN") return [];
  const out: TabAction[] = ["addLine"];
  if (t.lines.length > 0) out.push(t.accountId ? "charge" : "settle");
  out.push("void");
  return out;
}

export function validateQuantity(text: string): string | null {
  const n = Number(text.trim().replace(",", "."));
  if (text.trim() === "" || !Number.isFinite(n) || n <= 0) return "Enter a quantity above zero.";
  if (n > 1000) return "That quantity looks wrong. Check it.";
  return null;
}
export const quantityOf = (text: string): number => Number(text.trim().replace(",", "."));

export function validateOpenTab(reg: string): string | null {
  const r = reg.trim();
  if (!r) return "Enter the vehicle registration.";
  if (r.length > 20) return "The registration is too long.";
  return null;
}

export function validateVoid(reason: string): string | null {
  const r = reason.trim();
  if (r.length < 5) return "Give a reason of at least 5 characters.";
  if (r.length > 300) return "The reason is too long (300 characters at most).";
  return null;
}

// ---- desk booking and cancelling of stays ----

const ISO = /^\d{4}-\d{2}-\d{2}$/;
const validDay = (s: string): boolean => { if (!ISO.test(s)) return false; const d = new Date(`${s}T00:00:00Z`); return !Number.isNaN(d.getTime()) && d.toISOString().slice(0, 10) === s; };

export interface BookForm { vehicleRegistration: string; driverName: string; driverPhone: string; arrivalDate: string; departureDate: string; note: string }

/** Null when the booking can be sent. The server checks bays, rates and the vehicle again. */
export function validateBook(f: BookForm, today: string): string | null {
  if (!f.vehicleRegistration.trim()) return "Enter the vehicle registration.";
  if (f.vehicleRegistration.trim().length > 20) return "The registration is too long.";
  if (!validDay(f.arrivalDate)) return "Enter the arrival date as yyyy-mm-dd.";
  if (!validDay(f.departureDate)) return "Enter the departure date as yyyy-mm-dd.";
  if (f.arrivalDate < today) return "The arrival date cannot be in the past.";
  if (f.departureDate <= f.arrivalDate) return "The departure date must be after the arrival date.";
  if (f.driverPhone.trim() && !/^\+?[0-9 ()\-]{5,40}$/.test(f.driverPhone.trim())) return "The phone number must be digits, with an optional + at the start.";
  return null;
}

export const bookBody = (f: BookForm) => ({
  vehicleRegistration: f.vehicleRegistration.trim().toUpperCase(),
  driverName: f.driverName.trim() || null,
  driverPhone: f.driverPhone.trim() || null,
  arrivalDate: f.arrivalDate, departureDate: f.departureDate,
  note: f.note.trim() || null,
});

/** Only a stay that has not arrived can be cancelled; one that is in the yard is checked out instead. */
export const canCancelStay = (status: string): boolean => status === "BOOKED";

export function validateCancel(reason: string): string | null {
  const r = reason.trim();
  if (!r) return "Give a reason for the cancellation.";
  if (r.length > 300) return "The reason is too long (300 characters at most).";
  return null;
}
