// The office's timeline for one load. The server writes the words; this only types them and picks a colour.
export interface ExecEvent { at: string; type: string; text: string; severity: "OK" | "REVIEW" | "PROBLEM" }
export interface Execution {
  deliveryId: string; status: string; driverName: string | null; vehicleReg: string | null; receiverName: string | null; signedOnBehalf: boolean
  litresOrdered: number | null; litresLoaded: number | null; litresDelivered: number | null; meterStart: number | null; meterEnd: number | null
  failedAttempts: number; events: ExecEvent[]
}

export const TYPE_LABEL: Record<string, string> = {
  SCHEDULED: "Scheduled", LOADED: "Loaded", ARRIVAL_CHECK: "Arrival check", EVIDENCE: "Proof", FAILED_ATTEMPT: "Failed attempt", DELIVERED: "Delivered", RECEIPT: "Receipt", REVERSED: "Reversed",
}

/**
 * What the figures say about the load, in words: loaded vs ordered, delivered vs loaded, and the meter against delivered.
 * Only differences worth a person's attention are listed; none is a verdict, the variance process decides what is a loss.
 */
export function figureNotes(x: Execution): string[] {
  const out: string[] = []
  const n = (v: number | null | undefined) => (v == null ? null : Number(v))
  const ord = n(x.litresOrdered), load = n(x.litresLoaded), del = n(x.litresDelivered), m0 = n(x.meterStart), m1 = n(x.meterEnd)
  if (load != null && del != null && Math.abs(load - del) >= 1) out.push(`${Math.abs(load - del).toLocaleString("en-ZA")} L ${del < load ? "less delivered than loaded" : "more delivered than loaded"}.`)
  if (ord != null && del != null && Math.abs(ord - del) >= 1 && load == null) out.push(`${Math.abs(ord - del).toLocaleString("en-ZA")} L ${del < ord ? "less delivered than ordered" : "more delivered than ordered"}.`)
  if (m0 != null && m1 != null && del != null) {
    const metered = m1 - m0
    if (metered >= 0 && Math.abs(metered - del) >= 1) out.push(`The meter shows ${metered.toLocaleString("en-ZA")} L against ${del.toLocaleString("en-ZA")} L recorded.`)
  }
  if (x.failedAttempts > 0) out.push(`${x.failedAttempts} failed ${x.failedAttempts === 1 ? "attempt" : "attempts"}.`)
  return out
}

export const SEVERITY_TONE: Record<string, "green" | "amber" | "red"> = { OK: "green", REVIEW: "amber", PROBLEM: "red" }
