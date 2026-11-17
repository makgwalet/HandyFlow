import { z } from "zod";

/** The driver's check on arrival: the same eligibility rules as allocation, recorded by the server with its time. */
export const findingSchema = z.object({ code: z.string(), severity: z.enum(["BLOCK", "REVIEW", "INFO"]), message: z.string() });
export const arrivalCheckSchema = z.object({
  deliveryId: z.string(),
  allowed: z.boolean(),
  findings: z.array(findingSchema),
  checkedAt: z.string(),
});
export type ArrivalCheck = z.infer<typeof arrivalCheckSchema>;
export type Finding = z.infer<typeof findingSchema>;

/**
 * How long a check stays good. Offloading may start offline only on a check made online within this window, so an
 * offline phone cannot slip past a safety rule. The business sets the real number; 12 hours is the plan's default.
 */
export const FRESH_HOURS = 12;

export const checkAgeHours = (c: Pick<ArrivalCheck, "checkedAt">, now = Date.now()): number => (now - Date.parse(c.checkedAt)) / 3_600_000;
export const isFresh = (c: Pick<ArrivalCheck, "checkedAt">, now = Date.now()): boolean => checkAgeHours(c, now) <= FRESH_HOURS && checkAgeHours(c, now) >= -0.1;

export interface Gate { ok: boolean; reason: string | null }

/** Whether offloading may start: a check exists, it allows the load, and it is fresh. Review points do not stop the driver; they are shown. */
export function offloadGate(check: ArrivalCheck | null, now = Date.now()): Gate {
  if (!check) return { ok: false, reason: "Do the arrival check before you offload." };
  if (!check.allowed) {
    const why = check.findings.filter((f) => f.severity === "BLOCK").map((f) => f.message).join(" ");
    return { ok: false, reason: `You may not offload this load. ${why}`.trim() };
  }
  if (!isFresh(check, now)) return { ok: false, reason: `The arrival check is more than ${FRESH_HOURS} hours old. Check again when you have signal.` };
  return { ok: true, reason: null };
}

export const blocks = (c: ArrivalCheck): Finding[] => c.findings.filter((f) => f.severity === "BLOCK");
export const reviews = (c: ArrivalCheck): Finding[] => c.findings.filter((f) => f.severity === "REVIEW");
export const notes = (c: ArrivalCheck): Finding[] => c.findings.filter((f) => f.severity === "INFO");
