// A user's branch access: wording and small checks only. The server decides who may change what and enforces it.
// Branch access limits a user to the sites of the branches chosen (Can view or Manager). Clients and sites are set separately.
import { regionsOf, type ScopeBranch, type ScopeView } from "./siteScopeLogic"

export type BranchChoice = "NONE" | "VIEWER" | "MANAGER"
export interface BranchInfo { id: string; name: string; region?: string | null; active?: boolean }
export interface BranchRow { id: string; name: string; region: string | null; off: boolean }

export const MAX_BRANCHES = 500
export const CHOICE_LABEL: Record<BranchChoice, string> = { NONE: "No access", VIEWER: "Can view", MANAGER: "Manager" }
export const CHOICES: BranchChoice[] = ["NONE", "VIEWER", "MANAGER"]

const asChoice = (role: string): BranchChoice => role === "MANAGER" ? "MANAGER" : "VIEWER"

/** What the user has now, keyed by branch id. */
export function choicesFrom(current: ScopeBranch[]): Record<string, BranchChoice> {
  const out: Record<string, BranchChoice> = {}
  for (const b of current) out[b.id] = asChoice(b.role)
  return out
}

/**
 * The branches to list: every active branch, then any branch the user still has that is no longer active (marked off), so that it can be seen
 * and removed. A switched-off branch is never sent to the server, which refuses them; saving therefore removes it.
 */
export function branchRows(all: BranchInfo[], current: ScopeBranch[]): BranchRow[] {
  const rows: BranchRow[] = all.filter(b => b.active !== false).map(b => ({ id: b.id, name: b.name, region: b.region ?? null, off: false }))
  const known = new Set(rows.map(r => r.id))
  for (const c of current) if (!known.has(c.id)) rows.push({ id: c.id, name: c.name, region: null, off: true })
  return rows.sort((a, b) => a.name.localeCompare(b.name))
}

export const setChoice = (choices: Record<string, BranchChoice>, id: string, c: BranchChoice): Record<string, BranchChoice> => ({ ...choices, [id]: c })

/** Only branches with access, only switched-on ones, in name order. */
export function branchBody(choices: Record<string, BranchChoice>, rows: BranchRow[]) {
  return { branches: rows.filter(r => !r.off && (choices[r.id] ?? "NONE") !== "NONE").map(r => ({ branchId: r.id, role: choices[r.id] as "VIEWER" | "MANAGER" })) }
}

/** Whether what would be sent differs from what the user has. A switched-off branch they still hold counts: saving removes it. */
export function branchesChanged(current: ScopeBranch[], choices: Record<string, BranchChoice>, rows: BranchRow[]): boolean {
  const sent = branchBody(choices, rows).branches
  if (sent.length !== current.length) return true
  const have = new Map(current.map(c => [c.id, asChoice(c.role)]))
  return sent.some(s => have.get(s.branchId) !== s.role)
}

export function overLimit(choices: Record<string, BranchChoice>): string | null {
  return Object.values(choices).filter(c => c !== "NONE").length > MAX_BRANCHES ? `Choose at most ${MAX_BRANCHES} branches.` : null
}

/** Narrowing from the whole company is the step people regret, and so is lifting the last limit: say what saving does. */
export function branchWarning(view: ScopeView, sent: number): string | null {
  const regions = regionsOf(view).length
  const otherLimits = view.clients.length + view.sites.length + regions > 0
  if (!view.restricted && sent > 0) return "This user can see every site now. Saving limits them to the sites of the branches chosen here."
  if (view.restricted && view.branches.length > 0 && sent === 0 && !otherLimits) return "Saving removes the last limit: this user will see the whole company."
  if (view.restricted && view.branches.length > 0 && sent === 0 && otherLimits && regions === 0) return "Saving removes the branches. The user stays limited to their clients and named sites."
  if (view.restricted && view.branches.length > 0 && sent === 0 && otherLimits) {
    const kept = [regions > 0 ? "regions" : "", view.clients.length > 0 ? "clients" : "", view.sites.length > 0 ? "named sites" : ""].filter(Boolean)
    return `Saving removes the branches. The user stays limited to their ${kept.length === 3 ? "regions, clients and named sites" : kept.join(" and ")}.`
  }
  return null
}

export function branchSummary(rows: BranchRow[], choices: Record<string, BranchChoice>): string {
  const n = rows.filter(r => !r.off && (choices[r.id] ?? "NONE") !== "NONE").length
  return n === 0 ? "No branch access." : `Access through ${n} ${n === 1 ? "branch" : "branches"}.`
}
