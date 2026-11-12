// Phase 8b: a suggested stop order for a route. A suggestion only; the planner applies it with the normal stop-order call.
export interface SuggestedStop { deliveryId: string; sequence: number; previousSequence: number; customerName: string | null; status: string; movable: boolean; moved: boolean; windowStart: string | null; windowEnd: string | null; hasPoint: boolean }
export interface SuggestedOrder { routeId: string; method: "STRAIGHT_LINE" | "WINDOWS_AND_DISTANCE"; changed: boolean; currentKm: number | null; suggestedKm: number | null; savingKm: number | null; stops: SuggestedStop[]; sitesWithoutPoint: string[]; note: string }

/** One sentence saying what the suggestion is. */
export function suggestionHeadline(o: Pick<SuggestedOrder, "method" | "changed" | "savingKm">): string {
  if (!o.changed) return o.method === "STRAIGHT_LINE" ? "The current order is already the shortest we can find." : "The current order already follows the delivery windows and the stored distances."
  if (o.method === "STRAIGHT_LINE") return o.savingKm != null && o.savingKm > 0 ? `This order is about ${o.savingKm.toFixed(1)} km shorter in a straight line.` : "This order is no shorter in a straight line but keeps the delivery windows in order."
  return "This order puts stops with delivery windows first, then the nearest sites."
}

/** True when there are at least two stops that can still move, so a suggestion is worth asking for. */
export const canSuggest = (stops: { status: string }[]): boolean => stops.filter(s => s.status === "SCHEDULED").length >= 2

/** The full list of stop ids in the suggested order, ready for the stop-order call. */
export const suggestedIds = (o: Pick<SuggestedOrder, "stops">): string[] => o.stops.map(s => s.deliveryId)

/** Names the stops that moved, for a short summary under the list. */
export const movedCount = (o: Pick<SuggestedOrder, "stops">): number => o.stops.filter(s => s.moved).length
