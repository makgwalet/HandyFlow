// Pure helpers for the Armoury screen.

export interface LostFields {
  status: string
  sapsCaseNumber?: string | null
  sapsReportDueAt?: string | null
  sapsReportOverdue?: boolean
}

export const MAX_ROUNDS = 2000

/** The SAPS line for a lost firearm, or null for any other status. */
export function sapsLine(f: LostFields, now: number): { text: string; urgent: boolean } | null {
  if (f.status !== "LOST") return null
  if (f.sapsCaseNumber) return { text: `SAPS case ${f.sapsCaseNumber}`, urgent: false }
  if (f.sapsReportOverdue) return { text: "SAPS report overdue: report it now", urgent: true }
  if (f.sapsReportDueAt) {
    const hours = Math.max(1, Math.ceil((new Date(f.sapsReportDueAt).getTime() - now) / 3600000))
    return { text: `Report to SAPS within ${hours}h`, urgent: hours <= 6 }
  }
  return { text: "No SAPS case recorded", urgent: true }
}

/** "" or a number string to a number; anything else (negative, fraction, too big) to null. */
export function parseRounds(raw: string): number | null {
  if (raw.trim() === "") return null
  const n = Number(raw)
  return Number.isInteger(n) && n >= 0 && n <= MAX_ROUNDS ? n : null
}

/** Same rules as the server, so the form can explain before it submits. Null when fine. */
export function ammoReturnProblem(issued: number | null | undefined, returnedRaw: string, reason: string): string | null {
  const returned = parseRounds(returnedRaw)
  if (returnedRaw.trim() !== "" && returned === null) return `Rounds must be a whole number from 0 to ${MAX_ROUNDS}`
  if (issued == null) return null
  if (returned === null) return `Enter how many rounds were handed back (${issued} were issued)`
  if (returned > issued) return `More rounds were returned than were issued (${issued})`
  if (returned < issued && reason.trim() === "") return `${issued - returned} round${issued - returned === 1 ? "" : "s"} unaccounted for: explain below`
  return null
}

export function ammoText(rounds: number | null | undefined, calibre?: string | null): string | null {
  if (rounds == null) return null
  const c = (calibre ?? "").trim()
  return c ? `${rounds} x ${c}` : `${rounds} rounds`
}
