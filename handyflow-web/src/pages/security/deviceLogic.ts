// Pure helpers for the guard Devices tab.
import type { ChipTone } from "../../components/ui/Chip"

export interface GuardDevice {
  id: string
  deviceHardwareId?: string | null
  deviceName?: string | null
  deviceType?: string | null
  status: string
  lastSeenAt?: string | null
  createdAt?: string | null
}

const TONES: Record<string, ChipTone> = {
  ACTIVE: "ok", PENDING: "warn", REPLACED: "neutral", DECOMMISSIONED: "neutral",
  REVOKED: "bad", LOST: "bad", BLOCKED: "bad",
}
export const statusTone = (s: string): ChipTone => TONES[s] ?? "neutral"

/** "Ends …4F2A": enough to tell devices apart without showing the whole hardware id. */
export function deviceLabel(d: GuardDevice): string {
  if (d.deviceName && d.deviceName.trim()) return d.deviceName.trim()
  const id = (d.deviceHardwareId ?? "").trim()
  if (!id) return "Unnamed device"
  return id.length <= 4 ? id : `Device ending ${id.slice(-4).toUpperCase()}`
}

/** Newest first, the active device (if any) pinned to the top. */
export function orderDevices(list: GuardDevice[]): GuardDevice[] {
  return [...list].sort((a, b) => {
    if ((a.status === "ACTIVE") !== (b.status === "ACTIVE")) return a.status === "ACTIVE" ? -1 : 1
    return (b.createdAt ?? "").localeCompare(a.createdAt ?? "")
  })
}

/** "123456" -> "123 456". Anything else is returned as is. */
export function spacedCode(code: string): string {
  return /^\d{6}$/.test(code) ? `${code.slice(0, 3)} ${code.slice(3)}` : code
}

/** Whole minutes left, never negative. */
export function minutesLeft(expiresAt: string, now: number): number {
  const ms = new Date(expiresAt).getTime() - now
  return Number.isFinite(ms) ? Math.max(0, Math.ceil(ms / 60000)) : 0
}

export type DeviceAction = "lost" | "revoke" | "reinstate"

/**
 * Which supervisor actions to offer on a device row. Mirrors the server (DeviceStateRules): lost and revoke need
 * SECURITY_MANAGE, reinstate needs SECURITY_ADMIN; shared site devices have their own screen.
 */
export function deviceActions(d: GuardDevice, can: { manage: boolean; admin: boolean }): DeviceAction[] {
  if (d.deviceType === "SHARED_SITE_DEVICE") return []
  const out: DeviceAction[] = []
  if (can.manage && (d.status === "ACTIVE" || d.status === "PENDING")) out.push("lost")
  if (can.manage && (d.status === "ACTIVE" || d.status === "PENDING" || d.status === "LOST")) out.push("revoke")
  if (can.admin && (d.status === "LOST" || d.status === "REVOKED")) out.push("reinstate")
  return out
}

export const ACTION_LABEL: Record<DeviceAction, string> = { lost: "Report lost", revoke: "Revoke", reinstate: "Reinstate" }

export const ACTION_CONFIRM: Record<DeviceAction, string> = {
  lost: "This marks the phone lost and signs the guard out of it. It will not be able to log in.",
  revoke: "This takes the phone out of service and signs the guard out of it. It will not be able to log in.",
  reinstate: "This lets the guard log in on this phone again.",
}
