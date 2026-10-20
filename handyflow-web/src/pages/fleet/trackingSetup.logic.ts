// Tracking set-up rules: registering devices against vehicles, the speeding and offline rules, and provider API keys.
export const PROVIDERS = ["CARTRACK", "CTRACK", "MIX", "TELTONIKA", "GENERIC"]

export interface Device { id: string; provider: string; externalId: string; vehicleId: string; registration: string | null; name: string | null; active: boolean; lastSeenAt: string | null }
export interface DeviceForm { id: string | null; externalId: string; provider: string; vehicleId: string; name: string; active: boolean }
export const emptyDevice = (): DeviceForm => ({ id: null, externalId: "", provider: "GENERIC", vehicleId: "", name: "", active: true })
export const toDeviceForm = (d: Device): DeviceForm => ({ id: d.id, externalId: d.externalId, provider: d.provider, vehicleId: d.vehicleId, name: d.name ?? "", active: d.active })
export function deviceProblem(f: DeviceForm): string | null {
  if (!f.externalId.trim()) return "Enter the unit id the provider uses for this device."
  if (f.externalId.trim().length > 100) return "The unit id is too long (100 characters at most)."
  if (!f.vehicleId) return "Choose the vehicle it is fitted to."
  if (f.provider.trim().length > 50) return "The provider name is too long (50 characters at most)."
  if (f.name.trim().length > 150) return "The name is too long (150 characters at most)."
  return null
}
export const deviceBody = (f: DeviceForm) => ({ externalId: f.externalId.trim(), provider: f.provider.trim() || null, vehicleId: f.vehicleId, name: f.name.trim() || null, active: f.active })

export function agoText(iso: string | null | undefined, now = new Date()): string {
  if (!iso) return "Never"
  const mins = Math.floor((now.getTime() - new Date(iso).getTime()) / 60000)
  if (mins < 1) return "Just now"
  if (mins < 60) return `${mins} ${mins === 1 ? "minute" : "minutes"} ago`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours} ${hours === 1 ? "hour" : "hours"} ago`
  const days = Math.floor(hours / 24)
  return `${days} ${days === 1 ? "day" : "days"} ago`
}
export type DeviceState = "off" | "never" | "ok" | "silent"
/** A device that is switched off, has never reported, reports normally, or has gone quiet for longer than the offline rule. */
export function deviceState(d: Pick<Device, "active" | "lastSeenAt">, offlineAfterMinutes: number, now = new Date()): DeviceState {
  if (!d.active) return "off"
  if (!d.lastSeenAt) return "never"
  return (now.getTime() - new Date(d.lastSeenAt).getTime()) / 60000 > offlineAfterMinutes ? "silent" : "ok"
}
export const DEVICE_STATE_LABEL: Record<DeviceState, string> = { off: "Switched off", never: "Never reported", ok: "Reporting", silent: "Gone quiet" }
/** Vehicles with no active device, so nobody assumes they appear on the map. */
export const untracked = <V extends { id: string; status?: string }>(vehicles: V[], devices: Pick<Device, "vehicleId" | "active">[]): V[] => {
  const tracked = new Set(devices.filter(d => d.active).map(d => d.vehicleId))
  return vehicles.filter(v => v.status !== "RETIRED" && !tracked.has(v.id))
}

// ── Rules ───────────────────────────────────────────────────────────────────────────────────────────
export interface TrackingSettings { defaultSpeedLimitKph: number; speedingToleranceKph: number; speedingCooldownMinutes: number; alertOverspeedKph: number; idlingThresholdMinutes: number; offlineAfterMinutes: number; usingDefaults?: boolean }
export type SettingsForm = Record<"defaultSpeedLimitKph" | "speedingToleranceKph" | "speedingCooldownMinutes" | "alertOverspeedKph" | "idlingThresholdMinutes" | "offlineAfterMinutes", string>
export const SETTING_FIELDS: { key: keyof SettingsForm; label: string; min: number; max: number; unit: string; help: string }[] = [
  { key: "defaultSpeedLimitKph", label: "Default speed limit", min: 20, max: 200, unit: "km/h", help: "Used outside any geofence with its own limit." },
  { key: "speedingToleranceKph", label: "Speeding tolerance", min: 0, max: 30, unit: "km/h", help: "How far over the limit before it counts as speeding." },
  { key: "speedingCooldownMinutes", label: "Speeding cooldown", min: 1, max: 120, unit: "min", help: "Gap before the same vehicle can raise another speeding event." },
  { key: "alertOverspeedKph", label: "Serious overspeed", min: 5, max: 100, unit: "km/h over", help: "Speeding this far over the limit is raised as serious." },
  { key: "idlingThresholdMinutes", label: "Idling threshold", min: 1, max: 240, unit: "min", help: "Engine on and not moving for this long is idling." },
  { key: "offlineAfterMinutes", label: "Offline after", min: 5, max: 1440, unit: "min", help: "No report for this long and the vehicle shows as offline." },
]
export const toSettingsForm = (s: TrackingSettings): SettingsForm => Object.fromEntries(SETTING_FIELDS.map(f => [f.key, String(s[f.key])])) as SettingsForm
export function settingsProblem(f: SettingsForm): string | null {
  for (const s of SETTING_FIELDS) { const v = Number(f[s.key]); if (f[s.key].trim() === "" || !Number.isInteger(v) || v < s.min || v > s.max) return `${s.label} must be a whole number from ${s.min} to ${s.max}.` }
  return null
}
export const settingsBody = (f: SettingsForm) => Object.fromEntries(SETTING_FIELDS.map(s => [s.key, Number(f[s.key])])) as unknown as TrackingSettings
/** Not an error: the default of 120 km/h is the highest legal limit, so town speeding goes unnoticed unless this is lowered or slower geofences are drawn. */
export const speedLimitWarning = (f: SettingsForm): string | null => (Number(f.defaultSpeedLimitKph) >= 120 ? "At 120 km/h or more, speeding in towns goes unnoticed. Lower it, or draw slower geofences around towns and sites." : null)

// ── Ingest keys ─────────────────────────────────────────────────────────────────────────────────────
export interface IngestKey { id: string; name: string; keyPrefix: string; createdAt: string; lastUsedAt: string | null; revokedAt: string | null; active: boolean }
export const keyNameProblem = (name: string): string | null => (!name.trim() ? "Name the provider this key is for." : name.trim().length > 150 ? "The name is too long (150 characters at most)." : null)
export const keyStatusText = (k: Pick<IngestKey, "active" | "lastUsedAt" | "revokedAt">, now = new Date()): string =>
  !k.active ? `Revoked${k.revokedAt ? ` ${new Date(k.revokedAt).toLocaleDateString("en-ZA")}` : ""}` : k.lastUsedAt ? `Last used ${agoText(k.lastUsedAt, now).toLowerCase()}` : "Never used"
export const INGEST_PATH = "/api/v1/fleet/telematics/ingest"
export const INGEST_HEADER = "X-Telematics-Key"
