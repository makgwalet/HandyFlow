// Fleet health: which phones need attention. The server decides the problems; this only words and orders them.

export type FleetIssue = "OFFLINE" | "NEVER_SEEN" | "CRITICAL_BATTERY" | "LOW_BATTERY" | "OLD_APP"

export interface FleetItem {
  deviceId: string; deviceName: string | null; deviceType: string | null
  siteId: string | null; guardId: string | null; guardName: string | null
  lastSeenAt: string | null; batteryPct: number | null; appVersion: string | null
  onSession: boolean; critical: boolean; issues: FleetIssue[] | string[]
}

export interface FleetHealth { generatedAt: string; activeDevices: number; withIssues: number; critical: number; items: FleetItem[] }

export const ISSUE_LABEL: Record<FleetIssue, string> = {
  OFFLINE: "Silent while on shift",
  NEVER_SEEN: "Never connected",
  CRITICAL_BATTERY: "Battery almost flat",
  LOW_BATTERY: "Low battery",
  OLD_APP: "App needs updating",
}

export function issueLabel(code: string): string {
  return (ISSUE_LABEL as Record<string, string>)[code] ?? code
}

export function issueIsCritical(code: string): boolean {
  return code === "OFFLINE" || code === "CRITICAL_BATTERY"
}

/** "5 min ago", "3 h ago", "2 d ago", or "never". */
export function sinceText(iso: string | null, now = new Date()): string {
  if (!iso) return "never"
  const mins = Math.max(0, Math.round((now.getTime() - new Date(iso).getTime()) / 60000))
  if (mins < 1) return "just now"
  if (mins < 60) return `${mins} min ago`
  if (mins < 60 * 48) return `${Math.round(mins / 60)} h ago`
  return `${Math.round(mins / 1440)} d ago`
}

/** Critical phones first, then by name. */
export function orderItems(items: FleetItem[]): FleetItem[] {
  return [...items].sort((a, b) => Number(b.critical) - Number(a.critical) || (a.deviceName ?? "").localeCompare(b.deviceName ?? ""))
}

export function summaryText(h: FleetHealth): string {
  if (h.withIssues === 0) return h.activeDevices === 0 ? "No phones registered yet." : `All ${h.activeDevices} active phones are reporting normally.`
  const n = h.withIssues === 1 ? "1 phone needs" : `${h.withIssues} phones need`
  return h.critical > 0 ? `${n} attention, ${h.critical} urgent.` : `${n} attention.`
}
