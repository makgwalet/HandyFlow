// Phase 8c (G01): limits on which divisions and depots a user can use. No limits means the user can use everything they have permission for.
export const DIVISION_CHOICES: { id: string; label: string }[] = [
  { id: "WHOLESALE_SUPPLY", label: "Wholesale & Supply" },
  { id: "DISTRIBUTION", label: "Distribution" },
  { id: "RETAIL_FORECOURT", label: "Retail & Forecourt" },
  { id: "TRUCK_STOP", label: "Truck Stop & Driver Services" },
]

export type UserScope = { userId: string; divisions: string[]; depotIds: string[] }
export type UserOption = { id: string; name: string }

export function toggle<T>(list: T[], v: T): T[] { return list.includes(v) ? list.filter(x => x !== v) : [...list, v] }

export function describeScope(s: { divisions: string[]; depotIds: string[] } | undefined, depotNames: Record<string, string>): string {
  if (!s || (s.divisions.length === 0 && s.depotIds.length === 0)) return "No limits"
  const parts: string[] = []
  if (s.divisions.length) parts.push(s.divisions.map(d => DIVISION_CHOICES.find(c => c.id === d)?.label ?? d).join(", "))
  if (s.depotIds.length) parts.push(s.depotIds.map(d => depotNames[d] ?? "Unknown depot").join(", "))
  return parts.join(" · ")
}

/** Users come from the identity list; when the caller cannot read it, fall back to the ids that already have limits. */
export function userLabel(id: string, users: UserOption[]): string { return users.find(u => u.id === id)?.name ?? `User ${id.slice(0, 8)}` }
