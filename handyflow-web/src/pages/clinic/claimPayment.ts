/** Reads a typed rand amount ("300", "300.50", "300,50"); null when it is not a number. */
export function parseAmount(text: string): number | null {
  const t = text.trim().replace(/\s/g, "").replace(",", ".")
  if (!/^\d+(\.\d{1,2})?$/.test(t)) return null
  return Number(t)
}

/** Why a partial scheme payment of this text cannot be recorded against a claim of this total, or null. */
export function partialAmountProblem(text: string, gross: number): string | null {
  if (!text.trim()) return "Enter the amount the scheme paid"
  const n = parseAmount(text)
  if (n === null) return "Enter an amount like 300 or 300.50"
  if (n <= 0) return "The amount must be more than zero"
  if (n >= gross) return "A partial payment must be less than the claim total; use Mark paid for a full payment"
  return null
}
