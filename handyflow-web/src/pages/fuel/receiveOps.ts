// Phase 7b helpers: inbound freight on a stock receipt. The server does the real arithmetic; this only helps the person see it.

/** Freight per litre to four places, or null when there is no usable freight or litres. */
export function freightPerLitre(litres: string, freight: string): number | null {
  const l = Number(litres), f = Number(freight)
  if (freight.trim() === "" || !Number.isFinite(l) || l <= 0 || !Number.isFinite(f) || f <= 0) return null
  return Math.round((f / l) * 10000) / 10000
}

/** Message for the freight box, or null when it is fine (blank is fine). */
export function validateFreight(freight: string): string | null {
  if (freight.trim() === "") return null
  const f = Number(freight)
  if (!Number.isFinite(f) || f < 0) return "Freight must be zero or more."
  return null
}

/** What the tank's cost is built from, as text: "R20.30 a litre with freight (R0.30 freight)". Null with no freight. */
export function landedText(litres: string, price: string, freight: string): string | null {
  const per = freightPerLitre(litres, freight)
  const p = Number(price)
  if (per == null || !Number.isFinite(p) || p <= 0) return null
  return `R${(p + per).toFixed(4)} a litre with freight (R${per.toFixed(4)} of it freight)`
}
