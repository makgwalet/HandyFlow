// Tank-gauge feed: levels sent by a gauge system, held for a person to accept (it becomes a dip reading) or reject.
export type GaugeView = {
  id: string; source: string; externalId: string; status: "PENDING" | "ACCEPTED" | "REJECTED"; receivedAt: string; problems: string[]
  tankLabel: string; tankId: string | null; tankName: string | null; litres: number; readAt: string; senderName: string | null
  decidedBy: string | null; decidedAt: string | null; decisionNote: string | null; dipReadingId: string | null
}
export type TankAlias = { id: string; source: string; alias: string; tankId: string; tankName: string; createdAt: string }

export const GAUGE_STATUS: [string, string][] = [["PENDING", "Waiting for review"], ["ACCEPTED", "Accepted"], ["REJECTED", "Rejected"]]

export function isGauge(x: any): x is GaugeView { return !!x && typeof x.id === "string" && typeof x.litres === "number" && typeof x.tankLabel === "string" }
export function onlyGauges(x: unknown): GaugeView[] { return Array.isArray(x) ? x.filter(isGauge) : [] }
export function onlyAliases(x: unknown): TankAlias[] { return Array.isArray(x) ? x.filter((a: any) => a && typeof a.id === "string" && typeof a.alias === "string" && typeof a.tankName === "string") : [] }

/** A reading can be accepted only while it is waiting and its tank is known. */
export function canAccept(g: GaugeView): boolean { return g.status === "PENDING" && !!g.tankId }

/** Null when the label can be saved. */
export function tankAliasProblem(source: string, alias: string, tankId: string, existing: TankAlias[]): string | null {
  if (!source.trim()) return "Enter the source, as the sender names itself."
  if (!alias.trim()) return "Enter the tank label the sender uses."
  if (!tankId) return "Choose the Fuel tank."
  const s = source.trim().toLowerCase(), a = alias.trim().toLowerCase()
  return existing.some(x => x.source === s && x.alias === a) ? "That label is already mapped for this source." : null
}
