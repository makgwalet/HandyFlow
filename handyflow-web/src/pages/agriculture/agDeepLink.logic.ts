// src/pages/agriculture/agDeepLink.logic.ts
// What /agriculture/farms?farm=<id>[&animal=<id>|&group=<id>|&tab=<tab>] opens. The names match AgActionUrls on the server,
// which is where notifications get these links. Anything that is not a UUID or a known tab is ignored, never trusted.

export type FarmTab = "animals" | "groups" | "areas" | "enterprises" | "inventory" | "crops" | "seasons" | "costs" | "registers"
const TABS: FarmTab[] = ["animals", "groups", "areas", "enterprises", "inventory", "crops", "seasons", "costs", "registers"]
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export interface FarmLink { farmId: string; tab: FarmTab | null; animalId: string | null; groupId: string | null }

const uuid = (v: string | null): string | null => (v && UUID.test(v) ? v.toLowerCase() : null)

/** The farm link in a query string ("?farm=…"), or null when there is no valid farm. An animal wins over a group. */
export function parseFarmLink(search: string): FarmLink | null {
  const p = new URLSearchParams(search)
  const farmId = uuid(p.get("farm"))
  if (!farmId) return null
  const animalId = uuid(p.get("animal"))
  const groupId = animalId ? null : uuid(p.get("group"))
  const tab = p.get("tab") as FarmTab | null
  return { farmId, animalId, groupId, tab: tab && TABS.includes(tab) ? tab : null }
}
