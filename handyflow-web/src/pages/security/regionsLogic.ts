// Regions screen: wording and small checks only. The server enforces every rule (names, who may change regions, what can be deleted).
import type { RegionInfo } from "./regionAccessLogic"

export const NAME_MAX = 100
const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`

/** The name as the server will keep it: ends trimmed, runs of spaces made one. */
export const cleanName = (s: string): string => s.trim().replace(/\s+/g, " ")

/** Why this name cannot be used, or null. `selfId` is the region being renamed, which may keep its own name. */
export function nameProblem(raw: string, regions: Pick<RegionInfo, "id" | "name">[], selfId?: string): string | null {
  const n = cleanName(raw)
  if (!n) return "Give the region a name."
  if (n.length > NAME_MAX) return `A region name can be at most ${NAME_MAX} characters.`
  if (regions.some(r => r.id !== selfId && r.name.trim().toLowerCase() === n.toLowerCase())) return "A region with that name already exists."
  return null
}

export const regionLine = (r: Pick<RegionInfo, "branchIds" | "users">): string =>
  `${plural(r.branchIds.length, "branch", "branches")} · ${r.users === 0 ? "no users limited to it" : plural(r.users, "user", "users") + " limited to it"}`

/** A region that users are limited to cannot be deleted: say why before the server has to. */
export function deleteBlock(r: Pick<RegionInfo, "users">): string | null {
  return r.users > 0 ? `${plural(r.users, "user is", "users are")} limited to this region. Take it off them first.` : null
}

/** The region a branch is in now, or "". */
export const regionOfBranch = (regions: Pick<RegionInfo, "id" | "branchIds">[], branchId: string): string => regions.find(r => r.branchIds.includes(branchId))?.id ?? ""

/** Moving a branch changes what people limited to the old and the new region can see. Said before saving; null when nobody is affected. */
export function moveWarning(regions: Pick<RegionInfo, "id" | "name" | "users" | "branchIds">[], branchId: string, toId: string): string | null {
  const fromId = regionOfBranch(regions, branchId)
  if (fromId === toId) return null
  const from = regions.find(r => r.id === fromId), to = regions.find(r => r.id === toId)
  const parts: string[] = []
  if (to && to.users > 0) parts.push(`${plural(to.users, "user", "users")} limited to ${to.name} will start seeing this branch's sites`)
  if (from && from.users > 0) parts.push(`${plural(from.users, "user", "users")} limited to ${from.name} will stop seeing them`)
  return parts.length ? parts.join("; ") + "." : null
}
