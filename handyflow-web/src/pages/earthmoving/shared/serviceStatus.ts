// One place that decides how close a machine is to its next service, so the fleet card,
// the machine page and any alert agree.

export type ServiceState = "HEALTHY" | "APPROACHING" | "DUE" | "OVERDUE" | "CHECK_METER"

export interface ServiceStatus {
  state: ServiceState
  used: number        // meter units since the last service
  interval: number
  pct: number         // 0 to 100, for a bar
  label: string
  tone: "ok" | "warning" | "danger" | "unknown"
}

export const DEFAULT_INTERVAL = 250
const APPROACHING_AT = 0.8
const OVERDUE_AT = 1.2   // 20% past the interval

interface Meter { currentHours?: number | string | null; lastServiceHours?: number | string | null; serviceIntervalHours?: number | string | null }

export function serviceStatus(a: Meter): ServiceStatus {
  const cur = Number(a.currentHours ?? 0)
  const last = Number(a.lastServiceHours ?? 0)
  const interval = Number(a.serviceIntervalHours) > 0 ? Number(a.serviceIntervalHours) : DEFAULT_INTERVAL
  const used = cur - last
  if (used < 0) {
    return { state: "CHECK_METER", used, interval, pct: 0, label: "Check meter: reading is below the last service", tone: "unknown" }
  }
  const ratio = used / interval
  const pct = Math.min(100, ratio * 100)
  if (ratio >= OVERDUE_AT) return { state: "OVERDUE", used, interval, pct, label: "Service overdue", tone: "danger" }
  if (ratio >= 1) return { state: "DUE", used, interval, pct, label: "Service due", tone: "danger" }
  if (ratio >= APPROACHING_AT) return { state: "APPROACHING", used, interval, pct, label: "Service coming up", tone: "warning" }
  return { state: "HEALTHY", used, interval, pct, label: "Service on track", tone: "ok" }
}

export const toneColour = (t: ServiceStatus["tone"]) =>
  t === "danger" ? "var(--hf-danger)" : t === "warning" ? "var(--hf-warning)" : t === "unknown" ? "var(--hf-text-faint)" : "var(--hf-accent)"
