// Pure helpers for the shared site devices panel.
import type { ChipTone } from "../../components/ui/Chip"

export interface SharedDevice {
  id: string
  siteId?: string | null
  deviceHardwareId: string
  deviceName?: string | null
  status: string
  lastSeenAt?: string | null
}

export interface RegisterForm { hardwareId: string; name: string; siteId: string }

/** First problem with the form, or null. The server re-checks everything. */
export function validateRegister(f: RegisterForm): string | null {
  if (!f.hardwareId.trim()) return "Enter the device's hardware ID (shown in the Shield app on that device)."
  if (f.hardwareId.trim().length > 200) return "The hardware ID is too long."
  if (f.name.trim().length > 100) return "Keep the name under 100 characters."
  return null
}

export function registerBody(f: RegisterForm) {
  return {
    deviceHardwareId: f.hardwareId.trim(),
    deviceName: f.name.trim() || null,
    siteId: f.siteId || null,
  }
}

/** A device that is already out of service has nothing left to revoke. */
export const canRevokeShared = (status: string) => status === "ACTIVE" || status === "PENDING"

const TONES: Record<string, ChipTone> = { ACTIVE: "ok", PENDING: "warn", REVOKED: "bad", LOST: "bad", BLOCKED: "bad" }
export const sharedStatusTone = (s: string): ChipTone => TONES[s] ?? "neutral"

export function sharedLabel(d: SharedDevice): string {
  if (d.deviceName && d.deviceName.trim()) return d.deviceName.trim()
  const id = d.deviceHardwareId.trim()
  return id.length <= 4 ? id : `Device ending ${id.slice(-4).toUpperCase()}`
}

/** Devices at a named site first (by site name), then at an unnamed site, then unassigned; within a site by label. */
export function orderShared(list: SharedDevice[], siteNames: Record<string, string>): SharedDevice[] {
  const rank = (d: SharedDevice) => (d.siteId ? (siteNames[d.siteId] ? 0 : 1) : 2)
  const site = (d: SharedDevice) => (d.siteId ? siteNames[d.siteId] ?? "" : "")
  return [...list].sort((a, b) => rank(a) - rank(b) || site(a).localeCompare(site(b)) || sharedLabel(a).localeCompare(sharedLabel(b)))
}
