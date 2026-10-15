// src/pages/earthmoving/shared/format.ts

export const fmtCurrency = (n: number | null | undefined): string =>
  n != null ? `R ${Number(n).toLocaleString("en-ZA")}` : "—"

export const fmtCurrency2dp = (n: number | null | undefined): string =>
  n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"

export const fmtDate = (iso: string | null | undefined): string =>
  iso ? new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

export const fmtDateTime = (iso: string | null | undefined): string =>
  iso ? new Date(iso).toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short" }) : "—"

export const fmtTime = (iso: string | null | undefined): string =>
  iso ? new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" }) : "—"

/** The server's message from a failed request, or the fallback. Replaces `(e: any) => e.response?.data?.message`. */
export function apiMessage(e: unknown, fallback: string): string {
  const m = (e as { response?: { data?: { message?: unknown } } } | null)?.response?.data?.message
  return typeof m === "string" && m ? m : fallback
}

/** "hrs" or "km": the unit a machine's meter counts in. Machines from before meter types count hours. */
/** "Inspected today", "Inspected 3 days ago" or "Never inspected", for a machine's card. */
export function inspectionAgo(iso: string | null | undefined, now: Date = new Date()): string {
  if (!iso) return "Never inspected"
  const days = Math.floor((now.getTime() - new Date(iso).getTime()) / 86_400_000)
  return days <= 0 ? "Inspected today" : days === 1 ? "Inspected yesterday" : `Inspected ${days} days ago`
}
/** A report as CSV text: quotes doubled, so a machine name with a comma or quote stays in one cell. */
export function toCsv(rows: (string | number | null)[][]): string {
  return rows.map(r => r.map(v => (v == null ? "" : `"${String(v).replace(/"/g, '""')}"`)).join(",")).join("\r\n")
}
export const meterUnitOf = (a: { meterUnit?: string } | null | undefined) => a?.meterUnit ?? "hrs"
/** What the meter is called on a form: "Odometer" for kilometres, "Hour meter" otherwise. */
export const meterNameOf = (a: { meterType?: string } | null | undefined) => (a?.meterType === "KILOMETRES" ? "Odometer" : "Hour meter")
export const meterWordOf = (a: { meterType?: string } | null | undefined) => (a?.meterType === "KILOMETRES" ? "kilometres" : "hours")
