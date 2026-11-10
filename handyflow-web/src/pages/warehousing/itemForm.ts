// Pure helpers for the item master form: turn what was typed into the API payload, and catch mistakes before the round trip.

export interface ItemFormState {
  sku: string; description: string; uom: string; storageRate: string
  barcode: string; lengthMm: string; widthMm: string; heightMm: string; weightKg: string; unitCost: string; unitCostCurrency: string
  minQty: string; maxQty: string; temperatureClass: string; hazardous: boolean; fragile: boolean; stackable: boolean
}

export const TEMPERATURE_OPTIONS = [
  { value: "AMBIENT", label: "Ambient" }, { value: "CHILLED", label: "Chilled (2–8°C)" },
  { value: "FROZEN", label: "Frozen" }, { value: "CONTROLLED_ROOM", label: "Controlled room temperature" },
]

export const CURRENCY_OPTIONS = ["ZAR", "USD", "EUR", "GBP", "BWP", "NAD", "ZMW", "MZN", "LSL", "SZL"]

/** An amount with its currency, e.g. "R 12.50" or "US$ 12.50". Falls back to "CODE 12.50" for a code the runtime does not know. */
export function formatMoney(amount: number, currency: string | null | undefined): string {
  const code = (currency || "ZAR").toUpperCase()
  try { return new Intl.NumberFormat("en-ZA", { style: "currency", currency: code }).format(amount) }
  catch { return `${code} ${amount.toFixed(2)}` }
}

const num = (s: string): number | null => (s.trim() === "" ? null : Number(s.replace(",", ".")))

/** GTIN-8/12/13/14 mod-10 check, same rule as the server. */
export function isValidGtin(s: string): boolean {
  if (!/^(\d{8}|\d{12,14})$/.test(s)) return false
  let sum = 0
  for (let i = s.length - 2, w = 3; i >= 0; i--, w = w === 3 ? 1 : 3) sum += Number(s[i]) * w
  return (10 - (sum % 10)) % 10 === Number(s[s.length - 1])
}

/** First problem found, or null. Messages are written for the person typing. */
export function validateItemForm(f: ItemFormState): string | null {
  if (f.barcode.trim() !== "" && !isValidGtin(f.barcode.trim())) return "That barcode is not a valid 8, 12, 13 or 14 digit GTIN. Check the last (check) digit."
  for (const [label, v] of [["Length", f.lengthMm], ["Width", f.widthMm], ["Height", f.heightMm], ["Weight", f.weightKg]] as const) {
    const n = num(v)
    if (n !== null && (Number.isNaN(n) || n <= 0)) return `${label} must be a number greater than zero.`
  }
  if (!/^[A-Za-z]{3}$/.test(f.unitCostCurrency.trim())) return "The cost currency must be a 3-letter code such as ZAR or USD."
  for (const [label, v] of [["Unit cost", f.unitCost], ["Minimum level", f.minQty], ["Maximum level", f.maxQty]] as const) {
    const n = num(v)
    if (n !== null && (Number.isNaN(n) || n < 0)) return `${label} cannot be negative.`
  }
  const min = num(f.minQty), max = num(f.maxQty)
  if (min !== null && max !== null && max < min) return "The maximum level cannot be lower than the minimum level."
  return null
}

export function buildItemPayload(f: ItemFormState) {
  return {
    description: f.description || null, uom: f.uom || null, storageRatePerUnitPerMonth: num(f.storageRate),
    barcode: f.barcode.trim() || null, lengthMm: num(f.lengthMm), widthMm: num(f.widthMm), heightMm: num(f.heightMm),
    weightKg: num(f.weightKg), unitCost: num(f.unitCost), unitCostCurrency: f.unitCostCurrency.trim().toUpperCase(), minQty: num(f.minQty), maxQty: num(f.maxQty),
    temperatureClass: f.temperatureClass, hazardous: f.hazardous, fragile: f.fragile, stackable: f.stackable,
  }
}
