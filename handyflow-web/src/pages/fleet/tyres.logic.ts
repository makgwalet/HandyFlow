// Tyre and battery rules for the screens: what can be done in each status, tread wording, and the request bodies.
export const TREAD_LABEL: Record<string, string> = { OK: "OK", REPLACE_SOON: "Replace soon", REPLACE_NOW: "Replace now", NO_READING: "No reading" }
export const treadTone = (s: string): "ok" | "warn" | "bad" | "none" => (s === "OK" ? "ok" : s === "REPLACE_SOON" ? "warn" : s === "REPLACE_NOW" ? "bad" : "none")
export const TYRE_STATUS_LABEL: Record<string, string> = { IN_STOCK: "In stock", FITTED: "Fitted", AWAITING_RETREAD: "Awaiting retread", SCRAPPED: "Scrapped" }
export const BATTERY_STATUS_LABEL: Record<string, string> = { IN_STOCK: "In stock", INSTALLED: "Installed", FAULTY: "Faulty", SCRAPPED: "Scrapped" }
export const TEST_TONE: Record<string, "ok" | "warn" | "bad"> = { GOOD: "ok", WEAK: "warn", FAILED: "bad" }

export type TyreAction = "FIT" | "MEASURE" | "REMOVE" | "RETREAD" | "SCRAP"
export const tyreActions = (status: string): { action: TyreAction; label: string }[] =>
  status === "IN_STOCK" ? [{ action: "FIT", label: "Fit" }, { action: "SCRAP", label: "Scrap" }]
  : status === "FITTED" ? [{ action: "MEASURE", label: "Measure" }, { action: "REMOVE", label: "Remove" }]
  : status === "AWAITING_RETREAD" ? [{ action: "RETREAD", label: "Complete retread" }, { action: "SCRAP", label: "Scrap" }] : []

export type BatteryAction = "INSTALL" | "TEST" | "REMOVE" | "SCRAP"
export const batteryActions = (status: string): { action: BatteryAction; label: string }[] =>
  status === "IN_STOCK" ? [{ action: "INSTALL", label: "Install" }, { action: "SCRAP", label: "Scrap" }]
  : status === "INSTALLED" ? [{ action: "TEST", label: "Test" }, { action: "REMOVE", label: "Remove" }]
  : status === "FAULTY" ? [{ action: "SCRAP", label: "Scrap" }] : []

const bad = (v: string) => v.trim() !== "" && !(Number(v) >= 0)
const num = (v: string) => (v.trim() === "" ? null : Number(v))
const blank = (v: string) => v.trim() || null

export interface TyreForm { serialNumber: string; brand: string; pattern: string; size: string; purchasePrice: string; initialTreadDepthMm: string }
export const emptyTyre = (): TyreForm => ({ serialNumber: "", brand: "", pattern: "", size: "", purchasePrice: "", initialTreadDepthMm: "" })
export function tyreProblem(f: TyreForm) {
  if (!f.serialNumber.trim()) return "Enter the serial number."
  if (!f.brand.trim()) return "Enter the brand."
  if (!f.size.trim()) return "Enter the size, for example 295/80R22.5."
  if (bad(f.purchasePrice)) return "The price must be zero or more."
  if (f.initialTreadDepthMm.trim() !== "" && !(Number(f.initialTreadDepthMm) > 0 && Number(f.initialTreadDepthMm) <= 30)) return "Tread depth must be between 0 and 30 mm."
  return null
}
export const tyreBody = (f: TyreForm) => ({ serialNumber: f.serialNumber.trim(), brand: f.brand.trim(), pattern: blank(f.pattern), size: f.size.trim(), purchasePrice: num(f.purchasePrice), initialTreadDepthMm: num(f.initialTreadDepthMm) })

export interface BatteryForm { serialNumber: string; brand: string; model: string; nominalVoltage: string; capacityAh: string; warrantyMonths: string; purchaseDate: string }
export const emptyBattery = (): BatteryForm => ({ serialNumber: "", brand: "", model: "", nominalVoltage: "12", capacityAh: "", warrantyMonths: "", purchaseDate: "" })
export function batteryProblem(f: BatteryForm) {
  if (!f.serialNumber.trim()) return "Enter the serial number."
  if (!f.brand.trim()) return "Enter the brand."
  if (f.nominalVoltage !== "12" && f.nominalVoltage !== "24") return "Voltage must be 12 or 24."
  if (f.warrantyMonths.trim() !== "" && !f.purchaseDate) return "Give the purchase date so the warranty expiry can be worked out."
  return null
}
export const batteryBody = (f: BatteryForm) => ({ serialNumber: f.serialNumber.trim(), brand: f.brand.trim(), model: blank(f.model), nominalVoltage: Number(f.nominalVoltage), capacityAh: num(f.capacityAh), warrantyMonths: num(f.warrantyMonths), purchaseDate: f.purchaseDate || null })

/** Fit or install: a vehicle, and for tyres a position code. */
export function fitProblem(vehicleId: string, position: string, needsPosition: boolean) {
  if (!vehicleId) return "Choose the vehicle."
  if (needsPosition && !position.trim()) return "Say where it goes: FL, FR, RL, RR or SPARE."
  return null
}
export const normalisePosition = (p: string) => p.trim().toUpperCase()

export const treadProblem = (depth: string) => (!(Number(depth) > 0 && Number(depth) <= 30) ? "Enter the tread depth in mm (up to 30)." : null)
export const voltageProblem = (v: string) => (!(Number(v) > 0 && Number(v) <= 32) ? "Enter the resting voltage (up to 32 V)." : null)
export const retreadProblem = (depth: string) => (!(Number(depth) > 0 && Number(depth) <= 30) ? "Enter the new tread depth in mm (up to 30)." : null)
export function removeProblem(outcome: string, scrapReason: string) { return outcome === "SCRAPPED" && !scrapReason.trim() ? "Say why it is being scrapped." : null }

export const TYRE_REMOVE_REASONS = ["ROTATION", "WORN", "PUNCTURE_DAMAGE", "RETREAD", "VEHICLE_DISPOSED", "OTHER"]
export const BATTERY_REMOVE_REASONS = ["FAILED", "REPLACED", "WARRANTY_CLAIM", "UPGRADE", "VEHICLE_DISPOSED", "OTHER"]
export const humanise = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, " ")
