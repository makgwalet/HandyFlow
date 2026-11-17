import { z } from "zod";
import { api } from "./client";

/** Mirrors the backend AccessSummary (B9): what the signed-in person can reach in Fuel. */
export const accessSchema = z.object({
  legacyMode: z.boolean(),
  activeDivisions: z.array(z.string()),
  scope: z.object({ restricted: z.boolean(), divisions: z.array(z.string()), depotIds: z.array(z.string()) }),
  emergencyContact: z.object({ name: z.string().nullish(), phone: z.string().nullish() }),
});
export type Access = z.infer<typeof accessSchema>;

export async function fetchAccess(): Promise<Access> {
  return accessSchema.parse(await api<unknown>("/api/v1/fuel/me/access"));
}

/** The control-room contact if both a name and a number are set. */
export function emergencyContactOf(a: Access | null | undefined): { name: string; phone: string } | null {
  const c = a?.emergencyContact;
  return c?.name && c.phone ? { name: c.name, phone: c.phone } : null;
}

/** A tel: link a phone can open. Keeps digits and a leading +. */
export function telUrl(phone: string): string {
  const t = phone.trim();
  return `tel:${t.startsWith("+") ? "+" : ""}${t.replace(/[^0-9]/g, "")}`;
}
