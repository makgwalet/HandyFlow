// Edit form rules for a vehicle's details, identity and valuation. The server replaces the whole record, so the body always carries every field.
export const VEHICLE_TYPES = ["SEDAN", "SUV", "BAKKIE", "TRUCK", "MINIBUS", "VAN", "MOTORCYCLE", "OTHER"]
export const FUEL_TYPES = ["PETROL", "DIESEL", "ELECTRIC", "HYBRID", "LPG", "OTHER"]
export const TRANSMISSIONS = ["MANUAL", "AUTOMATIC", "AMT", "CVT", "OTHER"]
export const DRIVE_TYPES = ["FWD", "RWD", "AWD", "FOUR_BY_FOUR", "OTHER"]
export const ACQUISITION_METHODS = ["PURCHASED", "FINANCED", "LEASED", "RENTED", "TRANSFERRED_IN", "DONATED", "OTHER"]
/** The list plus the current value, so an older or unusual value is never silently changed on save. */
export const withCurrent = (list: string[], current: string) => (current && !list.includes(current) ? [current, ...list] : list)
export const optionLabel = (v: string) => v.charAt(0) + v.slice(1).toLowerCase().replace(/_/g, " ")

/** What the vehicle list returns; every field is optional because the form only needs what is there. */
export interface VehicleFull {
  registration?: string; make?: string; model?: string; year?: number | null; colour?: string | null; vehicleType?: string; fuelType?: string | null
  licenceDiscExpiry?: string | null; roadworthyExpiry?: string | null; insuranceExpiry?: string | null
  dailyRate?: number | null; tankCapacityLitres?: number | null; serviceIntervalKm?: number | null; serviceIntervalDays?: number | null; notes?: string | null
  vin?: string | null; engineNumber?: string | null; variant?: string | null; bodyType?: string | null; transmission?: string | null; driveType?: string | null
  axleCount?: number | null; gvmKg?: number | null; tareKg?: number | null; payloadKg?: number | null; gcmKg?: number | null
  acquisitionDate?: string | null; acquisitionMethod?: string | null; purchasePrice?: number | null; currentBookValue?: number | null; currentEstimatedValue?: number | null; valuedAt?: string | null
}

export const TEXT_FIELDS = ["registration", "make", "model", "year", "colour", "vehicleType", "fuelType", "licenceDiscExpiry", "roadworthyExpiry", "insuranceExpiry", "dailyRate", "tankCapacityLitres",
  "serviceIntervalKm", "serviceIntervalDays", "notes", "vin", "engineNumber", "variant", "bodyType", "transmission", "driveType", "axleCount", "gvmKg", "tareKg", "payloadKg", "gcmKg",
  "acquisitionDate", "acquisitionMethod", "purchasePrice", "currentBookValue", "currentEstimatedValue", "valuedAt"] as const
export type VehicleForm = Record<(typeof TEXT_FIELDS)[number], string>

export const toVehicleForm = (v: VehicleFull): VehicleForm =>
  Object.fromEntries(TEXT_FIELDS.map(k => [k, v[k] == null ? "" : String(v[k])])) as VehicleForm

const NUMBERS: { key: keyof VehicleForm; label: string; int?: boolean }[] = [
  { key: "dailyRate", label: "Daily rate" }, { key: "tankCapacityLitres", label: "Tank capacity" }, { key: "serviceIntervalKm", label: "Service interval (km)", int: true },
  { key: "serviceIntervalDays", label: "Service interval (days)", int: true }, { key: "gvmKg", label: "GVM", int: true }, { key: "tareKg", label: "Tare", int: true },
  { key: "payloadKg", label: "Payload", int: true }, { key: "gcmKg", label: "Gross combination mass", int: true },
  { key: "purchasePrice", label: "Purchase price" }, { key: "currentBookValue", label: "Book value" }, { key: "currentEstimatedValue", label: "Estimated value" },
]

/** The first thing wrong with the form, or null when it can be saved. */
export function vehicleProblem(f: VehicleForm, thisYear = new Date().getFullYear()): string | null {
  if (!f.registration.trim()) return "Registration is required."
  if (!f.make.trim()) return "Make is required."
  if (!f.model.trim()) return "Model is required."
  if (!f.vehicleType) return "Choose the vehicle type."
  if (f.year && (!Number.isInteger(Number(f.year)) || Number(f.year) < 1950 || Number(f.year) > thisYear + 1)) return "Enter a valid year."
  if (f.axleCount && (!Number.isInteger(Number(f.axleCount)) || Number(f.axleCount) < 1 || Number(f.axleCount) > 20)) return "Axles must be a whole number from 1 to 20."
  for (const n of NUMBERS) {
    if (!f[n.key]) continue
    const v = Number(f[n.key])
    if (!Number.isFinite(v) || v < 0) return `${n.label} must be zero or more.`
    if (n.int && !Number.isInteger(v)) return `${n.label} must be a whole number.`
  }
  if (f.gvmKg && f.tareKg && Number(f.tareKg) > Number(f.gvmKg)) return "Tare cannot be more than the GVM."
  return null
}

/** When payload is blank it is worked out as GVM minus tare, as the server does; the form shows this as a hint. */
export const derivedPayload = (f: VehicleForm): number | null => (f.gvmKg && f.tareKg && !f.payloadKg ? Number(f.gvmKg) - Number(f.tareKg) : null)

const s = (v: string) => (v.trim() === "" ? null : v.trim())
const n = (v: string) => (v.trim() === "" ? null : Number(v))
export function vehicleBody(f: VehicleForm) {
  return {
    registration: f.registration.trim(), make: f.make.trim(), model: f.model.trim(), year: n(f.year), colour: s(f.colour), vehicleType: f.vehicleType, fuelType: s(f.fuelType),
    licenceDiscExpiry: s(f.licenceDiscExpiry), roadworthyExpiry: s(f.roadworthyExpiry), insuranceExpiry: s(f.insuranceExpiry),
    dailyRate: n(f.dailyRate), tankCapacityLitres: n(f.tankCapacityLitres), serviceIntervalKm: n(f.serviceIntervalKm), serviceIntervalDays: n(f.serviceIntervalDays), notes: s(f.notes),
    vin: s(f.vin), engineNumber: s(f.engineNumber), variant: s(f.variant), bodyType: s(f.bodyType), transmission: s(f.transmission), driveType: s(f.driveType),
    axleCount: n(f.axleCount), gvmKg: n(f.gvmKg), tareKg: n(f.tareKg), payloadKg: n(f.payloadKg), gcmKg: n(f.gcmKg),
    acquisitionDate: s(f.acquisitionDate), acquisitionMethod: s(f.acquisitionMethod), purchasePrice: n(f.purchasePrice),
    currentBookValue: n(f.currentBookValue), currentEstimatedValue: n(f.currentEstimatedValue), valuedAt: s(f.valuedAt),
  }
}
