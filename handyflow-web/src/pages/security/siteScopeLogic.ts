// Which sites a user may see: wording and small checks only. The server decides who may see what and enforces it on every request.

export interface ScopeNamed { id: string; name: string }
export interface ScopeBranch { id: string; name: string; role: string }
/** `regions` is missing from older servers; treat that as none (see regionsOf). */
export interface ScopeView { restricted: boolean; branches: ScopeBranch[]; clients: ScopeNamed[]; sites: ScopeNamed[]; visibleSites: number; regions?: ScopeNamed[] }

export const regionsOf = (v: Pick<ScopeView, "regions">): ScopeNamed[] => v.regions ?? []

export const MAX_ITEMS = 200

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`

/** One line for a user's access. restricted=false means the whole company. */
export function scopeSummary(v: ScopeView): string {
  if (!v.restricted) return "Whole company: every site, current and future."
  const parts: string[] = []
  if (regionsOf(v).length) parts.push(plural(regionsOf(v).length, "region", "regions"))
  if (v.branches.length) parts.push(plural(v.branches.length, "branch", "branches"))
  if (v.clients.length) parts.push(plural(v.clients.length, "client", "clients"))
  if (v.sites.length) parts.push(plural(v.sites.length, "named site", "named sites"))
  return `Limited to ${plural(v.visibleSites, "site", "sites")}` + (parts.length ? ` (from ${parts.join(", ")})` : "") + "."
}

/** Shown to the signed-in user whose own access is limited. Null for everyone else. */
export function limitedBanner(v: ScopeView | undefined): string | null {
  if (!v || !v.restricted) return null
  return v.visibleSites === 0
    ? "Your access is limited and covers no sites yet. Ask an administrator to give you access to a site."
    : `Your access is limited to ${plural(v.visibleSites, "site", "sites")}. Company-wide pages and figures are not shown to you.`
}

export const toggleId = (ids: string[], id: string): string[] => ids.includes(id) ? ids.filter(x => x !== id) : [...ids, id]

export const scopeBody = (clientIds: string[], siteIds: string[]) => ({ clientIds, siteIds })

/** Same members, any order. */
export function sameIds(a: string[], b: string[]): boolean {
  return a.length === b.length && [...a].sort().every((x, i) => x === [...b].sort()[i])
}

export function scopeChanged(view: ScopeView, clientIds: string[], siteIds: string[]): boolean {
  return !sameIds(view.clients.map(c => c.id), clientIds) || !sameIds(view.sites.map(s => s.id), siteIds)
}

/** Why the signed-in user cannot change this user's access, or null when they can. The server checks the same. */
export function editBlock(actorId: string | undefined, targetId: string, actor: ScopeView | undefined): string | null {
  if (actorId && actorId === targetId) return "You cannot change your own site access. Ask another administrator."
  if (actor?.restricted) return "Only someone with access to the whole company can change site access."
  return null
}

/** "branches", "regions" or "branches and regions": the limits a user keeps when the clients and sites are removed. */
function heldText(v: ScopeView): string {
  const b = v.branches.length > 0, r = regionsOf(v).length > 0
  return b && r ? "branches and regions" : r ? "regions" : "branches"
}

/** Narrowing from whole company to a few sites is the step people regret: say what it does. */
export function saveWarning(view: ScopeView, clientIds: string[], siteIds: string[]): string | null {
  const limiting = clientIds.length + siteIds.length > 0
  const held = view.branches.length + regionsOf(view).length > 0
  if (!view.restricted && limiting) return "This user can see every site now. Saving limits them to the clients and sites chosen here."
  if (view.restricted && !held && !limiting) return "Saving removes every limit: this user will see the whole company."
  if (view.restricted && held && !limiting) return `Saving removes the clients and sites chosen here. The user stays limited to their ${heldText(view)}.`
  return null
}

export function overLimit(clientIds: string[], siteIds: string[]): string | null {
  if (clientIds.length > MAX_ITEMS) return `Choose at most ${MAX_ITEMS} clients.`
  if (siteIds.length > MAX_ITEMS) return `Choose at most ${MAX_ITEMS} sites.`
  return null
}

type Err = { response?: { status?: number; data?: { message?: unknown; errorCode?: unknown; code?: unknown } } } | null | undefined

/** What a limited user is told when the server refuses because of their access; null for any other error. */
export function scopeErrorText(e: unknown): string | null {
  const r = (e as Err)?.response
  const code = r?.data?.errorCode ?? r?.data?.code
  const msg = typeof r?.data?.message === "string" && r.data.message.trim() ? r.data.message : null
  if (code === "SITE_REQUIRED") return msg ?? "Choose a site first. Your access is limited to some sites."
  if (code === "SITE_SCOPE_LIMITED") return msg ?? "This needs access to the whole company. Your access is limited to some sites."
  if (code === "SITE_NOT_FOUND") return "That site is not available to you."
  return null
}

/** The server's message if it gave one, a scope message for scope refusals, otherwise the fallback. */
export function accessErrorText(e: unknown, fallback: string): string {
  const s = scopeErrorText(e)
  if (s) return s
  const m = (e as Err)?.response?.data?.message
  return typeof m === "string" && m.trim() ? m : fallback
}
