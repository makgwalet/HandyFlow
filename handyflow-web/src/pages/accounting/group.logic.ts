// Finance F26: groups of companies and consolidated statements.

export interface Member { tenantId: string; name: string; role: string; ownershipPercent: number; joinedAt: string }
export interface GroupInfo { id: string; name: string; myRole: "PARENT" | "SUBSIDIARY"; parentTenantId: string; parentName: string; members: Member[] }
export interface AccountRef { code: string; name: string; type: string }
export interface Mapping { id: string; tenantId: string; companyName: string; accountCode: string; groupCode: string; groupName: string | null }
export interface Elimination { id: string; name: string; tenantAId: string; companyA: string; accountCodeA: string; tenantBId: string; companyB: string; accountCodeB: string }
export interface Entity { tenantId: string; name: string; ownershipPercent: number }
export interface Line { code: string; name: string; type: string; amounts: number[]; elimination: number; consolidated: number }
export interface Report {
  from: string; to: string; entities: Entity[]; lines: Line[]; checks: { name: string; sideA: number; sideB: number; difference: number }[]
  income: number; expenses: number; profit: number; assets: number; liabilities: number; equity: number; balanceDifference: number; warnings: string[]
}

export const SECTIONS: { type: string; title: string }[] = [
  { type: "INCOME", title: "Income" },
  { type: "EXPENSE", title: "Expenses" },
  { type: "ASSET", title: "Assets" },
  { type: "LIABILITY", title: "Liabilities" },
  { type: "EQUITY", title: "Equity" },
  { type: "DIFFERENCE", title: "Intercompany differences" },
]

export const linesOf = (r: Report, type: string) => r.lines.filter(l => l.type === type)

export const money = (n: number | null | undefined) =>
  n == null ? "—" : `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/** Zero shows as a dash so a wide table stays readable. */
export const cellText = (n: number) => (Math.abs(n) < 0.005 ? "—" : money(n))

export function ownershipText(p: number): string {
  return `${Number.isInteger(p) ? p : p.toFixed(2).replace(/0+$/, "")}%`
}

/** Pairs where both companies have been chosen and differ, with an account each. */
export function ruleProblem(name: string, a: { tenant: string; code: string }, b: { tenant: string; code: string }): string | null {
  if (!name.trim()) return "Name the rule, for example Loan from head office."
  if (!a.tenant || !b.tenant || !a.code || !b.code) return "Choose both companies and both accounts."
  if (a.tenant === b.tenant) return "An intercompany rule needs two different companies."
  return null
}

export function mapProblem(tenant: string, account: string, groupCode: string): string | null {
  if (!tenant || !account) return "Choose the company and its account."
  if (!groupCode.trim()) return "Give the group line a code."
  if (!/^[A-Za-z0-9._-]{1,20}$/.test(groupCode.trim())) return "A group code uses letters, digits, dots, dashes and underscores (20 at most)."
  return null
}

/** Today's date at the end of the month's start: financial years in SA commonly start in March. */
export function yearStart(d: Date, startMonth = 3): string {
  const y = d.getMonth() + 1 >= startMonth ? d.getFullYear() : d.getFullYear() - 1
  return `${y}-${String(startMonth).padStart(2, "0")}-01`
}

/** Group codes already used, so the same line can be picked again for another company. */
export function knownGroupLines(mappings: Mapping[]): { code: string; name: string }[] {
  const seen = new Map<string, string>()
  for (const m of mappings) if (!seen.has(m.groupCode)) seen.set(m.groupCode, m.groupName ?? "")
  return [...seen.entries()].map(([code, name]) => ({ code, name })).sort((a, b) => a.code.localeCompare(b.code))
}
