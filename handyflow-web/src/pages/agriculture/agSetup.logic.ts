// src/pages/agriculture/agSetup.logic.ts
//
// The "Getting started" checklist at the top of a new farm: what has been set up and what is next. Steps are worked out from
// counts of what already exists, so nothing is stored and a step ticks itself when the work is done by any route.

export type SetupTab = "areas" | "animals" | "inventory"

export interface SetupCounts { areas: number; species: number; animals: number; groups: number; stock: number }
export interface SetupStep { key: "areas" | "species" | "livestock" | "stock"; title: string; hint: string; done: boolean; tab: SetupTab | null; action: string | null }

export function setupSteps(c: SetupCounts): SetupStep[] {
  return [
    { key: "species", title: "Choose your species", hint: "Species are set up once for the whole business (cattle, sheep, broilers...). Add the ones you keep under Species.", done: c.species > 0, tab: null, action: null },
    { key: "areas", title: "Add your camps and houses", hint: "Production areas: the camps, paddocks, houses or ponds your animals and crops are kept in.", done: c.areas > 0, tab: "areas", action: "Open Production Areas" },
    { key: "livestock", title: "Bring in your animals", hint: "Add them one by one, or use Import CSV on the Animals tab to load a whole herd from a spreadsheet.", done: c.animals + c.groups > 0, tab: "animals", action: "Open Animals" },
    { key: "stock", title: "Add your stock", hint: "Feed, seed, chemicals and medicine. Use Import CSV on the Inventory tab to load opening stock from a spreadsheet.", done: c.stock > 0, tab: "inventory", action: "Open Inventory" },
  ]
}

/** The first step still to do, or null when everything is done. */
export function nextStep(steps: SetupStep[]): SetupStep | null {
  return steps.find(s => !s.done) ?? null
}

export function setupProgress(steps: SetupStep[]): string {
  return `${steps.filter(s => s.done).length} of ${steps.length} done`
}

export const dismissKey = (farmId: string) => `hf-ag-setup-dismissed-${farmId}`
