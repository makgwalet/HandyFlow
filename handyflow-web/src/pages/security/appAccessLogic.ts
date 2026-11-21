// Pure helpers for giving guards Shield app access (single enrol + bulk). Mirrors BulkAccessRules on the server.

export const MAX_PER_REQUEST = 100

export interface AccessItem {
  guardId: string
  fullName?: string | null
  employeeCode?: string | null
  phone?: string | null
  outcome: string            // PROVISIONED | SKIPPED_NOT_ACTIVE | SKIPPED_ALREADY_ENROLLED | FAILED
  temporaryPin?: string | null
  message?: string | null
}
export interface AccessBatch { provisioned: number; skipped: number; failed: number; moreRemaining?: boolean; results: AccessItem[] }

/** Splits ids into requests the server accepts (it refuses more than 100 at once). */
export function chunkIds(ids: string[], size = MAX_PER_REQUEST): string[][] {
  const unique = [...new Set(ids.filter(Boolean))]
  const out: string[][] = []
  for (let i = 0; i < unique.length; i += size) out.push(unique.slice(i, i + size))
  return out
}

/** Adds up several batches into one result. */
export function mergeBatches(batches: AccessBatch[]): AccessBatch {
  return {
    provisioned: batches.reduce((n, b) => n + b.provisioned, 0),
    skipped: batches.reduce((n, b) => n + b.skipped, 0),
    failed: batches.reduce((n, b) => n + b.failed, 0),
    results: batches.flatMap(b => b.results ?? []),
  }
}

export function summaryText(b: AccessBatch): string {
  const parts = [`${b.provisioned} given access`]
  if (b.skipped) parts.push(`${b.skipped} skipped`)
  if (b.failed) parts.push(`${b.failed} failed`)
  return parts.join(", ") + "."
}

export const outcomeLabel = (o: string): string =>
  o === "PROVISIONED" ? "Access given"
    : o === "SKIPPED_ALREADY_ENROLLED" ? "Already has access"
    : o === "SKIPPED_NOT_ACTIVE" ? "Not active"
    : o === "FAILED" ? "Failed" : o

/** One CSV field; quotes when needed and defuses spreadsheet formulas, like the server's csvField. */
export function csvField(v: string | null | undefined): string {
  if (v == null) return ""
  let s = v
  if (s.length > 0 && "=+-@\t\r".includes(s[0])) s = "'" + s
  if (/[",\r\n]/.test(s)) s = '"' + s.replace(/"/g, '""') + '"'
  return s
}

/** A printable sheet of the new temporary PINs: only guards who actually received one. */
export function pinSheetCsv(items: AccessItem[]): string {
  const rows = items.filter(i => i.outcome === "PROVISIONED" && i.temporaryPin)
  const lines = [["Name", "Employee code", "Phone", "Temporary PIN"], ...rows.map(r => [r.fullName ?? "", r.employeeCode ?? "", r.phone ?? "", r.temporaryPin ?? ""])]
  return lines.map(l => l.map(csvField).join(",")).join("\r\n") + "\r\n"
}

/** Letters-only PIN checks aside, the same rule as the form: blank (server generates one) or exactly 6 digits. */
export const pinValid = (pin: string) => pin === "" || /^\d{6}$/.test(pin)

export const SMS_NOTE = "Give each guard their PIN in person or by SMS to their own phone, never by email. They must change it the first time they log in."
