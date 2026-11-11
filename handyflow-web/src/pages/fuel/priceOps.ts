// Stage 4A helpers: price list validation that mirrors the server, so people get an instant message.
export interface PriceRow {
  id: string; fuelType: string; accountId: string | null; customerName: string | null; pricePerLitre: number
  minLitres: number; validFrom: string; validTo: string | null; note: string | null; live: boolean
}

const num = (s: string) => (s.trim() === "" ? NaN : Number(s))

export function validatePrice(fuelType: string, price: string, minLitres: string, validFrom: string, validTo: string): string | null {
  if (!fuelType) return "Choose the fuel type."
  const p = num(price); if (Number.isNaN(p) || p < 0) return "Enter a price per litre (zero or more)."
  if (minLitres.trim() !== "") { const m = num(minLitres); if (Number.isNaN(m) || m < 0) return "The volume break cannot be negative." }
  if (!validFrom) return "Enter the date the price starts."
  if (validTo && validTo < validFrom) return "The end date is before the start date."
  return null
}

/** "Contract" prices are for one customer; the rest are the standard list. */
export const scopeLabel = (r: Pick<PriceRow, "accountId" | "customerName">) => (r.accountId ? (r.customerName ?? "Customer") : "Standard list")

export const breakLabel = (minLitres: number) => (minLitres > 0 ? `from ${minLitres.toLocaleString("en-ZA")} L` : "any volume")
