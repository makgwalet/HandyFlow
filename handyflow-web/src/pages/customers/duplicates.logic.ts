// Duplicate finder: types and small rules. Pure functions.
export interface DupMember { id: string; name: string; email: string | null; phone: string | null; taxNumber: string | null; customerType: string; createdAt: string }
export interface DupGroup { customers: DupMember[]; reasons: string[] }
export interface Duplicates { groups: DupGroup[]; scanned: number; truncated: boolean }

/** A stable key for a group, so a row keeps its place and its open confirm while the list reloads. */
export const groupKey = (g: DupGroup) => g.customers.map(c => c.id).sort().join("|")
/** Oldest record first: that one is usually the original worth keeping. */
export const oldestFirst = (g: DupGroup): DupMember[] => [...g.customers].sort((a, b) => a.createdAt.localeCompare(b.createdAt))
export const summaryText = (d: Duplicates) =>
  d.groups.length === 0 ? `No likely duplicates among ${d.scanned} customers.` : `${d.groups.length} possible duplicate ${d.groups.length === 1 ? "group" : "groups"} among ${d.scanned} customers.`
/** Fields that differ between the records, so the person can see at a glance what is not the same. */
export function differingFields(g: DupGroup): ("email" | "phone" | "taxNumber")[] {
  return (["email", "phone", "taxNumber"] as const).filter(f => new Set(g.customers.map(c => (c[f] ?? "").trim().toLowerCase())).size > 1)
}
