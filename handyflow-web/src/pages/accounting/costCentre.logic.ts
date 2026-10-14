// Finance F24: cost centres, departments, branches and projects.

export interface CostCentre {
  id: string; code: string; name: string; kind: string; parentId: string | null; active: boolean; lineCount: number
}

export const KINDS: { value: string; label: string }[] = [
  { value: "COST_CENTRE", label: "Cost centre" },
  { value: "DEPARTMENT", label: "Department" },
  { value: "BRANCH", label: "Branch" },
  { value: "PROJECT", label: "Project" },
]

export const kindLabel = (k: string | null | undefined) => KINDS.find(x => x.value === k)?.label ?? "—"

export const labelOf = (c: Pick<CostCentre, "code" | "name">) => `${c.code} — ${c.name}`

/** Parents before their children, children sorted by code, each with its depth. Orphans (unknown parent) are shown at the top level. */
export function treeOrder(list: CostCentre[]): { c: CostCentre; depth: number }[] {
  const ids = new Set(list.map(c => c.id))
  const byParent = new Map<string | null, CostCentre[]>()
  for (const c of list) {
    const p = c.parentId && ids.has(c.parentId) ? c.parentId : null
    byParent.set(p, [...(byParent.get(p) ?? []), c])
  }
  const out: { c: CostCentre; depth: number }[] = []
  const seen = new Set<string>()
  const walk = (p: string | null, depth: number) => {
    for (const c of (byParent.get(p) ?? []).slice().sort((a, b) => a.code.localeCompare(b.code))) {
      if (seen.has(c.id)) continue
      seen.add(c.id)
      out.push({ c, depth })
      walk(c.id, depth + 1)
    }
  }
  walk(null, 0)
  return out
}

/** Centres that can be the parent of this one: not itself, not anything beneath it. */
export function parentChoices(list: CostCentre[], selfId: string | null): CostCentre[] {
  if (!selfId) return list.filter(c => c.active)
  const banned = new Set<string>([selfId])
  let grew = true
  while (grew) {
    grew = false
    for (const c of list) if (c.parentId && banned.has(c.parentId) && !banned.has(c.id)) { banned.add(c.id); grew = true }
  }
  return list.filter(c => !banned.has(c.id))
}

export const money = (n: number | null | undefined) =>
  n == null ? "—" : `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/** First day of the financial year containing d, for a year starting in month startMonth (1 to 12). */
export function yearStart(d: Date, startMonth = 3): string {
  const y = d.getMonth() + 1 >= startMonth ? d.getFullYear() : d.getFullYear() - 1
  return `${y}-${String(startMonth).padStart(2, "0")}-01`
}

/** Margin as a percent of income, or null when there is no income. */
export function marginPercent(income: number, result: number): number | null {
  return income > 0 ? Math.round((result / income) * 1000) / 10 : null
}
