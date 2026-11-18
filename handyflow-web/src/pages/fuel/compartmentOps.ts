// Tanker compartment view: lay each compartment's planned loads against its capacity and what it may carry. Display only.
import type { CompartmentRating } from "./eligibilityOps"

export interface CompartmentLoad { deliveryId: string; status: string; scheduledAt: string; customerName: string | null; fuelType: string; compartmentId: string; litres: number }
export interface CompartmentCell {
  rating: CompartmentRating; loads: CompartmentLoad[]; litres: number; percent: number; fuels: string[]
  over: boolean; mixed: boolean; notRated: string[]
}

/** One cell per compartment in order. over = planned litres above capacity; mixed = more than one fuel planned in it; notRated = planned fuels it is not rated for. */
export function layOut(ratings: CompartmentRating[], loads: CompartmentLoad[]): CompartmentCell[] {
  return [...ratings].sort((a, b) => a.number - b.number).map(rating => {
    const mine = loads.filter(l => l.compartmentId === rating.compartmentId)
    const litres = mine.reduce((a, l) => a + l.litres, 0)
    const fuels = [...new Set(mine.map(l => l.fuelType))]
    return {
      rating, loads: mine, litres, fuels,
      percent: rating.capacityLitres > 0 ? Math.min(100, (litres / rating.capacityLitres) * 100) : 0,
      over: litres > rating.capacityLitres, mixed: fuels.length > 1,
      notRated: rating.fuelTypes.length === 0 ? [] : fuels.filter(f => !rating.fuelTypes.includes(f)),
    }
  })
}

/** Plain-words problems with the plan, one line each, so nobody has to read the blocks to find them. */
export function problems(cells: CompartmentCell[]): string[] {
  const out: string[] = []
  for (const c of cells) {
    const n = `Compartment ${c.rating.number}`
    if (c.over) out.push(`${n} is planned above its capacity.`)
    if (c.mixed) out.push(`${n} has more than one fuel planned in it on the same day.`)
    if (c.notRated.length > 0) out.push(`${n} is not rated for ${c.notRated.join(", ")}.`)
  }
  return out
}

export const totalPlanned = (cells: CompartmentCell[]) => cells.reduce((a, c) => a + c.litres, 0)
export const totalCapacity = (cells: CompartmentCell[]) => cells.reduce((a, c) => a + c.rating.capacityLitres, 0)
