// A user's region access: wording and small checks only. The server decides who may change what and enforces it.
// A region brings in every branch it holds, including branches added later.
import { regionsOf, sameIds, type ScopeView } from "./siteScopeLogic"

export interface RegionInfo { id: string; name: string; description?: string | null; branchIds: string[]; users: number }

export const MAX_REGIONS = 500

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`

/** The regions the user holds now. */
export const selectedFrom = (view: ScopeView): string[] => regionsOf(view).map(r => r.id)

export const regionBody = (ids: string[]) => ({ regionIds: ids })

export const regionsChanged = (view: ScopeView, ids: string[]): boolean => !sameIds(selectedFrom(view), ids)

export const regionOverLimit = (ids: string[]): string | null => ids.length > MAX_REGIONS ? `Choose at most ${MAX_REGIONS} regions.` : null

/**
 * The regions to list: every region, then any the user holds that the list did not return (kept by name so it can be seen and removed).
 * Sorted by name.
 */
export function regionRows(all: RegionInfo[], view: ScopeView): RegionInfo[] {
  const known = new Set(all.map(r => r.id))
  const extra = regionsOf(view).filter(r => !known.has(r.id)).map(r => ({ id: r.id, name: r.name, description: null, branchIds: [] as string[], users: 0 }))
  return [...all, ...extra].sort((a, b) => a.name.localeCompare(b.name))
}

/** What saving does to the user's overall reach. Narrowing from the whole company, and lifting the last limit, are the steps people regret. */
export function regionWarning(view: ScopeView, ids: string[]): string | null {
  const otherLimits = view.branches.length + view.clients.length + view.sites.length
  if (!view.restricted && ids.length > 0) return "This user can see every site now. Saving limits them to the sites of the branches in the regions chosen here."
  if (view.restricted && regionsOf(view).length > 0 && ids.length === 0 && otherLimits === 0) return "Saving removes the last limit: this user will see the whole company."
  if (view.restricted && regionsOf(view).length > 0 && ids.length === 0) {
    const kept = [view.branches.length > 0 ? "branches" : "", view.clients.length > 0 ? "clients" : "", view.sites.length > 0 ? "named sites" : ""].filter(Boolean)
    return `Saving removes the regions. The user stays limited to their ${kept.length === 3 ? "branches, clients and named sites" : kept.join(" and ")}.`
  }
  return null
}

/** A region with no branches gives no sites. Said plainly when every chosen region is empty and nothing else gives the user sites. */
export function emptyRegionNote(rows: RegionInfo[], ids: string[], view: ScopeView): string | null {
  if (ids.length === 0) return null
  const chosen = rows.filter(r => ids.includes(r.id))
  if (chosen.length === 0 || chosen.some(r => r.branchIds.length > 0)) return null
  if (view.branches.length + view.clients.length + view.sites.length > 0) return null
  return chosen.length === 1 ? "This region holds no branches yet, so this user would see no sites." : "These regions hold no branches yet, so this user would see no sites."
}

export function regionSummary(ids: string[]): string {
  return ids.length === 0 ? "No region access." : `Access through ${plural(ids.length, "region", "regions")}.`
}

export const toggle = (ids: string[], id: string): string[] => ids.includes(id) ? ids.filter(x => x !== id) : [...ids, id]
