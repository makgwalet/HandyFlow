// Automatic acceptance of clean imports. Off by default; gauge readings need a limit the company enters.
export type AutoAccept = { shiftsEnabled: boolean; gaugesEnabled: boolean; gaugeMaxDifferenceLitres: number | null }

export const AUTO_OFF: AutoAccept = { shiftsEnabled: false, gaugesEnabled: false, gaugeMaxDifferenceLitres: null }

/** Other tests answer every URL with generic data; accept only a real setting. */
export function asAutoAccept(raw: any): AutoAccept {
  if (!raw || typeof raw.shiftsEnabled !== "boolean" || typeof raw.gaugesEnabled !== "boolean") return AUTO_OFF
  return { shiftsEnabled: raw.shiftsEnabled, gaugesEnabled: raw.gaugesEnabled, gaugeMaxDifferenceLitres: raw.gaugeMaxDifferenceLitres ?? null }
}

export function autoAcceptProblem(gaugesEnabled: boolean, limit: string): string | null {
  if (!gaugesEnabled) return null
  const n = Number(limit)
  if (limit.trim() === "" || !Number.isFinite(n) || n <= 0) return "Enter the largest difference from the book level, in litres, above 0."
  return null
}

export function autoAcceptSummary(a: AutoAccept): string {
  const parts: string[] = []
  if (a.shiftsEnabled) parts.push("shifts")
  if (a.gaugesEnabled) parts.push(`gauge readings within ${a.gaugeMaxDifferenceLitres ?? "?"} L of the book level`)
  return parts.length ? "Accepted automatically: " + parts.join(" and ") : "Off: every import waits for a person"
}
