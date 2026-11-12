// Phase 8e (D03): shifts sent by a pump or POS system, held for a supervisor to accept or reject.
export type ImportReading = { pumpName: string; openingMeter: number; closingMeter: number; testLitres: number | null }
export type ImportShift = { source: string; externalId: string; attendantName: string; openedAt: string | null; closedAt: string | null; cash: number; card: number; account: number; other: number; fleetCard: number | null; notes: string | null; readings: ImportReading[] }
export type ImportView = { id: string; source: string; externalId: string; status: "PENDING" | "ACCEPTED" | "REJECTED"; receivedAt: string; problems: string[]; shift: ImportShift; decidedBy: string | null; decidedAt: string | null; decisionNote: string | null; shiftId: string | null }
export type KeyView = { id: string; name: string; keyPrefix: string; createdAt: string; lastUsedAt: string | null; revokedAt: string | null }
export type CreatedKey = { key: KeyView; apiKey: string }

export const STATUS_FILTERS: [string, string][] = [["PENDING", "Waiting for review"], ["ACCEPTED", "Accepted"], ["REJECTED", "Rejected"]]

export function litres(r: ImportReading): number { return Math.max(0, r.closingMeter - r.openingMeter - (r.testLitres ?? 0)) }
export function totalLitres(s: ImportShift): number { return s.readings.reduce((a, r) => a + litres(r), 0) }
export function totalTendered(s: ImportShift): number { return s.cash + s.card + s.account + s.other + (s.fleetCard ?? 0) }
export function statusLabel(s: string): string { return STATUS_FILTERS.find(f => f[0] === s)?.[1] ?? s }
