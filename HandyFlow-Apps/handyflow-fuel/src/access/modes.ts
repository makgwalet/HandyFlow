import type { Access } from "@/api/access";

/**
 * One app, several modes. What a person sees comes from the permissions on their sign-in and the divisions their company
 * uses, never from a setting on the phone. The server enforces every permission again; this only decides what to show.
 */
export type ModeId = "driver" | "forecourt" | "truckstop" | "depot" | "manager";

export interface Mode {
  id: ModeId;
  title: string;
  blurb: string;
  /** False while the screens for this mode are not built yet. The tile still shows so people know it is coming. */
  available: boolean;
}

interface Rule extends Mode {
  anyOf: string[];
  /** Divisions (FuelDivision names) this mode belongs to. Empty: not tied to one. */
  divisions: string[];
}

const RULES: Rule[] = [
  { id: "driver", title: "My loads", blurb: "Start a trip, check in, record the delivery, report a failed delivery, add photos.", available: true,
    anyOf: ["FUEL_DRIVER", "FUEL_MANAGE", "FUEL_TRANSPORT_MANAGE"], divisions: ["DISTRIBUTION"] },
  { id: "forecourt", title: "Forecourt", blurb: "Shift, pump readings and takings.", available: false,
    anyOf: ["FUEL_FORECOURT", "FUEL_FORECOURT_MANAGE", "FUEL_MANAGE"], divisions: ["RETAIL_FORECOURT"] },
  { id: "truckstop", title: "Truck stop", blurb: "Bays, stays and driver tabs.", available: false,
    anyOf: ["FUEL_TRUCKSTOP_MANAGE", "FUEL_MANAGE"], divisions: ["TRUCK_STOP"] },
  { id: "depot", title: "Depot", blurb: "Tank levels, dip readings and receiving fuel.", available: true,
    anyOf: ["FUEL_STOCK_MANAGE", "FUEL_OPERATIONS_MANAGE", "FUEL_DISTRIBUTION_MANAGE", "FUEL_MANAGE"], divisions: ["WHOLESALE_SUPPLY", "DISTRIBUTION"] },
  { id: "manager", title: "Overview", blurb: "This month's fuel, open deliveries and what needs attention.", available: true,
    anyOf: ["FUEL_READ", "FUEL_MANAGE"], divisions: [] },
];

export const hasFuelAccess = (permissions: readonly string[]): boolean => permissions.some((p) => p.startsWith("FUEL_"));

/** Whether a mode's divisions are open to this company and this person. Unknown access (not fetched yet) hides nothing. */
function divisionsOpen(divisions: string[], access: Access | null | undefined): boolean {
  if (!access || divisions.length === 0) return true;
  const companyHas = access.legacyMode || divisions.some((d) => access.activeDivisions.includes(d));
  const personHas = !access.scope.restricted || access.scope.divisions.length === 0 || divisions.some((d) => access.scope.divisions.includes(d));
  return companyHas && personHas;
}

export function modesFor(permissions: readonly string[], access?: Access | null): Mode[] {
  const have = new Set(permissions);
  return RULES.filter((r) => r.anyOf.some((p) => have.has(p)) && divisionsOpen(r.divisions, access))
    .map(({ anyOf: _a, divisions: _d, ...mode }) => mode);
}

/** The mode to open straight into when there is exactly one usable choice (a driver goes to their loads). */
export function defaultMode(modes: readonly Mode[]): ModeId | null {
  const usable = modes.filter((m) => m.available);
  return usable.length === 1 && modes.length === 1 ? (usable[0]?.id ?? null) : null;
}
