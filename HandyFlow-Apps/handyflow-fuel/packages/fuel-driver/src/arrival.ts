import { z } from "zod";

/** The driver's check on arrival: the same eligibility rules as allocation, recorded by the server with its time. */
export const findingSchema = z.object({ code: z.string(), severity: z.enum(["BLOCK", "REVIEW", "INFO"]), message: z.string() });
export const arrivalCheckSchema = z.object({
  deliveryId: z.string(),
  allowed: z.boolean(),
  findings: z.array(findingSchema),
  checkedAt: z.string(),
  // Decided by the server on its own clock. The phone shows them and uses them offline; the server still decides at each protected step.
  checkId: z.string().nullish(),
  status: z.enum(["MISSING", "BLOCKED", "FRESH", "STALE", "RELEASED"]).nullish(),
  freshHours: z.number().nullish(),
  expiresAt: z.string().nullish(),
  releasedAt: z.string().nullish(),
});
export type ArrivalCheck = z.infer<typeof arrivalCheckSchema>;
export type Finding = z.infer<typeof findingSchema>;

/**
 * The window used only when the server has not sent one (an old cached check). The company sets the real number on the server
 * and every check comes back with it as freshHours; 12 hours is the system default.
 */
export const FRESH_HOURS = 12;

export const windowHours = (c: Pick<ArrivalCheck, "freshHours">): number => c.freshHours ?? FRESH_HOURS;
export const checkAgeHours = (c: Pick<ArrivalCheck, "checkedAt">, now = Date.now()): number => (now - Date.parse(c.checkedAt)) / 3_600_000;

/**
 * Fresh until the server's expiresAt (which already counts a supervisor's release), else until checkedAt plus the company's
 * window. A check dated in the future is not trusted: the phone clock is wrong.
 */
export const isFresh = (c: Pick<ArrivalCheck, "checkedAt" | "freshHours" | "expiresAt">, now = Date.now()): boolean => {
  if (checkAgeHours(c, now) < -0.1) return false;
  if (c.expiresAt) return now <= Date.parse(c.expiresAt);
  return checkAgeHours(c, now) <= windowHours(c);
};

export interface Gate { ok: boolean; reason: string | null }

/** Whether offloading may start: a check exists, it allows the load, and it is fresh. Review points do not stop the driver; they are shown. */
export function offloadGate(check: ArrivalCheck | null, now = Date.now()): Gate {
  if (!check) return { ok: false, reason: "Do the arrival check before you offload." };
  if (!check.allowed) {
    const why = check.findings.filter((f) => f.severity === "BLOCK").map((f) => f.message).join(" ");
    return { ok: false, reason: `You may not offload this load. ${why}`.trim() };
  }
  if (!isFresh(check, now)) return { ok: false, reason: `The arrival check is more than ${windowHours(check)} hours old. Check again when you have signal, or ask a supervisor to release it.` };
  return { ok: true, reason: null };
}

export const blocks = (c: ArrivalCheck): Finding[] => c.findings.filter((f) => f.severity === "BLOCK");
export const reviews = (c: ArrivalCheck): Finding[] => c.findings.filter((f) => f.severity === "REVIEW");
export const notes = (c: ArrivalCheck): Finding[] => c.findings.filter((f) => f.severity === "INFO");

/** A stale check a supervisor may release: it passed, and has simply aged out. */
export const isReleasable = (c: ArrivalCheck, now = Date.now()): boolean => c.allowed && !isFresh(c, now) && c.status !== "RELEASED";

export const RELEASE_REASONS: { code: string; label: string }[] = [
  { code: "DRIVER_DELAYED", label: "Driver was delayed" },
  { code: "SITE_QUEUE", label: "Waited in the site queue" },
  { code: "OFFLINE_AT_SITE", label: "No signal at the site" },
  { code: "RECHECK_NOT_POSSIBLE", label: "A new check is not possible" },
  { code: "OTHER", label: "Other (explain)" },
];

export const releaseSchema = z.object({ reasonCode: z.string().min(1), explanation: z.string().trim().min(10, "Explain the release in at least 10 characters.").max(1000) });
export type ReleaseInput = z.infer<typeof releaseSchema>;
