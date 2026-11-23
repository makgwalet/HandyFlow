// The list of people whose access is limited to some sites: wording and small checks only. The server decides who is limited and how many sites that leaves.

export interface LimitedUser { userId: string; name: string; email: string; status: string; branches: number; clients: number; sites: number; visibleSites: number; regions?: number }

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`

/** How the person is limited, in words: "2 branches, 1 client, 3 named sites". */
export function limitText(u: Pick<LimitedUser, "branches" | "clients" | "sites"> & { regions?: number }): string {
  const parts: string[] = []
  if (u.regions) parts.push(plural(u.regions, "region", "regions"))
  if (u.branches) parts.push(plural(u.branches, "branch", "branches"))
  if (u.clients) parts.push(plural(u.clients, "client", "clients"))
  if (u.sites) parts.push(plural(u.sites, "named site", "named sites"))
  return parts.join(", ")
}

/** What that leaves them: a limited user who can see 0 sites sees nothing, which is usually a mistake worth showing. */
export const reachText = (u: Pick<LimitedUser, "visibleSites">): string => u.visibleSites === 0 ? "Sees no sites" : plural(u.visibleSites, "site", "sites")
export const seesNothing = (u: Pick<LimitedUser, "visibleSites">): boolean => u.visibleSites === 0

/** Name or email contains the text, ignoring case and surrounding spaces. Empty text keeps everyone. */
export function filterUsers<T extends Pick<LimitedUser, "name" | "email">>(users: T[], text: string): T[] {
  const t = text.trim().toLowerCase()
  return t ? users.filter(u => u.name.toLowerCase().includes(t) || u.email.toLowerCase().includes(t)) : users
}

export function summariseUsers(users: Pick<LimitedUser, "visibleSites">[]): { total: number; seeingNothing: number } {
  return { total: users.length, seeingNothing: users.filter(seesNothing).length }
}

/** A user who is not active keeps their limits on record but cannot sign in; say so rather than leave it unexplained. */
export const statusNote = (status: string): string | null => status === "ACTIVE" ? null : status.charAt(0) + status.slice(1).toLowerCase()
