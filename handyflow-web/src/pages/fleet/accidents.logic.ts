// Accident register rules the screen shares with the server: which cases keep a vehicle off the road, and what a report needs.
export const ACCIDENT_STATUSES = ["REPORTED", "UNDER_ASSESSMENT", "IN_REPAIR", "CLOSED", "WRITTEN_OFF"] as const
export type AccidentStatus = typeof ACCIDENT_STATUSES[number]

export const STATUS_LABEL: Record<string, string> = {
  REPORTED: "Reported", UNDER_ASSESSMENT: "Under assessment", IN_REPAIR: "In repair", CLOSED: "Closed", WRITTEN_OFF: "Written off",
}

/** Where a case may go next (mirrors the server; a written-off case is final and a closed one can be reopened for assessment). */
export const NEXT_STATUSES: Record<string, string[]> = {
  REPORTED: ["UNDER_ASSESSMENT", "IN_REPAIR", "CLOSED", "WRITTEN_OFF"],
  UNDER_ASSESSMENT: ["IN_REPAIR", "CLOSED", "WRITTEN_OFF"],
  IN_REPAIR: ["UNDER_ASSESSMENT", "CLOSED", "WRITTEN_OFF"],
  CLOSED: ["UNDER_ASSESSMENT"],
  WRITTEN_OFF: [],
}

const BLOCKING = new Set(["REPORTED", "UNDER_ASSESSMENT", "IN_REPAIR", "WRITTEN_OFF"])

export interface AccidentLike { status: string; vehicleDrivable: boolean }

/** True when this case stops the vehicle starting a trip or going back to Available. */
export const blocksVehicle = (a: AccidentLike): boolean => !a.vehicleDrivable && BLOCKING.has(a.status)

export interface ReportForm { vehicleId: string; occurredAt: string; location: string; description: string; injuries: string; fatalities: string; estimatedDamage: string; vehicleDrivable: boolean }
export const emptyReport = (): ReportForm => ({ vehicleId: "", occurredAt: "", location: "", description: "", injuries: "0", fatalities: "0", estimatedDamage: "", vehicleDrivable: true })

export function reportProblem(f: ReportForm): string | null {
  if (!f.vehicleId) return "Choose the vehicle."
  if (!f.occurredAt) return "Say when it happened."
  if (!f.description.trim()) return "Describe what happened."
  for (const [label, v] of [["Injuries", f.injuries], ["Fatalities", f.fatalities], ["Estimated damage", f.estimatedDamage]] as const) {
    if (v !== "" && !(Number(v) >= 0)) return `${label} must be zero or more.`
  }
  return null
}

export function reportBody(f: ReportForm) {
  const num = (v: string) => (v === "" ? null : Number(v))
  return {
    vehicleId: f.vehicleId,
    details: {
      occurredAt: new Date(f.occurredAt).toISOString(), location: f.location.trim() || null, description: f.description.trim(),
      injuriesCount: num(f.injuries) ?? 0, fatalitiesCount: num(f.fatalities) ?? 0, estimatedDamage: num(f.estimatedDamage), vehicleDrivable: f.vehicleDrivable,
    },
  }
}
