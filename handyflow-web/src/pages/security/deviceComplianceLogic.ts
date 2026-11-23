// Device compliance: what the company requires of a guard phone, and which phones fall short.
// The server decides who is compliant; this only words the result and checks the form before it is sent.

export type Violation = "APP_TOO_OLD" | "OS_TOO_OLD" | "NO_SCREEN_LOCK" | "ROOTED"
export type Missing = "APP_VERSION" | "OS_VERSION" | "SCREEN_LOCK" | "ROOT_STATUS"

export interface PolicyView {
  minAppVersion: string | null; minOsMajor: number | null; requireScreenLock: boolean; blockRooted: boolean
  asksAnything: boolean; updatedAt: string | null
}

export interface ComplianceItem {
  deviceId: string; deviceName: string | null; deviceType: string | null; guardId: string | null; guardName: string | null
  status: "NON_COMPLIANT" | "UNKNOWN" | string
  violations: Violation[] | string[]; missing: Missing[] | string[]
  appVersion: string | null; osVersion: string | null; screenLockOn: boolean | null; rooted: boolean | null; postureReportedAt: string | null
}

export interface ComplianceReport {
  generatedAt: string; policy: PolicyView; activeDevices: number; compliant: number; nonCompliant: number; unknown: number; items: ComplianceItem[]
}

export interface PolicyForm { minAppVersion: string; minOsMajor: string; requireScreenLock: boolean; blockRooted: boolean }

export const VIOLATION_LABEL: Record<Violation, string> = {
  APP_TOO_OLD: "App needs updating",
  OS_TOO_OLD: "Phone software too old",
  NO_SCREEN_LOCK: "No screen lock",
  ROOTED: "Rooted or jailbroken",
}

export const MISSING_LABEL: Record<Missing, string> = {
  APP_VERSION: "app version",
  OS_VERSION: "phone software version",
  SCREEN_LOCK: "screen lock",
  ROOT_STATUS: "root status",
}

export const violationLabel = (code: string): string => (VIOLATION_LABEL as Record<string, string>)[code] ?? code
export const missingLabel = (code: string): string => (MISSING_LABEL as Record<string, string>)[code] ?? code

/** "Not reported: app version, screen lock". */
export function missingText(codes: readonly string[]): string {
  return codes.length === 0 ? "" : `Not reported: ${codes.map(missingLabel).join(", ")}`
}

export const APP_VERSION_MAX = 30

export function policyToForm(p: PolicyView | null | undefined): PolicyForm {
  return {
    minAppVersion: p?.minAppVersion ?? "",
    minOsMajor: p?.minOsMajor == null ? "" : String(p.minOsMajor),
    requireScreenLock: !!p?.requireScreenLock,
    blockRooted: !!p?.blockRooted,
  }
}

/** What is wrong with the form, in words, or null. Same rules as the server, which checks again. */
export function formProblem(f: PolicyForm): string | null {
  const app = f.minAppVersion.trim()
  if (app && (app.length > APP_VERSION_MAX || !/^[0-9]+(\.[0-9]+){0,3}$/.test(app))) {
    return "The lowest app version must be numbers separated by dots, like 2.4 or 3.10.1."
  }
  const os = f.minOsMajor.trim()
  if (os && (!/^[0-9]{1,2}$/.test(os) || Number(os) < 1)) {
    return "The lowest Android or iOS version must be a whole number from 1 to 99."
  }
  return null
}

export function formToRequest(f: PolicyForm) {
  const os = f.minOsMajor.trim()
  return {
    minAppVersion: f.minAppVersion.trim() || null,
    minOsMajor: os ? Number(os) : null,
    requireScreenLock: f.requireScreenLock,
    blockRooted: f.blockRooted,
  }
}

/** True when saving would change something. Blank and missing mean the same thing. */
export function isChanged(f: PolicyForm, p: PolicyView | null | undefined): boolean {
  const a = formToRequest(f)
  return a.minAppVersion !== (p?.minAppVersion ?? null) || a.minOsMajor !== (p?.minOsMajor ?? null)
    || a.requireScreenLock !== !!p?.requireScreenLock || a.blockRooted !== !!p?.blockRooted
}

export function summaryText(r: ComplianceReport): string {
  if (!r.policy.asksAnything) return "No requirements set yet, so every phone counts as compliant."
  if (r.activeDevices === 0) return "No active phones."
  const one = r.activeDevices === 1
  let s = `${r.compliant} of ${r.activeDevices} active ${one ? "phone meets" : "phones meet"} the policy`
  if (r.nonCompliant > 0) s += `, ${r.nonCompliant} fall short`
  if (r.unknown > 0) s += `, ${r.unknown} not reported enough to tell`
  return s + "."
}

/** Known failures first, then phones we cannot judge yet; each by name. The server already sorts; this keeps the screen right if it did not. */
export function orderItems<T extends { status: string; deviceName: string | null }>(items: readonly T[]): T[] {
  return [...items].sort((a, b) => {
    const r = (a.status === "NON_COMPLIANT" ? 0 : 1) - (b.status === "NON_COMPLIANT" ? 0 : 1)
    return r !== 0 ? r : (a.deviceName ?? "").localeCompare(b.deviceName ?? "")
  })
}
