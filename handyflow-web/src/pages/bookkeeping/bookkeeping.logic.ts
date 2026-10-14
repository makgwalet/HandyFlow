// Bookkeeping front end (finance F19 screens): work queue, clients, bank lines, bank rules, client queries, handover, periods.
// The checks below mirror the server's own (BkWorkRules) so people see the reason before pressing a button; the server still decides.

export interface Client { id: string; clientCode: string; tradingName: string; registrationNumber: string | null; vatNumber: string | null; contactName: string | null; contactEmail: string | null; contactPhone: string | null; address: string | null; status: string }
export interface BankAccount { id: string; clientId: string; accountId: string | null; bankName: string; accountName: string; accountNumber: string; accountType: string; currentBalance: number; active: boolean }
export interface BankTx { id: string; bankAccountId: string; transactionDate: string; description: string; reference: string | null; amount: number; transactionType: "CREDIT" | "DEBIT" | string; reconciled: boolean }
export interface Account { id: string; accountCode: string; accountName: string; accountType: string }
export interface MatchCandidate { journalLineId: string; entryNumber: string; entryDate: string; description: string; amount: number; exactMatch: boolean }
export interface Rule { id: string; name: string; bankAccountId: string | null; direction: string; text: string | null; field: string; min: number | null; max: number | null; priority: number; active: boolean; contraAccountId: string }
export interface Suggestion { transactionId: string; date: string; description: string; amount: number; type: string; ruleId: string; ruleName: string; contraAccountId: string }
export interface Query { id: string; kind: string; transactionId: string | null; question: string; status: string; answer: string | null; documentRef: string | null; askedAt: string; answeredAt: string | null; daysOpen: number }
export interface Handover { id: string; periodEnd: string; status: string; openQueries: number; note: string | null; sentAt: string; respondedAt: string | null; responseNote: string | null; warnings: string[] }
export interface WorkRow { clientId: string; clientName: string; unreconciled: number; oldestUnreconciled: string | null; openQueries: number; missingDocuments: number; draftJournals: number }
export interface Period { id: string; periodYear: number; periodMonth: number; status: string }

export const MAX_BULK = 200
export const MIN_QUERY_TEXT = 5
export const MIN_RETURN_NOTE = 10

export const money = (n: number | null | undefined) => (n == null ? "—" : `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`)
export const dateText = (d: string | null | undefined) => (d ? new Date(d.length === 10 ? d + "T00:00:00" : d).toLocaleDateString("en-ZA") : "—")
export const errorOf = (e: any) => e?.response?.data?.message ?? "Something went wrong. Please try again."

/** The API wraps results as { data }; a Spring page has its rows in content. */
export const unwrap = (r: any) => (r?.data && typeof r.data === "object" && "data" in r.data ? r.data.data : r?.data)
export const rowsOf = <T,>(r: any): T[] => { const d = unwrap(r); return Array.isArray(d) ? d : (d?.content ?? []) }

export function chunks<T>(items: T[], size = MAX_BULK): T[][] {
  const out: T[][] = []
  for (let i = 0; i < items.length; i += size) out.push(items.slice(i, i + size))
  return out
}

// ── work queue ──────────────────────────────────────────────────────────
export const backlog = (r: WorkRow) => r.unreconciled + r.openQueries + r.missingDocuments + r.draftJournals

/** Clients with something outstanding, biggest backlog first; optionally everyone, A to Z within equal backlog. */
export function workRows(rows: WorkRow[], showAll: boolean): WorkRow[] {
  return rows.filter(r => showAll || backlog(r) > 0)
    .sort((a, b) => backlog(b) - backlog(a) || a.clientName.localeCompare(b.clientName))
}

export function daysSince(iso: string | null, today: Date): number | null {
  if (!iso) return null
  const d = new Date(iso + "T00:00:00")
  return Math.max(0, Math.round((today.getTime() - d.getTime()) / 86400000))
}

// ── bank rules ──────────────────────────────────────────────────────────
export const DIRECTIONS = [{ value: "ANY", label: "Money in or out" }, { value: "IN", label: "Money in" }, { value: "OUT", label: "Money out" }]
export const FIELDS = [{ value: "EITHER", label: "Description or reference" }, { value: "DESCRIPTION", label: "Description" }, { value: "REFERENCE", label: "Reference" }]

export interface RuleForm { name: string; bankAccountId: string; direction: string; text: string; field: string; min: string; max: string; priority: string; contraAccountId: string }
export const emptyRule = (): RuleForm => ({ name: "", bankAccountId: "", direction: "ANY", text: "", field: "EITHER", min: "", max: "", priority: "100", contraAccountId: "" })

const num = (s: string) => (s.trim() === "" ? null : Number(s))

export function ruleProblem(f: RuleForm): string | null {
  if (!f.name.trim()) return "Give the rule a name."
  if (!f.contraAccountId) return "Choose the account these lines are booked to."
  const min = num(f.min), max = num(f.max)
  if ((min != null && Number.isNaN(min)) || (max != null && Number.isNaN(max))) return "Amounts must be numbers."
  if (!f.text.trim() && min == null && max == null) return "A rule needs some text to look for, or an amount range, or it would match every line."
  if (min != null && min < 0) return "The smallest amount cannot be below zero."
  if (min != null && max != null && min > max) return "The smallest amount is more than the largest."
  return null
}

export const ruleRequest = (f: RuleForm) => ({
  name: f.name.trim(), bankAccountId: f.bankAccountId || null, direction: f.direction, text: f.text.trim() || null, field: f.field,
  min: num(f.min), max: num(f.max), priority: Number(f.priority) || 100, contraAccountId: f.contraAccountId,
})

export function ruleSummary(r: Pick<Rule, "direction" | "text" | "field" | "min" | "max">): string {
  const parts: string[] = []
  if (r.direction === "IN") parts.push("money in"); else if (r.direction === "OUT") parts.push("money out")
  if (r.text) parts.push(`${r.field === "DESCRIPTION" ? "description" : r.field === "REFERENCE" ? "reference" : "description or reference"} contains “${r.text}”`)
  if (r.min != null && r.max != null) parts.push(`between ${money(r.min)} and ${money(r.max)}`)
  else if (r.min != null) parts.push(`at least ${money(r.min)}`)
  else if (r.max != null) parts.push(`at most ${money(r.max)}`)
  return parts.join(", ") || "any line"
}

// ── client queries ──────────────────────────────────────────────────────
export const QUERY_KINDS = [{ value: "MISSING_DOCUMENT", label: "Missing document" }, { value: "QUESTION", label: "Question" }]
export const kindText = (k: string) => QUERY_KINDS.find(x => x.value === k)?.label ?? k

export function queryProblem(kind: string, text: string): string | null {
  if (!QUERY_KINDS.some(k => k.value === kind)) return "Choose whether you are asking for a document or asking a question."
  if (text.trim().length < MIN_QUERY_TEXT) return `Say what you need (at least ${MIN_QUERY_TEXT} characters).`
  return null
}
export function answerProblem(answer: string, documentRef: string): string | null {
  return answer.trim().length >= 2 || documentRef.trim() ? null : "Record the client's answer or where the document is."
}

// ── handover ────────────────────────────────────────────────────────────
/** Null when the handover may be sent; otherwise what stands in the way (the same wording the server uses). */
export function handoverProblem(periodEnd: string, today: string, work: WorkRow | undefined, openHandover: boolean): string | null {
  if (!periodEnd) return "Choose the date the handover runs to."
  if (periodEnd > today) return "The handover date cannot be in the future."
  if (openHandover) return "A handover is already with the accountant. Wait for it to be accepted or returned."
  const u = work?.unreconciled ?? 0, d = work?.draftJournals ?? 0
  if (u > 0) return `${u} bank ${u === 1 ? "line is" : "lines are"} not categorised yet.`
  if (d > 0) return `${d} ${d === 1 ? "journal is" : "journals are"} still a draft. Post or discard them.`
  return null
}
export const returnProblem = (note: string) => (note.trim().length < MIN_RETURN_NOTE ? `Say what needs fixing (at least ${MIN_RETURN_NOTE} characters) so the bookkeeper can act on it.` : null)

export const monthName = (year: number, month: number) => new Date(year, month - 1, 1).toLocaleDateString("en-ZA", { month: "long", year: "numeric" })
