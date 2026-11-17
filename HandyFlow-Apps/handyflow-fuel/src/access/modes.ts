/**
 * One app, several modes. What a person sees comes from the permissions on their sign-in, never from a setting on the phone.
 * The server enforces every permission again; this only decides what to show.
 */
export type ModeId = "driver" | "forecourt" | "truckstop" | "depot" | "manager";

export interface Mode {
  id: ModeId;
  title: string;
  blurb: string;
  /** False while the screens for this mode are not built yet. The tile still shows so people know it is coming. */
  available: boolean;
}

interface Rule { id: ModeId; title: string; blurb: string; available: boolean; anyOf: string[] }

const RULES: Rule[] = [
  { id: "driver", title: "My loads", blurb: "Start a trip, record the delivery, report a failed delivery, add photos.", available: true,
    anyOf: ["FUEL_DRIVER", "FUEL_MANAGE", "FUEL_TRANSPORT_MANAGE"] },
  { id: "forecourt", title: "Forecourt", blurb: "Shift, pump readings and takings.", available: false,
    anyOf: ["FUEL_FORECOURT", "FUEL_FORECOURT_MANAGE", "FUEL_MANAGE"] },
  { id: "truckstop", title: "Truck stop", blurb: "Bays, stays and driver tabs.", available: false,
    anyOf: ["FUEL_TRUCKSTOP_MANAGE", "FUEL_MANAGE"] },
  { id: "depot", title: "Depot", blurb: "Tank dips, receipts and loading.", available: false,
    anyOf: ["FUEL_STOCK_MANAGE", "FUEL_OPERATIONS_MANAGE", "FUEL_DISTRIBUTION_MANAGE", "FUEL_MANAGE"] },
  { id: "manager", title: "Overview", blurb: "Stock levels, deliveries and alerts.", available: false,
    anyOf: ["FUEL_READ", "FUEL_MANAGE"] },
];

export const hasFuelAccess = (permissions: readonly string[]): boolean => permissions.some((p) => p.startsWith("FUEL_"));

export function modesFor(permissions: readonly string[]): Mode[] {
  const have = new Set(permissions);
  return RULES.filter((r) => r.anyOf.some((p) => have.has(p))).map(({ anyOf: _anyOf, ...mode }) => mode);
}

/** The mode to open straight into when there is exactly one usable choice (a driver goes to their loads). */
export function defaultMode(modes: readonly Mode[]): ModeId | null {
  const usable = modes.filter((m) => m.available);
  return usable.length === 1 && modes.length === 1 ? (usable[0]?.id ?? null) : null;
}
